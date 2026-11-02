package com.gabrielifrim.hora.core.domain.recurrence

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class RecurrenceTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun weekly(rule: String, from: String = "2026-10-05T09:00") = Event(
        id = 1, uid = "u", agendaId = 1, title = "Réunion",
        timing = LocalDateTime.parse(from).atZone(paris).let { Timing.Timed(it.toInstant(), it.plusHours(1).toInstant(), paris) },
        recurrence = Recurrence(rule),
    )

    private fun Event.localStart() = (timing as Timing.Timed).start.atZone(paris).toLocalDateTime()

    @Test
    fun `chaque lundi a 9 h reste a 9 h apres le changement d'heure`() {
        val list = Recurrences.expand(weekly("FREQ=WEEKLY;BYDAY=MO"), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 2), paris)
        assertThat(list.map { it.localStart() }).containsExactly(
            LocalDateTime.of(2026, 10, 19, 9, 0), LocalDateTime.of(2026, 10, 26, 9, 0), LocalDateTime.of(2026, 11, 2, 9, 0),
        ).inOrder()
    }

    @Test
    fun `le 2e mardi du mois`() {
        val spec = RepeatSpec(Frequency.MONTHLY_BY_RANK)
        val start = LocalDate.of(2026, 11, 10) // 2e mardi de novembre
        assertThat(spec.toRule(start, paris)).isEqualTo("FREQ=MONTHLY;BYDAY=2TU")
        val list = Recurrences.expand(weekly(spec.toRule(start, paris), "2026-11-10T18:00"), start, LocalDate.of(2027, 1, 31), paris)
        assertThat(list.map { it.localStart().toLocalDate() }).containsExactly(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 8), LocalDate.of(2027, 1, 12)).inOrder()
    }

    @Test
    fun `fin apres N occurrences et fin a une date`() {
        val count = Recurrences.expand(weekly("FREQ=DAILY;COUNT=3"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), paris)
        assertThat(count).hasSize(3)
        val until = RepeatSpec(Frequency.DAILY, end = RepeatEnd.Until(LocalDate.of(2026, 10, 7))).toRule(LocalDate.of(2026, 10, 5), paris)
        assertThat(Recurrences.expand(weekly(until), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), paris).last().localStart().toLocalDate())
            .isEqualTo(LocalDate.of(2026, 10, 7))
    }

    @Test
    fun `une exception et une occurrence modifiee disparaissent de la serie`() {
        val series = weekly("FREQ=DAILY;COUNT=5")
        val second = Recurrences.expand(series, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9), paris)[1].occurrence!!
        val third = Recurrences.expand(series, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9), paris)[2].occurrence!!
        val shown = Recurrences.expand(series.copy(recurrence = Recurrence("FREQ=DAILY;COUNT=5", setOf(second))), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9), paris, overridden = setOf(third))
        assertThat(shown.map { it.localStart().dayOfMonth }).containsExactly(5, 8, 9).inOrder()
    }

    @Test
    fun `les suivants coupent la serie sans changer le total d'occurrences`() {
        val series = weekly("FREQ=DAILY;COUNT=10")
        val all = Recurrences.expand(series, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), paris)
        val fourth = all[3]
        val (head, tail) = Recurrences.split(series, fourth.occurrence!!, paris)
        val headShown = Recurrences.expand(series.copy(recurrence = head), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), paris)
        val tailShown = Recurrences.expand(fourth.copy(recurrence = tail, occurrence = null), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), paris)
        assertThat(headShown).hasSize(3)
        assertThat(tailShown).hasSize(7)
        assertThat(tailShown.first().localStart()).isEqualTo(fourth.localStart())
    }

    @Test
    fun `toute la journee chaque annee, et le fichier etranger lu en mode tolerant`() {
        val birthday = Event(uid = "b", agendaId = 1, title = "Anniversaire", timing = Timing.AllDay(LocalDate.of(1992, 3, 14), LocalDate.of(1992, 3, 14)), recurrence = Recurrence("RRULE:FREQ=YEARLY"))
        val shown = Recurrences.expand(birthday, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), paris).single()
        assertThat(shown.timing).isEqualTo(Timing.AllDay(LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 14)))
        // Règle illisible : l'événement s'affiche une fois au lieu de faire planter.
        assertThat(Recurrences.expand(birthday.copy(recurrence = Recurrence("FREQ=PARFOIS")), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), paris)).hasSize(1)
    }

    @Test
    fun `l'editeur relit ses propres regles et refuse les autres`() {
        val spec = RepeatSpec(Frequency.WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), end = RepeatEnd.After(8))
        assertThat(RepeatSpec.parse(spec.toRule(LocalDate.of(2026, 11, 3), paris), paris)).isEqualTo(spec)
        val until = RepeatSpec(Frequency.DAILY, end = RepeatEnd.Until(LocalDate.of(2026, 12, 24)))
        assertThat(RepeatSpec.parse(until.toRule(LocalDate.of(2026, 12, 1), paris), paris)).isEqualTo(until)
        assertThat(RepeatSpec.parse(until.toRule(LocalDate.of(2026, 12, 1), null), null)).isEqualTo(until)
        assertThat(RepeatSpec.parse("FREQ=MONTHLY;BYDAY=TU;BYSETPOS=-1", paris)).isNull()
        // Fossify : « chaque année » s'écrit avec le mois du début ; un autre mois reste une règle à part.
        val nov10 = LocalDate.of(2025, 11, 10)
        assertThat(RepeatSpec.parse("FREQ=YEARLY;INTERVAL=1;BYMONTH=11", null, nov10)).isEqualTo(RepeatSpec(Frequency.YEARLY))
        assertThat(RepeatSpec.parse("FREQ=YEARLY;BYMONTH=3", null, nov10)).isNull()
    }
}
