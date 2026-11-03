package com.gabrielifrim.hora.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.almanac.Place
import com.gabrielifrim.hora.core.domain.almanac.Places
import com.gabrielifrim.hora.core.domain.almanac.PrayerSettings
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.AlmanacRepository
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TodayViewModel @Inject constructor(
    agendaRepository: AgendaRepository,
    private val eventRepository: EventRepository,
    private val almanac: AlmanacRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val agendas: StateFlow<Map<Long, Agenda>> = combine(agendaRepository.agendas, almanac.agendas) { a, b -> a + b }
        .map { l -> l.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Lieu du lever et du coucher du soleil. */
    val place: StateFlow<Place> = settings.settings.map { it.place }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Places.DEFAULT)

    /** Pratique musulmane : la carte des prières ne s'affiche que si elle est activée. */
    val prayer: StateFlow<PrayerSettings> = settings.settings.map { it.prayer }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrayerSettings())

    /** Événements épinglés : compte à rebours ou « depuis ». */
    val pinned: StateFlow<List<Event>> = eventRepository.pinned
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Aujourd'hui et le mois qui suit : de quoi trouver le prochain rendez-vous ; fériés et fêtes du fil compris. */
    fun upcoming(today: LocalDate, zone: ZoneId): Flow<List<Event>> {
        val last = today.plusDays(UPCOMING_DAYS)
        return combine(eventRepository.eventsBetween(today, last, zone), almanac.eventsBetween(today, last, zone)) { a, b -> a + b }
    }

    private companion object {
        const val UPCOMING_DAYS = 31L
    }
}
