package com.gabrielifrim.hora.core.domain.recurrence

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Portée d'une modification dans une série. */
enum class EditScope { THIS, FOLLOWING, ALL }

/** Ce qu'il faut écrire en base : des événements à enregistrer, d'autres à supprimer. */
data class EditPlan(val save: List<Event> = emptyList(), val delete: List<Long> = emptyList())

/**
 * Traduit « cet événement / les suivants / tous » en écritures, sans toucher la
 * base : testable, et appliqué ensuite dans une seule transaction.
 *
 * [series] est l'événement d'origine (avec sa règle), [occurrence] l'occurrence
 * touchée (clé et horaire calculés), [children] les occurrences déjà modifiées à part.
 */
object SeriesEdits {

    fun update(scope: EditScope, series: Event, occurrence: Event, edited: Event, children: List<Event>, zone: ZoneId): EditPlan {
        val key = requireNotNull(occurrence.occurrence ?: occurrence.recurrenceId) { "pas une occurrence" }
        return when (scope) {
            EditScope.THIS -> {
                val existing = children.firstOrNull { it.recurrenceId == key }
                // L'occurrence modifiée devient un événement à part, rattaché à sa série.
                EditPlan(save = listOf(edited.copy(
                    id = existing?.id ?: 0, uid = existing?.uid ?: "${series.uid}#$key",
                    recurrence = null, parentId = series.id, recurrenceId = key, occurrence = null,
                )))
            }
            EditScope.FOLLOWING -> {
                if (series.recurrence == null || isFirst(series, key)) return update(EditScope.ALL, series, occurrence, edited, children, zone)
                val (head, tail) = Recurrences.split(series, key, zone)
                val later = children.filter { (it.recurrenceId ?: 0) >= key }
                EditPlan(
                    save = listOfNotNull(
                        head?.let { series.copy(recurrence = it) },
                        edited.copy(id = 0, uid = "${series.uid}-$key", recurrence = edited.recurrence ?: tail, parentId = null, recurrenceId = null, occurrence = null),
                    ),
                    delete = later.map { it.id } + if (head == null) listOf(series.id) else emptyList(),
                )
            }
            EditScope.ALL -> EditPlan(save = listOf(edited.copy(
                id = series.id, uid = series.uid, parentId = null, recurrenceId = null, occurrence = null,
                // Changer l'heure d'une occurrence change l'heure de toute la série, depuis son premier jour.
                timing = shiftSeries(series.timing, occurrence.timing, edited.timing),
                recurrence = edited.recurrence?.copy(exceptions = series.recurrence?.exceptions.orEmpty()),
            )))
        }
    }

    fun delete(scope: EditScope, series: Event, occurrence: Event, children: List<Event>, zone: ZoneId): EditPlan {
        val key = requireNotNull(occurrence.occurrence ?: occurrence.recurrenceId) { "pas une occurrence" }
        val recurrence = series.recurrence ?: return EditPlan(delete = listOf(series.id) + children.map { it.id })
        return when (scope) {
            EditScope.THIS -> EditPlan(
                save = listOf(series.copy(recurrence = recurrence.copy(exceptions = recurrence.exceptions + key))),
                delete = children.filter { it.recurrenceId == key }.map { it.id },
            )
            EditScope.FOLLOWING -> if (isFirst(series, key)) {
                delete(EditScope.ALL, series, occurrence, children, zone)
            } else {
                val (head, _) = Recurrences.split(series, key, zone)
                EditPlan(
                    save = listOfNotNull(head?.let { series.copy(recurrence = it) }),
                    delete = children.filter { (it.recurrenceId ?: 0) >= key }.map { it.id } + if (head == null) listOf(series.id) else emptyList(),
                )
            }
            EditScope.ALL -> EditPlan(delete = listOf(series.id) + children.map { it.id })
        }
    }

    private fun isFirst(series: Event, key: Long): Boolean = when (val t = series.timing) {
        is Timing.Timed -> key <= t.start.toEpochMilli()
        is Timing.AllDay -> key <= t.first.toEpochDay()
    }

    /** Applique à la série le décalage (heure, durée) fait sur une de ses occurrences. */
    private fun shiftSeries(series: Timing, before: Timing, after: Timing): Timing = when {
        series is Timing.Timed && before is Timing.Timed && after is Timing.Timed -> {
            val zone = after.zone
            val shift = Duration.between(LocalDateTime.ofInstant(before.start, before.zone), LocalDateTime.ofInstant(after.start, after.zone))
            val start = LocalDateTime.ofInstant(series.start, series.zone).plus(shift).atZone(zone)
            Timing.Timed(start.toInstant(), start.toInstant().plus(Duration.between(after.start, after.end)), zone)
        }
        series is Timing.AllDay && before is Timing.AllDay && after is Timing.AllDay -> {
            val first = series.first.plusDays(ChronoUnit.DAYS.between(before.first, after.first))
            Timing.AllDay(first, first.plusDays(ChronoUnit.DAYS.between(after.first, after.last)))
        }
        // Passage de « toute la journée » à une heure fixe (ou l'inverse) : la nouvelle forme, au jour de la série.
        else -> after
    }
}
