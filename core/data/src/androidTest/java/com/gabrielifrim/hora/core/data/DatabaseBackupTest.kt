package com.gabrielifrim.hora.core.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gabrielifrim.hora.core.data.backup.DatabaseBackup
import com.gabrielifrim.hora.core.data.database.AgendaEntity
import com.gabrielifrim.hora.core.data.database.EventEntity
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.applyHoraConfiguration
import com.gabrielifrim.hora.core.domain.repository.RestoreCheck
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** La sauvegarde est la seule protection de l'agenda : elle doit contenir ce qui vient d'être écrit. */
@RunWith(AndroidJUnit4::class)
class DatabaseBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: HoraDatabase
    private lateinit var backup: DatabaseBackup

    @Before
    fun open() {
        context.deleteDatabase(HoraDatabase.NAME)
        db = Room.databaseBuilder(context, HoraDatabase::class.java, HoraDatabase.NAME).applyHoraConfiguration().build()
        backup = DatabaseBackup(context, db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(HoraDatabase.NAME)
    }

    @Test
    fun la_copie_contient_les_ecritures_recentes() = runTest {
        val agendaId = db.agendaDao().upsert(AgendaEntity(name = "Perso", colorKey = "BLUE", hidden = false, sortOrder = 0))
        // Écrit juste avant : encore dans le journal WAL, pas dans le fichier de la base.
        db.eventDao().upsert(EventEntity(uid = "a", agendaId = agendaId, title = "A", location = "", notes = "", colorKey = null, allDay = true, start = 1, end = 1, timeZone = null))
        val file = File(context.cacheDir, "test-sauvegarde.db")
        file.outputStream().use { backup.snapshot(it) }

        val check = backup.inspect(Uri.fromFile(file).toString())
        assertThat(check).isInstanceOf(RestoreCheck.Ready::class.java)
        check as RestoreCheck.Ready
        assertThat(check.events).isEqualTo(1)
        assertThat(check.agendas).isEqualTo(1)
        file.delete()
    }

    @Test
    fun un_autre_fichier_est_refuse() = runTest {
        val file = File(context.cacheDir, "pas-une-base.db").apply { writeText("BEGIN:VCALENDAR") }
        assertThat(backup.inspect(Uri.fromFile(file).toString())).isEqualTo(RestoreCheck.NotHora)
        file.delete()
    }

    @Test
    fun prochaine_sauvegarde_au_prochain_3_h() {
        val day = LocalDateTime.of(2026, 11, 3, 0, 0)
        assertThat(DatabaseBackup.nextRun(day.withHour(1))).isEqualTo(day.withHour(3))
        assertThat(DatabaseBackup.nextRun(day.withHour(3))).isEqualTo(day.plusDays(1).withHour(3))
        assertThat(DatabaseBackup.nextRun(day.withHour(22))).isEqualTo(day.plusDays(1).withHour(3))
    }
}
