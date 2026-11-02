package com.gabrielifrim.hora.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.ui.R
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle

/** Choix d'un jour : un mois à la fois, flèches visibles, aujourd'hui cerclé. */
@Composable
fun DatePickerSheet(
    initial: LocalDate,
    formats: HoraFormats,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    val haptics = rememberHaptics()
    HoraSheet(onDismissRequest = onDismiss) {
        HoraText(stringResource(R.string.picker_date), style = HoraTheme.type.heading)
        Row(verticalAlignment = Alignment.CenterVertically) {
            HoraText(
                stringResource(R.string.month_year, formats.month(month.atDay(1)), month.year),
                style = HoraTheme.type.body,
                modifier = Modifier.weight(1f),
            )
            IconAction(HoraIcons.Previous, stringResource(R.string.action_previous), onClick = { month = month.minusMonths(1) })
            IconAction(HoraIcons.Next, stringResource(R.string.action_next), onClick = { month = month.plusMonths(1) })
        }
        MonthDays(month, formats, selected = initial, onPick = { haptics.tick(); onPick(it) })
    }
}

@Composable
private fun MonthDays(month: YearMonth, formats: HoraFormats, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val colors = HoraTheme.colors
    val today = LocalDate.now()
    val first = formats.weekStart(month.atDay(1))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                HoraText(
                    first.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, formats.locale),
                    style = HoraTheme.type.label,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        (0 until 6).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val day = first.plusDays((week * 7 + i).toLong())
                    val inMonth = YearMonth.from(day) == month
                    val isSelected = day == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .selectable(selected = isSelected, enabled = inMonth, role = Role.RadioButton) { onPick(day) }
                            .clearAndSetSemantics {
                                if (inMonth) contentDescription = formats.dayLong(day)
                                this.selected = isSelected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (inMonth) {
                            Box(
                                Modifier
                                    .padding(4.dp)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .then(
                                        when {
                                            isSelected -> Modifier.background(colors.ink)
                                            day == today -> Modifier.border(1.5.dp, colors.accent, CircleShape)
                                            else -> Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                HoraText(day.dayOfMonth.toString(), style = HoraTheme.type.hour, color = if (isSelected) colors.onInk else colors.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Choix d'une heure : les 24 heures puis les minutes de 5 en 5, en grandes cibles
 * plutôt qu'une roue à faire tourner. Une minute hors pas (14:37) reste proposée.
 */
@Composable
fun TimePickerSheet(
    initial: LocalTime,
    formats: HoraFormats,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    val haptics = rememberHaptics()
    val minutes = ((0 until 60 step 5) + initial.minute).distinct().sorted()
    HoraSheet(
        onDismissRequest = onDismiss,
        actions = {
            HoraButton(stringResource(R.string.action_ok), onClick = { onPick(LocalTime.of(hour, minute)) }, modifier = Modifier.fillMaxWidth())
        },
    ) {
        HoraText(formats.time(LocalTime.of(hour, minute)), style = HoraTheme.type.figure)
        Eyebrow(stringResource(R.string.picker_hours))
        ChipGrid((0 until 24).toList(), columns = 6, selected = hour, label = { "%02d".format(it) }) { haptics.tick(); hour = it }
        Eyebrow(stringResource(R.string.picker_minutes))
        ChipGrid(minutes, columns = 6, selected = minute, label = { "%02d".format(it) }) { haptics.tick(); minute = it }
    }
}

@Composable
private fun ChipGrid(values: List<Int>, columns: Int, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    val colors = HoraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { v ->
                    val on = v == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .then(if (on) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.rule, RoundedCornerShape(6.dp)))
                            .selectable(selected = on, role = Role.RadioButton) { onSelect(v) },
                        contentAlignment = Alignment.Center,
                    ) {
                        HoraText(label(v), style = HoraTheme.type.hour, color = if (on) colors.onInk else colors.ink)
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
