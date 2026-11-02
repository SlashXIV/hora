package com.gabrielifrim.hora.core.domain.almanac

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Test

class AlmanacTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val parisPlace = Places.FRANCE.single { it.name == "Paris" }

    private fun entries(year: Int, options: AlmanacOptions = AlmanacOptions(), groups: Set<AlmanacGroup> = AlmanacGroup.entries.toSet()) =
        Almanac.entries(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), paris, options, groups)

    private fun near(actual: Instant?, expected: Instant, minutes: Long) =
        assertThat(abs(Duration.between(expected, actual!!).toMinutes())).isAtMost(minutes)

    @Test
    fun `Pâques`() {
        assertThat(listOf(2024, 2025, 2026, 2027).map(Almanac::easter))
            .containsExactly(LocalDate.of(2024, 3, 31), LocalDate.of(2025, 4, 20), LocalDate.of(2026, 4, 5), LocalDate.of(2027, 3, 28)).inOrder()
    }

    @Test
    fun `fériés 2026 - onze, Alsace-Moselle en plus sur demande, ponts`() {
        val days = Almanac.holidays(2026, AlmanacOptions(bridges = false))
        assertThat(days).hasSize(11)
        assertThat(days.single { it.kind == AlmanacKind.ASCENSION }.date).isEqualTo(LocalDate.of(2026, 5, 14))
        assertThat(days.single { it.kind == AlmanacKind.WHIT_MONDAY }.date).isEqualTo(LocalDate.of(2026, 5, 25))
        assertThat(Almanac.holidays(2026, AlmanacOptions(alsaceMoselle = true, bridges = false)).map { it.kind })
            .containsAtLeast(AlmanacKind.GOOD_FRIDAY, AlmanacKind.SAINT_STEPHEN)
        // 1er janvier et Ascension un jeudi, 14 juillet un mardi.
        assertThat(Almanac.holidays(2026).filter { it.kind == AlmanacKind.BRIDGE }.map { it.date })
            .containsExactly(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 5, 15), LocalDate.of(2026, 7, 13))
    }

    @Test
    fun `fête des mères - repoussée d'une semaine quand elle tombe à la Pentecôte`() {
        fun mothers(year: Int) = Almanac.feasts(year).single { it.kind == AlmanacKind.MOTHERS_DAY }.date
        assertThat(mothers(2026)).isEqualTo(LocalDate.of(2026, 5, 31))
        assertThat(mothers(2012)).isEqualTo(LocalDate.of(2012, 6, 3))
        assertThat(Almanac.feasts(2026).single { it.kind == AlmanacKind.FATHERS_DAY }.date).isEqualTo(LocalDate.of(2026, 6, 21))
    }

    @Test
    fun `changements d'heure 2026 à Paris`() {
        val changes = entries(2026, groups = setOf(AlmanacGroup.CLOCK))
        assertThat(changes.map { it.kind to it.date }).containsExactly(
            AlmanacKind.SUMMER_TIME to LocalDate.of(2026, 3, 29),
            AlmanacKind.WINTER_TIME to LocalDate.of(2026, 10, 25),
        ).inOrder()
    }

    @Test
    fun `soleil à Paris aux solstices, à quelques minutes près`() {
        fun at(date: LocalDate, h: Int, m: Int) = date.atTime(LocalTime.of(h, m)).atZone(paris).toInstant()
        val summer = LocalDate.of(2026, 6, 21)
        Sun.day(summer, parisPlace).let { near(it.sunrise, at(summer, 5, 47), 3); near(it.sunset, at(summer, 21, 58), 3); near(it.noon, at(summer, 13, 52), 3) }
        val winter = LocalDate.of(2026, 12, 21)
        Sun.day(winter, parisPlace).let { near(it.sunrise, at(winter, 8, 42), 3); near(it.sunset, at(winter, 16, 56), 3) }
        // Au-delà du cercle polaire en été, le soleil ne se couche pas.
        assertThat(Sun.crossing(summer, Place("Tromsø", 69.65, 18.96), Sun.HORIZON, morning = false)).isNull()
    }

    @Test
    fun `phases de la lune - éclipses connues`() {
        val april2024 = Moon.phases(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30), ZoneId.of("UTC"))
        near(april2024.single { it.kind == AlmanacKind.NEW_MOON }.at, Instant.parse("2024-04-08T18:21:00Z"), 10)
        val march2025 = Moon.phases(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), ZoneId.of("UTC"))
        near(march2025.single { it.kind == AlmanacKind.FULL_MOON }.at, Instant.parse("2025-03-14T06:55:00Z"), 10)
        // Une lunaison : quatre phases dans l'ordre, environ une par semaine.
        assertThat(entries(2026, groups = setOf(AlmanacGroup.MOON)).size).isIn(48..51)
    }
}
