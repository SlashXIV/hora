package com.gabrielifrim.hora.core.data.repository

import com.gabrielifrim.hora.core.data.database.SettingsDao
import com.gabrielifrim.hora.core.data.database.SettingsEntity
import com.gabrielifrim.hora.core.domain.almanac.AlmanacGroup
import com.gabrielifrim.hora.core.domain.almanac.AlmanacOptions
import com.gabrielifrim.hora.core.domain.almanac.Place
import com.gabrielifrim.hora.core.domain.almanac.PrayerCodec
import com.gabrielifrim.hora.core.domain.model.PlageCodec
import com.gabrielifrim.hora.core.domain.model.UserSettings
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import java.time.DayOfWeek
import java.time.LocalTime
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
        almanac = AlmanacOptions(alsaceMoselle = alsaceMoselle, bridges = bridges, moreFeasts = moreFeasts),
        hiddenAlmanac = hiddenAlmanac.split(',').mapNotNull { n -> AlmanacGroup.entries.firstOrNull { it.name == n.trim() } }.toSet(),
        place = Place(placeName, latitude, longitude),
        prayer = PrayerCodec.decode(prayer),
        plages = PlageCodec.decode(plages),
        digest = if (digest.isEmpty()) null else runCatching { LocalTime.parse(digest) }.getOrDefault(defaults.digest),
        popoteAgenda = popoteAgenda,
    )
}

private fun UserSettings.toEntity() = SettingsEntity(
    theme = theme.name,
    motion = motion.name,
    haptics = haptics,
    firstDayOfWeek = firstDayOfWeek?.name,
    showWeekNumbers = showWeekNumbers,
    clockFormat = clockFormat.name,
    alsaceMoselle = almanac.alsaceMoselle,
    bridges = almanac.bridges,
    moreFeasts = almanac.moreFeasts,
    hiddenAlmanac = hiddenAlmanac.sorted().joinToString(",") { it.name },
    placeName = place.name,
    latitude = place.latitude,
    longitude = place.longitude,
    prayer = PrayerCodec.encode(prayer),
    plages = PlageCodec.encode(plages),
    digest = digest?.toString().orEmpty(),
    popoteAgenda = popoteAgenda,
)
