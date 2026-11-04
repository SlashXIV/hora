package com.gabrielifrim.hora.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.calendar.DayLoad
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.FitText
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * L'année et sa charge : douze mois, chaque jour teinté selon ses heures occupées.
 * La couleur n'est jamais seule : le jour touché se lit en bas, en toutes lettres.
 */
@Composable
internal fun YearPage(
    viewModel: CalendarViewModel,
    year: Int,
    zone: ZoneId,
    formats: HoraFormats,
    onMonth: (LocalDate) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val colors = HoraTheme.colors
    val haptics = rememberHaptics()
    val loads by remember(year, zone) { viewModel.yearLoad(year, zone) }.collectAsStateWithLifecycle(emptyMap())
    val today = LocalDate.now()
    var selected by remember(year) { mutableStateOf(if (today.year == year) today else LocalDate.of(year, 1, 1)) }
    val tints = LEVELS.map { lerp(colors.background, colors.accent, it) }
    val scroll = rememberScrollState()
    // L'année en cours s'ouvre sur la rangée du mois présent.
    LaunchedEffect(year) {
        if (year != today.year) return@LaunchedEffect
        val max = snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
        scroll.scrollTo(max * ((today.monthValue - 1) / 3) / 3)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (1..12).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { m ->
                        MiniMonth(YearMonth.of(year, m), loads, today, selected, tints, formats, Modifier.weight(1f), onMonth = onMonth) { d ->
                            haptics.tick(); selected = d
                        }
                    }
                }
            }
        }
        Rule()
        val load = loads[selected]
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics { }) {
                HoraText(stringResource(R.string.year_free), style = HoraTheme.type.label, color = colors.ink2)
                tints.forEach { Box(Modifier.size(12.dp).background(it, RoundedCornerShape(2.dp)).border(1.dp, colors.rule, RoundedCornerShape(2.dp))) }
                HoraText(stringResource(R.string.year_legend_max), style = HoraTheme.type.label, color = colors.ink2)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                    Eyebrow(formats.dayLong(selected))
                    HoraText(if (load == null) "" else loadText(load), style = HoraTheme.type.body)
                }
                HoraButton(stringResource(R.string.year_open), onClick = { onDay(selected) }, style = ButtonStyle.Line)
            }
        }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    loads: Map<LocalDate, DayLoad>,
    today: LocalDate,
    selected: LocalDate,
    tints: List<Color>,
    formats: HoraFormats,
    modifier: Modifier,
    onMonth: (LocalDate) -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val colors = HoraTheme.colors
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - formats.weekStart(first).dayOfWeek.value) + 7) % 7
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        FitText(
            formats.month(first),
            style = HoraTheme.type.bodySmall.copy(fontFamily = HoraTheme.type.heading.fontFamily, fontWeight = HoraTheme.type.heading.fontWeight),
            color = if (YearMonth.from(today) == month) colors.accent else colors.ink,
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onMonth(first) }.padding(vertical = 4.dp).semantics { heading() },
        )
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val load = loads[day]
                        val shape = RoundedCornerShape(2.dp)
                        val spoken = formats.daySpoken(day) + if (load == null) "" else ", " + loadText(load)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .background(tints[load?.level ?: 0], shape)
                                .border(
                                    when (day) { selected -> 2.dp; today -> 1.5.dp; else -> 0.5.dp },
                                    when (day) { selected -> colors.accent; today -> colors.ink; else -> colors.rule },
                                    shape,
                                )
                                // shortcut: une case fait ~15 dp, sous les 48 dp ; le détail se lit en bas et TalkBack parcourt jour par jour. À revoir si le toucher manque sur le téléphone.
                                .clickable { onPick(day) }
                                .clearAndSetSemantics { contentDescription = spoken; role = Role.Button },
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

/** « 7 h 30 occupées · 4 événements », « Libre · 1 événement ». */
@Composable
private fun loadText(load: DayLoad): String {
    val m = load.busyMinutes
    val busy = when {
        m == 0 -> stringResource(R.string.year_free)
        m < 60 -> stringResource(R.string.year_busy, stringResource(R.string.year_minutes, m))
        else -> stringResource(
            if (m < 120) R.string.year_busy_one else R.string.year_busy,
            if (m % 60 == 0) stringResource(R.string.year_hours, m / 60) else stringResource(R.string.year_hours_minutes, m / 60, m % 60),
        )
    }
    return if (load.events == 0) busy else busy + " · " + pluralStringResource(R.plurals.event_count, load.events, load.events)
}

/** Part de l'accent dans la teinte de chaque palier, du fond (libre) à presque l'accent. */
private val LEVELS = listOf(0f, 0.18f, 0.36f, 0.6f, 0.88f)
