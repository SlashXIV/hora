package com.gabrielifrim.hora.feature.today

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TodayViewModel @Inject constructor(
    agendaRepository: AgendaRepository,
    private val eventRepository: EventRepository,
) : ViewModel() {
    val agendas: StateFlow<Map<Long, Agenda>> = agendaRepository.agendas.map { l -> l.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Aujourd'hui et le mois qui suit : de quoi trouver le prochain rendez-vous. */
    fun upcoming(today: LocalDate, zone: ZoneId): Flow<List<Event>> = eventRepository.eventsBetween(today, today.plusDays(UPCOMING_DAYS), zone)

    private companion object {
        const val UPCOMING_DAYS = 31L
    }
}
