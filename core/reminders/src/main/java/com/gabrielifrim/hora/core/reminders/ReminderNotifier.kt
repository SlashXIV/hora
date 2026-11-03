package com.gabrielifrim.hora.core.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.reminder.DueReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ce que l'application fournit au moteur : ouvrir un événement, ouvrir l'alarme
 * plein écran. Le module ne connaît pas les écrans.
 */
interface ReminderLauncher {
    fun openEvent(context: Context, eventId: Long, occurrence: Long): Intent
    fun fullScreenAlarm(context: Context, eventId: Long, occurrence: Long): Intent
    fun openApp(context: Context): Intent
}

@Singleton
class ReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val launcher: ReminderLauncher,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val locale = Locale.forLanguageTag(context.getString(R.string.reminders_locale))

    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reminders_hint)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, context.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alarms_hint)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                enableVibration(true)
            },
        )
    }

    /** Présente un rappel ; sans autorisation de notifier, il n'y a rien à faire de plus ici. */
    fun show(due: DueReminder, late: Boolean) {
        ensureChannels()
        val id = notificationId(due.event.id, due.occurrence)
        val event = due.event
        val text = listOf(timeText(event.timing), event.location).filter { it.isNotBlank() }.joinToString(" · ")
        val title = when {
            late -> context.getString(R.string.reminder_missed, event.title)
            due.isDeparture -> context.getString(R.string.reminder_leave, event.title)
            else -> context.getString(R.string.reminder_title, event.title, untilText(event.timing))
        }
        val open = PendingIntent.getActivity(context, id, launcher.openEvent(context, event.id, due.occurrence), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, if (due.alarm) CHANNEL_ALARMS else CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(if (due.alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.snooze_10), snooze(due, SnoozeReceiver.TEN_MINUTES, id))
            .addAction(0, context.getString(R.string.snooze_hour), snooze(due, SnoozeReceiver.ONE_HOUR, id))
            .addAction(0, context.getString(R.string.snooze_morning), snooze(due, SnoozeReceiver.TOMORROW_MORNING, id))
        if (due.alarm) {
            val fullScreen = PendingIntent.getActivity(
                context, id, launcher.fullScreenAlarm(context, event.id, due.occurrence),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(fullScreen, true).setOngoing(true)
                .addAction(0, context.getString(R.string.alarm_stop), snooze(due, SnoozeReceiver.STOP, id))
        }
        if (ReminderPermissions.canNotify(context)) {
            @Suppress("MissingPermission") // vérifié juste au-dessus
            manager.notify(id, builder.build())
        }
    }

    fun cancel(eventId: Long, occurrence: Long) = manager.cancel(notificationId(eventId, occurrence))

    fun openAppIntent(): PendingIntent = PendingIntent.getActivity(context, 1, launcher.openApp(context), PendingIntent.FLAG_IMMUTABLE)

    private fun snooze(due: DueReminder, kind: String, requestBase: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestBase * 5 + SnoozeReceiver.ACTIONS.indexOf(kind),
        Intent(context, SnoozeReceiver::class.java)
            .setAction(kind)
            .putExtra(SnoozeReceiver.EXTRA_EVENT, due.event.id)
            .putExtra(SnoozeReceiver.EXTRA_OCCURRENCE, due.occurrence)
            .putExtra(SnoozeReceiver.EXTRA_ALARM, due.alarm),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun timeText(timing: Timing): String = when (timing) {
        is Timing.AllDay -> context.getString(R.string.reminder_all_day)
        is Timing.Timed -> {
            val zone = ZoneId.systemDefault()
            val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
            context.getString(R.string.reminder_time_range, fmt.format(timing.start.atZone(zone)), fmt.format(timing.end.atZone(zone)))
        }
    }

    /** « dans 1 h », « dans 10 min », « maintenant », « demain ». */
    private fun untilText(timing: Timing): String {
        val start = when (timing) {
            is Timing.Timed -> timing.start
            is Timing.AllDay -> timing.first.atStartOfDay(ZoneId.systemDefault()).toInstant()
        }
        val minutes = Duration.between(Instant.now(), start).toMinutes()
        return when {
            minutes <= 0 -> context.getString(R.string.until_now)
            minutes < 60 -> context.resources.getQuantityString(R.plurals.until_minutes, minutes.toInt(), minutes.toInt())
            minutes < 24 * 60 -> context.resources.getQuantityString(R.plurals.until_hours, (minutes / 60).toInt(), (minutes / 60).toInt())
            else -> context.resources.getQuantityString(R.plurals.until_days, (minutes / (24 * 60)).toInt(), (minutes / (24 * 60)).toInt())
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_ALARMS = "alarms"

        fun notificationId(eventId: Long, occurrence: Long): Int = (eventId * 31 + occurrence).hashCode()
    }
}
