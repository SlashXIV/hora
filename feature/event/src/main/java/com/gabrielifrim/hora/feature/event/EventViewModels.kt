package com.gabrielifrim.hora.feature.event

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.recurrence.EditScope
import com.gabrielifrim.hora.core.domain.recurrence.Recurrences
import com.gabrielifrim.hora.core.domain.recurrence.SeriesEdits
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.AlmanacRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import com.gabrielifrim.hora.core.domain.repository.ReminderSet
import com.gabrielifrim.hora.core.domain.repository.ReminderSetRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import com.gabrielifrim.hora.core.ics.IcsWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * L'occurrence de clé [key] d'une série, calculée sur son jour ; la série elle-même
 * si elle n'est pas répétée ou si la clé ne correspond à rien.
 */
internal fun occurrenceOf(series: Event, key: Long?, zone: ZoneId): Event {
    if (key == null || key < 0 || series.recurrence == null) return series
    val day = when (series.timing) {
        is Timing.Timed -> Instant.ofEpochMilli(key).atZone(zone).toLocalDate()
        is Timing.AllDay -> LocalDate.ofEpochDay(key)
    }
    return Recurrences.expand(series, day.minusDays(1), day.plusDays(1), zone).firstOrNull { it.occurrence == key } ?: series
}

@HiltViewModel(assistedFactory = EventDetailViewModel.Factory::class)
class EventDetailViewModel @AssistedInject constructor(
    @Assisted("eventId") private val eventId: Long,
    @Assisted("occurrence") private val occurrence: Long,
    private val events: EventRepository,
    agendas: AgendaRepository,
    @param:ApplicationContext private val context: Context,
    almanac: AlmanacRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("eventId") eventId: Long, @Assisted("occurrence") occurrence: Long): EventDetailViewModel
    }

    private val zone = ZoneId.systemDefault()

    /** Ce qu'on affiche : l'occurrence demandée, avec son horaire. */
    /** Un id négatif est une entrée d'un agenda calculé (férié, fête…), en lecture seule. */
    val event: StateFlow<Event?> = (if (eventId < 0) almanac.event(eventId) else events.event(eventId)).map { it?.let { s -> occurrenceOf(s, occurrence, zone) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val agendas: StateFlow<Map<Long, Agenda>> = combine(agendas.agendas, almanac.agendas) { a, b -> a + b }.map { l -> l.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Déplacer une occurrence d'une série ne déplace qu'elle. */
    fun moveTo(date: LocalDate) {
        val shown = event.value ?: return
        viewModelScope.launch {
            val moved = shown.movedTo(date)
            if (shown.occurrence != null) {
                val series = events.event(eventId).first() ?: return@launch
                events.apply(SeriesEdits.update(EditScope.THIS, series, shown, moved, events.children(series.id), zone))
            } else {
                events.save(moved)
            }
        }
    }

    /**
     * Écrit l'événement dans un .ics à partager : toute sa série, occurrences
     * modifiées comprises, pour que le destinataire ait les mêmes dates.
     */
    suspend fun shareFile(): Uri? {
        val shown = event.value ?: return null
        val rootId = shown.parentId ?: eventId
        val root = events.event(rootId).first() ?: return null
        val text = IcsWriter.write(listOf(root) + events.children(rootId), agendas.value.filterKeys { it == root.agendaId })
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "partage").apply { deleteRecursively(); mkdirs() }
            val name = root.title.replace(Regex("""[^\p{L}\p{N} _-]"""), "_").trim().take(60).ifEmpty { "evenement" } // texte-technique : nom de fichier
            val file = File(dir, "$name.ics").apply { writeText(text) }
            FileProvider.getUriForFile(context, context.packageName + ".files", file)
        }
    }

    /** La fiche se ferme d'elle-même quand l'événement disparaît. */
    fun delete(scope: EditScope) {
        val shown = event.value ?: return
        viewModelScope.launch {
            val parentId = shown.parentId
            when {
                parentId != null -> {
                    // Occurrence modifiée à part : « cet événement » la retire de sa série ; sinon on agit sur la série.
                    val series = events.event(parentId).first() ?: return@launch events.delete(shown.id)
                    events.apply(SeriesEdits.delete(scope, series, shown, events.children(series.id), zone))
                }
                shown.recurrence != null && shown.occurrence != null -> {
                    val series = events.event(eventId).first() ?: return@launch
                    events.apply(SeriesEdits.delete(scope, series, shown, events.children(series.id), zone))
                }
                else -> events.delete(eventId)
            }
        }
    }
}

/**
 * Éditeur : [eventId] > 0 modifie (l'occurrence [occurrence] si c'est une série),
 * [duplicateOf] > 0 copie, sinon crée au jour [date] (et à la minute [minute]).
 */
@HiltViewModel(assistedFactory = EventEditorViewModel.Factory::class)
class EventEditorViewModel @AssistedInject constructor(
    @Assisted("eventId") private val eventId: Long,
    @Assisted("occurrence") private val occurrence: Long,
    @Assisted("duplicateOf") private val duplicateOf: Long,
    @Assisted private val date: LocalDate,
    @Assisted("minute") private val minute: Int?,
    private val events: EventRepository,
    private val agendaRepository: AgendaRepository,
    private val setRepository: ReminderSetRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("eventId") eventId: Long,
            @Assisted("occurrence") occurrence: Long,
            @Assisted("duplicateOf") duplicateOf: Long,
            date: LocalDate,
            @Assisted("minute") minute: Int?,
        ): EventEditorViewModel
    }

    private val zone = ZoneId.systemDefault()

    val agendas: StateFlow<List<Agenda>> = agendaRepository.agendas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reminderSets: StateFlow<List<ReminderSet>> = setRepository.sets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null tant que le brouillon se charge. */
    val draft = MutableStateFlow<EventDraft?>(null)

    /** La série et l'occurrence d'origine, si l'on modifie une occurrence : il faudra choisir la portée. */
    private var series: Event? = null
    private var original: Event? = null
    val needsScope: Boolean get() = original?.let { it.occurrence != null || it.parentId != null } == true

    init {
        viewModelScope.launch {
            draft.value = when {
                eventId > 0 -> events.event(eventId).firstOrNull()?.let { s ->
                    val shown = occurrenceOf(s, occurrence, zone)
                    series = s.parentId?.let { events.event(it).firstOrNull() } ?: s
                    original = shown
                    // Une occurrence modifiée à part se montre avec la règle de sa série, pour « les suivants » et « tous ».
                    EventDraft.of(if (s.parentId != null) shown.copy(recurrence = series?.recurrence) else shown, zone)
                }
                duplicateOf > 0 -> events.event(duplicateOf).firstOrNull()?.let { EventDraft.duplicate(occurrenceOf(it, occurrence, zone), zone) }
                else -> null
            } ?: run {
                val list = agendaRepository.agendas.first { it.isNotEmpty() }
                val agenda = list.firstOrNull { !it.hidden } ?: list.first()
                EventDraft.new(agendaId = agenda.id, date = date, minute = minute, zone = zone, reminders = agenda.defaultReminders)
            }
        }
    }

    fun update(transform: (EventDraft) -> EventDraft) {
        draft.value = draft.value?.let(transform)
    }

    /** Changer d'agenda un nouvel événement remplace ses rappels par défaut, s'ils n'ont pas été touchés. */
    fun chooseAgenda(id: Long) = update { d ->
        val from = agendas.value.firstOrNull { it.id == d.agendaId }?.defaultReminders.orEmpty()
        val to = agendas.value.firstOrNull { it.id == id }?.defaultReminders.orEmpty()
        d.copy(agendaId = id, reminders = if (d.id == 0L && d.reminders == from) to else d.reminders)
    }

    fun saveAsSet(name: String) {
        val reminders = draft.value?.reminders.orEmpty()
        if (name.isBlank() || reminders.isEmpty()) return
        viewModelScope.launch { setRepository.save(ReminderSet(name = name.trim(), reminders = reminders)) }
    }

    /** @param scope portée pour une occurrence de série ; ignorée sinon. */
    fun save(scope: EditScope, onDone: (Long) -> Unit) {
        val current = draft.value?.takeIf { it.canSave } ?: return
        viewModelScope.launch {
            val edited = current.toEvent()
            val s = series
            val o = original
            val id = if (needsScope && s != null && o != null) {
                events.apply(SeriesEdits.update(scope, s, o, edited, events.children(s.id), zone)) ?: s.id
            } else {
                events.save(edited)
            }
            onDone(id)
        }
    }
}
