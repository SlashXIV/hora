package com.gabrielifrim.hora.core.domain.quick

import com.gabrielifrim.hora.core.domain.model.Reminder
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class QuickEntryTest {
    // Mardi 3 novembre 2026, 10:00.
    private val now = LocalDateTime.of(2026, 11, 3, 10, 0)
    private fun parse(s: String) = QuickEntry.parse(s, now)
    private fun day(d: Int, m: Int = 11, y: Int = 2026) = LocalDate.of(y, m, d)

    @Test
    fun `phrase complète - titre, jour, heure, durée, lieu, rappel`() {
        assertThat(parse("Dentiste jeudi 14h30 1h @Cabinet Dupont rappel 1h avant")).isEqualTo(
            QuickEntry(
                title = "Dentiste", date = day(5), start = LocalTime.of(14, 30), duration = Duration.ofHours(1),
                location = "Cabinet Dupont", reminders = listOf(Reminder.Before(60)),
            ),
        )
    }

    @Test
    fun `mots de liaison consommés seulement s'ils introduisent quelque chose`() {
        val e = parse("Dîner de famille demain à 20h")
        assertThat(e.title).isEqualTo("Dîner de famille")
        assertThat(e.date).isEqualTo(day(4))
        assertThat(e.start).isEqualTo(LocalTime.of(20, 0))
    }

    @Test
    fun `plages horaires - de 14h à 16h, 9h-12h30, midi`() {
        parse("Atelier de 14h à 16h").let { assertThat(listOf(it.title, it.start, it.end)).containsExactly("Atelier", LocalTime.of(14, 0), LocalTime.of(16, 0)).inOrder() }
        parse("Formation 9h-12h30").let { assertThat(listOf(it.start, it.end)).containsExactly(LocalTime.of(9, 0), LocalTime.of(12, 30)).inOrder() }
        assertThat(parse("Déjeuner avec Éric midi").start).isEqualTo(LocalTime.NOON)
        assertThat(parse("Appel 14:45 pendant 30 min").let { it.start to it.duration }).isEqualTo(LocalTime.of(14, 45) to Duration.ofMinutes(30))
    }

    @Test
    fun `dates - le 12, 12 novembre, 25 12, 1er janvier, Après-demain`() {
        assertThat(parse("Loyer le 1").date).isEqualTo(day(1, 12)) // le 1er est passé : mois suivant
        assertThat(parse("Loyer le 12").date).isEqualTo(day(12))
        assertThat(parse("Noël 25/12").date).isEqualTo(day(25, 12))
        assertThat(parse("Bilan 2/11").date).isEqualTo(day(2, 11, 2027)) // passé : l'an prochain
        assertThat(parse("Réveillon 1er janvier").date).isEqualTo(day(1, 1, 2027))
        assertThat(parse("Concert 14 mars 2027 21h").let { it.date to it.start }).isEqualTo(day(14, 3, 2027) to LocalTime.of(21, 0))
        assertThat(parse("Après-demain, courses").let { it.date to it.title }).isEqualTo(day(5) to "courses")
    }

    @Test
    fun `jour de la semaine - aujourd'hui si l'heure n'est pas passée`() {
        assertThat(parse("Réunion mardi 14h").date).isEqualTo(day(3))
        assertThat(parse("Réunion mardi 9h").date).isEqualTo(day(10))
        assertThat(parse("Foot samedi prochain").date).isEqualTo(day(7))
    }

    @Test
    fun `sans rien de reconnu - tout est le titre`() {
        assertThat(parse("Appeler maman")).isEqualTo(QuickEntry(title = "Appeler maman"))
        assertThat(parse("Rappel")).isEqualTo(QuickEntry(title = "Rappel"))
        assertThat(parse("Lire 3 chapitres").title).isEqualTo("Lire 3 chapitres")
    }

    @Test
    fun `rappel la veille - 20 h par défaut, ou l'heure dite`() {
        assertThat(parse("Contrôle technique vendredi 9h rappel la veille").reminders).containsExactly(Reminder.Anchored(1, LocalTime.of(20, 0)))
        parse("Contrôle vendredi rappel la veille à 18h").let {
            assertThat(it.reminders).containsExactly(Reminder.Anchored(1, LocalTime.of(18, 0)))
            assertThat(it.title).isEqualTo("Contrôle")
        }
    }

    @Test
    fun `parties - chaque mot coloré selon ce qu'il est devenu`() {
        val text = "Dîner de famille  demain à 20h @Chez Éric rappel 1h avant"
        val parts = QuickEntry.parts(text, now).map { (range, part) -> text.substring(range) to part }
        assertThat(parts).containsExactly(
            "Dîner" to QuickEntry.Part.TITLE, "de" to QuickEntry.Part.TITLE, "famille" to QuickEntry.Part.TITLE,
            "demain" to QuickEntry.Part.WHEN, "à" to QuickEntry.Part.WHEN, "20h" to QuickEntry.Part.WHEN,
            "@Chez" to QuickEntry.Part.PLACE, "Éric" to QuickEntry.Part.PLACE,
            "rappel" to QuickEntry.Part.REMINDER, "1h" to QuickEntry.Part.REMINDER, "avant" to QuickEntry.Part.REMINDER,
        ).inOrder()
    }
}
