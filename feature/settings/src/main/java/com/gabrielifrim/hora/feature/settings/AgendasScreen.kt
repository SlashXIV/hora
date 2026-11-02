package com.gabrielifrim.hora.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.ui.component.AddReminderSheet
import com.gabrielifrim.hora.core.ui.component.AgendaMark
import com.gabrielifrim.hora.core.ui.component.ReminderList
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.ConfirmDialog
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraIcons
import com.gabrielifrim.hora.core.ui.component.HoraSheet
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.HoraTextField
import com.gabrielifrim.hora.core.ui.component.IconAction
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.component.Tag
import com.gabrielifrim.hora.core.ui.component.ToggleSwitch
import com.gabrielifrim.hora.core.ui.component.bleed
import com.gabrielifrim.hora.core.ui.text.agendaColorKey
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.labelRes
import com.gabrielifrim.hora.core.ui.theme.AgendaColorKey
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AgendasViewModel @Inject constructor(private val repository: AgendaRepository) : ViewModel() {
    val agendas: StateFlow<List<Agenda>> = repository.agendas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(agenda: Agenda) {
        viewModelScope.launch { repository.save(agenda) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

/** Les agendas : couleur et forme, visible ou masqué, nom. Il en reste toujours au moins un. */
@Composable
fun AgendasScreen(onBack: () -> Unit, viewModel: AgendasViewModel = hiltViewModel()) {
    val agendas by viewModel.agendas.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Agenda?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            PageHeader(
                stringResource(R.string.agendas_title),
                onBack = onBack,
                backLabel = stringResource(R.string.back),
                modifier = Modifier.bleed(16.dp),
                actions = {
                    IconAction(HoraIcons.Add, stringResource(R.string.agenda_add), onClick = {
                        editing = Agenda(name = "", colorKey = AgendaColorKey.entries[agendas.size % AgendaColorKey.entries.size].name, sortOrder = agendas.size)
                    })
                },
            )
        }
        items(agendas, key = { it.id }) { agenda ->
            val key = agendaColorKey(agenda.colorKey)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgendaMark(key.shape, key.color(), size = 14.dp)
                Column(Modifier.weight(1f).clickable { editing = agenda }.padding(vertical = 8.dp)) {
                    HoraText(agenda.name, style = HoraTheme.type.body)
                    HoraText(stringResource(if (agenda.hidden) R.string.agenda_hidden else R.string.agenda_edit_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                }
                ToggleSwitch(checked = !agenda.hidden, onCheckedChange = { viewModel.save(agenda.copy(hidden = !it)) }, label = stringResource(R.string.agenda_visible, agenda.name))
            }
            Rule()
        }
    }

    editing?.let { agenda ->
        AgendaSheet(
            agenda = agenda,
            canDelete = agenda.id != 0L && agendas.size > 1,
            onSave = { viewModel.save(it); editing = null },
            onDelete = { viewModel.delete(agenda.id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AgendaSheet(agenda: Agenda, canDelete: Boolean, onSave: (Agenda) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(agenda.name) }
    var color by remember { mutableStateOf(agenda.colorKey) }
    var defaults by remember { mutableStateOf(agenda.defaultReminders) }
    var adding by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val formats = rememberFormats()
    HoraSheet(
        onDismissRequest = onDismiss,
        actions = {
            HoraButton(stringResource(R.string.save), onClick = { onSave(agenda.copy(name = name.trim(), colorKey = color, defaultReminders = defaults)) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
            if (canDelete) {
                HoraButton(stringResource(R.string.agenda_delete), onClick = { confirm = true }, style = ButtonStyle.Text, contentColor = HoraTheme.colors.error, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        HoraText(stringResource(if (agenda.id == 0L) R.string.agenda_new else R.string.agenda_edit), style = HoraTheme.type.heading)
        HoraTextField(name, { name = it }, label = stringResource(R.string.agenda_name))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AgendaColorKey.entries.forEach { key ->
                Tag(
                    stringResource(key.labelRes()),
                    selected = color == key.name,
                    onClick = { color = key.name },
                    leading = { AgendaMark(key.shape, key.color(), size = 10.dp) },
                )
            }
        }
        HoraText(stringResource(R.string.agenda_defaults_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        ReminderList(defaults, formats, onRemove = { defaults = defaults - it }, onAdd = { adding = true })
    }
    if (adding) {
        AddReminderSheet(emptyList(), formats, onAdd = { defaults = (defaults + it).distinct(); adding = false }, onDismiss = { adding = false })
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.agenda_delete_title),
            message = stringResource(R.string.agenda_delete_message, agenda.name),
            confirmLabel = stringResource(R.string.agenda_delete),
            onConfirm = { confirm = false; onDelete() },
            onDismiss = { confirm = false },
        )
    }
}
