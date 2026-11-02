package com.gabrielifrim.hora.feature.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = EventDetailViewModel.Factory::class)
class EventDetailViewModel @AssistedInject constructor(
    @Assisted private val eventId: Long,
    private val events: EventRepository,
    agendas: AgendaRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(eventId: Long): EventDetailViewModel
    }

    val event: StateFlow<Event?> = events.event(eventId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val agendas: StateFlow<Map<Long, Agenda>> = agendas.agendas.map { l -> l.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun moveTo(date: LocalDate) {
        val current = event.value ?: return
        viewModelScope.launch { events.save(current.movedTo(date)) }
    }

    /** La fiche se ferme d'elle-même quand l'événement disparaît. */
    fun delete() {
        viewModelScope.launch { events.delete(eventId) }
    }
}

/**
 * Éditeur : [eventId] > 0 modifie, [duplicateOf] > 0 copie, sinon crée au jour
 * [date] (et à la minute [minute] si elle est donnée).
 */
@HiltViewModel(assistedFactory = EventEditorViewModel.Factory::class)
class EventEditorViewModel @AssistedInject constructor(
    @Assisted("eventId") private val eventId: Long,
    @Assisted("duplicateOf") private val duplicateOf: Long,
    @Assisted private val date: LocalDate,
    @Assisted("minute") private val minute: Int?,
    private val events: EventRepository,
    private val agendaRepository: AgendaRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("eventId") eventId: Long,
            @Assisted("duplicateOf") duplicateOf: Long,
            date: LocalDate,
            @Assisted("minute") minute: Int?,
        ): EventEditorViewModel
    }

    val agendas: StateFlow<List<Agenda>> = agendaRepository.agendas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null tant que le brouillon se charge. */
    val draft = MutableStateFlow<EventDraft?>(null)

    init {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            draft.value = when {
                eventId > 0 -> events.event(eventId).firstOrNull()?.let { EventDraft.of(it, zone) }
                duplicateOf > 0 -> events.event(duplicateOf).firstOrNull()?.let { EventDraft.duplicate(it, zone) }
                else -> null
            } ?: run {
                val list = agendaRepository.agendas.first { it.isNotEmpty() }
                EventDraft.new(agendaId = (list.firstOrNull { !it.hidden } ?: list.first()).id, date = date, minute = minute, zone = zone)
            }
        }
    }

    fun update(transform: (EventDraft) -> EventDraft) {
        draft.value = draft.value?.let(transform)
    }

    fun save(onDone: (Long) -> Unit) {
        val current = draft.value?.takeIf { it.canSave } ?: return
        viewModelScope.launch { onDone(events.save(current.toEvent())) }
    }
}
