package com.gabrielifrim.hora.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gabrielifrim.hora.core.domain.reminder.ReminderPlanner
import com.gabrielifrim.hora.core.domain.repository.ReminderStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Le moteur d'alarmes : à chaque réveil (alarme, démarrage, changement d'heure,
 * modification d'un événement), présente ce qui est dû et arme la prochaine
 * échéance. Une seule alarme armée à la fois ; tout se recalcule depuis la base.
 */
@Singleton
class ReminderEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: ReminderStore,
    private val notifier: ReminderNotifier,
) {
    // Deux réveils simultanés (démarrage + modification) ne présentent pas deux fois le même rappel.
    private val lock = Mutex()

    suspend fun run(now: Instant = Instant.now()) = lock.withLock {
        val zone = ZoneId.systemDefault()
        val plan = ReminderPlanner.plan(
            events = store.eventsWithReminders(),
            overrides = store.overrides(),
            snoozes = store.snoozes(),
            fired = store.firedSince(now.minus(ReminderPlanner.catchUp)),
            now = now,
            zone = zone,
        )
        for (due in plan.due) {
            // Journal d'abord : un plantage pendant l'affichage ne fait pas sonner deux fois.
            due.firedKey?.let { store.markFired(it, due.event.id, due.occurrence, now) }
            due.snoozeId?.let { store.deleteSnooze(it) }
            val late = Duration.between(due.at, now) > LATE
            notifier.show(due, late)
        }
        store.forgetFiredBefore(now.minus(Duration.ofDays(30)))
        arm(plan.next, plan.nextIsAlarm)
    }

    private fun arm(next: Instant?, alarm: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (next == null) {
            manager.cancel(pending)
            return
        }
        val at = next.toEpochMilli()
        when {
            // Alarme plein écran : setAlarmClock, que même MIUI ne retarde pas, et l'icône réveil s'affiche.
            alarm && ReminderPermissions.canScheduleExact(context) ->
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, notifier.openAppIntent()), pending)
            ReminderPermissions.canScheduleExact(context) -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            // Sans alarme exacte (refusée sur Android 12), une alarme approchée vaut mieux que rien.
            else -> manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private companion object {
        /** Au-delà, le rappel est présenté comme « manqué » (téléphone éteint, appli arrêtée). */
        val LATE: Duration = Duration.ofMinutes(5)
    }
}
