package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import java.time.LocalDate
import java.time.ZoneId

/**
 * La charge d'un jour, pour la vue année : les minutes occupées (réunion des événements
 * « Occupé », sans compter deux fois un chevauchement ; toute la journée = la journée
 * entière) et le nombre d'événements du jour, libres compris.
 */
data class DayLoad(val busyMinutes: Int, val events: Int) {
    /** Palier de 0 (libre) à 4 (6 h et plus), celui de la teinte. */
    val level: Int get() = when {
        busyMinutes == 0 -> 0
        busyMinutes < 2 * 60 -> 1
        busyMinutes < 4 * 60 -> 2
        busyMinutes < 6 * 60 -> 3
        else -> 4
    }

    companion object {
        private const val DAY_MINUTES = 24 * 60

        /** Charge de chaque jour de [first] à [last] ; [events] : occurrences déjà développées. */
        fun days(events: List<Event>, first: LocalDate, last: LocalDate, zone: ZoneId): Map<LocalDate, DayLoad> {
            val result = LinkedHashMap<LocalDate, DayLoad>()
            var day = first
            while (!day.isAfter(last)) {
                val onDay = events.filter { DayLayout.occursOn(it, day, zone) }
                result[day] = DayLoad(busyMinutes(onDay.filter { it.busy }, day, zone), onDay.size)
                day = day.plusDays(1)
            }
            return result
        }

        private fun busyMinutes(busy: List<Event>, day: LocalDate, zone: ZoneId): Int {
            if (DayLayout.allDayOn(busy, day).isNotEmpty()) return DAY_MINUTES
            var total = 0
            var end = 0
            for (s in DayLayout.timedSlices(busy, day, zone)) { // triés par début
                if (s.endMinute <= end) continue
                total += s.endMinute - maxOf(s.startMinute, end)
                end = s.endMinute
            }
            return total
        }
    }
}
