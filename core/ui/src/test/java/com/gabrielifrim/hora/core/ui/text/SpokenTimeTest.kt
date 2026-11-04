package com.gabrielifrim.hora.core.ui.text

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import org.junit.Test

class SpokenTimeTest {
    private val french = Locale.FRANCE

    @Test
    fun `en 12 h, TalkBack lit l'heure sur 24 h`() {
        val formats = HoraFormats(french, clock24 = false, firstDayOfWeek = DayOfWeek.MONDAY, showWeekNumbers = false)
        assertThat(formats.spoken(LocalTime.of(19, 32))).isEqualTo("19 h 32")
        assertThat(formats.spoken(LocalTime.of(22, 0))).isEqualTo("22 h")
        assertThat(formats.time(LocalTime.of(19, 32))).doesNotContain("19") // l'écran reste en 12 h
    }

    @Test
    fun `en 24 h, rien ne change`() {
        val formats = HoraFormats(french, clock24 = true, firstDayOfWeek = DayOfWeek.MONDAY, showWeekNumbers = false)
        assertThat(formats.spoken(LocalTime.of(9, 5))).isEqualTo("9 h 05")
    }
}
