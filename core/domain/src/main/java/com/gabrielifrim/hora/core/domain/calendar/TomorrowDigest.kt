package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Le résumé du lendemain, envoyé le soir : ce qui a lieu [day], dans l'ordre où on le vivra. */
object TomorrowDigest {
    /** Les journées entières d'abord, puis par heure de début ; un événement de plusieurs jours compte chaque jour. */
    fun events(events: List<Event>, day: LocalDate, zone: ZoneId): List<Event> =
        events.filter { DayLayout.occursOn(it, day, zone) }
            .distinctBy { it.id to it.occurrence }
            .sortedWith(compareBy({ it.timing !is Timing.AllDay }, { (it.timing as? Timing.Timed)?.start }, { it.title }))

    /** Le prochain passage strictement après [now] : ce soir si l'heure n'est pas passée, sinon demain soir. */
    fun nextAt(at: LocalTime, now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val tonight = today.atTime(at).atZone(zone).toInstant()
        return if (tonight.isAfter(now)) tonight else today.plusDays(1).atTime(at).atZone(zone).toInstant()
    }
}
