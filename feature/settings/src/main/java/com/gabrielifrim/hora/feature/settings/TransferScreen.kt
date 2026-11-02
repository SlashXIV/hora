package com.gabrielifrim.hora.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.ui.component.AgendaMark
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.ConfirmDialog
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.component.ToggleRow
import com.gabrielifrim.hora.core.ui.component.ToggleSwitch
import com.gabrielifrim.hora.core.ui.component.bleed
import com.gabrielifrim.hora.core.ui.text.agendaColorKey
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.ZoneId

/** Importer un .ics, exporter des agendas, sauvegarder et restaurer toute la base. */
@Composable
fun TransferScreen(onBack: () -> Unit, viewModel: TransferViewModel = hiltViewModel()) {
    val agendas by viewModel.agendas.collectAsStateWithLifecycle()
    val importUi by viewModel.import.collectAsStateWithLifecycle()
    val backup by viewModel.backupState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    val unnamed = stringResource(R.string.import_unnamed)
    var excluded by remember { mutableStateOf(emptySet<Long>()) }
    val exported = agendas.map { it.id }.filterNot { it in excluded }.toSet()
    val formats = rememberFormats()

    // « */* » : selon le gestionnaire de fichiers, un .ics n'a pas toujours le bon type ; le lecteur trie.
    val pickIcs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.read(it, unnamed) } }
    val createIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri -> uri?.let { viewModel.export(it, exported) } }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::chooseFolder) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::inspect) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item { PageHeader(stringResource(R.string.transfer_title), onBack = onBack, backLabel = stringResource(R.string.back), modifier = Modifier.bleed(16.dp)) }

        item {
            message?.let { m ->
                HoraText(
                    stringResource(m.label()),
                    style = HoraTheme.type.body,
                    color = if (m.isError()) HoraTheme.colors.error else HoraTheme.colors.ink,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        item {
            Section(stringResource(R.string.import_section)) {
                HoraText(stringResource(R.string.import_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                when (val ui = importUi) {
                    ImportUi.Idle, ImportUi.Unreadable, ImportUi.Failed -> {
                        if (ui != ImportUi.Idle) {
                            HoraText(stringResource(if (ui == ImportUi.Failed) R.string.import_failed else R.string.import_unreadable), style = HoraTheme.type.body, color = HoraTheme.colors.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        HoraButton(stringResource(R.string.import_pick), onClick = { viewModel.messageShown(); pickIcs.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth())
                    }
                    ImportUi.Reading -> HoraText(stringResource(R.string.import_reading), style = HoraTheme.type.body, color = HoraTheme.colors.ink2)
                    is ImportUi.Preview -> ImportPreview(ui, onToggle = viewModel::toggleSource, onConfirm = { viewModel.confirmImport(unnamed) }, onCancel = viewModel::resetImport)
                    is ImportUi.Done -> {
                        HoraText(
                            stringResource(R.string.import_done, ui.report.added, ui.report.updated, ui.report.agendasCreated),
                            style = HoraTheme.type.body,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        HoraButton(stringResource(R.string.import_again), onClick = viewModel::resetImport, style = ButtonStyle.Line, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.export_section)) {
                HoraText(stringResource(R.string.export_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                agendas.forEach { agenda ->
                    val key = agendaColorKey(agenda.colorKey)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AgendaMark(key.shape, key.color(), size = 12.dp)
                        HoraText(agenda.name, style = HoraTheme.type.body, modifier = Modifier.weight(1f))
                        ToggleSwitch(
                            checked = agenda.id !in excluded,
                            onCheckedChange = { on -> excluded = if (on) excluded - agenda.id else excluded + agenda.id },
                            label = stringResource(R.string.export_agenda, agenda.name),
                        )
                    }
                }
                HoraButton(
                    stringResource(R.string.export_button, pluralStringResource(R.plurals.agenda_count, exported.size, exported.size)),
                    onClick = { viewModel.messageShown(); createIcs.launch("hora-${LocalDate.now()}.ics") }, // texte-technique : nom de fichier
                    enabled = exported.isNotEmpty(),
                    style = ButtonStyle.Line,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Section(stringResource(R.string.backup_section)) {
                HoraText(stringResource(R.string.backup_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        HoraText(stringResource(R.string.backup_folder), style = HoraTheme.type.body)
                        HoraText(backup.folderName ?: stringResource(R.string.backup_no_folder), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                    }
                    HoraButton(stringResource(R.string.backup_choose), onClick = { pickFolder.launch(null) }, style = ButtonStyle.Text)
                }
                val last = backup.last?.atZone(ZoneId.systemDefault())
                HoraText(
                    if (last == null) stringResource(R.string.backup_never) else stringResource(R.string.backup_last, formats.daySpoken(last.toLocalDate()), formats.time(last.toLocalTime())),
                    style = HoraTheme.type.bodySmall,
                    color = HoraTheme.colors.ink2,
                )
                if (backup.lastFailed) HoraText(stringResource(R.string.backup_failed), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.error)
                HoraButton(stringResource(R.string.backup_now), onClick = { viewModel.messageShown(); viewModel.backupNow() }, enabled = backup.folder != null, modifier = Modifier.fillMaxWidth())
                if (backup.folder != null) {
                    ToggleRow(
                        title = stringResource(R.string.backup_auto),
                        hint = stringResource(R.string.backup_auto_hint),
                        checked = backup.auto,
                        onCheckedChange = viewModel::setAuto,
                    )
                }
                HoraButton(stringResource(R.string.restore_pick), onClick = { viewModel.messageShown(); pickBackup.launch(arrayOf("*/*")) }, style = ButtonStyle.Line, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    restore?.let { check ->
        ConfirmDialog(
            title = stringResource(R.string.restore_title),
            message = stringResource(
                R.string.restore_message,
                pluralStringResource(R.plurals.import_count, check.events, check.events),
                pluralStringResource(R.plurals.agenda_count, check.agendas, check.agendas),
            ),
            confirmLabel = stringResource(R.string.restore_confirm),
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore,
        )
    }
}

/** Les agendas trouvés dans le fichier, à cocher, et ce qui a dû être adapté. */
@Composable
private fun ImportPreview(ui: ImportUi.Preview, onToggle: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    ui.sources.forEach { source ->
        val key = agendaColorKey(source.colorKey)
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AgendaMark(key.shape, key.color(), size = 12.dp)
            Column(Modifier.weight(1f)) {
                HoraText(source.name, style = HoraTheme.type.body)
                HoraText(
                    pluralStringResource(R.plurals.import_count, source.count, source.count) + " · " + // texte-technique : séparateur
                        if (source.joinsExisting) stringResource(R.string.import_joins) else stringResource(R.string.import_new),
                    style = HoraTheme.type.bodySmall,
                    color = HoraTheme.colors.ink2,
                )
            }
            ToggleSwitch(checked = source.include, onCheckedChange = { onToggle(source.name) }, label = stringResource(R.string.import_include, source.name))
        }
    }
    val f = ui.file
    val notes = buildList {
        if (f.tasks > 0) add(pluralStringResource(R.plurals.import_tasks, f.tasks, f.tasks))
        if (f.skipped > 0) add(pluralStringResource(R.plurals.import_skipped, f.skipped, f.skipped))
        if (f.shiftedReminders > 0) add(pluralStringResource(R.plurals.import_shifted, f.shiftedReminders, f.shiftedReminders))
        if (f.unknownZones.isNotEmpty()) add(stringResource(R.string.import_zones, f.unknownZones.joinToString()))
    }
    notes.forEach { HoraText(it, style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2) }
    HoraButton(
        pluralStringResource(R.plurals.import_confirm, ui.selected, ui.selected),
        onClick = onConfirm,
        enabled = ui.selected > 0,
        modifier = Modifier.fillMaxWidth(),
    )
    HoraButton(stringResource(com.gabrielifrim.hora.core.ui.R.string.action_cancel), onClick = onCancel, style = ButtonStyle.Text, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(title, Modifier.semantics { heading() })
        content()
        Rule(Modifier.padding(top = 6.dp))
    }
}

private fun TransferMessage.label(): Int = when (this) {
    TransferMessage.EXPORTED -> R.string.transfer_exported
    TransferMessage.EXPORT_FAILED -> R.string.transfer_export_failed
    TransferMessage.BACKED_UP -> R.string.transfer_backed_up
    TransferMessage.BACKUP_FAILED -> R.string.transfer_backup_failed
    TransferMessage.NOT_HORA -> R.string.transfer_not_hora
    TransferMessage.TOO_NEW -> R.string.transfer_too_new
    TransferMessage.RESTORE_FAILED -> R.string.transfer_restore_failed
}

private fun TransferMessage.isError(): Boolean = this != TransferMessage.EXPORTED && this != TransferMessage.BACKED_UP
