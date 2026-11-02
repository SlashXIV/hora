package com.gabrielifrim.hora.feature.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.domain.recurrence.EditScope
import com.gabrielifrim.hora.core.domain.recurrence.Frequency
import com.gabrielifrim.hora.core.domain.recurrence.RepeatEnd
import com.gabrielifrim.hora.core.domain.recurrence.RepeatSpec
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.DatePickerSheet
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraDialog
import com.gabrielifrim.hora.core.ui.component.HoraIcons
import com.gabrielifrim.hora.core.ui.component.HoraSheet
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.IconAction
import com.gabrielifrim.hora.core.ui.component.Tag
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** « Toutes les 2 semaines, le mardi et le jeudi, 8 fois ». */
@Composable
fun repeatText(spec: RepeatSpec?, customRule: String?, start: LocalDate, formats: HoraFormats): String {
    if (spec == null) return stringResource(if (customRule != null) R.string.repeat_custom else R.string.repeat_none)
    val n = spec.interval
    val base = when (spec.frequency) {
        Frequency.DAILY -> pluralStringResource(R.plurals.repeat_daily, n, n)
        Frequency.WEEKLY -> pluralStringResource(R.plurals.repeat_weekly, n, n, (spec.weekdays.ifEmpty { setOf(start.dayOfWeek) }).sorted().joinToString(", ") { it.getDisplayName(TextStyle.FULL, formats.locale) })
        Frequency.MONTHLY_BY_DATE -> pluralStringResource(R.plurals.repeat_monthly_date, n, n, start.dayOfMonth)
        Frequency.MONTHLY_BY_RANK -> pluralStringResource(R.plurals.repeat_monthly_rank, n, n, rankText(start), start.dayOfWeek.getDisplayName(TextStyle.FULL, formats.locale))
        Frequency.YEARLY -> pluralStringResource(R.plurals.repeat_yearly, n, n)
    }
    val end = when (val e = spec.end) {
        RepeatEnd.Never -> null
        is RepeatEnd.After -> pluralStringResource(R.plurals.repeat_times, e.count, e.count)
        is RepeatEnd.Until -> stringResource(R.string.repeat_until, formats.dayShort(e.lastDay))
    }
    return listOfNotNull(base, end).joinToString(", ")
}

@Composable
private fun rankText(day: LocalDate): String = when (val r = RepeatSpec.rankOf(day)) {
    -1 -> stringResource(R.string.rank_last)
    1 -> stringResource(R.string.rank_first)
    else -> stringResource(R.string.rank_nth, r)
}

/**
 * Choix de la répétition : fréquence, intervalle, jours, fin. Rien n'est appliqué
 * avant « Valider » ; une règle importée reste intacte tant qu'on ne touche à rien.
 */
@Composable
fun RepeatSheet(
    initial: RepeatSpec?,
    customRule: String?,
    start: LocalDate,
    formats: HoraFormats,
    onPick: (RepeatSpec?) -> Unit,
    onDismiss: () -> Unit,
) {
    var spec by remember { mutableStateOf(initial) }
    var touched by remember { mutableStateOf(false) }
    var pickingUntil by remember { mutableStateOf(false) }
    val set: (RepeatSpec?) -> Unit = { spec = it; touched = true }
    HoraSheet(
        onDismissRequest = onDismiss,
        actions = { HoraButton(stringResource(R.string.action_validate), onClick = { if (touched) onPick(spec) else onDismiss() }, modifier = Modifier.fillMaxWidth()) },
    ) {
        HoraText(stringResource(R.string.field_repeat), style = HoraTheme.type.heading)
        HoraText(
            repeatText(spec, customRule.takeIf { !touched }, start, formats),
            style = HoraTheme.type.body, color = HoraTheme.colors.accent,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(stringResource(R.string.repeat_none), selected = spec == null && (touched || customRule == null), onClick = { set(null) })
            listOf(
                Frequency.DAILY to R.string.freq_daily, Frequency.WEEKLY to R.string.freq_weekly,
                Frequency.MONTHLY_BY_DATE to R.string.freq_monthly_date, Frequency.MONTHLY_BY_RANK to R.string.freq_monthly_rank,
                Frequency.YEARLY to R.string.freq_yearly,
            ).forEach { (f, label) ->
                val text = if (f == Frequency.MONTHLY_BY_RANK) stringResource(label, rankText(start), start.dayOfWeek.getDisplayName(TextStyle.FULL, formats.locale)) else stringResource(label, start.dayOfMonth)
                Tag(text, selected = spec?.frequency == f, onClick = { set((spec ?: RepeatSpec(f)).copy(frequency = f)) })
            }
        }
        val s = spec ?: return@HoraSheet
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(HoraIcons.Previous, stringResource(R.string.interval_less), onClick = { set(s.copy(interval = (s.interval - 1).coerceAtLeast(1))) })
            HoraText(pluralStringResource(R.plurals.interval_every, s.interval, s.interval), style = HoraTheme.type.body, modifier = Modifier.weight(1f))
            IconAction(HoraIcons.Next, stringResource(R.string.interval_more), onClick = { set(s.copy(interval = (s.interval + 1).coerceAtMost(99))) })
        }
        if (s.frequency == Frequency.WEEKLY) {
            Eyebrow(stringResource(R.string.repeat_on_days))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val days = s.weekdays.ifEmpty { setOf(start.dayOfWeek) }
                DayOfWeek.entries.forEach { d ->
                    Tag(d.getDisplayName(TextStyle.SHORT, formats.locale), selected = d in days, onClick = {
                        val next = if (d in days) days - d else days + d
                        if (next.isNotEmpty()) set(s.copy(weekdays = next))
                    })
                }
            }
        }
        Eyebrow(stringResource(R.string.repeat_end))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(stringResource(R.string.end_never), selected = s.end == RepeatEnd.Never, onClick = { set(s.copy(end = RepeatEnd.Never)) })
            Tag(stringResource(R.string.end_after), selected = s.end is RepeatEnd.After, onClick = { set(s.copy(end = RepeatEnd.After(10))) })
            Tag(
                (s.end as? RepeatEnd.Until)?.let { stringResource(R.string.repeat_until, formats.dayShort(it.lastDay)) } ?: stringResource(R.string.end_until),
                selected = s.end is RepeatEnd.Until, onClick = { pickingUntil = true },
            )
        }
        (s.end as? RepeatEnd.After)?.let { after ->
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(HoraIcons.Previous, stringResource(R.string.count_less), onClick = { set(s.copy(end = RepeatEnd.After((after.count - 1).coerceAtLeast(1)))) })
                HoraText(pluralStringResource(R.plurals.repeat_times, after.count, after.count), style = HoraTheme.type.body, modifier = Modifier.weight(1f))
                IconAction(HoraIcons.Next, stringResource(R.string.count_more), onClick = { set(s.copy(end = RepeatEnd.After((after.count + 1).coerceAtMost(999)))) })
            }
        }
    }
    if (pickingUntil) {
        DatePickerSheet((spec?.end as? RepeatEnd.Until)?.lastDay ?: start.plusMonths(1), formats, onPick = { day ->
            set(spec?.copy(end = RepeatEnd.Until(maxOf(day, start)))); pickingUntil = false
        }, onDismiss = { pickingUntil = false })
    }
}

/** « Cet événement / Les suivants / Tous » : la question des agendas, posée au moment d'écrire. */
@Composable
fun ScopeDialog(title: String, onPick: (EditScope) -> Unit, onDismiss: () -> Unit) {
    HoraDialog(
        title = title,
        onDismissRequest = onDismiss,
        actions = { HoraButton(stringResource(R.string.cancel), onClick = onDismiss, style = ButtonStyle.Text) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HoraButton(stringResource(R.string.scope_this), onClick = { onPick(EditScope.THIS) }, style = ButtonStyle.Line, modifier = Modifier.fillMaxWidth())
            HoraButton(stringResource(R.string.scope_following), onClick = { onPick(EditScope.FOLLOWING) }, style = ButtonStyle.Line, modifier = Modifier.fillMaxWidth())
            HoraButton(stringResource(R.string.scope_all), onClick = { onPick(EditScope.ALL) }, style = ButtonStyle.Line, modifier = Modifier.fillMaxWidth())
        }
    }
}
