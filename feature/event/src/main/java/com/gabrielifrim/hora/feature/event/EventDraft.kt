package com.gabrielifrim.hora.feature.event

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.recurrence.RepeatSpec
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.quick.QuickEntry
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Ce que l'éditeur modifie : des dates et des heures murales dans un fuseau,
 * converties en instants seulement à l'enregistrement. Ainsi, cocher « toute la
 * journée » puis le décocher rend les heures d'origine.
 */
data class EventDraft(
    val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val agendaId: Long,
    val title: String = "",
    val location: String = "",
    val notes: String = "",
    val colorKey: String? = null,
    val allDay: Boolean = false,
    val busy: Boolean = true,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val zone: ZoneId,
    val reminders: List<Reminder> = emptyList(),
    /** Répétition éditable ; null avec [customRule] null : ne se répète pas. */
    val repeat: RepeatSpec? = null,
    /** Règle importée que l'éditeur ne sait pas montrer : gardée telle quelle tant qu'on ne choisit pas autre chose. */
    val customRule: String? = null,
    val exceptions: Set<Long> = emptySet(),
) {
    val canSave: Boolean get() = title.isNotBlank() && !endDateTimeForSave().isBefore(start)

    /** Change le début en gardant la durée : déplacer un rendez-vous ne le raccourcit pas. */
    fun withStart(newStart: LocalDateTime): EventDraft = copy(start = newStart, end = newStart.plus(Duration.between(start, end)))

    /** Toute la journée, la fin est un jour ; à heure fixe, un instant. */
    fun withEnd(newEnd: LocalDateTime): EventDraft = copy(end = if (newEnd.isBefore(start)) start else newEnd)

    private fun endDateTimeForSave(): LocalDateTime = if (allDay) end.toLocalDate().atStartOfDay() else end

    val repeats: Boolean get() = repeat != null || customRule != null

    /**
     * Saisie rapide : ce qui a été dit remplace le brouillon neuf. Un jour sans heure
     * donne la journée entière ; sans fin ni durée, une heure.
     */
    fun withQuick(q: QuickEntry): EventDraft {
        val day = q.date ?: start.toLocalDate()
        val base = copy(
            title = q.title,
            location = q.location.ifEmpty { location },
            reminders = (reminders + q.reminders).distinct(),
        )
        val from = q.start?.let(day::atTime) ?: day.atTime(start.toLocalTime())
        val length = when {
            q.end != null && q.start != null -> Duration.between(q.start, q.end)
            q.duration != null -> q.duration
            else -> Duration.between(start, end)
        }
        return when {
            q.start == null && q.date != null && q.duration == null -> base.copy(allDay = true, start = day.atTime(9, 0), end = day.atTime(10, 0))
            else -> base.copy(allDay = false, start = from, end = from.plus(length))
        }
    }

    fun toEvent(): Event = Event(
        id = id,
        uid = uid,
        agendaId = agendaId,
        title = title.trim(),
        location = location.trim(),
        notes = notes.trim(),
        colorKey = colorKey,
        busy = busy,
        reminders = reminders,
        recurrence = (repeat?.toRule(start.toLocalDate(), if (allDay) null else zone) ?: customRule)?.let { Recurrence(it, exceptions) },
        timing = if (allDay) {
            Timing.AllDay(start.toLocalDate(), maxOf(start.toLocalDate(), end.toLocalDate()))
        } else {
            // Une heure qui n'existe pas (2 h 30 la nuit du passage à l'heure d'été) est
            // repoussée par ZonedDateTime à l'heure qui suit : 3 h 30.
            Timing.Timed(start.atZone(zone).toInstant(), end.atZone(zone).toInstant(), zone)
        },
    )

    companion object {
        /** Nouvel événement : à l'heure donnée, sinon à la prochaine heure pleine ; une heure. */
        fun new(agendaId: Long, date: LocalDate, minute: Int?, zone: ZoneId, now: LocalDateTime = LocalDateTime.now(), reminders: List<Reminder> = emptyList()): EventDraft {
            val time = if (minute != null) {
                LocalTime.of(minute / 60, minute % 60)
            } else {
                now.toLocalTime().truncatedTo(ChronoUnit.HOURS).plusHours(1)
            }
            val start = date.atTime(time)
            return EventDraft(agendaId = agendaId, start = start, end = start.plusHours(1), zone = zone, reminders = reminders)
        }

        fun of(event: Event, displayZone: ZoneId): EventDraft = base(event, displayZone).let { d ->
            val rule = event.recurrence?.rule
            val zone = if (event.timing is Timing.AllDay) null else d.zone
            val spec = rule?.let { RepeatSpec.parse(it, zone, d.start.toLocalDate()) }
            d.copy(
                reminders = event.reminders,
                repeat = spec,
                customRule = rule.takeIf { spec == null },
                exceptions = event.recurrence?.exceptions.orEmpty(),
            )
        }

        private fun base(event: Event, displayZone: ZoneId): EventDraft = when (val t = event.timing) {
            is Timing.AllDay -> EventDraft(
                id = event.id, uid = event.uid, agendaId = event.agendaId, title = event.title,
                location = event.location, notes = event.notes, colorKey = event.colorKey, busy = event.busy,
                allDay = true, start = t.first.atTime(9, 0), end = t.last.atTime(10, 0), zone = displayZone,
            )
            is Timing.Timed -> EventDraft(
                id = event.id, uid = event.uid, agendaId = event.agendaId, title = event.title,
                location = event.location, notes = event.notes, colorKey = event.colorKey, busy = event.busy,
                start = LocalDateTime.ofInstant(t.start, t.zone), end = LocalDateTime.ofInstant(t.end, t.zone), zone = t.zone,
            )
        }

        /** Copie à enregistrer comme un nouvel événement : nouvel identifiant, nouvel UID. */
        fun duplicate(event: Event, displayZone: ZoneId): EventDraft = of(event, displayZone).copy(id = 0, uid = UUID.randomUUID().toString(), exceptions = emptySet())
    }
}

/** Déplace l'événement au jour [to], aux mêmes heures et avec la même durée. */
fun Event.movedTo(to: LocalDate): Event = when (val t = timing) {
    is Timing.AllDay -> copy(timing = Timing.AllDay(to, to.plusDays(ChronoUnit.DAYS.between(t.first, t.last))))
    is Timing.Timed -> {
        val start = t.start.atZone(t.zone)
        val newStart = to.atTime(start.toLocalTime()).atZone(t.zone)
        copy(timing = Timing.Timed(newStart.toInstant(), newStart.toInstant().plus(Duration.between(t.start, t.end)), t.zone))
    }
}
