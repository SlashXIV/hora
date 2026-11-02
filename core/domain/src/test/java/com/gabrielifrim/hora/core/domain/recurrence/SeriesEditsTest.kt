package com.gabrielifrim.hora.core.domain.recurrence

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class SeriesEditsTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val series = Event(
        id = 7, uid = "sport", agendaId = 1, title = "Sport",
        timing = LocalDateTime.of(2026, 11, 3, 18, 30).atZone(paris).let { Timing.Timed(it.toInstant(), it.plusHours(1).toInstant(), paris) },
        recurrence = Recurrence("FREQ=WEEKLY;BYDAY=TU,TH"),
    )
    private val occurrences = Recurrences.expand(series, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), paris)
    private val third = occurrences[2] // mardi 10 novembre

    private fun Event.at(hour: Int) = copy(timing = (timing as Timing.Timed).let { t ->
        val s = t.start.atZone(paris).withHour(hour)
        Timing.Timed(s.toInstant(), s.plusHours(1).toInstant(), paris)
    })

    @Test
    fun `cet evenement cree une occurrence a part, sans toucher la serie`() {
        val plan = SeriesEdits.update(EditScope.THIS, series, third, third.copy(title = "Sport annulé ?").at(20), emptyList(), paris)
        val child = plan.save.single()
        assertThat(child.parentId).isEqualTo(7)
        assertThat(child.recurrenceId).isEqualTo(third.occurrence)
        assertThat(child.recurrence).isNull()
        assertThat(child.id).isEqualTo(0)
    }

    @Test
    fun `tous decale l'heure de la serie depuis son premier jour`() {
        val plan = SeriesEdits.update(EditScope.ALL, series, third, third.at(19), emptyList(), paris)
        val saved = plan.save.single()
        assertThat((saved.timing as Timing.Timed).start.atZone(paris).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 3, 19, 30))
        assertThat(saved.id).isEqualTo(7)
    }

    @Test
    fun `les suivants coupent la serie et effacent les occurrences modifiees d'apres`() {
        val laterChild = third.copy(id = 99, parentId = 7, recurrenceId = occurrences[4].occurrence, occurrence = null)
        val earlierChild = third.copy(id = 98, parentId = 7, recurrenceId = occurrences[0].occurrence, occurrence = null)
        val plan = SeriesEdits.update(EditScope.FOLLOWING, series, third, third.at(19), listOf(laterChild, earlierChild), paris)
        assertThat(plan.save).hasSize(2)
        assertThat(plan.delete).containsExactly(99L)
        val head = plan.save.first { it.id == 7L }
        assertThat(Recurrences.expand(head, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), paris)).hasSize(2)
    }

    @Test
    fun `supprimer cet evenement ajoute une exception`() {
        val plan = SeriesEdits.delete(EditScope.THIS, series, third, emptyList(), paris)
        assertThat(plan.save.single().recurrence!!.exceptions).containsExactly(third.occurrence)
        assertThat(plan.delete).isEmpty()
    }

    @Test
    fun `supprimer depuis la premiere occurrence supprime toute la serie`() {
        val plan = SeriesEdits.delete(EditScope.FOLLOWING, series, occurrences.first(), emptyList(), paris)
        assertThat(plan.delete).containsExactly(7L)
        assertThat(plan.save).isEmpty()
    }
}
