package com.gabrielifrim.hora.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.almanac.Place
import com.gabrielifrim.hora.core.domain.almanac.Sun
import com.gabrielifrim.hora.core.domain.calendar.DayLayout
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.ui.component.AgendaMark
import com.gabrielifrim.hora.core.ui.component.EventRow
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraIcons
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.IconAction
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.colorKeyOf
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.text.spokenEvent
import com.gabrielifrim.hora.core.ui.text.timeRange
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * L'écran d'ouverture : la date, le prochain rendez-vous et le fil du jour. Les
 * plages, la fête et le compte à rebours s'y ajoutent aux jalons suivants.
 */
@Composable
fun TodayScreen(
    onOpenEvent: (Long, Long?) -> Unit,
    onCreate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    // La date se relit au retour au premier plan et à chaque minute : l'écran
    // laissé ouvert la veille ne doit pas afficher hier, ni « dans 2 h » périmé.
    var now by remember { mutableStateOf(Instant.now()) }
    LifecycleResumeEffect(Unit) {
        now = Instant.now()
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            now = Instant.now()
        }
    }
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()
    val formats = rememberFormats()
    val agendas by viewModel.agendas.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    val sun = remember(today, place) { Sun.day(today, place) }
    val events by remember(today, zone) { viewModel.upcoming(today, zone) }.collectAsStateWithLifecycle(emptyList())
    val todays = remember(events, today) {
        events.filter { DayLayout.occursOn(it, today, zone) }
            .sortedWith(compareBy({ it.timing !is Timing.AllDay }, { (it.timing as? Timing.Timed)?.start }))
    }
    // Le prochain rendez-vous est un vrai événement : pas une pleine lune ni un changement d'heure.
    val next = remember(events, now) {
        events.filter { it.id > 0 }.mapNotNull { e -> (e.timing as? Timing.Timed)?.takeIf { it.start.isAfter(now) }?.let { e to it.start } }.minByOrNull { it.second }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(
            title = formats.dayMonth(today),
            eyebrow = stringResource(R.string.today_eyebrow, formats.weekday(today), formats.week(today)),
            poster = true,
            actions = { IconAction(HoraIcons.Add, stringResource(R.string.new_event), onClick = { onCreate(today) }) },
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SunLine(sun, place, zone, formats)
            if (next != null) NextUp(next.first, Duration.between(now, next.second), today, zone, formats, agendas, onOpenEvent)
            Rule()
            if (todays.isEmpty()) {
                HoraText(stringResource(R.string.today_empty_title), style = HoraTheme.type.heading)
                HoraText(stringResource(R.string.today_empty_hint), style = HoraTheme.type.body, color = HoraTheme.colors.ink2)
            } else {
                Eyebrow(stringResource(R.string.today_list))
                Column {
                    todays.forEach { e -> EventRow(e, agendas, zone, formats, onClick = { onOpenEvent(e.id, e.occurrence) }, day = today) }
                }
            }
        }
    }
}

/** « Bordeaux · lever 08:12, coucher 17:44 » ; rien de lisible sous le cercle polaire. */
@Composable
private fun SunLine(sun: Sun.Day, place: Place, zone: ZoneId, formats: HoraFormats) {
    val rise = sun.sunrise ?: return
    val set = sun.sunset ?: return
    val riseTime = rise.atZone(zone).toLocalTime()
    val setTime = set.atZone(zone).toLocalTime()
    // TalkBack dit « 8 h 12 » plutôt que « 08:12 ».
    val spoken = stringResource(R.string.today_sun, place.name, formats.spoken(riseTime), formats.spoken(setTime))
    HoraText(
        stringResource(R.string.today_sun, place.name, formats.time(riseTime), formats.time(setTime)),
        style = HoraTheme.type.bodySmall,
        color = HoraTheme.colors.ink2,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

@Composable
private fun NextUp(
    event: Event,
    inTime: Duration,
    today: LocalDate,
    zone: ZoneId,
    formats: HoraFormats,
    agendas: Map<Long, Agenda>,
    onOpenEvent: (Long, Long?) -> Unit,
) {
    val key = colorKeyOf(event, agendas)
    val delayText = relativeDelay(inTime)
    val spoken = stringResource(R.string.next_spoken, delayText, spokenEvent(event, zone, formats))
    val start = (event.timing as Timing.Timed).start.atZone(zone).toLocalDate()
    val whenText = if (start == today) timeRange(event, zone, formats) else stringResource(R.string.day_and_time, formats.dayLong(start), timeRange(event, zone, formats))
    Column(
        Modifier.fillMaxWidth().clickable { onOpenEvent(event.id, event.occurrence) }
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Button }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Eyebrow(stringResource(R.string.next_eyebrow, delayText))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AgendaMark(key.shape, key.color(), size = 12.dp)
            HoraText(event.title, style = HoraTheme.type.heading)
        }
        HoraText(whenText, style = HoraTheme.type.hour.copy(fontSize = HoraTheme.type.bodySmall.fontSize), color = HoraTheme.colors.ink2)
        if (event.location.isNotBlank()) HoraText(event.location, style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
}

/** « 2 h 12 », « 25 min », « 3 jours ». */
@Composable
private fun relativeDelay(d: Duration): String {
    val minutes = d.toMinutes().coerceAtLeast(1)
    return when {
        minutes < 60 -> pluralStringResource(R.plurals.in_minutes, minutes.toInt(), minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.in_hours_minutes, minutes / 60, minutes % 60)
        else -> (minutes / (24 * 60)).toInt().let { pluralStringResource(R.plurals.in_days, it, it) }
    }
}
