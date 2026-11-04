package com.gabrielifrim.hora.feature.widget

import com.gabrielifrim.hora.core.domain.almanac.Prayer
import com.gabrielifrim.hora.core.domain.almanac.PrayerWindow
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class PrayerContentTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 4)
    private fun at(h: Int, m: Int) = LocalDateTime.of(day, java.time.LocalTime.of(h, m)).atZone(paris).toInstant()
    private val windows = listOf(
        PrayerWindow(Prayer.SOBH, at(6, 24), at(7, 0), at(7, 34), false),
        PrayerWindow(Prayer.DHOHR, at(12, 36), at(14, 0), at(15, 1), false),
        PrayerWindow(Prayer.ASR, at(15, 1), at(16, 30), at(17, 21), false),
        PrayerWindow(Prayer.MAGHRIB, at(17, 21), at(17, 45), at(18, 33), false),
        PrayerWindow(Prayer.ISHA, at(18, 33), at(22, 0), at(23, 59), false),
    )

    @Test
    fun `entre deux temps, aucune n'est en cours et le widget change au début de la suivante`() {
        val c = PrayerContent.of(windows, at(10, 0), day, paris)
        assertThat(c.current).isNull()
        assertThat(c.past).containsExactly(Prayer.SOBH)
        assertThat(c.changesAt).isEqualTo(at(12, 36))
    }

    @Test
    fun `pendant Dhohr, elle est en cours et Sobh est passée`() {
        val c = PrayerContent.of(windows, at(13, 0), day, paris)
        assertThat(c.current).isEqualTo(Prayer.DHOHR)
        assertThat(c.past).containsExactly(Prayer.SOBH)
        assertThat(c.changesAt).isEqualTo(at(15, 1))
    }
}
