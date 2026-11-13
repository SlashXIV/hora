package com.gabrielifrim.hora.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.AlmanacRepository
import com.gabrielifrim.hora.core.domain.repository.DeviceCalendarRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import com.gabrielifrim.hora.core.domain.model.Plage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.gabrielifrim.hora.core.domain.calendar.DayLoad
import kotlinx.coroutines.flow.stateIn

/** Dans l'ordre de la barre des vues. */
enum class CalendarMode { DAY, THREE_DAYS, WEEK, MONTH, YEAR, LIST }

@HiltViewModel
class CalendarViewModel @Inject constructor(
    agendaRepository: AgendaRepository,
    private val eventRepository: EventRepository,
    private val almanac: AlmanacRepository,
    private val device: DeviceCalendarRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val plages: StateFlow<List<Plage>> = settings.settings.map { it.plages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val agendas: StateFlow<Map<Long, Agenda>> = combine(agendaRepository.agendas, almanac.agendas, device.agendas) { a, b, c -> a + b + c }
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Vue affichée et jour de référence : ils survivent au passage par un autre onglet. */
    val mode = MutableStateFlow(CalendarMode.MONTH)
    val anchor = MutableStateFlow(LocalDate.now())

    /** Les événements, les agendas calculés visibles (fériés, fêtes…) et ceux d'Android. */
    fun events(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>> =
        combine(eventRepository.eventsBetween(first, last, zone), almanac.eventsBetween(first, last, zone), device.eventsBetween(first, last, zone)) { a, b, c -> a + b + c }

    /** La charge de chaque jour de [year], agendas d'Android compris : les agendas calculés (fériés, fêtes) n'occupent personne. */
    fun yearLoad(year: Int, zone: ZoneId): Flow<Map<LocalDate, DayLoad>> {
        val first = LocalDate.of(year, 1, 1)
        val last = LocalDate.of(year, 12, 31)
        return combine(eventRepository.eventsBetween(first, last, zone), device.eventsBetween(first, last, zone)) { a, b -> DayLoad.days(a + b, first, last, zone) }.flowOn(Dispatchers.Default)
    }

    fun search(query: String): Flow<List<Event>> = combine(eventRepository.search(query), device.search(query)) { a, b -> a + b }
}
