package com.gabrielifrim.hora.core.data.repository

import com.gabrielifrim.hora.core.data.database.SettingsDao
import com.gabrielifrim.hora.core.data.database.SettingsEntity
import com.gabrielifrim.hora.core.domain.model.UserSettings
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SettingsRepositoryImpl @Inject constructor(
    private val dao: SettingsDao,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dao.observe().map { it?.toDomain() ?: UserSettings() }

    // Lire puis écrire sous verrou : deux réglages touchés coup sur coup ne s'écrasent pas.
    private val writeLock = Mutex()

    override suspend fun update(transform: (UserSettings) -> UserSettings) = writeLock.withLock {
        val current = dao.get()?.toDomain() ?: UserSettings()
        dao.upsert(transform(current).toEntity())
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

private fun SettingsEntity.toDomain(): UserSettings {
    val defaults = UserSettings()
    return UserSettings(
        theme = enumOr(theme, defaults.theme),
        motion = enumOr(motion, defaults.motion),
        haptics = haptics,
        firstDayOfWeek = firstDayOfWeek?.let { name -> DayOfWeek.entries.firstOrNull { it.name == name } },
        showWeekNumbers = showWeekNumbers,
        clockFormat = enumOr(clockFormat, defaults.clockFormat),
    )
}

private fun UserSettings.toEntity() = SettingsEntity(
    theme = theme.name,
    motion = motion.name,
    haptics = haptics,
    firstDayOfWeek = firstDayOfWeek?.name,
    showWeekNumbers = showWeekNumbers,
    clockFormat = clockFormat.name,
)
