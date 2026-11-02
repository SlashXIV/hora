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
import com.gabrielifrim.hora.core.domain.almanac.Prayer
import com.gabrielifrim.hora.core.domain.almanac.PrayerSettings
import com.gabrielifrim.hora.core.domain.almanac.Prayers
import com.gabrielifrim.hora.core.domain.model.UserSettings
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Une notification de prière à venir : le début, ou la fin du temps préférable dans 15 min. */
data class PrayerCue(val prayer: Prayer, val at: Instant, val endingSoon: Boolean, val until: Instant, val estimated: Boolean)

/**
 * Notifications facultatives de la pratique musulmane. Comme pour les rappels, une
 * seule alarme est armée : la prochaine ; à son réveil, on notifie puis on arme la suivante.
 */
@Singleton
class PrayerAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    suspend fun run(now: Instant = Instant.now()) = arm(next(settings.settings.first(), now))

    /** La prochaine notification due strictement après [now] ; null si rien n'est demandé. */
    internal fun next(s: UserSettings, now: Instant): PrayerCue? {
        val p = s.prayer
        if (!p.enabled || (!p.notifyStart && !p.notifyPreferredEnd)) return null
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        return (0L..1L).flatMap { cues(today.plusDays(it), s, p) }.filter { it.at.isAfter(now) }.minByOrNull { it.at }
    }

    private fun cues(date: LocalDate, s: UserSettings, p: PrayerSettings): List<PrayerCue> =
        Prayers.day(date, s.place, p).orEmpty().flatMap { w ->
            listOfNotNull(
                PrayerCue(w.prayer, w.start, endingSoon = false, until = w.preferredEnd, estimated = w.estimated).takeIf { p.notifyStart },
                PrayerCue(w.prayer, w.preferredEnd.minus(SOON), endingSoon = true, until = w.preferredEnd, estimated = w.estimated)
                    .takeIf { p.notifyPreferredEnd && w.preferredEnd.minus(SOON).isAfter(w.start) },
            )
        }

    private fun arm(cue: PrayerCue?) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, PrayerReceiver::class.java)
        if (cue != null) {
            intent.putExtra(EXTRA_PRAYER, cue.prayer.name).putExtra(EXTRA_SOON, cue.endingSoon)
                .putExtra(EXTRA_UNTIL, cue.until.toEpochMilli()).putExtra(EXTRA_ESTIMATED, cue.estimated)
        }
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (cue == null) return manager.cancel(pending)
        val at = cue.at.toEpochMilli()
        if (ReminderPermissions.canScheduleExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** Présente la notification portée par l'alarme. */
    internal fun show(intent: Intent, fajrName: Boolean, openApp: PendingIntent) {
        val prayer = Prayer.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PRAYER) } ?: return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_prayers), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_prayers_hint)
            },
        )
        val name = context.getString(prayerName(prayer, fajrName))
        val until = DateFormat.getTimeFormat(context).format(Date(intent.getLongExtra(EXTRA_UNTIL, 0)))
        val estimated = intent.getBooleanExtra(EXTRA_ESTIMATED, false)
        val text = context.getString(if (estimated) R.string.prayer_preferred_until_estimated else R.string.prayer_preferred_until, until)
        val title = context.getString(if (intent.getBooleanExtra(EXTRA_SOON, false)) R.string.prayer_ending_soon else R.string.prayer_starts, name)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        if (ReminderPermissions.canNotify(context)) {
            @Suppress("MissingPermission") // vérifié juste au-dessus
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + prayer.ordinal, notification)
        }
    }

    companion object {
        private val SOON: Duration = Duration.ofMinutes(15)
        private const val CHANNEL = "prayers"
        private const val NOTIFICATION_ID = 7_000
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_SOON = "soon"
        private const val EXTRA_UNTIL = "until"
        private const val EXTRA_ESTIMATED = "estimated"

        fun prayerName(p: Prayer, fajr: Boolean): Int = when (p) {
            Prayer.SOBH -> if (fajr) R.string.prayer_fajr else R.string.prayer_sobh
            Prayer.DHOHR -> R.string.prayer_dhohr
            Prayer.ASR -> R.string.prayer_asr
            Prayer.MAGHRIB -> R.string.prayer_maghrib
            Prayer.ISHA -> R.string.prayer_isha
        }
    }
}

/** L'alarme de prière a sonné : notifier, puis armer la suivante. */
@AndroidEntryPoint
class PrayerReceiver : BroadcastReceiver() {
    @Inject lateinit var prayers: PrayerAlarms
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val s = settings.settings.first().prayer
                if (s.enabled) prayers.show(intent, s.fajrName, notifier.openAppIntent())
                prayers.run()
            } finally {
                pending.finish()
            }
        }
    }
}
