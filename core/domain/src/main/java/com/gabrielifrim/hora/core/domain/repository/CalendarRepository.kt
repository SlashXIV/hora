package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.model.EventTemplate
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.recurrence.EditPlan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface AgendaRepository {
    /** Tous les agendas, masqués compris, dans l'ordre choisi. */
    val agendas: Flow<List<Agenda>>

    suspend fun save(agenda: Agenda): Long

    /** Supprime l'agenda ET ses événements. */
    suspend fun delete(id: Long)

    /** Crée Perso, Taff et Famille si aucun agenda n'existe : il en faut toujours un. */
    suspend fun ensureDefaults()
}

interface EventRepository {
    /**
     * Événements des agendas visibles qui touchent [first]..[last], vus depuis
     * [zone], séries développées en occurrences (`Event.occurrence` rempli).
     */
    fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>>

    fun event(id: Long): Flow<Event?>

    /** Événements épinglés sur Aujourd'hui des agendas visibles, séries non développées. */
    val pinned: Flow<List<Event>>

    /** Les occurrences modifiées à part d'une série. */
    suspend fun children(seriesId: Long): List<Event>

    /** Recherche dans le titre, le lieu et les notes, agendas masqués exclus. */
    fun search(query: String): Flow<List<Event>>

    suspend fun save(event: Event): Long

    /** Supprime l'événement et, si c'est une série, ses occurrences modifiées. */
    suspend fun delete(id: Long)

    /** Applique d'un bloc un plan de modification de série. @return l'id du dernier enregistré (l'événement modifié). */
    suspend fun apply(plan: EditPlan): Long?
}

data class ReminderSet(val id: Long = 0, val name: String, val reminders: List<Reminder>)

interface TemplateRepository {
    /** Par titre. */
    val templates: Flow<List<EventTemplate>>

    suspend fun save(template: EventTemplate): Long

    suspend fun delete(id: Long)
}

interface ReminderSetRepository {
    val sets: Flow<List<ReminderSet>>

    suspend fun save(set: ReminderSet): Long

    suspend fun delete(id: Long)
}

/** Un rappel reporté, qui sonnera à [at]. */
data class Snooze(val id: Long, val eventId: Long, val occurrence: Long, val at: Instant, val alarm: Boolean)

/** Ce dont le moteur d'alarmes a besoin, et rien de plus. */
interface ReminderStore {
    /** Toute écriture qui peut changer la prochaine échéance (événements, reports). */
    val changes: Flow<Unit>

    /** Événements et séries qui ont des rappels, agendas masqués compris. */
    suspend fun eventsWithReminders(): List<Event>

    /** Clés des occurrences modifiées à part, par série. */
    suspend fun overrides(): Map<Long, Set<Long>>

    suspend fun firedSince(since: Instant): Set<String>

    suspend fun markFired(key: String, eventId: Long, occurrence: Long, at: Instant)

    suspend fun forgetFiredBefore(before: Instant)

    suspend fun snoozes(): List<Snooze>

    suspend fun snooze(eventId: Long, occurrence: Long, at: Instant, alarm: Boolean)

    suspend fun deleteSnooze(id: Long)

    /** Un événement par son id, agenda masqué ou non. */
    suspend fun event(id: Long): Event?
}

/** Clé d'un rappel présenté : un même rappel d'une même occurrence ne sonne qu'une fois. */
fun firedKey(eventId: Long, occurrence: Long, reminder: String): String = "$eventId|$occurrence|$reminder"
