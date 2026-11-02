package com.gabrielifrim.hora.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gabrielifrim.hora.core.data.database.AgendaEntity
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.applyHoraConfiguration
import com.gabrielifrim.hora.core.data.repository.TransferRepositoryImpl
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.ImportGroup
import com.gabrielifrim.hora.core.domain.repository.ImportItem
import com.gabrielifrim.hora.core.domain.repository.ImportReport
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** L'import ne perd rien et ne duplique rien : réimporter le même fichier ne change rien. */
@RunWith(AndroidJUnit4::class)
class TransferRepositoryTest {
    private lateinit var db: HoraDatabase
    private lateinit var repository: TransferRepositoryImpl

    private val zone = ZoneId.of("Europe/Paris")
    private val start = Instant.parse("2026-01-05T08:00:00Z")
    private val series = Event(
        uid = "serie", agendaId = 0, title = "Point",
        timing = Timing.Timed(start, start.plus(Duration.ofHours(1)), zone),
        recurrence = Recurrence("FREQ=WEEKLY"),
    )
    private val movedKey = start.plus(Duration.ofDays(7)).toEpochMilli()
    private val moved = Event(
        uid = "serie#$movedKey", agendaId = 0, title = "Point décalé",
        timing = Timing.Timed(Instant.ofEpochMilli(movedKey).plus(Duration.ofHours(2)), Instant.ofEpochMilli(movedKey).plus(Duration.ofHours(3)), zone),
        recurrenceId = movedKey,
    )
    private val birthday = Event(uid = "anniv", agendaId = 0, title = "Anniversaire", timing = Timing.AllDay(LocalDate.of(1990, 3, 2), LocalDate.of(1990, 3, 2)))

    private val groups = listOf(
        ImportGroup("Travail", "FIR", listOf(ImportItem(moved, parentUid = "serie"), ImportItem(series))),
        ImportGroup("perso", "BLUE", listOf(ImportItem(birthday))),
    )

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HoraDatabase::class.java)
            .applyHoraConfiguration()
            .build()
        db.agendaDao().upsert(AgendaEntity(name = "Perso", colorKey = "PLUM", hidden = false, sortOrder = 0))
        repository = TransferRepositoryImpl(db, db.agendaDao(), db.eventDao(), db.reminderDao())
    }

    @After
    fun close() = db.close()

    @Test
    fun importe_rattache_et_retrouve_les_agendas() = runTest {
        assertThat(repository.import(groups)).isEqualTo(ImportReport(added = 3, updated = 0, agendasCreated = 1))

        val agendas = db.agendaDao().all()
        assertThat(agendas.map { it.name }).containsExactly("Perso", "Travail").inOrder()
        // « perso » rejoint l'agenda Perso existant, sans changer sa couleur.
        assertThat(agendas.first { it.name == "Perso" }.colorKey).isEqualTo("PLUM")

        val events = repository.events(agendas.map { it.id }.toSet())
        val savedSeries = events.single { it.uid == "serie" }
        val savedMoved = events.single { it.uid == moved.uid }
        assertThat(savedMoved.parentId).isEqualTo(savedSeries.id)
        assertThat(savedMoved.recurrenceId).isEqualTo(movedKey)
        assertThat(events.single { it.uid == "anniv" }.agendaId).isEqualTo(agendas.first { it.name == "Perso" }.id)
    }

    @Test
    fun reimporter_met_a_jour_sans_dupliquer() = runTest {
        repository.import(groups)
        val renamed = groups.map { g -> g.copy(items = g.items.map { it.copy(event = it.event.copy(title = it.event.title + " !")) }) }
        assertThat(repository.import(renamed)).isEqualTo(ImportReport(added = 0, updated = 3, agendasCreated = 0))
        val events = repository.events(db.agendaDao().all().map { it.id }.toSet())
        assertThat(events).hasSize(3)
        assertThat(events.map { it.title }).containsExactly("Point !", "Point décalé !", "Anniversaire !")
    }

    @Test
    fun occurrence_sans_sa_serie_reste_un_evenement_simple() = runTest {
        repository.import(listOf(ImportGroup("Travail", "FIR", listOf(ImportItem(moved, parentUid = "absente")))))
        val saved = repository.events(db.agendaDao().all().map { it.id }.toSet()).single()
        assertThat(saved.parentId).isNull()
        assertThat(saved.recurrenceId).isNull()
    }

    @Test
    fun rappel_tout_juste_passe_n_est_pas_rattrape() = runTest {
        val soon = Instant.now().plus(Duration.ofMinutes(5))
        val event = Event(uid = "bientot", agendaId = 0, title = "Bientôt", timing = Timing.Timed(soon, soon, zone), reminders = listOf(Reminder.Before(30), Reminder.Before(1)))
        repository.import(listOf(ImportGroup("Perso", "BLUE", listOf(ImportItem(event)))))
        // 30 min avant : passé, noté comme présenté ; 1 min avant : à venir, il sonnera.
        val fired = db.reminderDao().firedSince(0)
        assertThat(fired.map { it.reminder }).containsExactly(Reminder.Before(30).key())
    }

    @Test
    fun numero_fossify_deja_pris_ne_remplace_pas_un_autre_evenement() = runTest {
        fun local(title: String) = Event(uid = "62", agendaId = 0, title = title, timing = Timing.AllDay(LocalDate.of(1990, 3, 2), LocalDate.of(1990, 3, 2)))
        repository.import(listOf(ImportGroup("Anniversaires", "BLUE", listOf(ImportItem(local("Alice"))))))
        // Même fichier réimporté : mis à jour. Autre téléphone, même numéro : ajouté à côté.
        assertThat(repository.import(listOf(ImportGroup("Anniversaires", "BLUE", listOf(ImportItem(local("Alice"))))))).isEqualTo(ImportReport(0, 1, 0))
        assertThat(repository.import(listOf(ImportGroup("Anniversaires", "BLUE", listOf(ImportItem(local("Bob"))))))).isEqualTo(ImportReport(1, 0, 0))
        val titles = repository.events(db.agendaDao().all().map { it.id }.toSet()).map { it.title }
        assertThat(titles).containsExactly("Alice", "Bob")
    }
}
