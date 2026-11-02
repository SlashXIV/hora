package com.gabrielifrim.hora.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.BackupRepository
import com.gabrielifrim.hora.core.domain.repository.BackupState
import com.gabrielifrim.hora.core.domain.repository.ImportGroup
import com.gabrielifrim.hora.core.domain.repository.ImportReport
import com.gabrielifrim.hora.core.domain.repository.RestoreCheck
import com.gabrielifrim.hora.core.domain.repository.TransferRepository
import com.gabrielifrim.hora.core.ics.IcsImport
import com.gabrielifrim.hora.core.ics.IcsReader
import com.gabrielifrim.hora.core.ics.IcsWriter
import com.gabrielifrim.hora.core.ui.theme.AgendaColorKey
import com.gabrielifrim.hora.core.ui.theme.nearestAgendaColor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un agenda du fichier, tel qu'il sera importé : dans un agenda existant de même nom, ou nouveau. */
data class SourceAgendaChoice(
    val name: String,
    val colorKey: String,
    val count: Int,
    val include: Boolean = true,
    /** Un agenda Hora porte déjà ce nom : les événements le rejoignent. */
    val joinsExisting: Boolean,
)

sealed interface ImportUi {
    data object Idle : ImportUi
    data object Reading : ImportUi
    data class Preview(val file: IcsImport, val sources: List<SourceAgendaChoice>) : ImportUi {
        val selected: Int get() = sources.filter { it.include }.sumOf { it.count }
    }
    data class Done(val report: ImportReport) : ImportUi
    /** Fichier illisible ou sans événement. */
    data object Unreadable : ImportUi
}

/** Résultat d'une action ponctuelle, affiché une fois. */
enum class TransferMessage { EXPORTED, EXPORT_FAILED, BACKED_UP, BACKUP_FAILED, NOT_HORA, TOO_NEW }

@HiltViewModel
class TransferViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transfer: TransferRepository,
    private val backup: BackupRepository,
    private val agendaRepository: AgendaRepository,
) : ViewModel() {
    val agendas: StateFlow<List<Agenda>> = agendaRepository.agendas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val backupState: StateFlow<BackupState> = backup.state

    private val _import = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val import: StateFlow<ImportUi> = _import.asStateFlow()

    private val _message = MutableStateFlow<TransferMessage?>(null)
    val message: StateFlow<TransferMessage?> = _message.asStateFlow()

    private val _restore = MutableStateFlow<RestoreCheck.Ready?>(null)
    val restore: StateFlow<RestoreCheck.Ready?> = _restore.asStateFlow()

    /** Lit le fichier et prépare l'aperçu ; rien n'est écrit avant [confirmImport]. */
    fun read(uri: Uri, unnamedAgenda: String) {
        _import.value = ImportUi.Reading
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
                    IcsReader.read(bytes, ZoneId.systemDefault())
                }.getOrNull()
            }
            if (file == null || file.entries.isEmpty()) {
                _import.value = ImportUi.Unreadable
                return@launch
            }
            val existing = agendaRepository.agendas.first()
            val used = existing.map { it.colorKey }.toMutableSet()
            val sources = file.entries.groupBy { it.agenda.name ?: unnamedAgenda }.map { (name, entries) ->
                val match = existing.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                val source = entries.first().agenda
                val color = match?.colorKey
                    ?: source.colorKey?.takeIf { k -> AgendaColorKey.entries.any { it.name == k } }
                    ?: source.argb?.let { nearestAgendaColor(it).name }
                    ?: (AgendaColorKey.entries.firstOrNull { it.name !in used } ?: AgendaColorKey.SLATE).name
                used += color
                SourceAgendaChoice(name, color, entries.size, joinsExisting = match != null)
            }
            _import.value = ImportUi.Preview(file, sources)
        }
    }

    fun toggleSource(name: String) = _import.update { ui ->
        if (ui !is ImportUi.Preview) ui else ui.copy(sources = ui.sources.map { if (it.name == name) it.copy(include = !it.include) else it })
    }

    fun confirmImport(unnamedAgenda: String) {
        val preview = _import.value as? ImportUi.Preview ?: return
        _import.value = ImportUi.Reading
        viewModelScope.launch {
            val chosen = preview.sources.filter { it.include }.associateBy { it.name }
            val groups = preview.file.entries.groupBy { it.agenda.name ?: unnamedAgenda }.mapNotNull { (name, entries) ->
                chosen[name]?.let { c -> ImportGroup(name, c.colorKey, entries.map { it.item }) }
            }
            _import.value = ImportUi.Done(transfer.import(groups))
        }
    }

    fun resetImport() {
        _import.value = ImportUi.Idle
    }

    /** Écrit les agendas [agendaIds] dans le fichier choisi. */
    fun export(uri: Uri, agendaIds: Set<Long>) {
        viewModelScope.launch {
            val ok = runCatching {
                val events = transfer.events(agendaIds)
                val text = IcsWriter.write(events, agendaRepository.agendas.first().filter { it.id in agendaIds }.associateBy { it.id })
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            _message.value = if (ok) TransferMessage.EXPORTED else TransferMessage.EXPORT_FAILED
        }
    }

    fun chooseFolder(uri: Uri) = backup.chooseFolder(uri.toString())

    fun setAuto(on: Boolean) = backup.setAuto(on)

    fun backupNow() {
        viewModelScope.launch {
            _message.value = if (backup.backupNow().isSuccess) TransferMessage.BACKED_UP else TransferMessage.BACKUP_FAILED
        }
    }

    fun inspect(uri: Uri) {
        viewModelScope.launch {
            when (val check = backup.inspect(uri.toString())) {
                is RestoreCheck.Ready -> _restore.value = check
                RestoreCheck.NotHora -> _message.value = TransferMessage.NOT_HORA
                RestoreCheck.TooNew -> _message.value = TransferMessage.TOO_NEW
            }
        }
    }

    fun cancelRestore() {
        _restore.value = null
    }

    fun confirmRestore() {
        val check = _restore.value ?: return
        viewModelScope.launch { backup.restore(check) }
    }

    fun messageShown() {
        _message.value = null
    }
}
