package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Le morceau d'un événement qui tombe un jour donné, en minutes depuis minuit (heure locale). */
data class DaySlice(
    val event: Event,
    val startMinute: Int,
    val endMinute: Int,
    /** L'événement a commencé un jour précédent / continue le lendemain. */
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/** Un morceau placé dans la grille : sa colonne parmi [columns] côte à côte. */
data class PlacedSlice(val slice: DaySlice, val column: Int, val columns: Int)

object DayLayout {
    private const val DAY_MINUTES = 24 * 60

    /** L'événement touche-t-il ce jour, vu depuis le fuseau [zone] du téléphone ? */
    fun occursOn(event: Event, date: LocalDate, zone: ZoneId): Boolean = when (val t = event.timing) {
        is Timing.AllDay -> !date.isBefore(t.first) && !date.isAfter(t.last)
        is Timing.Timed -> {
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            // Un événement de durée nulle (rappel « à 9 h ») appartient au jour de son début.
            t.start.isBefore(dayEnd) && (t.end.isAfter(dayStart) || (t.end == t.start && !t.start.isBefore(dayStart)))
        }
    }

    /**
     * Les morceaux d'événements à heure fixe pour [date], affichés dans [zone]. Un
     * événement de 22 h à 2 h donne deux morceaux : 22 h–24 h, puis 0 h–2 h.
     * Les journées de changement d'heure comptent leurs vraies minutes locales.
     */
    fun timedSlices(events: List<Event>, date: LocalDate, zone: ZoneId): List<DaySlice> =
        events.mapNotNull { event ->
            val t = event.timing as? Timing.Timed ?: return@mapNotNull null
            if (!occursOn(event, date, zone)) return@mapNotNull null
            val dayStart = date.atStartOfDay(zone).toInstant()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant()
            val startsBefore = t.start.isBefore(dayStart)
            DaySlice(
                event = event,
                startMinute = if (startsBefore) 0 else minuteOfDay(t.start.atZone(zone)),
                endMinute = if (t.end.isBefore(nextMidnight)) minuteOfDay(t.end.atZone(zone)) else DAY_MINUTES,
                continuesBefore = startsBefore,
                continuesAfter = t.end.isAfter(nextMidnight),
            )
        }.sortedWith(compareBy({ it.startMinute }, { -it.endMinute }))

    /** Les événements sur toute la journée (ou plusieurs) qui couvrent [date]. */
    fun allDayOn(events: List<Event>, date: LocalDate): List<Event> =
        events.filter { val t = it.timing; t is Timing.AllDay && !date.isBefore(t.first) && !date.isAfter(t.last) }

    /**
     * Place côte à côte les morceaux qui se chevauchent : chaque grappe de morceaux
     * liés par un chevauchement partage le même nombre de colonnes, et chaque
     * morceau prend la première colonne libre.
     */
    fun place(slices: List<DaySlice>): List<PlacedSlice> {
        val sorted = slices.sortedWith(compareBy({ it.startMinute }, { -it.endMinute }))
        val result = mutableListOf<PlacedSlice>()
        var cluster = mutableListOf<Pair<DaySlice, Int>>()
        val columnEnds = mutableListOf<Int>()
        var clusterEnd = -1

        fun flush() {
            val columns = columnEnds.size.coerceAtLeast(1)
            cluster.forEach { (slice, column) -> result += PlacedSlice(slice, column, columns) }
            cluster = mutableListOf()
            columnEnds.clear()
        }

        for (slice in sorted) {
            // Un morceau trop court reste visible : il occupe au moins 15 minutes.
            val end = maxOf(slice.endMinute, slice.startMinute + MIN_VISIBLE_MINUTES)
            if (slice.startMinute >= clusterEnd && cluster.isNotEmpty()) flush()
            val free = columnEnds.indexOfFirst { it <= slice.startMinute }
            val column = if (free >= 0) free else columnEnds.size.also { columnEnds += 0 }
            columnEnds[column] = end
            cluster += slice to column
            clusterEnd = maxOf(clusterEnd, end)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }

    private fun minuteOfDay(time: ZonedDateTime): Int = time.hour * 60 + time.minute

    const val MIN_VISIBLE_MINUTES = 15
}
