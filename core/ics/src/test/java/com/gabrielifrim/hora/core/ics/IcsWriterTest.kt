package com.gabrielifrim.hora.core.ics

import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class IcsWriterTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val agenda = Agenda(id = 3, name = "Perso; à part", colorKey = "PLUM")

    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 3, day, hour, 0).atZone(paris).toInstant()

    private val series = Event(
        id = 10, uid = "serie-1", agendaId = 3, title = "Cours, salle 2",
        timing = Timing.Timed(at(23, 9), at(23, 10), paris),
        location = "Bât. B", notes = "Ligne 1\nLigne 2 « citée »",
        reminders = listOf(Reminder.Before(15), Reminder.Anchored(1, LocalTime.of(20, 0), alarm = true)),
        recurrence = Recurrence("FREQ=WEEKLY;BYDAY=MO", setOf(at(30, 9).toEpochMilli())),
    )
    private val moved = Event(
        id = 11, uid = "serie-1#${at(6 + 7, 9).toEpochMilli()}", agendaId = 3, title = "Cours déplacé",
        timing = Timing.Timed(at(13, 14), at(13, 15), paris), parentId = 10, recurrenceId = at(6 + 7, 9).toEpochMilli(),
    )
    private val trip = Event(
        id = 12, uid = "voyage", agendaId = 3, title = "Voyage", colorKey = "TEAL", busy = false,
        timing = Timing.AllDay(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 31)),
        reminders = listOf(Reminder.Anchored(2, LocalTime.of(9, 30))),
    )

    @Test
    fun `aller-retour Hora - rien ne se perd`() {
        val text = IcsWriter.write(listOf(series, moved, trip), mapOf(3L to agenda))
        val back = IcsReader.read(text.toByteArray(), ZoneId.of("UTC"))

        assertThat(back.entries.map { it.agenda.name }.toSet()).containsExactly("Perso; à part")
        assertThat(back.entries.map { it.agenda.colorKey }.toSet()).containsExactly("PLUM")
        val events = back.entries.map { it.item.event.copy(agendaId = 3) }
        assertThat(events.single { it.uid == "serie-1" }).isEqualTo(series.copy(id = 0))
        assertThat(events.single { it.uid == "voyage" }).isEqualTo(trip.copy(id = 0))
        val child = back.entries.single { it.item.parentUid == "serie-1" }.item
        assertThat(child.event.copy(agendaId = 3)).isEqualTo(moved.copy(id = 0, parentId = null))
    }

    @Test
    fun `lignes pliées à 75 octets au plus, fin CRLF`() {
        val long = trip.copy(notes = "é".repeat(200))
        val text = IcsWriter.write(listOf(long), mapOf(3L to agenda))
        assertThat(text).endsWith("END:VCALENDAR\r\n")
        text.split("\r\n").forEach { assertThat(it.toByteArray().size).isAtMost(75) }
        assertThat(IcsReader.read(text.toByteArray(), paris).entries.single().item.event.notes).isEqualTo("é".repeat(200))
    }

    @Test
    fun `rappels lisibles ailleurs - durées relatives au début`() {
        val text = IcsWriter.write(listOf(trip, series), mapOf(3L to agenda))
        assertThat(text).contains("TRIGGER:-P1DT14H30M0S") // 2 jours avant à 9 h 30, depuis minuit
        assertThat(text).contains("TRIGGER:-P0DT0H15M0S")
        assertThat(IcsWriter.duration(Duration.ofMinutes(600))).isEqualTo("P0DT10H0M0S")
    }

    @Test
    fun `VTIMEZONE de Paris - dernier dimanche de mars et d'octobre`() {
        val lines = vtimezone(paris)
        assertThat(lines).contains("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=SU;BYMONTHDAY=25,26,27,28,29,30,31")
        assertThat(lines).contains("RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=SU;BYMONTHDAY=25,26,27,28,29,30,31")
        assertThat(lines).containsAtLeast("TZOFFSETFROM:+0100", "TZOFFSETTO:+0200")
    }
}
