package com.gabrielifrim.hora.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.domain.model.Plage
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraSheet
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.HoraTextField
import com.gabrielifrim.hora.core.ui.component.Tag
import com.gabrielifrim.hora.core.ui.component.TimePickerSheet
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

/** Les plages (« Taff », « Sport ») : la liste, et une feuille pour en créer ou en changer une. */
@Composable
internal fun PlagesSection(plages: List<Plage>, onChange: (List<Plage>) -> Unit) {
    val formats = rememberFormats()
    // Index de la plage ouverte ; plages.size : une nouvelle.
    var editing by remember { mutableStateOf<Int?>(null) }
    HoraText(stringResource(R.string.plages_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    plages.forEachIndexed { i, p ->
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { editing = i }.padding(vertical = 4.dp)) {
            HoraText(p.name, style = HoraTheme.type.body)
            HoraText(summary(p, formats), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        }
    }
    HoraButton(stringResource(R.string.plage_add), onClick = { editing = plages.size }, style = ButtonStyle.Line)
    editing?.let { i ->
        PlageSheet(
            initial = plages.getOrNull(i),
            formats = formats,
            onSave = { p -> onChange(if (i < plages.size) plages.toMutableList().also { it[i] = p } else plages + p); editing = null },
            onDelete = { onChange(plages.filterIndexed { j, _ -> j != i }); editing = null },
            onDismiss = { editing = null },
        )
    }
}

private fun weekFrom(first: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { first.plus(it) }

/** « lun., mar., mer. · 09:00–17:30 » */
@Composable
private fun summary(p: Plage, formats: HoraFormats): String = stringResource(
    R.string.plage_summary,
    weekFrom(formats.firstDayOfWeek).filter(p::isOn).joinToString(", ") { it.getDisplayName(TextStyle.SHORT, formats.locale) },
    formats.time(p.start),
    formats.time(p.end),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlageSheet(initial: Plage?, formats: HoraFormats, onSave: (Plage) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    var draft by remember {
        mutableStateOf(initial ?: Plage("", setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(17, 0)))
    }
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }
    val canSave = draft.name.isNotBlank() && draft.days.isNotEmpty()
    HoraSheet(
        onDismissRequest = onDismiss,
        actions = {
            HoraButton(stringResource(R.string.plage_save), onClick = { onSave(draft.copy(name = draft.name.trim())) }, enabled = canSave, modifier = Modifier.fillMaxWidth())
            if (initial != null) HoraButton(stringResource(R.string.plage_delete), onClick = onDelete, style = ButtonStyle.Text, modifier = Modifier.fillMaxWidth())
        },
    ) {
        HoraTextField(draft.name, { v -> draft = draft.copy(name = v) }, label = stringResource(R.string.plage_name), placeholder = stringResource(R.string.plage_name_placeholder))
        Eyebrow(stringResource(R.string.plage_days))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekFrom(formats.firstDayOfWeek).forEach { d ->
                Tag(
                    d.getDisplayName(TextStyle.SHORT, formats.locale),
                    selected = draft.isOn(d),
                    onClick = { haptics.tick(); draft = draft.copy(days = if (draft.isOn(d)) draft.days - d else draft.days + d) },
                    multiple = true,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TimeField(stringResource(R.string.plage_start), formats.time(draft.start), Modifier.weight(1f)) { pickingStart = true }
            TimeField(stringResource(R.string.plage_end), formats.time(draft.end), Modifier.weight(1f)) { pickingStart = false }
        }
        if (!canSave) HoraText(stringResource(R.string.plage_incomplete), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
    pickingStart?.let { start ->
        TimePickerSheet(
            initial = if (start) draft.start else draft.end,
            formats = formats,
            onPick = { t -> draft = if (start) draft.withStart(t) else draft.withEnd(t); pickingStart = null },
            onDismiss = { pickingStart = null },
        )
    }
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(label)
        HoraText(value, style = HoraTheme.type.hour)
    }
}
