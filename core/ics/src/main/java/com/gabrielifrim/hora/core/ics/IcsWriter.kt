package com.gabrielifrim.hora.core.ics

import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Écrit des événements Hora en iCalendar. Ce que RFC 5545 ne sait pas dire (couleurs,
 * rappels ancrés, alarme plein écran) voyage en propriétés X-HORA-* : un fichier
 * exporté puis réimporté dans Hora ne perd rien, et reste lisible ailleurs.
 */
object IcsWriter {

    /**
     * @param events séries non développées et occurrences modifiées, comme en base.
     * @param agendas pour CATEGORIES (le nom de l'agenda, à la manière de Fossify).
     */
    fun write(events: List<Event>, agendas: Map<Long, Agenda>, now: Instant = Instant.now()): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Gabriel Ifrim//Hora//FR", "CALSCALE:GREGORIAN")
        agendas.values.singleOrNull()?.let { lines += "X-WR-CALNAME:" + IcsSyntax.escape(it.name) }
        events.mapNotNull { (it.timing as? Timing.Timed)?.zone }.filter { it.isRegion() }.distinct().forEach { lines += vtimezone(it) }
        val uidById = events.associate { it.id to it.uid }
        for (event in events) lines += vevent(event, agendas[event.agendaId], uidById, now)
        lines += "END:VCALENDAR"
        return lines.joinToString("") { IcsSyntax.fold(it) }
    }

    private fun vevent(e: Event, agenda: Agenda?, uidById: Map<Long, String>, now: Instant): List<String> {
        val out = mutableListOf("BEGIN:VEVENT")
        val parentUid = e.parentId?.let { uidById[it] } ?: e.uid.substringBefore('#').takeIf { e.recurrenceId != null }
        out += "UID:" + (if (e.recurrenceId != null) parentUid ?: e.uid else e.uid)
        out += "DTSTAMP:" + utc(now)
        out += "SUMMARY:" + IcsSyntax.escape(e.title)
        if (e.location.isNotBlank()) out += "LOCATION:" + IcsSyntax.escape(e.location)
        if (e.notes.isNotBlank()) out += "DESCRIPTION:" + IcsSyntax.escape(e.notes)
        agenda?.let {
            out += "CATEGORIES:" + IcsSyntax.escape(it.name)
            out += "X-HORA-AGENDA-COLOR:" + it.colorKey
        }
        e.colorKey?.let { out += "X-HORA-COLOR:$it" }
        out += "TRANSP:" + if (e.busy) "OPAQUE" else "TRANSPARENT"
        if (e.pinned) out += "X-HORA-PINNED:1"
        if (e.travelMinutes > 0) out += "X-HORA-TRAVEL:" + e.travelMinutes
        when (val t = e.timing) {
            is Timing.AllDay -> {
                out += "DTSTART;VALUE=DATE:" + day(t.first)
                out += "DTEND;VALUE=DATE:" + day(t.last.plusDays(1))
            }
            is Timing.Timed -> {
                out += "DTSTART" + dateTime(t.start, t.zone)
                out += "DTEND" + dateTime(t.end, t.zone)
            }
        }
        e.recurrence?.let { r ->
            out += "RRULE:" + r.rule.removePrefix("RRULE:")
            r.exceptions.sorted().forEach { key -> out += "EXDATE" + keyValue(key, e.timing) }
        }
        e.recurrenceId?.let { key -> out += "RECURRENCE-ID" + keyValue(key, e.timing) }
        e.reminders.forEach { r -> out += valarm(r, e) }
        out += "END:VEVENT"
        return out
    }

    private fun valarm(r: Reminder, e: Event): List<String> = listOf(
        "BEGIN:VALARM",
        "ACTION:DISPLAY",
        "DESCRIPTION:" + IcsSyntax.escape(e.title),
        "TRIGGER:" + duration(triggerOffset(r, e.timing)),
        "X-HORA-REMINDER:" + r.key(),
        "END:VALARM",
    )

    /**
     * Décalage du rappel depuis le début (minuit pour la journée). Un rappel ancré
     * d'un événement à heure fixe dépend du jour : on prend sa première occurrence,
     * X-HORA-REMINDER gardant la forme exacte.
     */
    private fun triggerOffset(r: Reminder, timing: Timing): Duration = when (timing) {
        is Timing.AllDay -> when (r) {
            is Reminder.Before -> Duration.ofMinutes(-r.minutes.toLong())
            is Reminder.Anchored -> Duration.ofDays(-r.daysBefore.toLong()).plusMinutes(r.time.hour * 60L + r.time.minute)
        }
        is Timing.Timed -> Duration.between(timing.start, r.triggerAt(timing, timing.zone))
    }

    /** À la manière de Fossify, lisible partout : « -P2DT14H0M0S ». */
    internal fun duration(d: Duration): String {
        // Pas de toHoursPart() : API 31, et lint ne voit pas un module JVM.
        val s = d.abs().seconds
        val sign = if (d.isNegative) "-" else ""
        return "${sign}P${s / 86_400}DT${s / 3_600 % 24}H${s / 60 % 60}M${s % 60}S"
    }

    /** Valeur d'une clé d'occurrence (EXDATE, RECURRENCE-ID), du même type que DTSTART. */
    private fun keyValue(key: Long, timing: Timing): String = when (timing) {
        is Timing.AllDay -> ";VALUE=DATE:" + day(LocalDate.ofEpochDay(key))
        is Timing.Timed -> dateTime(Instant.ofEpochMilli(key), timing.zone)
    }

    /** « ;TZID=Europe/Paris:20261102T143000 », ou en UTC pour un fuseau sans nom. */
    private fun dateTime(at: Instant, zone: ZoneId): String =
        if (zone.isRegion()) ";TZID=${zone.id}:" + BASIC_DATE_TIME.format(at.atZone(zone).toLocalDateTime()) else ":" + utc(at)

    private fun ZoneId.isRegion(): Boolean = this !is ZoneOffset && normalized() !is ZoneOffset

    private fun utc(at: Instant): String = BASIC_DATE_TIME.format(at.atOffset(ZoneOffset.UTC).toLocalDateTime()) + "Z"

    private fun day(d: LocalDate): String = BASIC_DATE.format(d)
}
