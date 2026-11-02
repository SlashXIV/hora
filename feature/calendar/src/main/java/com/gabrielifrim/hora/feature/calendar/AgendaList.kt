package com.gabrielifrim.hora.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.calendar.DayLayout
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.EventRow
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.timeRange
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * L'agenda à lire d'un trait, de [start] à trois mois plus tard. Seuls les jours
 * qui ont quelque chose apparaissent ; un événement sur plusieurs jours revient
 * chaque jour.
 */
@Composable
internal fun AgendaList(
    viewModel: CalendarViewModel,
    start: LocalDate,
    agendas: Map<Long, Agenda>,
    zone: ZoneId,
    formats: HoraFormats,
    onOpenEvent: (Long) -> Unit,
) {
    val last = start.plusDays(LIST_DAYS)
    val events by remember(start, zone) { viewModel.events(start, last, zone) }.collectAsStateWithLifecycle(emptyList())
    val days = remember(events, start, zone) {
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }
            .map { day -> day to eventsOfDay(events, day, zone) }
            .filter { it.second.isNotEmpty() }
            .toList()
    }
    val today = LocalDate.now()

    if (days.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HoraText(stringResource(R.string.list_empty_title), style = HoraTheme.type.heading)
            HoraText(stringResource(R.string.list_empty_hint), color = HoraTheme.colors.ink2)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        days.forEach { (day, list) ->
            item(key = day.toEpochDay()) {
                Column(Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HoraText(formats.dayLong(day), style = HoraTheme.type.heading.copy(fontSize = HoraTheme.type.body.fontSize * 1.1f), modifier = Modifier.semantics { heading() })
                        if (day == today) Eyebrow(stringResource(R.string.today_label), color = HoraTheme.colors.accent)
                    }
                    Rule(Modifier.padding(top = 4.dp))
                }
            }
            items(list, key = { "${day.toEpochDay()}-${it.id}" }) { event ->
                EventRow(event, agendas, zone, formats, onClick = { onOpenEvent(event.id) }, day = day, subtitle = subtitleOf(event, zone, formats), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun subtitleOf(event: Event, zone: ZoneId, formats: HoraFormats): String =
    listOf(timeRange(event, zone, formats), event.location).filter { it.isNotBlank() }.joinToString(" · ")

/** Journée entière d'abord, puis par heure de début. */
internal fun eventsOfDay(events: List<Event>, day: LocalDate, zone: ZoneId): List<Event> =
    events.filter { DayLayout.occursOn(it, day, zone) }
        .sortedWith(compareBy({ it.timing !is Timing.AllDay }, { (it.timing as? Timing.Timed)?.start }))

private const val LIST_DAYS = 92L
