package com.gabrielifrim.hora.core.domain

import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.ReminderCodec
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class ReminderTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun at(local: String) = LocalDateTime.parse(local).atZone(paris).let { Timing.Timed(it.toInstant(), it.plusMinutes(30).toInstant(), paris) }
    private fun java.time.Instant.local() = atZone(paris).toLocalDateTime()

    @Test
    fun `relatif et ancre sur un rendez-vous du matin`() {
        val dentist = at("2026-11-03T09:00")
        assertThat(Reminder.Before(10).triggerAt(dentist, paris).local()).isEqualTo(LocalDateTime.of(2026, 11, 3, 8, 50))
        assertThat(Reminder.Before(3 * 24 * 60).triggerAt(dentist, paris).local()).isEqualTo(LocalDateTime.of(2026, 10, 31, 9, 0))
        assertThat(Reminder.Anchored(1, LocalTime.of(20, 0)).triggerAt(dentist, paris).local()).isEqualTo(LocalDateTime.of(2026, 11, 2, 20, 0))
    }

    @Test
    fun `un relatif traverse le changement d'heure en vraie duree, un ancre garde son heure`() {
        // Dimanche 25 octobre 2026, 3 h → 2 h. Rendez-vous lundi 26 à 9 h.
        val monday = at("2026-10-26T09:00")
        // 24 h avant = dimanche 9 h, et non 8 h ou 10 h : une vraie durée.
        assertThat(Reminder.Before(24 * 60).triggerAt(monday, paris).local()).isEqualTo(LocalDateTime.of(2026, 10, 25, 9, 0))
        // 2 jours avant à 9 h = samedi 9 h, heure d'été.
        assertThat(Reminder.Anchored(2, LocalTime.of(9, 0)).triggerAt(monday, paris).local()).isEqualTo(LocalDateTime.of(2026, 10, 24, 9, 0))
    }

    @Test
    fun `une heure ancree qui n'existe pas passe a l'heure qui suit`() {
        // Nuit du 28 au 29 mars 2026 : 2 h 30 n'existe pas, le rappel sonne à 3 h 30.
        val brunch = at("2026-03-30T11:00")
        assertThat(Reminder.Anchored(1, LocalTime.of(2, 30)).triggerAt(brunch, paris).local()).isEqualTo(LocalDateTime.of(2026, 3, 29, 3, 30))
    }

    @Test
    fun `toute la journee part de minuit dans le fuseau du telephone`() {
        val day = Timing.AllDay(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 14))
        assertThat(Reminder.Anchored(1, LocalTime.of(20, 0)).triggerAt(day, paris).local()).isEqualTo(LocalDateTime.of(2026, 8, 2, 20, 0))
    }

    @Test
    fun `le codec relit ce qu'il ecrit et ignore une entree illisible`() {
        val list = listOf(Reminder.Before(0), Reminder.Before(60, alarm = true), Reminder.Anchored(1, LocalTime.of(20, 0)), Reminder.Anchored(2, LocalTime.of(9, 5), alarm = true))
        assertThat(ReminderCodec.decode(ReminderCodec.encode(list))).isEqualTo(list)
        assertThat(ReminderCodec.decode("B10;Zz;A1@25:00;")).containsExactly(Reminder.Before(10))
    }
}
