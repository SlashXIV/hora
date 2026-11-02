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
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
