package com.gabrielifrim.hora.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalTime
import org.junit.Test

class PlageTest {
    @Test
    fun `aller-retour - nom avec point-virgule, jours, minutes`() {
        val plages = listOf(
            Plage("Taff", setOf(MONDAY, TUESDAY, FRIDAY), LocalTime.of(9, 0), LocalTime.of(17, 30)),
            Plage("Sport; salle", setOf(TUESDAY, THURSDAY), LocalTime.of(18, 30), LocalTime.of(20, 0)),
        )
        assertThat(PlageCodec.decode(PlageCodec.encode(plages))).isEqualTo(plages)
        assertThat(PlageCodec.decode("")).isEmpty()
    }

    @Test
    fun `ligne abîmée ou fin avant le début - ignorée, les autres gardées`() {
        val text = "09:00-17:30;MONDAY;Taff\n18:00-08:00;MONDAY;Nuit\nabc\n10:00-11:00;INCONNU;Rien"
        assertThat(PlageCodec.decode(text)).containsExactly(
            Plage("Taff", setOf(MONDAY), LocalTime.of(9, 0), LocalTime.of(17, 30)),
            Plage("Rien", emptySet(), LocalTime.of(10, 0), LocalTime.of(11, 0)),
        )
    }

    @Test
    fun `heures - la plage reste dans la journée, fin après début`() {
        val taff = Plage("Taff", setOf(MONDAY), LocalTime.of(9, 0), LocalTime.of(17, 0))
        assertThat(taff.withStart(LocalTime.of(8, 0)).start).isEqualTo(LocalTime.of(8, 0))
        assertThat(taff.withStart(LocalTime.of(18, 0)).end).isEqualTo(LocalTime.of(19, 0))
        assertThat(taff.withStart(LocalTime.of(23, 59)).end).isEqualTo(LocalTime.MAX)
        assertThat(PlageCodec.decode(PlageCodec.encode(listOf(taff.withStart(LocalTime.of(23, 59)))))).hasSize(1)
        assertThat(taff.withEnd(LocalTime.of(8, 0))).isEqualTo(taff.copy(start = LocalTime.of(7, 0), end = LocalTime.of(8, 0)))
        assertThat(taff.withEnd(LocalTime.MIDNIGHT)).isEqualTo(taff.copy(start = LocalTime.MIDNIGHT, end = LocalTime.of(1, 0)))
        assertThat(taff.withEnd(LocalTime.of(0, 30)).start).isEqualTo(LocalTime.MIDNIGHT)
    }
}
