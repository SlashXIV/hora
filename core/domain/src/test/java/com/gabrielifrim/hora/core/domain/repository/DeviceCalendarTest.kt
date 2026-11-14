package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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

    @Test fun copie_importee_reconnue_sans_uid_commun() {
        // Côté Hora : jour (epoch) ou millisecondes ; côté Android : DTSTART, minuit UTC pour une journée entière.
        val d11 = LocalDate.of(2026, 11, 11)
        val utc = d11.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertThat(copyKey(true, deviceStart(true, utc), "Armistice")).isEqualTo(copyKey(true, d11.toEpochDay(), "Armistice"))
        val train = LocalDateTime.of(d11, LocalTime.of(7, 20)).atZone(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli()
        assertThat(copyKey(false, deviceStart(false, train), "Train ")).isEqualTo(copyKey(false, train, "Train"))
        // Un autre début, une autre nature ou un autre titre : pas une copie.
        assertThat(copyKey(false, train + 60_000, "Train")).isNotEqualTo(copyKey(false, train, "Train"))
        assertThat(copyKey(true, d11.toEpochDay(), "Train")).isNotEqualTo(copyKey(false, d11.toEpochDay(), "Train"))
        assertThat(copyKey(false, train, "Train retour")).isNotEqualTo(copyKey(false, train, "Train"))
        // Avant 1970, la division arrondit vers le bas : le bon jour.
        val d1969 = LocalDate.of(1969, 12, 31)
        assertThat(deviceStart(true, d1969.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())).isEqualTo(d1969.toEpochDay())
    }

    @Test fun journee_entiere_ecrite_relue_a_l_identique() {
        val allDay = Timing.AllDay(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 16))
        val t = deviceTimes(allDay, recurring = false)
        assertThat(t.allDay).isTrue()
        assertThat(t.zone).isEqualTo("UTC")
        assertThat(deviceAllDay(t.start, t.end!!)).isEqualTo(allDay)
        // Une série porte une durée à la place de la fin.
        assertThat(deviceTimes(allDay, recurring = true).let { it.end to it.duration }).isEqualTo(null to "P3D")
    }

    @Test fun heure_fixe_ecrite_avec_son_fuseau() {
        val zone = ZoneId.of("Europe/Paris")
        val start = LocalDateTime.of(2026, 11, 14, 9, 30).atZone(zone).toInstant()
        val timed = Timing.Timed(start, start.plusSeconds(5400), zone)
        assertThat(deviceTimes(timed, recurring = false)).isEqualTo(DeviceTimes(start.toEpochMilli(), start.toEpochMilli() + 5_400_000, null, false, "Europe/Paris"))
        assertThat(deviceTimes(timed, recurring = true).duration).isEqualTo("P5400S")
    }

    @Test fun rappel_ancre_converti_en_minutes() {
        val zone = ZoneId.of("Europe/Paris")
        val start = LocalDateTime.of(2026, 11, 14, 9, 30).atZone(zone).toInstant()
        val timed = Timing.Timed(start, start, zone)
        assertThat(deviceMinutes(Reminder.Before(15), timed, zone)).isEqualTo(15)
        // La veille à 20 h pour 9 h 30 : 13 h 30 avant.
        assertThat(deviceMinutes(Reminder.Anchored(1, LocalTime.of(20, 0)), timed, zone)).isEqualTo(810)
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
