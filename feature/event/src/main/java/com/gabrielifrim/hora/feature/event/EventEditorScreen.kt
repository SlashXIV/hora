package com.gabrielifrim.hora.feature.event

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.gabrielifrim.hora.core.domain.recurrence.EditScope
import com.gabrielifrim.hora.core.ui.component.AddReminderSheet
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.HoraDialog
import com.gabrielifrim.hora.core.ui.component.ReminderList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.ui.component.AgendaMark
import com.gabrielifrim.hora.core.ui.component.DatePickerSheet
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraSheet
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.HoraTextField
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.component.Tag
import com.gabrielifrim.hora.core.ui.component.TimePickerSheet
import com.gabrielifrim.hora.core.ui.component.ToggleRow
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.agendaColorKey
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.labelRes
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.theme.AgendaColorKey
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.ZoneId

private enum class Picking { START_DATE, START_TIME, END_DATE, END_TIME, ZONE, REPEAT, REMINDER, SAVE_SET, SCOPE }

@Composable
fun EventEditorScreen(
    eventId: Long,
    occurrence: Long,
    duplicateOf: Long,
    date: LocalDate,
    minute: Int?,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: EventEditorViewModel = hiltViewModel<EventEditorViewModel, EventEditorViewModel.Factory> {
        it.create(eventId, occurrence, duplicateOf, date, minute)
    },
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val agendas by viewModel.agendas.collectAsStateWithLifecycle()
    val sets by viewModel.reminderSets.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val haptics = rememberHaptics()
    var picking by remember { mutableStateOf<Picking?>(null) }
    val context = LocalContext.current
    // Notifications (Android 13+) : demandées au moment où un rappel en a besoin, pas au lancement.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val save: () -> Unit = {
        haptics.confirm()
        if (viewModel.needsScope) picking = Picking.SCOPE else viewModel.save(EditScope.ALL, onSaved)
    }
    val d = draft ?: return

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PageHeader(
                title = stringResource(if (d.id == 0L) R.string.editor_new else R.string.editor_edit),
                onBack = onClose,
                backLabel = stringResource(R.string.back),
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HoraTextField(
                    value = d.title,
                    onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                    label = stringResource(R.string.field_title),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                ToggleRow(
                    title = stringResource(R.string.field_all_day),
                    checked = d.allDay,
                    onCheckedChange = { on -> haptics.tick(); viewModel.update { it.copy(allDay = on) } },
                )
                WhenRow(stringResource(R.string.field_start), d.start.toLocalDate(), d.start.toLocalTime().takeUnless { d.allDay }, formats,
                    onDate = { picking = Picking.START_DATE }, onTime = { picking = Picking.START_TIME })
                WhenRow(stringResource(R.string.field_end), d.end.toLocalDate(), d.end.toLocalTime().takeUnless { d.allDay }, formats,
                    onDate = { picking = Picking.END_DATE }, onTime = { picking = Picking.END_TIME })
                if (!d.allDay) {
                    ValueRow(stringResource(R.string.field_zone), d.zone.id, onClick = { picking = Picking.ZONE })
                }
                ValueRow(stringResource(R.string.field_repeat), repeatText(d.repeat, d.customRule, d.start.toLocalDate(), formats), onClick = { picking = Picking.REPEAT })
                ToggleRow(
                    title = stringResource(R.string.field_busy),
                    checked = d.busy,
                    onCheckedChange = { on -> haptics.tick(); viewModel.update { it.copy(busy = on) } },
                )
                Rule()
                ReminderList(
                    reminders = d.reminders,
                    formats = formats,
                    onRemove = { r -> haptics.tick(); viewModel.update { it.copy(reminders = it.reminders - r) } },
                    onAdd = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        picking = Picking.REMINDER
                    },
                )
                if (d.reminders.size > 1) {
                    HoraButton(stringResource(R.string.save_as_set), onClick = { picking = Picking.SAVE_SET }, style = ButtonStyle.Text)
                }
                Rule()
                AgendaChoice(agendas, d.agendaId) { id -> haptics.tick(); viewModel.chooseAgenda(id) }
                ColorChoice(d.colorKey) { key -> haptics.tick(); viewModel.update { it.copy(colorKey = key) } }
                Rule()
                HoraTextField(
                    value = d.location,
                    onValueChange = { v -> viewModel.update { it.copy(location = v) } },
                    label = stringResource(R.string.field_location),
                )
                HoraTextField(
                    value = d.notes,
                    onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                    label = stringResource(R.string.field_notes),
                    singleLine = false,
                    minLines = 3,
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (d.title.isBlank()) HoraText(stringResource(R.string.editor_title_needed), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
            HoraButton(
                stringResource(R.string.action_save),
                onClick = save,
                enabled = d.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (picking) {
        Picking.START_DATE -> DatePickerSheet(d.start.toLocalDate(), formats, onPick = { day ->
            viewModel.update { it.withStart(day.atTime(it.start.toLocalTime())) }; picking = null
        }, onDismiss = { picking = null })
        Picking.END_DATE -> DatePickerSheet(d.end.toLocalDate(), formats, onPick = { day ->
            viewModel.update { it.withEnd(day.atTime(it.end.toLocalTime())) }; picking = null
        }, onDismiss = { picking = null })
        Picking.START_TIME -> TimePickerSheet(d.start.toLocalTime(), formats, onPick = { t ->
            viewModel.update { it.withStart(it.start.toLocalDate().atTime(t)) }; picking = null
        }, onDismiss = { picking = null })
        Picking.END_TIME -> TimePickerSheet(d.end.toLocalTime(), formats, onPick = { t ->
            viewModel.update { it.withEnd(it.end.toLocalDate().atTime(t)) }; picking = null
        }, onDismiss = { picking = null })
        Picking.ZONE -> ZonePickerSheet(d.zone, onPick = { z -> viewModel.update { it.copy(zone = z) }; picking = null }, onDismiss = { picking = null })
        Picking.REPEAT -> RepeatSheet(d.repeat, d.customRule, d.start.toLocalDate(), formats, onPick = { spec ->
            viewModel.update { it.copy(repeat = spec, customRule = null) }; picking = null
        }, onDismiss = { picking = null })
        Picking.REMINDER -> AddReminderSheet(sets, formats, onAdd = { added ->
            haptics.tick(); viewModel.update { it.copy(reminders = (it.reminders + added).distinct()) }; picking = null
        }, onDismiss = { picking = null })
        Picking.SAVE_SET -> SaveSetDialog(onSave = { name -> viewModel.saveAsSet(name); picking = null }, onDismiss = { picking = null })
        Picking.SCOPE -> ScopeDialog(stringResource(R.string.edit_scope_title), onPick = { scope -> picking = null; viewModel.save(scope, onSaved) }, onDismiss = { picking = null })
        null -> Unit
    }
}

@Composable
private fun WhenRow(label: String, date: LocalDate, time: java.time.LocalTime?, formats: HoraFormats, onDate: () -> Unit, onTime: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(formats.dayLong(date), stringResource(R.string.change_value, label, formats.dayLong(date)), Modifier.weight(1f), onDate)
            // Largeur du texte seulement : sans borne, le filet du champ heure prendrait toute la ligne.
            if (time != null) Pill(formats.time(time), stringResource(R.string.change_value, label, formats.time(time)), Modifier.width(IntrinsicSize.Max), onTime)
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(label)
        Pill(value, stringResource(R.string.change_value, label, value), Modifier.fillMaxWidth(), onClick)
    }
}

/** Valeur qu'on touche pour la changer : soulignée d'un filet, comme un champ. */
@Composable
private fun Pill(text: String, spoken: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Button },
        verticalArrangement = Arrangement.Center,
    ) {
        HoraText(text, style = HoraTheme.type.body)
        Rule(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun AgendaChoice(agendas: List<Agenda>, selected: Long, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(stringResource(R.string.field_agenda))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            agendas.forEach { agenda ->
                val key = agendaColorKey(agenda.colorKey)
                Tag(agenda.name, selected = agenda.id == selected, onClick = { onSelect(agenda.id) }, leading = { AgendaMark(key.shape, key.color(), size = 10.dp) })
            }
        }
    }
}

@Composable
private fun ColorChoice(selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(stringResource(R.string.field_color))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(stringResource(R.string.color_of_agenda), selected = selected == null, onClick = { onSelect(null) })
            AgendaColorKey.entries.forEach { key ->
                Tag(
                    stringResource(key.labelRes()),
                    selected = selected == key.name,
                    onClick = { onSelect(key.name) },
                    leading = { AgendaMark(key.shape, key.color(), size = 10.dp) },
                )
            }
        }
    }
}

@Composable
private fun SaveSetDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    HoraDialog(
        title = stringResource(R.string.save_as_set),
        onDismissRequest = onDismiss,
        actions = {
            HoraButton(stringResource(R.string.cancel), onClick = onDismiss, style = ButtonStyle.Text)
            HoraButton(stringResource(R.string.action_save), onClick = { onSave(name) }, enabled = name.isNotBlank())
        },
    ) {
        HoraTextField(name, { name = it }, label = stringResource(R.string.set_name), placeholder = stringResource(R.string.set_name_hint))
    }
}

/** Fuseau horaire : la recherche filtre les quelque 600 identifiants (« Paris », « New_York »). */
@Composable
private fun ZonePickerSheet(current: ZoneId, onPick: (ZoneId) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember { ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc") }.sorted() }
    val shown = remember(query) {
        val q = query.trim().replace(' ', '_')
        listOf(ZoneId.systemDefault().id) + all.filter { q.isEmpty() || it.contains(q, ignoreCase = true) }.take(80)
    }.distinct()
    HoraSheet(onDismissRequest = onDismiss) {
        HoraText(stringResource(R.string.field_zone), style = HoraTheme.type.heading)
        HoraTextField(query, { query = it }, label = stringResource(R.string.zone_search))
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(shown) { id ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(ZoneId.of(id)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HoraText(id.replace('_', ' '), color = if (id == current.id) HoraTheme.colors.accent else HoraTheme.colors.ink)
                }
            }
        }
    }
}
