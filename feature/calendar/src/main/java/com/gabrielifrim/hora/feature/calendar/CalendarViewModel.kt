package com.gabrielifrim.hora.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class CalendarMode { DAY, WEEK, MONTH, LIST }

@HiltViewModel
class CalendarViewModel @Inject constructor(
    agendaRepository: AgendaRepository,
    private val eventRepository: EventRepository,
) : ViewModel() {

    val agendas: StateFlow<Map<Long, Agenda>> = agendaRepository.agendas
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Vue affichée et jour de référence : ils survivent au passage par un autre onglet. */
    val mode = MutableStateFlow(CalendarMode.MONTH)
    val anchor = MutableStateFlow(LocalDate.now())

    fun events(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>> = eventRepository.eventsBetween(first, last, zone)

    fun search(query: String): Flow<List<Event>> = eventRepository.search(query)
}
