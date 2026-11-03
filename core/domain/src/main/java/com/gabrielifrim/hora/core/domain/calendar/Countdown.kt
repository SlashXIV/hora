package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.recurrence.Recurrences
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Compte à rebours et compteur « depuis » d'un événement épinglé sur Aujourd'hui. */
object Countdown {
    /** Fenêtre cherchée de part et d'autre d'aujourd'hui : un anniversaire annuel y tient toujours. */
    private const val WINDOW_DAYS = 400L

    /**
     * Jours jusqu'au prochain jour de l'événement (> 0), 0 s'il a lieu aujourd'hui,
     * jours depuis son dernier début (< 0) ; null pour une série sans occurrence proche.
     * Une série compte vers sa prochaine occurrence, sinon depuis la dernière.
     */
    fun days(event: Event, today: LocalDate, zone: ZoneId): Long? {
        val spans = if (event.recurrence == null) listOf(event) else Recurrences.expand(event, today.minusDays(WINDOW_DAYS), today.plusDays(WINDOW_DAYS), zone)
        val days = spans.map { firstDay(it, zone) to lastDay(it, zone) }
        if (days.any { (first, last) -> !first.isAfter(today) && !last.isBefore(today) }) return 0
        days.map { it.first }.filter { it.isAfter(today) }.minOrNull()?.let { return ChronoUnit.DAYS.between(today, it) }
        return days.map { it.first }.filter { it.isBefore(today) }.maxOrNull()?.let { -ChronoUnit.DAYS.between(it, today) }
    }

    private fun firstDay(e: Event, zone: ZoneId): LocalDate = when (val t = e.timing) {
        is Timing.AllDay -> t.first
        is Timing.Timed -> t.start.atZone(zone).toLocalDate()
    }

    private fun lastDay(e: Event, zone: ZoneId): LocalDate = when (val t = e.timing) {
        is Timing.AllDay -> t.last
        // Finir pile à minuit n'occupe pas le lendemain.
        is Timing.Timed -> maxOf(t.end.minusNanos(1), t.start).atZone(zone).toLocalDate()
    }
}
