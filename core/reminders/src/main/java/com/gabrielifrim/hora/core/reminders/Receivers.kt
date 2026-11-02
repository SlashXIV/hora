package com.gabrielifrim.hora.core.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gabrielifrim.hora.core.domain.repository.ReminderStore
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Lance [block] hors du fil principal en gardant le récepteur en vie jusqu'à la fin. */
private fun BroadcastReceiver.work(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** L'alarme armée a sonné : présenter ce qui est dû, armer la suivante. */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: ReminderEngine

    override fun onReceive(context: Context, intent: Intent) = work { engine.run() }
}

/** Démarrage, heure ou fuseau changés, mise à jour : les alarmes sont perdues ou fausses, on replanifie. */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: ReminderEngine
    @Inject lateinit var prayers: PrayerAlarms

    override fun onReceive(context: Context, intent: Intent) = work {
        engine.run()
        prayers.run()
    }
}

/** « Reporter » depuis la notification ou l'alarme plein écran. */
@AndroidEntryPoint
class SnoozeReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: ReminderEngine
    @Inject lateinit var store: ReminderStore
    @Inject lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT, -1)
        val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE, -1)
        if (eventId < 0) return
        notifier.cancel(eventId, occurrence)
        if (intent.action == STOP) return
        work {
            store.snooze(eventId, occurrence, snoozeUntil(intent.action, Instant.now(), ZoneId.systemDefault()), intent.getBooleanExtra(EXTRA_ALARM, false))
            engine.run()
        }
    }

    companion object {
        const val TEN_MINUTES = "com.gabrielifrim.hora.SNOOZE_10"
        const val ONE_HOUR = "com.gabrielifrim.hora.SNOOZE_60"
        const val TOMORROW_MORNING = "com.gabrielifrim.hora.SNOOZE_MORNING"
        /** Arrêter une alarme : on retire seulement la notification. */
        const val STOP = "com.gabrielifrim.hora.ALARM_STOP"
        val ACTIONS = listOf(TEN_MINUTES, ONE_HOUR, TOMORROW_MORNING, STOP)
        const val EXTRA_EVENT = "event"
        const val EXTRA_OCCURRENCE = "occurrence"
        const val EXTRA_ALARM = "alarm"

        /** « Demain matin » : 8 h le lendemain, heure locale. */
        fun snoozeUntil(action: String?, now: Instant, zone: ZoneId): Instant = when (action) {
            ONE_HOUR -> now.plus(Duration.ofHours(1))
            TOMORROW_MORNING -> now.atZone(zone).toLocalDate().plusDays(1).atTime(LocalTime.of(8, 0)).atZone(zone).toInstant()
            else -> now.plus(Duration.ofMinutes(10))
        }
    }
}

/** Ce qui peut empêcher un rappel de sonner, lu par la fenêtre « Fiabilité des rappels ». */
object ReminderPermissions {
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** USE_EXACT_ALARM est accordée d'office dès Android 13 ; sur 12, l'utilisateur peut la retirer. */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** Android 14 et plus : l'alarme plein écran demande l'accord de l'utilisateur. */
    fun canFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
}
