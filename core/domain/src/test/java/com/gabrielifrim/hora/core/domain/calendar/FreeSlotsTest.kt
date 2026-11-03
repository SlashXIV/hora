package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Plage
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class FreeSlotsTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val hour = Duration.ofHours(1)
    private val taff = Plage("Taff", DayOfWeek.entries.take(5).toSet(), LocalTime.of(9, 0), LocalTime.of(17, 30))

    private fun at(s: String) = LocalDateTime.parse(s)
    private fun timed(from: String, to: String, busy: Boolean = true) = Event(
        uid = from, agendaId = 1, title = from, busy = busy,
        timing = Timing.Timed(at(from).atZone(paris).toInstant(), at(to).atZone(paris).toInstant(), paris),
    )

    @Test
    fun `un créneau par trou, dans la plage, l'occupé évité`() {
        // Mardi 3 novembre 2026.
        val events = listOf(timed("2026-11-03T09:30", "2026-11-03T12:00"), timed("2026-11-03T13:00", "2026-11-03T17:00"))
        val slots = FreeSlots.find(events, at("2026-11-03T08:00"), hour, taff, paris)
        assertThat(slots).containsExactly(at("2026-11-03T12:00"), at("2026-11-04T09:00"), at("2026-11-05T09:00")).inOrder()
    }

    @Test
    fun `libre et toute la journée ne bloquent pas`() {
        val events = listOf(
            timed("2026-11-03T09:00", "2026-11-03T17:30", busy = false),
            Event(uid = "anniv", agendaId = 1, title = "Anniv", timing = Timing.AllDay(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3))),
        )
        assertThat(FreeSlots.find(events, at("2026-11-03T08:00"), hour, taff, paris, count = 1)).containsExactly(at("2026-11-03T09:00"))
    }

    @Test
    fun `sans plage - 8 h à 20 h, week-end compris, depuis le quart d'heure suivant`() {
        // Vendredi 6 novembre à 19:01 : plus rien ce soir ; samedi 8:00.
        assertThat(FreeSlots.find(emptyList(), at("2026-11-06T19:01"), hour, null, paris, count = 1)).containsExactly(at("2026-11-07T08:00"))
        assertThat(FreeSlots.find(emptyList(), at("2026-11-06T18:50"), hour, null, paris, count = 1)).containsExactly(at("2026-11-06T19:00"))
    }

    @Test
    fun `avec la plage - le week-end est sauté, et la fin de plage tient`() {
        val slots = FreeSlots.find(emptyList(), at("2026-11-06T16:31"), hour, taff, paris, count = 1)
        assertThat(slots).containsExactly(at("2026-11-09T09:00")) // 16:45 + 1 h dépasserait 17:30
    }
}
