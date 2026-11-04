package com.gabrielifrim.hora.feature.widget

import com.gabrielifrim.hora.core.domain.calendar.DayLayout
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ce que montre le widget Aujourd'hui à l'instant [now] : le prochain rendez-vous (un vrai
 * événement, à venir, aujourd'hui ou plus tard), puis ce qui reste du jour, et l'instant où
 * ce contenu change. Le widget ne tourne pas : il se redessine à cet instant-là.
 */
internal data class TodayContent(val next: Event?, val rest: List<Event>, val changesAt: Instant) {
    companion object {
        const val MAX_ROWS = 3

        fun of(events: List<Event>, now: Instant, zone: ZoneId): TodayContent {
            val today = now.atZone(zone).toLocalDate()
            val next = events.filter { it.id > 0 }
                .mapNotNull { e -> (e.timing as? Timing.Timed)?.takeIf { it.start.isAfter(now) }?.let { e to it.start } }
                .minByOrNull { it.second }?.first
            val rest = events
                .filter { it != next && DayLayout.occursOn(it, today, zone) }
                .filter { val t = it.timing; t !is Timing.Timed || t.end.isAfter(now) }
                .sortedWith(compareBy({ it.timing !is Timing.AllDay }, { (it.timing as? Timing.Timed)?.start }))
                .take(MAX_ROWS)
            val midnight = today.plusDays(1).atStartOfDay(zone).toInstant()
            val changes = listOfNotNull(
                midnight,
                (next?.timing as? Timing.Timed)?.start,
            ) + rest.mapNotNull { (it.timing as? Timing.Timed)?.end }
            return TodayContent(next, rest, changes.filter { it.isAfter(now) }.min())
        }

        /** Le prochain peut tomber un autre jour : le widget dit alors lequel. */
        fun isToday(event: Event, today: LocalDate, zone: ZoneId): Boolean = DayLayout.occursOn(event, today, zone)
    }
}
