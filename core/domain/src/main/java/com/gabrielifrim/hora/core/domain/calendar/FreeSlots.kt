package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Plage
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Trouver un créneau libre : sans réseau, seulement dans l'agenda de Hora. */
object FreeSlots {
    /** Heures cherchées quand aucune plage n'est choisie. */
    val DAY_START: LocalTime = LocalTime.of(8, 0)
    val DAY_END: LocalTime = LocalTime.of(20, 0)

    /**
     * Les [count] premiers débuts de créneau de [duration] à partir de [from] (arrondi
     * au quart d'heure suivant), un par trou : dans [plage] les jours où elle a lieu,
     * sinon de 8:00 à 20:00 chaque jour, sur [days] jours. Seuls les événements occupés
     * à heure fixe bloquent : un anniversaire ou des congés sur la journée, non.
     */
    fun find(events: List<Event>, from: LocalDateTime, duration: Duration, plage: Plage?, zone: ZoneId, count: Int = 3, days: Int = 14): List<LocalDateTime> {
        val busy = events.filter { it.busy }.mapNotNull { it.timing as? Timing.Timed }.sortedBy { it.start }
        val earliest = ceilQuarter(from).atZone(zone).toInstant()
        val found = mutableListOf<LocalDateTime>()
        for (i in 0 until days) {
            val day = from.toLocalDate().plusDays(i.toLong())
            val (start, end) = when {
                plage == null -> DAY_START to DAY_END
                plage.isOn(day.dayOfWeek) -> plage.start to plage.end
                else -> continue
            }
            var cursor = maxOf(day.atTime(start).atZone(zone).toInstant(), earliest)
            val windowEnd = day.atTime(end).atZone(zone).toInstant()
            for (b in busy) {
                if (b.end <= cursor) continue
                if (b.start >= windowEnd) break
                if (cursor.plus(duration) <= b.start) {
                    found += LocalDateTime.ofInstant(cursor, zone)
                    if (found.size == count) return found
                }
                cursor = maxOf(cursor, b.end)
            }
            if (cursor.plus(duration) <= windowEnd) {
                found += LocalDateTime.ofInstant(cursor, zone)
                if (found.size == count) return found
            }
        }
        return found
    }

    private fun ceilQuarter(t: LocalDateTime): LocalDateTime {
        val floor = t.truncatedTo(ChronoUnit.HOURS).plusMinutes(t.minute / 15 * 15L)
        return if (floor == t) t else floor.plusMinutes(15)
    }
}
