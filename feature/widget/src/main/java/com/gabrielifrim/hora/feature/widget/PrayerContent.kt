package com.gabrielifrim.hora.feature.widget

import com.gabrielifrim.hora.core.domain.almanac.Prayer
import com.gabrielifrim.hora.core.domain.almanac.PrayerWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ce que montre le widget Prières à [now] : la prière en cours (comme sur la carte : la
 * dernière commencée dont le temps de nécessité court encore), celles déjà passées, et
 * l'instant où cela change.
 */
internal data class PrayerContent(val current: Prayer?, val past: Set<Prayer>, val changesAt: Instant) {
    companion object {
        fun of(windows: List<PrayerWindow>, now: Instant, today: LocalDate, zone: ZoneId): PrayerContent {
            val current = windows.lastOrNull { !now.isBefore(it.start) && now.isBefore(it.necessaryEnd) }?.prayer
            val past = windows.filter { it.prayer != current && !now.isBefore(it.start) }.map { it.prayer }.toSet()
            val midnight = today.plusDays(1).atStartOfDay(zone).toInstant()
            val changes = windows.flatMap { listOf(it.start, it.necessaryEnd) } + midnight
            return PrayerContent(current, past, changes.filter { it.isAfter(now) }.min())
        }
    }
}
