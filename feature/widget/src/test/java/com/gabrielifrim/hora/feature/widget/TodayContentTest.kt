package com.gabrielifrim.hora.feature.widget

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class TodayContentTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(paris).toInstant()
    private fun timed(id: Long, title: String, from: String, to: String) =
        Event(id = id, uid = title, agendaId = 1, title = title, timing = Timing.Timed(at(from), at(to), paris))

    private val dentist = timed(1, "Dentiste", "2026-11-04T14:30", "2026-11-04T15:00")
    private val shopping = timed(2, "Courses", "2026-11-04T18:30", "2026-11-04T19:00")
    private val lunch = timed(3, "Déjeuner", "2026-11-04T12:30", "2026-11-04T13:30")
    private val tomorrow = timed(4, "Train", "2026-11-05T07:58", "2026-11-05T10:00")

    @Test
    fun `le prochain sort de la liste, ce qui est fini aussi, et le widget change au début du prochain`() {
        val c = TodayContent.of(listOf(shopping, dentist, lunch, tomorrow), at("2026-11-04T14:00"), paris)
        assertThat(c.next).isEqualTo(dentist)
        assertThat(c.rest).containsExactly(shopping) // le déjeuner est fini
        assertThat(c.changesAt).isEqualTo(at("2026-11-04T14:30"))
    }

    @Test
    fun `le soir, le prochain est demain et le widget change à minuit`() {
        val c = TodayContent.of(listOf(shopping, tomorrow), at("2026-11-04T21:00"), paris)
        assertThat(c.next).isEqualTo(tomorrow)
        assertThat(c.rest).isEmpty()
        assertThat(c.changesAt).isEqualTo(at("2026-11-05T00:00"))
    }

    @Test
    fun `un événement en cours reste affiché jusqu'à sa fin`() {
        val c = TodayContent.of(listOf(lunch, shopping), at("2026-11-04T13:00"), paris)
        assertThat(c.next).isEqualTo(shopping)
        assertThat(c.rest).containsExactly(lunch)
        assertThat(c.changesAt).isEqualTo(at("2026-11-04T13:30"))
    }
}
