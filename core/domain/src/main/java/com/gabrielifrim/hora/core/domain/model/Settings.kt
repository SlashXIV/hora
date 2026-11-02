package com.gabrielifrim.hora.core.domain.model

import com.gabrielifrim.hora.core.domain.almanac.AlmanacGroup
import com.gabrielifrim.hora.core.domain.almanac.AlmanacOptions
import com.gabrielifrim.hora.core.domain.almanac.Place
import com.gabrielifrim.hora.core.domain.almanac.Places
import java.time.DayOfWeek

/** Apparence choisie : suivre le téléphone, ou forcer Midi (clair) ou Nocturne (sombre). */
enum class ThemePreference { SYSTEM, MIDI, NOCTURNE }

/** Ambiance : Expressive (tout le mouvement) ou Sobre (fondus courts). */
enum class MotionPreference { EXPRESSIVE, CALM }

/** Format des heures ; SYSTEM suit le réglage Android. */
enum class ClockFormat { SYSTEM, H24, H12 }

/**
 * Réglages de Hora, stockés en base : ils voyagent avec la sauvegarde.
 *
 * @param firstDayOfWeek null : suivre la langue du téléphone (lundi en français).
 */
data class UserSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val motion: MotionPreference = MotionPreference.EXPRESSIVE,
    val haptics: Boolean = true,
    val firstDayOfWeek: DayOfWeek? = null,
    val showWeekNumbers: Boolean = true,
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val almanac: AlmanacOptions = AlmanacOptions(),
    /** Agendas calculés masqués ; la lune l'est d'abord (voir le journal des décisions). */
    val hiddenAlmanac: Set<AlmanacGroup> = setOf(AlmanacGroup.MOON),
    /** Lieu du lever et du coucher du soleil. */
    val place: Place = Places.DEFAULT,
)
