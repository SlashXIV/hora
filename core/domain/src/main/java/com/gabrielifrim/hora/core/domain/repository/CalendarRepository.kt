package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface AgendaRepository {
    /** Tous les agendas, masqués compris, dans l'ordre choisi. */
    val agendas: Flow<List<Agenda>>

    suspend fun save(agenda: Agenda): Long

    /** Supprime l'agenda ET ses événements. */
    suspend fun delete(id: Long)

    /** Crée Perso, Taff et Famille si aucun agenda n'existe : il en faut toujours un. */
    suspend fun ensureDefaults()
}

interface EventRepository {
    /** Événements des agendas visibles qui touchent [first]..[last], vus depuis [zone]. */
    fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>>

    fun event(id: Long): Flow<Event?>

    /** Recherche dans le titre, le lieu et les notes, agendas masqués exclus. */
    fun search(query: String): Flow<List<Event>>

    suspend fun save(event: Event): Long

    suspend fun delete(id: Long)
}
