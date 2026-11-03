package com.gabrielifrim.hora.core.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.gabrielifrim.hora.core.data.R
import com.gabrielifrim.hora.core.data.database.AgendaDao
import com.gabrielifrim.hora.core.data.database.AgendaEntity
import com.gabrielifrim.hora.core.data.database.EventDao
import com.gabrielifrim.hora.core.data.database.EventEntity
import com.gabrielifrim.hora.core.data.database.FiredReminderEntity
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.ReminderDao
import com.gabrielifrim.hora.core.data.database.ReminderSetEntity
import com.gabrielifrim.hora.core.data.database.SnoozeEntity
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.ReminderCodec
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.recurrence.EditPlan
import com.gabrielifrim.hora.core.domain.recurrence.Recurrences
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.reminder.ReminderPlanner
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import com.gabrielifrim.hora.core.domain.repository.ImportGroup
import com.gabrielifrim.hora.core.domain.repository.ImportReport
import com.gabrielifrim.hora.core.domain.repository.TransferRepository
import com.gabrielifrim.hora.core.domain.repository.ReminderSet
import com.gabrielifrim.hora.core.domain.repository.ReminderSetRepository
import com.gabrielifrim.hora.core.domain.repository.TemplateRepository
import com.gabrielifrim.hora.core.domain.model.EventTemplate
import com.gabrielifrim.hora.core.data.database.EventTemplateEntity
import com.gabrielifrim.hora.core.domain.repository.ReminderStore
import com.gabrielifrim.hora.core.domain.repository.Snooze
import com.gabrielifrim.hora.core.domain.repository.firedKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class AgendaRepositoryImpl @Inject constructor(
    private val dao: AgendaDao,
    @param:ApplicationContext private val context: Context,
) : AgendaRepository {

    override val agendas: Flow<List<Agenda>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(agenda: Agenda): Long = dao.upsert(agenda.toEntity()).let { if (it == -1L) agenda.id else it }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun ensureDefaults() {
        if (dao.count() > 0) return
        // Noms dans strings.xml : ce sont des textes vus par l'utilisateur, renommables ensuite.
        listOf(R.string.agenda_personal to "BLUE", R.string.agenda_work to "FIR", R.string.agenda_family to "PLUM")
            .forEachIndexed { i, (name, color) ->
                dao.upsert(AgendaEntity(name = context.getString(name), colorKey = color, hidden = false, sortOrder = i))
            }
    }
}

internal class EventRepositoryImpl @Inject constructor(
    private val db: HoraDatabase,
    private val dao: EventDao,
) : EventRepository {

    override fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>> {
        val toMs = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val simple = dao.observeBetween(first.atStartOfDay(zone).toInstant().toEpochMilli(), toMs, first.toEpochDay(), last.toEpochDay())
        return combine(simple, dao.observeSeries(toMs, last.toEpochDay()), dao.observeOverrides()) { singles, series, overrides ->
            val bySeries = overrides.groupBy({ it.parentId }, { it.recurrenceId })
            singles.map { it.toDomain() } + series.flatMap { s ->
                Recurrences.expand(s.toDomain(), first, last, zone, bySeries[s.id].orEmpty().toSet())
            }
        }
    }

    override fun event(id: Long): Flow<Event?> = dao.observe(id).map { it?.toDomain() }

    override val pinned: Flow<List<Event>> = dao.observePinned().map { list -> list.map { it.toDomain() } }

    override suspend fun children(seriesId: Long): List<Event> = dao.children(seriesId).map { it.toDomain() }

    override fun search(query: String): Flow<List<Event>> {
        // % et _ tapés par l'utilisateur sont des caractères, pas des jokers SQL.
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.search("%$escaped%").map { list -> list.map { it.toDomain() } }
    }

    override suspend fun save(event: Event): Long = dao.upsert(event.toEntity()).let { if (it == -1L) event.id else it }

    override suspend fun delete(id: Long) = db.withTransaction {
        dao.deleteChildren(id)
        dao.delete(id)
    }

    override suspend fun apply(plan: EditPlan): Long? = db.withTransaction {
        plan.delete.forEach { id ->
            dao.deleteChildren(id)
            dao.delete(id)
        }
        // Le dernier enregistré est celui que l'on vient de modifier : l'occurrence ou la nouvelle série.
        plan.save.map { save(it) }.lastOrNull()
    }
}

internal class ReminderSetRepositoryImpl @Inject constructor(private val dao: ReminderDao) : ReminderSetRepository {
    override val sets: Flow<List<ReminderSet>> = dao.observeSets().map { list ->
        list.map { ReminderSet(it.id, it.name, ReminderCodec.decode(it.spec)) }
    }

    override suspend fun save(set: ReminderSet): Long =
        dao.upsertSet(ReminderSetEntity(set.id, set.name, ReminderCodec.encode(set.reminders))).let { if (it == -1L) set.id else it }

    override suspend fun delete(id: Long) = dao.deleteSet(id)
}

internal class TemplateRepositoryImpl @Inject constructor(private val dao: ReminderDao) : TemplateRepository {
    override val templates: Flow<List<EventTemplate>> = dao.observeTemplates().map { list ->
        list.map { EventTemplate(it.id, it.title, it.agendaId, it.colorKey, it.location, it.notes, it.allDay, it.minutes, it.busy, ReminderCodec.decode(it.reminders)) }
    }

    override suspend fun save(template: EventTemplate): Long = with(template) {
        dao.upsertTemplate(EventTemplateEntity(id, title, agendaId, colorKey, location, notes, allDay, minutes, busy, ReminderCodec.encode(reminders)))
            .let { if (it == -1L) id else it }
    }

    override suspend fun delete(id: Long) = dao.deleteTemplate(id)
}

internal class ReminderStoreImpl @Inject constructor(
    private val db: HoraDatabase,
    private val events: EventDao,
    private val reminders: ReminderDao,
) : ReminderStore {

    override val changes: Flow<Unit> = db.invalidationTracker.createFlow("event", "snooze").map { }

    override suspend fun eventsWithReminders(): List<Event> = events.withReminders().map { it.toDomain() }

    override suspend fun overrides(): Map<Long, Set<Long>> =
        events.overrideRows().groupBy({ it.parentId }, { it.recurrenceId }).mapValues { it.value.toSet() }

    override suspend fun firedSince(since: Instant): Set<String> =
        reminders.firedSince(since.toEpochMilli()).map { firedKey(it.eventId, it.occurrence, it.reminder) }.toSet()

    override suspend fun markFired(key: String, eventId: Long, occurrence: Long, at: Instant) =
        reminders.markFired(FiredReminderEntity(eventId, occurrence, key.substringAfterLast('|'), at.toEpochMilli()))

    override suspend fun forgetFiredBefore(before: Instant) = reminders.forgetFiredBefore(before.toEpochMilli())

    override suspend fun snoozes(): List<Snooze> = reminders.snoozes().map { Snooze(it.id, it.eventId, it.occurrence, Instant.ofEpochMilli(it.triggerAt), it.alarm) }

    override suspend fun snooze(eventId: Long, occurrence: Long, at: Instant, alarm: Boolean) =
        reminders.snooze(SnoozeEntity(eventId = eventId, occurrence = occurrence, triggerAt = at.toEpochMilli(), alarm = alarm))

    override suspend fun deleteSnooze(id: Long) = reminders.deleteSnooze(id)

    override suspend fun event(id: Long): Event? = events.get(id)?.toDomain()
}

internal class TransferRepositoryImpl @Inject constructor(
    private val db: HoraDatabase,
    private val agendas: AgendaDao,
    private val events: EventDao,
    private val reminders: ReminderDao,
) : TransferRepository {

    override suspend fun events(agendaIds: Set<Long>): List<Event> = events.inAgendas(agendaIds.toList()).map { it.toDomain() }

    override suspend fun import(groups: List<ImportGroup>): ImportReport = db.withTransaction {
        val existingAgendas = agendas.all().toMutableList()
        var added = 0
        var updated = 0
        var agendasCreated = 0
        val idByUid = mutableMapOf<String, Long>()
        val saved = mutableListOf<Event>()

        for (group in groups) {
            val agendaId = existingAgendas.firstOrNull { it.name.trim().equals(group.agendaName.trim(), ignoreCase = true) }?.id
                ?: AgendaEntity(name = group.agendaName.trim(), colorKey = group.colorKey, hidden = false, sortOrder = (existingAgendas.maxOfOrNull { it.sortOrder } ?: -1) + 1)
                    .let { entity -> entity.copy(id = agendas.upsert(entity)) }
                    .also { existingAgendas += it; agendasCreated++ }
                    .id
            // Les séries d'abord : une occurrence modifiée a besoin de l'id de la sienne.
            for (item in group.items.sortedBy { it.parentUid != null }) {
                val uid = distinctUid(item.event)
                val previous = events.byUid(uid)
                val parentId = item.parentUid?.let { idByUid[it] ?: events.byUid(it)?.id }
                // Sans sa série (ni dans le fichier, ni en base), l'occurrence reste un événement simple.
                val event = item.event.copy(
                    id = previous?.id ?: 0,
                    uid = uid,
                    agendaId = agendaId,
                    parentId = parentId,
                    recurrenceId = item.event.recurrenceId.takeIf { parentId != null },
                )
                val id = events.upsert(event.toEntity()).let { if (it == -1L) event.id else it }
                idByUid[item.event.uid] = id
                saved += event.copy(id = id)
                if (previous == null) added++ else updated++
            }
        }
        markPastRemindersFired(saved)
        ImportReport(added, updated, agendasCreated)
    }

    /**
     * Fossify numérote les événements de son agenda local (« 62 ») : deux exports de
     * deux téléphones réutilisent les mêmes numéros. Un tel UID déjà pris par un
     * autre événement (autre titre ou autre début) en reçoit un dérivé plutôt que
     * d'écraser l'existant. Limite : un événement renommé dans Fossify puis
     * réimporté est ajouté au lieu d'être mis à jour.
     */
    private suspend fun distinctUid(event: Event): String {
        val uid = event.uid
        if (uid.isEmpty() || !uid.all(Char::isDigit)) return uid
        val existing = events.byUid(uid) ?: return uid
        val start = event.toEntity().start
        if (existing.title == event.title && existing.start == start) return uid
        return "$uid~" + "${event.title}|$start".hashCode().toUInt().toString(16)
    }

    /**
     * Un rappel d'un événement importé dont l'heure vient de passer serait rattrapé
     * comme « manqué » : sur un import de tout un agenda, une avalanche. Il est noté
     * comme déjà présenté ; les rappels à venir sonnent normalement.
     */
    private suspend fun markPastRemindersFired(saved: List<Event>) {
        val now = Instant.now()
        val overrides = saved.filter { it.parentId != null && it.recurrenceId != null }.groupBy({ it.parentId!! }, { it.recurrenceId!! }).mapValues { it.value.toSet() }
        ReminderPlanner.plan(saved.filter { it.reminders.isNotEmpty() }, overrides, emptyList(), emptySet(), now, ZoneId.systemDefault())
            .due.forEach { due ->
                val key = due.firedKey ?: return@forEach
                reminders.markFired(FiredReminderEntity(due.event.id, due.occurrence, key.substringAfterLast('|'), now.toEpochMilli()))
            }
    }
}

private fun AgendaEntity.toDomain() =Agenda(id, name, colorKey, hidden, sortOrder, ReminderCodec.decode(defaultReminders))
private fun Agenda.toEntity() = AgendaEntity(id, name, colorKey, hidden, sortOrder, ReminderCodec.encode(defaultReminders))

private fun EventEntity.toDomain() = Event(
    id = id,
    uid = uid,
    agendaId = agendaId,
    title = title,
    location = location,
    notes = notes,
    colorKey = colorKey,
    busy = busy,
    pinned = pinned,
    timing = if (allDay) {
        Timing.AllDay(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end))
    } else {
        // Un fuseau inconnu (base restaurée sur un autre appareil) retombe sur celui du téléphone.
        val zone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        Timing.Timed(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), zone)
    },
    reminders = ReminderCodec.decode(reminders),
    recurrence = rrule?.let { rule -> Recurrence(rule, exdates.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()) },
    parentId = parentId,
    recurrenceId = recurrenceId,
)

private fun Event.toEntity(): EventEntity {
    val t = timing
    return EventEntity(
        id = id,
        uid = uid,
        agendaId = agendaId,
        title = title,
        location = location,
        notes = notes,
        colorKey = colorKey,
        allDay = t is Timing.AllDay,
        start = when (t) {
            is Timing.AllDay -> t.first.toEpochDay()
            is Timing.Timed -> t.start.toEpochMilli()
        },
        end = when (t) {
            is Timing.AllDay -> t.last.toEpochDay()
            is Timing.Timed -> t.end.toEpochMilli()
        },
        timeZone = (t as? Timing.Timed)?.zone?.id,
        reminders = ReminderCodec.encode(reminders),
        rrule = recurrence?.rule,
        exdates = recurrence?.exceptions.orEmpty().sorted().joinToString(","),
        parentId = parentId,
        recurrenceId = recurrenceId,
        busy = busy,
        pinned = pinned,
    )
}
