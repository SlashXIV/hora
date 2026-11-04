package com.gabrielifrim.hora.core.domain.calendar

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class DayLoadTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 10)

    private fun timed(title: String, from: String, to: String, busy: Boolean = true) = Event(
        uid = title, agendaId = 1, title = title, busy = busy,
        timing = Timing.Timed(LocalDateTime.parse(from).atZone(paris).toInstant(), LocalDateTime.parse(to).atZone(paris).toInstant(), paris),
    )

    @Test
    fun `les chevauchements ne comptent qu'une fois, les libres pas du tout`() {
        val events = listOf(
            timed("Réunion", "2026-11-10T09:00", "2026-11-10T11:00"),
            timed("Appel", "2026-11-10T10:30", "2026-11-10T11:30"),
            timed("Déjeuner", "2026-11-10T12:30", "2026-11-10T13:30", busy = false),
            timed("Dentiste", "2026-11-10T14:30", "2026-11-10T15:00"),
        )
        val load = DayLoad.days(events, day, day, paris).getValue(day)
        assertThat(load.busyMinutes).isEqualTo(3 * 60) // 9 h–11 h 30, puis 30 min
        assertThat(load.events).isEqualTo(4)
        assertThat(load.level).isEqualTo(2)
    }

    @Test
    fun `toute la journée n'occupe pas d'heures, une nuit se partage`() {
        // L'import Fossify marque les anniversaires « Occupé » : ils ne doivent pas noircir l'année.
        val birthday = Event(uid = "c", agendaId = 1, title = "Anniversaire", timing = Timing.AllDay(day, day))
        val night = timed("Garde", "2026-11-12T22:00", "2026-11-13T02:00")
        val loads = DayLoad.days(listOf(birthday, night), day, day.plusDays(3), paris)
        assertThat(loads.getValue(day).busyMinutes).isEqualTo(0)
        assertThat(loads.getValue(day).events).isEqualTo(1)
        assertThat(loads.getValue(day.plusDays(2)).busyMinutes).isEqualTo(120)
        assertThat(loads.getValue(day.plusDays(3)).busyMinutes).isEqualTo(120)
        assertThat(loads.getValue(day.plusDays(3)).level).isEqualTo(2)
    }
}
