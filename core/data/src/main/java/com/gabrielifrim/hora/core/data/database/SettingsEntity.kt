package com.gabrielifrim.hora.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Les réglages, en une seule ligne. Énumérations stockées par NOM : réordonner une
 * énumération ne corrompt rien ; un nom inconnu retombe sur la valeur par défaut.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val theme: String,
    val motion: String,
    val haptics: Boolean,
    /** Nom d'un `DayOfWeek`, ou null pour suivre la langue du téléphone. */
    @ColumnInfo(name = "first_day_of_week") val firstDayOfWeek: String?,
    @ColumnInfo(name = "show_week_numbers") val showWeekNumbers: Boolean,
    @ColumnInfo(name = "clock_format") val clockFormat: String,
    @ColumnInfo(name = "alsace_moselle", defaultValue = "0") val alsaceMoselle: Boolean = false,
    @ColumnInfo(defaultValue = "1") val bridges: Boolean = true,
    @ColumnInfo(name = "more_feasts", defaultValue = "0") val moreFeasts: Boolean = false,
    /** Noms des `AlmanacGroup` masqués, séparés par des virgules. */
    @ColumnInfo(name = "hidden_almanac", defaultValue = "MOON") val hiddenAlmanac: String = "MOON",
    @ColumnInfo(name = "place_name", defaultValue = "Bordeaux") val placeName: String = "Bordeaux",
    @ColumnInfo(defaultValue = "44.8378") val latitude: Double = 44.8378,
    @ColumnInfo(defaultValue = "-0.5792") val longitude: Double = -0.5792,
    /** Réglages des prières (`PrayerCodec`) ; vide : valeurs par défaut, pratique désactivée. */
    @ColumnInfo(defaultValue = "") val prayer: String = "",
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
