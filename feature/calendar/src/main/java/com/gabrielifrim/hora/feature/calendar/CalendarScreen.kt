package com.gabrielifrim.hora.feature.calendar

import com.gabrielifrim.hora.core.ui.R as UiR
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraIcons
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.IconAction
import com.gabrielifrim.hora.core.ui.component.SegmentedChoice
import com.gabrielifrim.hora.core.ui.motion.gnomonShadow
import com.gabrielifrim.hora.core.ui.motion.monthShadowAngle
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/**
 * L'agenda : jour, semaine, mois ou liste. On passe d'une page à l'autre en
 * glissant, et aussi par les flèches et « Aujourd'hui », toujours visibles.
 */
@Composable
fun CalendarScreen(
    onOpenEvent: (Long, Long?) -> Unit,
    onCreate: (LocalDate, Int?) -> Unit,
    onQuick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    val agendas by viewModel.agendas.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val zone = ZoneId.systemDefault()
    val haptics = rememberHaptics()
    val today = LocalDate.now()

    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        // Le pager repart de la page centrale à chaque changement de vue ou de jour de référence.
        key(mode, anchor) {
            val pager = rememberPagerState(initialPage = CENTER) { PAGE_COUNT }
            val scope = rememberCoroutineScope()
            val shown = dateOfPage(mode, anchor, pager.currentPage - CENTER, formats)
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { if (it != CENTER) haptics.tick() }
            }

            Header(
                mode = mode,
                shown = shown,
                formats = formats,
                onPrevious = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                onToday = { viewModel.anchor.value = today; if (anchor == today) scope.launch { pager.animateScrollToPage(CENTER) } },
                onAdd = { onCreate(if (mode == CalendarMode.DAY) shown else today, null) },
                onQuick = onQuick,
            )
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                SegmentedChoice(
                    options = listOf(R.string.mode_day, R.string.mode_three_days, R.string.mode_week, R.string.mode_month, R.string.mode_year, R.string.mode_list).map { stringResource(it) },
                    columns = 3,
                    selectedIndex = mode.ordinal,
                    onSelect = { i ->
                        haptics.tick()
                        // Si la période affichée contient aujourd'hui, la nouvelle vue s'ouvre sur aujourd'hui.
                        viewModel.anchor.value = if (contains(mode, shown, today)) today else shown
                        viewModel.mode.value = CalendarMode.entries[i]
                    },
                )
            }

            if (mode == CalendarMode.LIST) {
                AgendaList(viewModel, start = anchor, agendas = agendas, zone = zone, formats = formats, onOpenEvent = onOpenEvent)
            } else {
                // Le mois qui tourne : l'ombre du style avance d'un cran à chaque mois.
                val shadow = if (mode == CalendarMode.MONTH) Modifier.gnomonShadow(monthShadowAngle(shown.monthValue)) else Modifier
                HorizontalPager(pager, Modifier.fillMaxSize().then(shadow), beyondViewportPageCount = 1, key = { it }) { page ->
                    val date = dateOfPage(mode, anchor, page - CENTER, formats)
                    when (mode) {
                        CalendarMode.MONTH -> MonthPage(
                            viewModel, YearMonth.from(date), agendas, zone, formats,
                            onDay = { d -> haptics.tick(); viewModel.anchor.value = d; viewModel.mode.value = CalendarMode.DAY },
                        )
                        CalendarMode.YEAR -> YearPage(
                            viewModel, date.year, zone, formats,
                            onMonth = { d -> haptics.tick(); viewModel.anchor.value = d; viewModel.mode.value = CalendarMode.MONTH },
                            onDay = { d -> haptics.tick(); viewModel.anchor.value = d; viewModel.mode.value = CalendarMode.DAY },
                        )
                        CalendarMode.THREE_DAYS -> HourGridPage(
                            viewModel, days = (0L until 3L).map { date.plusDays(it) }, agendas, zone, formats, onOpenEvent, onCreate,
                            onDay = { d -> haptics.tick(); viewModel.anchor.value = d; viewModel.mode.value = CalendarMode.DAY },
                        )
                        CalendarMode.WEEK -> HourGridPage(
                            viewModel, days = (0L until 7L).map { date.plusDays(it) }, agendas, zone, formats, onOpenEvent, onCreate,
                            onDay = { d -> haptics.tick(); viewModel.anchor.value = d; viewModel.mode.value = CalendarMode.DAY },
                        )
                        else -> HourGridPage(viewModel, days = listOf(date), agendas, zone, formats, onOpenEvent, onCreate, onDay = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    mode: CalendarMode,
    shown: LocalDate,
    formats: HoraFormats,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAdd: () -> Unit,
    onQuick: () -> Unit,
) {
    val (eyebrow, title) = when (mode) {
        CalendarMode.DAY -> stringResource(R.string.week_number, formats.week(shown)) to formats.dayLong(shown)
        CalendarMode.THREE_DAYS -> stringResource(R.string.week_number, formats.week(shown)) to
            stringResource(R.string.week_span, formats.dayShort(shown), formats.dayShort(shown.plusDays(2)))
        CalendarMode.WEEK -> stringResource(R.string.week_number_year, formats.week(shown), shown.year) to
            stringResource(R.string.week_span, formats.dayShort(shown), formats.dayShort(shown.plusDays(6)))
        CalendarMode.MONTH, CalendarMode.LIST -> shown.year.toString() to formats.month(shown)
        CalendarMode.YEAR -> stringResource(R.string.mode_year) to shown.year.toString()
    }
    // Titre sur toute la largeur, commandes en dessous : à 200 %, côte à côte, le
    // titre n'avait plus qu'une lettre par ligne.
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp)) {
        Eyebrow(eyebrow)
        HoraText(title, style = HoraTheme.type.heading.copy(fontSize = HoraTheme.type.heading.fontSize * 1.2f), modifier = Modifier.semantics { heading() })
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
    ) {
        if (mode != CalendarMode.LIST) {
            IconAction(HoraIcons.Previous, stringResource(R.string.previous_period), onClick = onPrevious)
            IconAction(HoraIcons.Next, stringResource(R.string.next_period), onClick = onNext)
        }
        HoraButton(stringResource(R.string.today), onClick = onToday, style = ButtonStyle.Line)
        IconAction(HoraIcons.Add, stringResource(R.string.new_event), onClick = onAdd, onLongClick = onQuick, onLongClickLabel = stringResource(UiR.string.quick_title))
    }
}

internal fun contains(mode: CalendarMode, first: LocalDate, day: LocalDate): Boolean = when (mode) {
    CalendarMode.DAY, CalendarMode.LIST -> first == day
    CalendarMode.THREE_DAYS -> !day.isBefore(first) && day.isBefore(first.plusDays(3))
    CalendarMode.WEEK -> !day.isBefore(first) && day.isBefore(first.plusDays(7))
    CalendarMode.MONTH -> YearMonth.from(first) == YearMonth.from(day)
    CalendarMode.YEAR -> first.year == day.year
}

/** Premier jour de la page [offset] (0 = celle du jour de référence) pour la vue [mode]. */
internal fun dateOfPage(mode: CalendarMode, anchor: LocalDate, offset: Int, formats: HoraFormats): LocalDate = when (mode) {
    CalendarMode.DAY, CalendarMode.LIST -> anchor.plusDays(offset.toLong())
    CalendarMode.THREE_DAYS -> anchor.plusDays(3L * offset)
    CalendarMode.WEEK -> formats.weekStart(anchor).plus(offset.toLong(), ChronoUnit.WEEKS)
    CalendarMode.MONTH -> YearMonth.from(anchor).plusMonths(offset.toLong()).atDay(1)
    CalendarMode.YEAR -> LocalDate.of(anchor.year + offset, 1, 1)
}

// Assez de pages pour des années dans les deux sens, sans liste infinie.
private const val PAGE_COUNT = 20_000
private const val CENTER = PAGE_COUNT / 2
