package com.gabrielifrim.hora.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.applyHoraConfiguration
import com.google.common.truth.Truth.assertThat
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

    private companion object {
        const val DB = "migration-test.db"
    }
}
