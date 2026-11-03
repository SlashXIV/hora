package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class TomorrowDigestTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val tomorrow = LocalDate.of(2026, 11, 4)

    private fun timed(id: Long, title: String, from: String, to: String) = Event(
        id = id, uid = title, agendaId = 1, title = title,
        timing = Timing.Timed(LocalDateTime.parse(from).atZone(paris).toInstant(), LocalDateTime.parse(to).atZone(paris).toInstant(), paris),
    )

    @Test
    fun `journées d'abord, puis par heure, la veille et le surlendemain écartés`() {
        val events = listOf(
            timed(1, "Dentiste", "2026-11-04T14:30", "2026-11-04T15:15"),
            timed(2, "Café", "2026-11-04T08:00", "2026-11-04T08:30"),
            Event(id = 3, uid = "c", agendaId = 1, title = "Congés", timing = Timing.AllDay(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6))),
            timed(4, "Ce soir", "2026-11-03T20:00", "2026-11-03T22:00"),
            timed(5, "Après-demain", "2026-11-05T09:00", "2026-11-05T10:00"),
        )
        assertThat(TomorrowDigest.events(events, tomorrow, paris).map { it.title }).containsExactly("Congés", "Café", "Dentiste").inOrder()
    }

    @Test
    fun `prochain passage - ce soir, ou demain soir une fois l'heure passée`() {
        val eight = java.time.LocalTime.of(20, 0)
        val at = { s: String -> LocalDateTime.parse(s).atZone(paris).toInstant() }
        assertThat(TomorrowDigest.nextAt(eight, at("2026-11-03T19:59"), paris)).isEqualTo(at("2026-11-03T20:00"))
        assertThat(TomorrowDigest.nextAt(eight, at("2026-11-03T20:00"), paris)).isEqualTo(at("2026-11-04T20:00"))
        // Le soir du passage à l'heure d'hiver, 20:00 reste 20:00 à l'horloge.
        assertThat(TomorrowDigest.nextAt(eight, at("2026-10-25T21:00"), paris)).isEqualTo(at("2026-10-26T20:00"))
    }
}
