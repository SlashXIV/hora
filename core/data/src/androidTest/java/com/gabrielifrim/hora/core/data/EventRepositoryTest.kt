package com.gabrielifrim.hora.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gabrielifrim.hora.core.data.database.AgendaEntity
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.applyHoraConfiguration
import com.gabrielifrim.hora.core.data.repository.EventRepositoryImpl
import com.gabrielifrim.hora.core.data.repository.ReminderStoreImpl
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Écrire un événement ne fait pas ressonner en « manqué » un rappel dont l'heure est déjà passée. */
@RunWith(AndroidJUnit4::class)
class EventRepositoryTest {
    private lateinit var db: HoraDatabase
    private lateinit var events: EventRepositoryImpl
    private lateinit var store: ReminderStoreImpl
    private var agenda = 0L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java).applyHoraConfiguration().build()
        agenda = db.agendaDao().upsert(AgendaEntity(name = "Perso", colorKey = "PLUM", hidden = false, sortOrder = 0))
        events = EventRepositoryImpl(db, db.eventDao(), db.reminderDao())
        store = ReminderStoreImpl(db, db.eventDao(), db.reminderDao())
    }

    @After
    fun close() = db.close()

    private fun startingIn(minutes: Long): Event {
        val start = Instant.now().plus(Duration.ofMinutes(minutes))
        return Event(uid = "e$minutes", agendaId = agenda, title = "Rendez-vous", timing = Timing.Timed(start, start.plus(Duration.ofHours(1)), ZoneId.of("Europe/Paris")), reminders = listOf(Reminder.Before(10)))
    }

    @Test
    fun rappel_passe_note_comme_presente() = runTest {
        // Commencé il y a 5 min : son rappel « 10 min avant » est passé depuis 15 min.
        events.save(startingIn(-5))
        assertThat(store.firedSince(Instant.now().minus(Duration.ofDays(1)))).hasSize(1)
    }

    @Test
    fun rappel_a_venir_reste_a_sonner() = runTest {
        // Dans 10 min 20 s : son rappel tombe dans 20 s, à l'intérieur de la tolérance de 30 s du planificateur.
        events.save(startingIn(10).let { e -> e.copy(timing = (e.timing as Timing.Timed).let { t -> Timing.Timed(t.start.plusSeconds(20), t.end.plusSeconds(20), t.zone) }) })
        events.save(startingIn(60))
        assertThat(store.firedSince(Instant.now().minus(Duration.ofDays(1)))).isEmpty()
    }
}
