package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class DayLayoutTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 2)

    private fun timed(title: String, from: String, to: String, zone: ZoneId = paris) = Event(
        uid = title, agendaId = 1, title = title,
        timing = Timing.Timed(
            LocalDateTime.parse(from).atZone(zone).toInstant(),
            LocalDateTime.parse(to).atZone(zone).toInstant(),
            zone,
        ),
    )

    @Test
    fun `un evenement de nuit se coupe en deux morceaux`() {
        val night = timed("Garde", "2026-11-02T22:00", "2026-11-03T02:00")
        val today = DayLayout.timedSlices(listOf(night), day, paris).single()
        val tomorrow = DayLayout.timedSlices(listOf(night), day.plusDays(1), paris).single()
        assertThat(today.startMinute to today.endMinute).isEqualTo(22 * 60 to 24 * 60)
        assertThat(today.continuesAfter).isTrue()
        assertThat(tomorrow.startMinute to tomorrow.endMinute).isEqualTo(0 to 2 * 60)
        assertThat(tomorrow.continuesBefore).isTrue()
    }

    @Test
    fun `finir pile a minuit ne deborde pas sur le lendemain`() {
        val late = timed("Soirée", "2026-11-02T20:00", "2026-11-03T00:00")
        assertThat(DayLayout.timedSlices(listOf(late), day, paris).single().continuesAfter).isFalse()
        assertThat(DayLayout.occursOn(late, day.plusDays(1), paris)).isFalse()
    }

    @Test
    fun `un evenement d'un autre fuseau s'affiche a l'heure locale`() {
        // 9 h à New York = 15 h à Paris en novembre (après le changement d'heure des deux côtés).
        val call = timed("Appel", "2026-11-02T09:00", "2026-11-02T10:00", ZoneId.of("America/New_York"))
        val slice = DayLayout.timedSlices(listOf(call), day, paris).single()
        assertThat(slice.startMinute).isEqualTo(15 * 60)
    }

    @Test
    fun `les chevauchements partagent des colonnes, les autres prennent toute la largeur`() {
        val a = timed("A", "2026-11-02T09:00", "2026-11-02T10:00")
        val b = timed("B", "2026-11-02T09:30", "2026-11-02T11:00")
        val c = timed("C", "2026-11-02T10:00", "2026-11-02T10:30") // libère la colonne de A
        val d = timed("D", "2026-11-02T14:00", "2026-11-02T15:00")
        val placed = DayLayout.place(DayLayout.timedSlices(listOf(a, b, c, d), day, paris)).associateBy { it.slice.event.title }
        assertThat(placed.getValue("A").columns).isEqualTo(2)
        assertThat(placed.getValue("C").column).isEqualTo(placed.getValue("A").column)
        assertThat(placed.getValue("B").column).isNotEqualTo(placed.getValue("A").column)
        assertThat(placed.getValue("D").columns).isEqualTo(1)
    }

    @Test
    fun `un evenement sur plusieurs jours couvre chacun de ses jours`() {
        val leave = Event(uid = "c", agendaId = 1, title = "Congés", timing = Timing.AllDay(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 14)))
        assertThat(DayLayout.allDayOn(listOf(leave), LocalDate.of(2026, 8, 14))).hasSize(1)
        assertThat(DayLayout.allDayOn(listOf(leave), LocalDate.of(2026, 8, 15))).isEmpty()
    }

    @Test
    fun `la journee du changement d'heure compte ses heures locales`() {
        // 29 mars 2026 : 2 h → 3 h. Un rendez-vous à 10 h reste à 10 h dans la grille.
        val brunch = timed("Brunch", "2026-03-29T10:00", "2026-03-29T11:00")
        assertThat(DayLayout.timedSlices(listOf(brunch), LocalDate.of(2026, 3, 29), paris).single().startMinute).isEqualTo(10 * 60)
    }
}
