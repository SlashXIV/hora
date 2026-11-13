package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

class DeviceCalendarTest {
    @Test fun ids_se_retrouvent_sans_croiser_ceux_des_autres() {
        assertThat(deviceCalendarOf(deviceAgendaId(7))).isEqualTo(7)
        assertThat(deviceEventOf(deviceEventId(123_456))).isEqualTo(123_456)
        // Un agenda d'Hora, un agenda calculé ou un événement d'Android ne passent pas pour un agenda d'Android.
        assertThat(deviceCalendarOf(3)).isNull()
        assertThat(deviceCalendarOf(-2)).isNull()
        assertThat(deviceCalendarOf(deviceEventId(1))).isNull()
        assertThat(deviceEventOf(-29_000_000)).isNull()
        assertThat(almanacEntryOf(deviceEventId(1))).isNull()
    }

    @Test fun journee_entiere_d_android_sans_jour_de_trop() {
        fun utc(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val d14 = LocalDate.of(2026, 11, 14)
        // Du 14 à minuit au 15 à minuit : le seul 14, pas le 15.
        assertThat(deviceAllDay(utc(d14), utc(d14.plusDays(1)))).isEqualTo(Timing.AllDay(d14, d14))
        assertThat(deviceAllDay(utc(d14), utc(d14.plusDays(3)))).isEqualTo(Timing.AllDay(d14, d14.plusDays(2)))
        // Une fin égale au début (saisie bancale d'une autre app) reste un jour.
        assertThat(deviceAllDay(utc(d14), utc(d14))).isEqualTo(Timing.AllDay(d14, d14))
    }

    @Test fun couleur_d_android_ramenee_a_celle_d_hora() {
        assertThat(nearestColorKey(0xFFD50000.toInt())).isEqualTo("BRICK") // tomate de Google Agenda
        assertThat(nearestColorKey(0xFF0B8043.toInt())).isEqualTo("FIR") // basilic
        assertThat(nearestColorKey(0xFF039BE5.toInt())).isEqualTo("BLUE") // paon
        assertThat(nearestColorKey(0xFF8E24AA.toInt())).isEqualTo("PLUM") // raisin
        assertThat(nearestColorKey(0xFFF6BF26.toInt())).isEqualTo("OCHRE") // banane
        assertThat(nearestColorKey(0xFFE91E63.toInt())).isEqualTo("RASPBERRY")
        assertThat(nearestColorKey(0xFF616161.toInt())).isEqualTo("SLATE") // graphite
        assertThat(nearestColorKey(0xFF000000.toInt())).isEqualTo("SLATE")
    }
}
