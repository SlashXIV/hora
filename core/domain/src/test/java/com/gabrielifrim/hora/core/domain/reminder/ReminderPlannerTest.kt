package com.gabrielifrim.hora.core.domain.reminder

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.Snooze
import com.gabrielifrim.hora.core.domain.repository.firedKey
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class ReminderPlannerTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun t(local: String) = LocalDateTime.parse(local).atZone(paris).toInstant()

    private val dentist = Event(
        id = 1, uid = "d", agendaId = 1, title = "Dentiste",
        timing = Timing.Timed(t("2026-11-03T09:00"), t("2026-11-03T09:30"), paris),
        reminders = listOf(Reminder.Anchored(1, LocalTime.of(20, 0)), Reminder.Before(60), Reminder.Before(10, alarm = true)),
    )

    /** Les rappels déjà présentés de l'occurrence du 3 novembre, sauf [except]. */
    private fun firedAll(vararg except: Reminder) = dentist.reminders.filter { it !in except }
        .map { firedKey(1, t("2026-11-03T09:00").toEpochMilli(), it.key()) }.toSet()

    @Test
    fun `la prochaine echeance est la plus proche, et l'alarme est reperee`() {
        val before = ReminderPlanner.plan(listOf(dentist), emptyMap(), emptyList(), emptySet(), t("2026-11-02T19:00"), paris)
        assertThat(before.next).isEqualTo(t("2026-11-02T20:00"))
        assertThat(before.nextIsAlarm).isFalse()
        val plan = ReminderPlanner.plan(listOf(dentist), emptyMap(), emptyList(), firedAll(Reminder.Before(10, alarm = true)), t("2026-11-03T08:30"), paris)
        assertThat(plan.due).isEmpty()
        assertThat(plan.next).isEqualTo(t("2026-11-03T08:50"))
        assertThat(plan.nextIsAlarm).isTrue()
    }

    @Test
    fun `un rappel manque depuis moins de 24 h est rattrape, une seule fois`() {
        // Téléphone éteint la veille au soir : à 7 h, le rappel de 20 h est rattrapé.
        val first = ReminderPlanner.plan(listOf(dentist), emptyMap(), emptyList(), emptySet(), t("2026-11-03T07:00"), paris)
        assertThat(first.due.map { it.at }).containsExactly(t("2026-11-02T20:00"))
        val again = ReminderPlanner.plan(listOf(dentist), emptyMap(), emptyList(), setOf(first.due.single().firedKey!!), t("2026-11-03T07:00"), paris)
        assertThat(again.due).isEmpty()
        assertThat(again.next).isEqualTo(t("2026-11-03T08:00"))
    }

    @Test
    fun `une serie arme le rappel de sa prochaine occurrence, pas celui d'une occurrence modifiee`() {
        val weekly = dentist.copy(id = 2, recurrence = Recurrence("FREQ=WEEKLY"), reminders = listOf(Reminder.Before(60)))
        val plan = ReminderPlanner.plan(listOf(weekly), emptyMap(), emptyList(), emptySet(), t("2026-11-03T12:00"), paris)
        assertThat(plan.next).isEqualTo(t("2026-11-10T08:00"))
        val skipped = ReminderPlanner.plan(listOf(weekly), mapOf(2L to setOf(t("2026-11-10T09:00").toEpochMilli())), emptyList(), emptySet(), t("2026-11-03T12:00"), paris)
        assertThat(skipped.next).isEqualTo(t("2026-11-17T08:00"))
    }

    @Test
    fun `un report sonne a son heure`() {
        val snooze = Snooze(id = 9, eventId = 1, occurrence = t("2026-11-03T09:00").toEpochMilli(), at = t("2026-11-03T08:20"), alarm = false)
        val plan = ReminderPlanner.plan(listOf(dentist), emptyMap(), listOf(snooze), firedAll(), t("2026-11-03T08:20"), paris)
        assertThat(plan.due.single().snoozeId).isEqualTo(9)
        assertThat(plan.due.single().event.title).isEqualTo("Dentiste")
    }
}
