package com.gabrielifrim.hora.core.domain.reminder

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.model.occurrenceKey
import com.gabrielifrim.hora.core.domain.recurrence.Recurrences
import com.gabrielifrim.hora.core.domain.repository.Snooze
import com.gabrielifrim.hora.core.domain.repository.firedKey
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Un rappel à présenter : l'occurrence (horaire compris) et ce qui le déclenche. */
data class DueReminder(
    val event: Event,
    val occurrence: Long,
    val at: Instant,
    val alarm: Boolean,
    /** Clé du journal ; null pour un report, qui s'efface une fois présenté. */
    val firedKey: String?,
    val snoozeId: Long? = null,
) {
    /** Le rappel tombe à l'heure de partir (début moins la marge de trajet) : la notification dit « Partir ». */
    val isDeparture: Boolean
        get() = snoozeId == null && event.travelMinutes > 0 &&
            (event.timing as? Timing.Timed)?.start?.minusSeconds(event.travelMinutes * 60L) == at
}

data class ReminderPlan(val due: List<DueReminder>, val next: Instant?, val nextIsAlarm: Boolean)

/**
 * Le cœur du moteur d'alarmes, sans Android : ce qui doit sonner maintenant, et la
 * prochaine échéance à armer. Une seule alarme est armée à la fois ; à chaque
 * réveil, on recalcule tout depuis la base.
 *
 * Un rappel passé depuis moins de [catchUp] et jamais présenté (téléphone éteint,
 * application tuée) est rattrapé ; au-delà, il est abandonné.
 */
object ReminderPlanner {
    val catchUp: Duration = Duration.ofHours(24)

    /** Assez loin pour « 1 semaine avant » et une durée libre de quelques semaines, pas plus. */
    private val horizon: Duration = Duration.ofDays(60)

    /** Un rappel qui tombe dans les 30 s est présenté tout de suite plutôt que réarmé. */
    private val tolerance: Duration = Duration.ofSeconds(30)

    fun plan(
        events: List<Event>,
        overrides: Map<Long, Set<Long>>,
        snoozes: List<Snooze>,
        fired: Set<String>,
        now: Instant,
        zone: ZoneId,
    ): ReminderPlan {
        val dueBefore = now.plus(tolerance)
        val oldest = now.minus(catchUp)
        val due = mutableListOf<DueReminder>()
        var next: Instant? = null
        var nextIsAlarm = false

        fun consider(at: Instant, alarm: Boolean, make: () -> DueReminder?) {
            when {
                at.isBefore(oldest) -> Unit
                !at.isAfter(dueBefore) -> make()?.let(due::add)
                next == null || at.isBefore(next) -> { next = at; nextIsAlarm = alarm }
                at == next && alarm -> nextIsAlarm = true
            }
        }

        for (event in events) {
            if (event.reminders.isEmpty()) continue
            val maxLead = event.reminders.maxOf { leadOf(it) }
            val from = oldest.atZone(zone).toLocalDate()
            val to = now.plus(horizon).plus(maxLead).atZone(zone).toLocalDate()
            val occurrences = Recurrences.expand(event, from, to, zone, overrides[event.id].orEmpty())
            for (occ in occurrences) {
                val key = occ.occurrence ?: occ.timing.occurrenceKey()
                for (r in event.reminders) {
                    val at = r.triggerAt(occ.timing, zone)
                    if (at.isAfter(now.plus(horizon))) continue
                    val fk = firedKey(event.id, key, r.key())
                    consider(at, r.alarm) { if (fk in fired) null else DueReminder(occ, key, at, r.alarm, fk) }
                }
            }
        }
        for (s in snoozes) {
            val event = events.firstOrNull { it.id == s.eventId } ?: continue
            val occ = Recurrences.expand(event, s.occurrenceDay(event, zone), s.occurrenceDay(event, zone), zone).firstOrNull { (it.occurrence ?: it.timing.occurrenceKey()) == s.occurrence } ?: event
            consider(s.at, s.alarm) { DueReminder(occ, s.occurrence, s.at, s.alarm, firedKey = null, snoozeId = s.id) }
        }
        return ReminderPlan(due.sortedBy { it.at }, next, nextIsAlarm)
    }

    private fun leadOf(r: Reminder): Duration = when (r) {
        is Reminder.Before -> Duration.ofMinutes(r.minutes.toLong())
        is Reminder.Anchored -> Duration.ofDays(r.daysBefore.toLong() + 1)
    }

    private fun Snooze.occurrenceDay(event: Event, zone: ZoneId) = when (event.timing) {
        is Timing.Timed -> Instant.ofEpochMilli(occurrence).atZone(zone).toLocalDate()
        is Timing.AllDay -> java.time.LocalDate.ofEpochDay(occurrence)
    }
}
