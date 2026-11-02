package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.model.Event

/**
 * Un événement à importer. Une occurrence modifiée à part porte l'UID de sa série
 * ([parentUid]) : la série est retrouvée dans le même import ou déjà en base.
 */
data class ImportItem(val event: Event, val parentUid: String? = null)

/** Des événements à ranger dans un agenda, retrouvé par son nom ou créé avec [colorKey]. */
data class ImportGroup(val agendaName: String, val colorKey: String, val items: List<ImportItem>)

data class ImportReport(val added: Int, val updated: Int, val agendasCreated: Int)

interface TransferRepository {
    /** Les événements tels qu'en base, séries non développées, occurrences modifiées comprises. */
    suspend fun events(agendaIds: Set<Long>): List<Event>

    /**
     * Importe d'un bloc. Un événement dont l'UID existe déjà est mis à jour, jamais
     * dupliqué : réimporter le même fichier ne change rien.
     */
    suspend fun import(groups: List<ImportGroup>): ImportReport
}
