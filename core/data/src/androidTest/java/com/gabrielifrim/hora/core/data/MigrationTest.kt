package com.gabrielifrim.hora.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.MIGRATION_1_2
import com.gabrielifrim.hora.core.data.database.MIGRATION_2_3
import com.gabrielifrim.hora.core.data.database.MIGRATION_3_4
import com.gabrielifrim.hora.core.data.database.MIGRATION_4_5
import com.gabrielifrim.hora.core.data.database.MIGRATION_5_6
import com.gabrielifrim.hora.core.data.database.MIGRATION_6_7
import com.gabrielifrim.hora.core.data.database.MIGRATION_7_8
import com.gabrielifrim.hora.core.data.database.applyHoraConfiguration
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chaque version du schéma exporté (core/data/schemas) doit s'ouvrir avec la
 * configuration de production et garder ses données. Une migration ajoutée sans
 * test ici est un trou dans la protection de l'agenda.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HoraDatabase::class.java)

    @Test
    fun v1_opensWithProductionConfigurationAndKeepsSettings() = runTest {
        // createDatabase(…, 1) puis l'ouverture en production fait passer la migration 1 → 2.
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO settings (id, theme, motion, haptics, first_day_of_week, show_week_numbers, clock_format) " +
                    "VALUES (0, 'NOCTURNE', 'CALM', 0, 'MONDAY', 1, 'H24')",
            )
            close()
        }
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java, DB)
            .applyHoraConfiguration()
            .build()
        val row = db.settingsDao().get()
        assertThat(row?.theme).isEqualTo("NOCTURNE")
        assertThat(row?.haptics).isFalse()
        db.close()
    }

    @Test
    fun v1_to_v2_keepsSettingsAndCreatesEmptyCalendarTables() = runTest {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO settings (id, theme, motion, haptics, first_day_of_week, show_week_numbers, clock_format) " +
                    "VALUES (0, 'MIDI', 'EXPRESSIVE', 1, NULL, 0, 'SYSTEM')",
            )
            close()
        }
        // Compare le résultat au schéma exporté 2.json : tables, colonnes, index, clés étrangères.
        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java, DB)
            .applyHoraConfiguration()
            .build()
        assertThat(db.settingsDao().get()?.showWeekNumbers).isFalse()
        assertThat(db.agendaDao().count()).isEqualTo(0)
        db.close()
    }

    @Test
    fun v2_to_v3_keepsEventsAsSimpleEventsWithoutReminders() = runTest {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO agenda (id, name, color_key, hidden, sort_order) VALUES (1, 'Perso', 'BLUE', 0, 0)")
            execSQL(
                "INSERT INTO event (id, uid, agenda_id, title, location, notes, color_key, all_day, start, `end`, time_zone) " +
                    "VALUES (5, 'u-5', 1, 'Dentiste', 'Cabinet', '', NULL, 0, 1793700000000, 1793701800000, 'Europe/Paris')",
            )
            close()
        }
        helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_1_2, MIGRATION_2_3).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java, DB)
            .applyHoraConfiguration()
            .build()
        val event = db.eventDao().get(5)!!
        assertThat(event.title).isEqualTo("Dentiste")
        assertThat(event.reminders).isEmpty()
        assertThat(event.rrule).isNull()
        assertThat(event.parentId).isNull()
        db.close()
    }

    @Test
    fun v3_to_v4_keepsSettingsAndAddsAlmanacDefaults() = runTest {
        helper.createDatabase(DB, 3).apply {
            execSQL(
                "INSERT INTO settings (id, theme, motion, haptics, first_day_of_week, show_week_numbers, clock_format) " +
                    "VALUES (0, 'NOCTURNE', 'CALM', 1, 'MONDAY', 1, 'H24')",
            )
            close()
        }
        helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java, DB)
            .applyHoraConfiguration()
            .build()
        val row = db.settingsDao().get()!!
        assertThat(row.theme).isEqualTo("NOCTURNE")
        assertThat(row.bridges).isTrue()
        assertThat(row.hiddenAlmanac).isEqualTo("MOON")
        assertThat(row.placeName).isEqualTo("Bordeaux")
        db.close()
    }

    @Test
    fun v4_to_v5_keepsAlmanacSettingsAndLeavesPrayerOff() = runTest {
        helper.createDatabase(DB, 4).apply {
            execSQL(
                "INSERT INTO settings (id, theme, motion, haptics, first_day_of_week, show_week_numbers, clock_format, " +
                    "alsace_moselle, bridges, more_feasts, hidden_almanac, place_name, latitude, longitude) " +
                    "VALUES (0, 'MIDI', 'CALM', 1, NULL, 1, 'H24', 1, 0, 0, 'MOON', 'Talence', 44.809, -0.588)",
            )
            close()
        }
        helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java, DB)
            .applyHoraConfiguration()
            .build()
        val row = db.settingsDao().get()!!
        assertThat(row.placeName).isEqualTo("Talence")
        assertThat(row.alsaceMoselle).isTrue()
        assertThat(row.prayer).isEmpty()
        db.close()
    }

    @Test
    fun v5_to_v6_keepsEventsBusy() = runTest {
        helper.createDatabase(DB, 5).apply {
            execSQL("INSERT INTO agenda (id, name, color_key, hidden, sort_order, default_reminders) VALUES (1, 'Perso', 'BLUE', 0, 0, '')")
            execSQL(
                "INSERT INTO event (id, uid, agenda_id, title, location, notes, color_key, all_day, start, `end`, time_zone, reminders, rrule, exdates) " +
                    "VALUES (5, 'u-5', 1, 'Dentiste', 'Cabinet', '', NULL, 0, 1793700000000, 1793701800000, 'Europe/Paris', '', NULL, '')",
            )
            close()
        }
        helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java, DB)
            .applyHoraConfiguration()
            .build()
        val event = db.eventDao().get(5)!!
        assertThat(event.title).isEqualTo("Dentiste")
        assertThat(event.busy).isTrue()
        db.close()
    }

    @Test
    fun v6_to_v7_keepsSettingsWithoutPlages() = runTest {
        helper.createDatabase(DB, 6).apply {
            execSQL(
                "INSERT INTO settings (id, theme, motion, haptics, first_day_of_week, show_week_numbers, clock_format, " +
                    "alsace_moselle, bridges, more_feasts, hidden_almanac, place_name, latitude, longitude, prayer) " +
                    "VALUES (0, 'MIDI', 'CALM', 1, NULL, 1, 'H24', 0, 1, 0, 'MOON', 'Talence', 44.809, -0.588, 'on=true')",
            )
            close()
        }
        helper.runMigrationsAndValidate(DB, 7, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java, DB)
            .applyHoraConfiguration()
            .build()
        val row = db.settingsDao().get()!!
        assertThat(row.prayer).isEqualTo("on=true")
        assertThat(row.plages).isEmpty()
        db.close()
    }

    @Test
    fun v7_to_v8_addsEmptyTemplates() = runTest {
        helper.createDatabase(DB, 7).close()
        helper.runMigrationsAndValidate(DB, 8, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).close()

        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java, DB)
            .applyHoraConfiguration()
            .build()
        assertThat(db.reminderDao().observeTemplates().first()).isEmpty()
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
