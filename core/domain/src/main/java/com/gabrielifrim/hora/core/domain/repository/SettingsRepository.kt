package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Les réglages courants ; les valeurs par défaut tant que rien n'a été enregistré. */
    val settings: Flow<UserSettings>

    suspend fun update(transform: (UserSettings) -> UserSettings)
}
