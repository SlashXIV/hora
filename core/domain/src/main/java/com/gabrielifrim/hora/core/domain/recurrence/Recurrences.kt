package com.gabrielifrim.hora.core.domain.recurrence

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule

/**
 * Développe une série en occurrences avec lib-recur (RFC 5545 complète : une règle
 * importée de Google ou d'Outlook ne perd aucune occurrence).
 *
 * Une occurrence à heure fixe garde son heure murale dans le fuseau de la série :
 * « chaque lundi à 9 h » reste à 9 h après le changement d'heure.
 */
object Recurrences {
    /** Garde-fou contre une règle qui produirait des milliers d'occurrences dans la fenêtre. */
    private const val MAX_INSTANCES = 2_000

    /**
     * Les occurrences de [event] qui touchent [first]..[last] (vu depuis [zone]),
     * sans les exceptions ni les clés [overridden] (occurrences modifiées à part).
     * Un événement sans règle est rendu tel quel s'il touche la fenêtre.
     */
    fun expand(event: Event, first: LocalDate, last: LocalDate, zone: ZoneId, overridden: Set<Long> = emptySet()): List<Event> {
        val recurrence = event.recurrence ?: return listOf(event)
        val rule = parse(recurrence.rule) ?: return listOf(event)
        val skip = recurrence.exceptions + overridden
        return when (val t = event.timing) {
            is Timing.Timed -> expandTimed(event, t, rule, first, last, zone, skip)
            is Timing.AllDay -> expandAllDay(event, t, rule, first, last, skip)
        }
    }

    private fun expandTimed(event: Event, t: Timing.Timed, rule: RecurrenceRule, first: LocalDate, last: LocalDate, zone: ZoneId, skip: Set<Long>): List<Event> {
        val duration = Duration.between(t.start, t.end)
        val windowStart = first.atStartOfDay(zone).toInstant()
        val windowEnd = last.plusDays(1).atStartOfDay(zone).toInstant()
        val iterator = rule.iterator(DateTime(TimeZone.getTimeZone(t.zone), t.start.toEpochMilli()))
        // Saute ce qui finit avant la fenêtre, sans perdre une occurrence qui la chevauche.
        iterator.fastForward(windowStart.minus(duration).toEpochMilli())
        val out = mutableListOf<Event>()
        while (iterator.hasNext() && out.size < MAX_INSTANCES) {
            val start = Instant.ofEpochMilli(iterator.nextMillis())
            if (!start.isBefore(windowEnd)) break
            val end = start.plus(duration)
            if (start.toEpochMilli() in skip || (!end.isAfter(windowStart) && end != start)) continue
            out += event.copy(timing = Timing.Timed(start, end, t.zone), occurrence = start.toEpochMilli())
        }
        return out
    }

    private fun expandAllDay(event: Event, t: Timing.AllDay, rule: RecurrenceRule, first: LocalDate, last: LocalDate, skip: Set<Long>): List<Event> {
        val span = ChronoUnit.DAYS.between(t.first, t.last)
        val iterator = rule.iterator(DateTime(t.first.year, t.first.monthValue - 1, t.first.dayOfMonth))
        val from = first.minusDays(span)
        iterator.fastForward(DateTime(from.year, from.monthValue - 1, from.dayOfMonth))
        val out = mutableListOf<Event>()
        while (iterator.hasNext() && out.size < MAX_INSTANCES) {
            val dt = iterator.nextDateTime()
            val day = LocalDate.of(dt.year, dt.month + 1, dt.dayOfMonth)
            if (day.isAfter(last)) break
            if (day.toEpochDay() in skip || day.plusDays(span).isBefore(first)) continue
            out += event.copy(timing = Timing.AllDay(day, day.plusDays(span)), occurrence = day.toEpochDay())
        }
        return out
    }

    /** Règle lisible, sinon null : un événement à la règle corrompue s'affiche une fois plutôt que de faire planter. */
    fun parse(rule: String): RecurrenceRule? = runCatching {
        RecurrenceRule(rule.removePrefix("RRULE:"), RecurrenceRule.RfcMode.RFC5545_LAX)
    }.getOrNull()

    /**
     * « Cette occurrence et les suivantes » : la série d'origine s'arrête juste avant
     * [occurrenceKey], et la suite reprend la même règle à partir de cette occurrence.
     * Un COUNT est partagé entre les deux pour que le total ne change pas.
     *
     * @return la règle tronquée de la série d'origine, et la règle de la suite.
     */
    fun split(series: Event, occurrenceKey: Long, zone: ZoneId): Pair<Recurrence?, Recurrence> {
        val recurrence = requireNotNull(series.recurrence) { "pas une série" }
        val rule = requireNotNull(parse(recurrence.rule)) { "règle illisible" }
        val count = rule.count
        val before = if (count != null) occurrencesBefore(series, occurrenceKey, zone) else 0
        val head = parse(recurrence.rule)!!.apply {
            when (val t = series.timing) {
                is Timing.Timed -> setUntil(DateTime(DateTime.UTC, occurrenceKey - 1_000))
                is Timing.AllDay -> LocalDate.ofEpochDay(occurrenceKey).minusDays(1).let { setUntil(DateTime(it.year, it.monthValue - 1, it.dayOfMonth)) }
            }
        }
        val tail = parse(recurrence.rule)!!.apply { if (count != null) setCount(count - before) }
        val exceptions = recurrence.exceptions.partition { it < occurrenceKey }
        val headRecurrence = if (before == 0 && count != null) null else Recurrence(head.toString(), exceptions.first.toSet())
        return headRecurrence to Recurrence(tail.toString(), exceptions.second.toSet())
    }

    private fun occurrencesBefore(series: Event, key: Long, zone: ZoneId): Int {
        val first = when (val t = series.timing) {
            is Timing.Timed -> t.start.atZone(zone).toLocalDate()
            is Timing.AllDay -> t.first
        }
        val last = when (series.timing) {
            is Timing.Timed -> Instant.ofEpochMilli(key).atZone(zone).toLocalDate()
            is Timing.AllDay -> LocalDate.ofEpochDay(key)
        }
        return expand(series.copy(recurrence = series.recurrence!!.copy(exceptions = emptySet())), first, last, zone).count { it.occurrence!! < key }
    }
}
