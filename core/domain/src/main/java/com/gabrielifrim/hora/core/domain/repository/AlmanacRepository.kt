package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.almanac.AlmanacGroup
import com.gabrielifrim.hora.core.domain.almanac.AlmanacKind
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Les agendas calculés (fériés, fêtes, lune, changements d'heure) vus comme des
 * agendas et des événements en lecture seule. Ils ne passent jamais par la base :
 * ni import, ni export, ni rappel ne peut les toucher.
 *
 * Identifiants négatifs : agenda = -(groupe + 1) ; événement = voir [almanacEventId].
 */
interface AlmanacRepository {
    /** Les agendas calculés, masqués compris. */
    val agendas: Flow<List<Agenda>>

    /** Entrées des agendas calculés visibles qui touchent la période. */
    fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>>

    fun event(id: Long): Flow<Event?>

    suspend fun setHidden(group: AlmanacGroup, hidden: Boolean)
}

fun almanacAgendaId(group: AlmanacGroup): Long = -(group.ordinal + 1L)

fun almanacGroupOf(agendaId: Long): AlmanacGroup? = AlmanacGroup.entries.getOrNull((-agendaId - 1).toInt())

/** Un jour et une sorte d'entrée tiennent dans un id négatif, retrouvable sans rien stocker. */
fun almanacEventId(kind: AlmanacKind, date: LocalDate): Long = -((kind.ordinal + 1L) * 1_000_000 + date.toEpochDay() + 500_000)

fun almanacEntryOf(id: Long): Pair<AlmanacKind, LocalDate>? {
    if (id >= 0) return null
    val v = -id
    val kind = AlmanacKind.entries.getOrNull((v / 1_000_000 - 1).toInt()) ?: return null
    return kind to LocalDate.ofEpochDay(v % 1_000_000 - 500_000)
}
