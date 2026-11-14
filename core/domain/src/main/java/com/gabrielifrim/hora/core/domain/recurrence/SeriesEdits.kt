package com.gabrielifrim.hora.core.domain.recurrence

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
                        // Règle inchangée : la suite garde le compte restant (COUNT) ; répétition retirée : un événement seul.
                        edited.copy(
                            id = 0, uid = "${series.uid}-$key", parentId = null, recurrenceId = null, occurrence = null,
                            recurrence = edited.recurrence?.let { if (it.rule == series.recurrence.rule) tail else it },
                        ),
                    ),
                    delete = later.map { it.id } + if (head == null) listOf(series.id) else emptyList(),
                )
            }
            EditScope.ALL -> {
                // Changer l'heure d'une occurrence change l'heure de toute la série, depuis son premier jour.
                val timing = shiftSeries(series.timing, occurrence.timing, edited.timing)
                // Les clés d'occurrence sont l'instant (ou le jour) de début : supprimées et modifiées suivent le décalage,
                // y compris quand la série passe de la journée à une heure fixe.
                val move = { k: Long -> shiftKey(k, series.timing, timing) }
                val saved = edited.copy(
                    id = series.id, uid = series.uid, parentId = null, recurrenceId = null, occurrence = null, timing = timing,
                    recurrence = edited.recurrence?.copy(exceptions = series.recurrence?.exceptions.orEmpty().map(move).toSet()),
                )
                val moved = children.map { c ->
                    val key = c.recurrenceId?.let(move)
                    // L'uid « série#clé » suit sa clé : sinon une future occurrence modifiée à cette date le reprendrait.
                    c.copy(recurrenceId = key, uid = if (key != null && c.uid == "${series.uid}#${c.recurrenceId}") "${series.uid}#$key" else c.uid)
                }
                // La série en dernier : c'est elle que l'éditeur rouvre.
                EditPlan(save = moved + saved)
            }
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
    fun shiftSeries(series: Timing, before: Timing, after: Timing): Timing = when {
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
        // Passage de « toute la journée » à une heure fixe (ou l'inverse) : la nouvelle forme, au jour de la série
        // décalé comme l'occurrence ; sinon tout le passé de la série disparaîtrait.
        after is Timing.Timed -> {
            val day = dayOf(series).plusDays(ChronoUnit.DAYS.between(dayOf(before), dayOf(after)))
            val start = day.atTime(after.start.atZone(after.zone).toLocalTime()).atZone(after.zone).toInstant()
            Timing.Timed(start, start.plus(Duration.between(after.start, after.end)), after.zone)
        }
        after is Timing.AllDay -> {
            val first = dayOf(series).plusDays(ChronoUnit.DAYS.between(dayOf(before), after.first))
            Timing.AllDay(first, first.plusDays(ChronoUnit.DAYS.between(after.first, after.last)))
        }
        else -> after
    }

    private fun dayOf(t: Timing) = when (t) {
        is Timing.Timed -> t.start.atZone(t.zone).toLocalDate()
        is Timing.AllDay -> t.first
    }

    /** La clé [key] d'une occurrence de la série [from], décalée comme la série devenue [to]. */
    private fun shiftKey(key: Long, from: Timing, to: Timing): Long {
        val days = ChronoUnit.DAYS.between(dayOf(from), dayOf(to))
        return when {
            from is Timing.Timed && to is Timing.Timed -> {
                val shift = Duration.between(LocalDateTime.ofInstant(from.start, from.zone), LocalDateTime.ofInstant(to.start, to.zone))
                LocalDateTime.ofInstant(Instant.ofEpochMilli(key), from.zone).plus(shift).atZone(to.zone).toInstant().toEpochMilli()
            }
            from is Timing.AllDay && to is Timing.AllDay -> key + days
            // Journée vers heure fixe : ce jour-là, à la nouvelle heure.
            from is Timing.AllDay && to is Timing.Timed ->
                LocalDate.ofEpochDay(key + days).atTime(to.start.atZone(to.zone).toLocalTime()).atZone(to.zone).toInstant().toEpochMilli()
            // Heure fixe vers journée : le jour de l'occurrence.
            from is Timing.Timed && to is Timing.AllDay -> Instant.ofEpochMilli(key).atZone(from.zone).toLocalDate().plusDays(days).toEpochDay()
            else -> key
        }
    }
}
