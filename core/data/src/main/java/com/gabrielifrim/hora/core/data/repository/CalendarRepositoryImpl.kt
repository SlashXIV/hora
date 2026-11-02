package com.gabrielifrim.hora.core.data.repository

import android.content.Context
import com.gabrielifrim.hora.core.data.R
import com.gabrielifrim.hora.core.data.database.AgendaDao
import com.gabrielifrim.hora.core.data.database.AgendaEntity
import com.gabrielifrim.hora.core.data.database.EventDao
import com.gabrielifrim.hora.core.data.database.EventEntity
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
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
    private val dao: EventDao,
) : EventRepository {

    override fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>> = dao.observeBetween(
        fromMs = first.atStartOfDay(zone).toInstant().toEpochMilli(),
        toMs = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        firstDay = first.toEpochDay(),
        lastDay = last.toEpochDay(),
    ).map { list -> list.map { it.toDomain() } }

    override fun event(id: Long): Flow<Event?> = dao.observe(id).map { it?.toDomain() }

    override fun search(query: String): Flow<List<Event>> {
        // % et _ tapés par l'utilisateur sont des caractères, pas des jokers SQL.
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.search("%$escaped%").map { list -> list.map { it.toDomain() } }
    }

    override suspend fun save(event: Event): Long = dao.upsert(event.toEntity()).let { if (it == -1L) event.id else it }

    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun AgendaEntity.toDomain() = Agenda(id, name, colorKey, hidden, sortOrder)
private fun Agenda.toEntity() = AgendaEntity(id, name, colorKey, hidden, sortOrder)

private fun EventEntity.toDomain() = Event(
    id = id,
    uid = uid,
    agendaId = agendaId,
    title = title,
    location = location,
    notes = notes,
    colorKey = colorKey,
    timing = if (allDay) {
        Timing.AllDay(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end))
    } else {
        // Un fuseau inconnu (base restaurée sur un autre appareil) retombe sur celui du téléphone.
        val zone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        Timing.Timed(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), zone)
    },
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
    )
}
