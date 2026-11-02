package com.gabrielifrim.hora.core.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** Où et quand l'agenda a été sauvegardé. */
data class BackupState(
    /** Dossier choisi (URI du Storage Access Framework) ; null tant qu'aucun n'est choisi. */
    val folder: String? = null,
    val folderName: String? = null,
    val auto: Boolean = false,
    val last: Instant? = null,
    /** La dernière sauvegarde automatique a échoué (dossier retiré, carte SD absente…). */
    val lastFailed: Boolean = false,
)

/** Ce que contient un fichier à restaurer, vérifié avant de toucher à quoi que ce soit. */
sealed interface RestoreCheck {
    /** [path] : la copie examinée, prête à remplacer la base. */
    data class Ready(val path: String, val events: Int, val agendas: Int) : RestoreCheck

    /** Pas une base Hora (autre fichier, base abîmée). */
    data object NotHora : RestoreCheck

    /** Sauvegarde d'une version plus récente de Hora : on ne sait pas la relire. */
    data object TooNew : RestoreCheck
}

/** Sauvegarde et restauration de la base, seule copie de l'agenda. */
interface BackupRepository {
    val state: StateFlow<BackupState>

    fun chooseFolder(uri: String)

    fun setAuto(on: Boolean)

    /** @return le nom du fichier écrit. */
    suspend fun backupNow(auto: Boolean = false): Result<String>

    suspend fun inspect(uri: String): RestoreCheck

    /** Remplace la base et relance l'application ; ne revient pas. */
    suspend fun restore(check: RestoreCheck.Ready)
}
