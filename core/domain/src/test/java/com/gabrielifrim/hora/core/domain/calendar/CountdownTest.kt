package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class CountdownTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 3)

    private fun day(first: LocalDate, last: LocalDate = first, rule: String? = null) =
        Event(uid = "e", agendaId = 1, title = "e", timing = Timing.AllDay(first, last), recurrence = rule?.let { Recurrence(it) })

    @Test
    fun `à venir, aujourd'hui, en cours, passé`() {
        assertThat(Countdown.days(day(LocalDate.of(2026, 11, 15)), today, paris)).isEqualTo(12)
        assertThat(Countdown.days(day(today), today, paris)).isEqualTo(0)
        assertThat(Countdown.days(day(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)), today, paris)).isEqualTo(0)
        assertThat(Countdown.days(day(LocalDate.of(2026, 9, 24)), today, paris)).isEqualTo(-40)
    }

    @Test
    fun `série annuelle - vers la prochaine occurrence`() {
        val birthday = day(LocalDate.of(1990, 3, 2), rule = "FREQ=YEARLY")
        assertThat(Countdown.days(birthday, today, paris)).isEqualTo(119) // 2 mars 2027
    }

    @Test
    fun `heure fixe finissant à minuit - le lendemain n'est pas en cours`() {
        val late = Event(
            uid = "s", agendaId = 1, title = "Soirée",
            timing = Timing.Timed(LocalDateTime.of(2026, 11, 2, 20, 0).atZone(paris).toInstant(), LocalDateTime.of(2026, 11, 3, 0, 0).atZone(paris).toInstant(), paris),
        )
        assertThat(Countdown.days(late, today, paris)).isEqualTo(-1)
    }
}
