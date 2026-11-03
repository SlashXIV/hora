package com.gabrielifrim.hora.core.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Une plage récurrente (« Taff », du lundi au vendredi, 9:00–17:30) : une bande
 * en fond des vues jour et semaine, pas un événement. Elle tient dans la journée :
 * [end] est après [start].
 */
data class Plage(val name: String, val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime) {
    fun isOn(day: DayOfWeek): Boolean = day in days

    /** Nouveau début ; une fin qui ne suit plus est repoussée d'une heure (fin du jour au plus). */
    fun withStart(t: LocalTime): Plage =
        if (end.isAfter(t)) copy(start = t) else copy(start = t, end = if (t.hour < 23) t.plusHours(1) else LocalTime.MAX)

    /** Nouvelle fin ; un début qui ne précède plus est avancé d'une heure (minuit au plus tôt). */
    fun withEnd(t: LocalTime): Plage = when {
        t.isAfter(start) -> copy(end = t)
        t == LocalTime.MIDNIGHT -> copy(start = LocalTime.MIDNIGHT, end = LocalTime.of(1, 0))
        else -> copy(start = if (t.hour >= 1) t.minusHours(1) else LocalTime.MIDNIGHT, end = t)
    }
}

/**
 * Les plages tiennent dans une colonne : une par ligne, « 09:00-17:30;MONDAY,TUESDAY;Taff »,
 * le nom en dernier (il peut contenir des « ; »). Une ligne illisible est ignorée.
 */
object PlageCodec {
    fun encode(plages: List<Plage>): String = plages.joinToString("\n") { p ->
        "${p.start}-${p.end};${p.days.sorted().joinToString(",") { it.name }};${p.name.replace('\n', ' ')}"
    }

    fun decode(text: String?): List<Plage> = text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split(';', limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
        val times = parts[0].split('-').mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
        val days = parts[1].split(',').mapNotNull { n -> DayOfWeek.entries.firstOrNull { it.name == n } }.toSet()
        if (times.size != 2 || !times[1].isAfter(times[0])) return@mapNotNull null
        Plage(parts[2], days, times[0], times[1])
    }
}
