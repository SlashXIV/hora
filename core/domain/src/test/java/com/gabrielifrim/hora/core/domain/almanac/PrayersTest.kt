package com.gabrielifrim.hora.core.domain.almanac

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Test

class PrayersTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val talence = Place("Talence", 44.8090, -0.5880)
    private val parisPlace = Places.FRANCE.single { it.name == "Paris" }
    private val defaults = PrayerSettings()

    private fun at(date: LocalDate, h: Int, m: Int): Instant = date.atTime(h, m).atZone(paris).toInstant()
    private fun near(actual: Instant, expected: Instant, minutes: Long = 2) =
        assertThat(abs(Duration.between(expected, actual).toMinutes())).isAtMost(minutes)

    @Test
    fun `réglages de départ - l'horaire publié de la mosquée de Talence`() {
        val d = LocalDate.of(2026, 9, 1)
        val w = Prayers.day(d, talence, defaults)!!.associateBy { it.prayer }
        near(w.getValue(Prayer.SOBH).start, at(d, 6, 13))
        near(w.getValue(Prayer.DHOHR).start, at(d, 14, 7))
        near(w.getValue(Prayer.ASR).start, at(d, 17, 44))
        near(w.getValue(Prayer.MAGHRIB).start, at(d, 20, 44))
        near(w.getValue(Prayer.ISHA).start, at(d, 21, 51))
    }

    @Test
    fun `bornes dans l'ordre, été comme hiver à Paris`() {
        for (d in listOf(LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21))) {
            val w = Prayers.day(d, parisPlace, defaults)!!
            assertThat(w.map { it.prayer }).containsExactlyElementsIn(Prayer.entries).inOrder()
            w.forEach { assertThat(it.start).isLessThan(it.preferredEnd); assertThat(it.preferredEnd).isAtMost(it.necessaryEnd) }
            assertThat(w.map { it.start }).isInOrder()
        }
    }

    @Test
    fun `'Isha préférable jusqu'au premier tiers de la nuit, du coucher à l'aube`() {
        val d = LocalDate.of(2026, 12, 21)
        val w = Prayers.day(d, parisPlace, defaults)!!.associateBy { it.prayer }
        val sunset = w.getValue(Prayer.ASR).necessaryEnd
        val nextDawn = w.getValue(Prayer.ISHA).necessaryEnd
        assertThat(w.getValue(Prayer.ISHA).preferredEnd).isEqualTo(sunset.plus(Duration.between(sunset, nextDawn).dividedBy(3)))
    }

    @Test
    fun `mashhour - Isfar 30 min avant le lever, Maghrib préférable 15 min`() {
        val d = LocalDate.of(2026, 6, 21)
        val mashhour = Prayers.day(d, parisPlace, defaults)!!.associateBy { it.prayer }
        assertThat(Duration.between(mashhour.getValue(Prayer.SOBH).preferredEnd, mashhour.getValue(Prayer.SOBH).necessaryEnd)).isEqualTo(Duration.ofMinutes(30))
        assertThat(Duration.between(mashhour.getValue(Prayer.MAGHRIB).start, mashhour.getValue(Prayer.MAGHRIB).preferredEnd)).isEqualTo(Duration.ofMinutes(15))
    }

    @Test
    fun `caler sur la mosquée - retrouve les angles de l'horaire`() {
        val d = LocalDate.of(2026, 9, 1)
        val (dawn, isha) = Prayers.calibrate(at(d, 6, 13), at(d, 21, 51), talence, defaults)
        assertThat(dawn).isWithin(0.3).of(12.8)
        assertThat(isha).isWithin(0.3).of(12.8)
    }

    @Test
    fun `réglages - aller-retour, et un texte abîmé garde les valeurs par défaut`() {
        val s = PrayerSettings(enabled = true, dawnAngle = 15.0, offsets = mapOf(Prayer.ASR to 3), fajrName = true, hijriAdjust = -1)
        assertThat(PrayerCodec.decode(PrayerCodec.encode(s))).isEqualTo(s.copy(offsets = Prayer.entries.associateWith { s.offset(it) }))
        assertThat(PrayerCodec.decode("dawn=abc;inconnu=1")).isEqualTo(PrayerSettings())
        // Un ancien réglage « rajih » (avant la v0.6.2) se lit sans erreur : l'avis n'existe plus.
        assertThat(PrayerCodec.decode("on=true;opinion=RAJIH").enabled).isTrue()
    }

    @Test
    fun `hégire - 1er ramadan 1447`() {
        assertThat(Prayers.hijri(LocalDate.of(2026, 2, 18))).isEqualTo(Triple(1, 9, 1447))
    }
}
