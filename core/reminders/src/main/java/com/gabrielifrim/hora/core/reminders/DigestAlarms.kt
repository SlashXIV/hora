package com.gabrielifrim.hora.core.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gabrielifrim.hora.core.domain.calendar.TomorrowDigest
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.AlmanacRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Le résumé du lendemain : chaque soir à l'heure choisie, ce qui attend demain. Une
 * seule alarme, réarmée à chaque passage, au démarrage et quand le réglage change.
 */
@Singleton
class DigestAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val events: EventRepository,
    private val almanac: AlmanacRepository,
    private val notifier: ReminderNotifier,
) {
    suspend fun run(now: Instant = Instant.now()) = arm(settings.settings.first().digest?.let { TomorrowDigest.nextAt(it, now, ZoneId.systemDefault()) })

    private fun arm(at: Instant?) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, DigestReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (at == null) return manager.cancel(pending)
        if (ReminderPermissions.canScheduleExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    /** Notifie ce qui a lieu le lendemain de [now] ; rien s'il n'y a rien. */
    internal suspend fun show(now: Instant = Instant.now()) {
        val zone = ZoneId.systemDefault()
        val day = now.atZone(zone).toLocalDate().plusDays(1)
        val all = combine(events.eventsBetween(day, day, zone), almanac.eventsBetween(day, day, zone)) { a, b -> a + b }.first()
        val list = TomorrowDigest.events(all, day, zone)
        if (list.isEmpty()) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_digest_hint)
            },
        )
        // La langue de l'application, pas celle du téléphone : comme les rappels.
        val locale = Locale.forLanguageTag(context.getString(R.string.reminders_locale))
        val time = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
        val lines = list.map { e ->
            val at = when (val t = e.timing) {
                is Timing.AllDay -> context.getString(R.string.digest_all_day)
                is Timing.Timed -> time.format(t.start.atZone(zone))
            }
            context.getString(R.string.digest_line, at, e.title)
        }
        val title = context.getString(R.string.digest_title, DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day))
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach(style::addLine)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.resources.getQuantityString(R.plurals.digest_count, list.size, list.size, lines.first()))
            .setStyle(style)
            .setContentIntent(notifier.openAppIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        if (ReminderPermissions.canNotify(context)) {
            @Suppress("MissingPermission") // vérifié juste au-dessus
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "digest"
        private const val NOTIFICATION_ID = 8_000
    }
}

/** L'heure du résumé : notifier le lendemain, puis réarmer pour le soir suivant. */
@AndroidEntryPoint
class DigestReceiver : BroadcastReceiver() {
    @Inject lateinit var digest: DigestAlarms

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                digest.show()
            } finally {
                // Réarmé même si la notification a échoué : le résumé de demain soir ne doit pas se perdre.
                digest.run()
                pending.finish()
            }
        }
    }
}
