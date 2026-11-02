package com.gabrielifrim.hora.feature.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.recurrence.EditScope
import com.gabrielifrim.hora.core.domain.recurrence.RepeatSpec
import com.gabrielifrim.hora.core.ui.component.reminderText
import com.gabrielifrim.hora.core.ui.R as UiR
import androidx.compose.ui.res.pluralStringResource
import com.gabrielifrim.hora.core.ui.component.AgendaMark
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.ConfirmDialog
import com.gabrielifrim.hora.core.ui.component.DatePickerSheet
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.colorKeyOf
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.text.timeRange
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.ZoneId

/** La fiche d'un événement : quand, où, notes, et ce qu'on peut en faire. */
@Composable
fun EventDetailScreen(
    eventId: Long,
    occurrence: Long,
    onBack: () -> Unit,
    onEdit: (Long, Long?) -> Unit,
    onDuplicate: (Long, Long?) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel<EventDetailViewModel, EventDetailViewModel.Factory> { it.create(eventId, occurrence) },
) {
    val event by viewModel.event.collectAsStateWithLifecycle()
    val agendas by viewModel.agendas.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val zone = ZoneId.systemDefault()
    val haptics = rememberHaptics()
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Un événement supprimé ailleurs (ou introuvable) ferme sa fiche.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(event) { if (event != null) seen = true else if (seen) onBack() }

    val e = event ?: return
    val key = colorKeyOf(e, agendas)
    val day = when (val t = e.timing) {
        is Timing.Timed -> t.start.atZone(zone).toLocalDate()
        is Timing.AllDay -> t.first
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PageHeader(title = e.title, onBack = onBack, backLabel = stringResource(R.string.back))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AgendaMark(key.shape, key.color(), size = 12.dp)
                    Eyebrow(agendas[e.agendaId]?.name.orEmpty())
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HoraText(formats.dayLong(day), style = HoraTheme.type.body)
                    HoraText(timeRange(e, zone, formats), style = HoraTheme.type.hour.copy(fontSize = HoraTheme.type.body.fontSize))
                    val t = e.timing
                    if (t is Timing.Timed && t.zone != zone) {
                        HoraText(stringResource(R.string.other_zone, t.zone.id), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                    }
                }
                if (e.isRecurring) {
                    val spec = e.recurrence?.rule?.let { RepeatSpec.parse(it, (e.timing as? Timing.Timed)?.zone) }
                    Field(stringResource(R.string.field_repeat), if (e.parentId != null) stringResource(R.string.repeat_modified_occurrence) else repeatText(spec, e.recurrence?.rule, day, formats))
                }
                if (e.reminders.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Eyebrow(pluralStringResource(UiR.plurals.reminders_count, e.reminders.size, e.reminders.size))
                        e.reminders.forEach { r ->
                            HoraText(reminderText(r, formats) + if (r.alarm) " · " + stringResource(UiR.string.reminder_alarm) else "", style = HoraTheme.type.body)
                        }
                    }
                }
                if (e.location.isNotBlank()) Field(stringResource(R.string.field_location), e.location)
                if (e.notes.isNotBlank()) Field(stringResource(R.string.field_notes), e.notes)
                Rule()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoraButton(stringResource(R.string.action_duplicate), onClick = { onDuplicate(e.id, e.occurrence) }, style = ButtonStyle.Line, modifier = Modifier.weight(1f))
                    HoraButton(stringResource(R.string.action_move), onClick = { moving = true }, style = ButtonStyle.Line, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoraButton(stringResource(R.string.action_delete), onClick = { confirmDelete = true }, style = ButtonStyle.Line, contentColor = HoraTheme.colors.error, modifier = Modifier.weight(1f))
            HoraButton(stringResource(R.string.action_edit), onClick = { onEdit(e.id, e.occurrence) }, modifier = Modifier.weight(1f))
        }
    }

    if (moving) {
        DatePickerSheet(
            initial = day,
            formats = formats,
            onPick = { haptics.confirm(); viewModel.moveTo(it); moving = false },
            onDismiss = { moving = false },
        )
    }
    if (confirmDelete && e.isRecurring) {
        ScopeDialog(
            title = stringResource(R.string.delete_scope_title),
            onPick = { scope -> confirmDelete = false; viewModel.delete(scope) },
            onDismiss = { confirmDelete = false },
        )
    } else if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            message = stringResource(R.string.delete_message, e.title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; viewModel.delete(EditScope.ALL) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(label)
        HoraText(value, style = HoraTheme.type.body)
    }
}
