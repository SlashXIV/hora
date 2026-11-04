package com.gabrielifrim.hora.feature.event

import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.quick.QuickEntry
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class EventDraftTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `changer le debut garde la duree`() {
        val draft = EventDraft.new(1, LocalDate.of(2026, 11, 3), minute = 14 * 60 + 30, zone = paris)
        val moved = draft.withStart(LocalDateTime.of(2026, 11, 4, 9, 0))
        assertThat(moved.end).isEqualTo(LocalDateTime.of(2026, 11, 4, 10, 0))
    }

    @Test
    fun `une fin avant le debut est ramenee au debut`() {
        val draft = EventDraft.new(1, LocalDate.of(2026, 11, 3), minute = 600, zone = paris)
        assertThat(draft.withEnd(LocalDateTime.of(2026, 11, 3, 8, 0)).end).isEqualTo(draft.start)
    }

    @Test
    fun `deplacer un evenement le pose au meme horaire, meme apres le changement d'heure`() {
        // Le 24 octobre 2026 (heure d'été) à 9 h, déplacé au 26 (heure d'hiver) : toujours 9 h.
        val event = EventDraft(agendaId = 1, title = "Dentiste", start = LocalDateTime.of(2026, 10, 24, 9, 0), end = LocalDateTime.of(2026, 10, 24, 10, 0), zone = paris).toEvent()
        val moved = event.movedTo(LocalDate.of(2026, 10, 26)).timing as Timing.Timed
        assertThat(moved.start.atZone(paris).toLocalTime().hour).isEqualTo(9)
        assertThat(java.time.Duration.between(moved.start, moved.end).toMinutes()).isEqualTo(60)
    }

    @Test
    fun `sans titre on ne peut pas enregistrer`() {
        val draft = EventDraft.new(1, LocalDate.of(2026, 11, 3), minute = null, zone = paris)
        assertThat(draft.canSave).isFalse()
        assertThat(draft.copy(title = "Courses").canSave).isTrue()
    }

    @Test
    fun `toute la journee couvre du premier au dernier jour`() {
        val draft = EventDraft(agendaId = 1, title = "Congés", allDay = true, start = LocalDateTime.of(2026, 8, 3, 9, 0), end = LocalDateTime.of(2026, 8, 14, 10, 0), zone = paris)
        assertThat(draft.toEvent().timing).isEqualTo(Timing.AllDay(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 14)))
    }

    @Test
    fun `saisie rapide - heure et durée, journée sans heure, rappels ajoutés aux défauts`() {
        val base = EventDraft.new(agendaId = 1, date = LocalDate.of(2026, 11, 3), minute = null, zone = paris, now = LocalDateTime.of(2026, 11, 3, 10, 20), reminders = listOf(Reminder.Before(15)))
        val now = LocalDateTime.of(2026, 11, 3, 10, 20)
        val rdv = base.withQuick(QuickEntry.parse("Dentiste jeudi 14h30 45 min rappel 1h avant", now))
        assertThat(rdv.title).isEqualTo("Dentiste")
        assertThat(rdv.start to rdv.end).isEqualTo(LocalDateTime.of(2026, 11, 5, 14, 30) to LocalDateTime.of(2026, 11, 5, 15, 15))
        assertThat(rdv.reminders).containsExactly(Reminder.Before(15), Reminder.Before(60))
        val anniv = base.withQuick(QuickEntry.parse("Anniversaire Eric samedi", now))
        assertThat(anniv.allDay).isTrue()
        assertThat(anniv.canSave).isTrue()
        assertThat(anniv.start.toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 7))
        // Rien de daté : l'heure par défaut du brouillon, une heure.
        val call = base.withQuick(QuickEntry.parse("Appeler maman", now))
        assertThat(call.start to call.end).isEqualTo(LocalDateTime.of(2026, 11, 3, 11, 0) to LocalDateTime.of(2026, 11, 3, 12, 0))
    }

    @Test
    fun `modèle - tout sauf la date, durée reprise`() {
        val base = EventDraft.new(agendaId = 1, date = LocalDate.of(2026, 11, 3), minute = 9 * 60, zone = paris)
        val dentist = com.gabrielifrim.hora.core.domain.model.EventTemplate(
            title = "Dentiste", agendaId = 4, location = "Cabinet", minutes = 45, busy = true, reminders = listOf(Reminder.Before(60)),
        )
        val d = base.withTemplate(dentist)
        assertThat(listOf(d.title, d.agendaId, d.location, d.reminders)).containsExactly("Dentiste", 4L, "Cabinet", listOf(Reminder.Before(60))).inOrder()
        assertThat(d.start to d.end).isEqualTo(LocalDateTime.of(2026, 11, 3, 9, 0) to LocalDateTime.of(2026, 11, 3, 9, 45))
        // Un modèle dont l'agenda a été supprimé garde celui du brouillon.
        assertThat(base.withTemplate(dentist.copy(agendaId = null)).agendaId).isEqualTo(1L)
    }
}
