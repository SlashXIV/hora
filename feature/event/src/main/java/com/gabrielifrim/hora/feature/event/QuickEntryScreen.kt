package com.gabrielifrim.hora.feature.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.quick.QuickEntry
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.EventRow
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.HoraTextField
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.component.Tag
import com.gabrielifrim.hora.core.ui.component.reminderText
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.theme.AgendaColorKey
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import com.gabrielifrim.hora.core.ui.text.color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class QuickEntryViewModel @Inject constructor(agendaRepository: AgendaRepository, private val events: EventRepository) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    val text = MutableStateFlow("")
    val agendas: StateFlow<Map<Long, Agenda>> = agendaRepository.agendas.map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Le brouillon que la phrase donnerait : premier agenda visible, ses rappels par défaut. */
    val draft: StateFlow<EventDraft?> = combine(text, agendaRepository.agendas) { t, list ->
        val agenda = list.firstOrNull { !it.hidden } ?: list.firstOrNull() ?: return@combine null
        EventDraft.new(agendaId = agenda.id, date = LocalDate.now(), minute = null, zone = zone, reminders = agenda.defaultReminders)
            .withQuick(QuickEntry.parse(t, LocalDateTime.now()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(onCreated: (Long) -> Unit) {
        val d = draft.value?.takeIf { it.canSave } ?: return
        viewModelScope.launch { onCreated(events.save(d.toEvent())) }
    }
}

/**
 * « Écris comme tu parles » : l'aperçu suit chaque lettre, rien n'est créé avant
 * « Créer ». « Détailler » ouvre l'éditeur pré-rempli.
 */
@Composable
fun QuickEntryScreen(onClose: () -> Unit, onDetail: (String) -> Unit, onCreated: (Long) -> Unit, viewModel: QuickEntryViewModel = hiltViewModel()) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val agendas by viewModel.agendas.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val haptics = rememberHaptics()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val d = draft
    val canCreate = d != null && d.canSave
    val create = { if (canCreate) { haptics.confirm(); viewModel.create(onCreated) } }
    // Les mots compris prennent la couleur de ce qu'ils sont devenus, comme sur la planche.
    val partColors = mapOf(
        QuickEntry.Part.TITLE to HoraTheme.colors.accent,
        QuickEntry.Part.WHEN to AgendaColorKey.FIR.color(),
        QuickEntry.Part.REMINDER to AgendaColorKey.BRICK.color(),
        QuickEntry.Part.PLACE to AgendaColorKey.TEAL.color(),
    )
    val highlight = VisualTransformation { input ->
        val styled = buildAnnotatedString {
            append(input.text)
            QuickEntry.parts(input.text, LocalDateTime.now()).forEach { (range, part) ->
                addStyle(SpanStyle(color = partColors.getValue(part)), range.first, range.last + 1)
            }
        }
        TransformedText(styled, OffsetMapping.Identity)
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PageHeader(stringResource(R.string.quick_screen_title), onBack = onClose, backLabel = stringResource(R.string.back))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HoraTextField(
                    value = text,
                    onValueChange = { viewModel.text.value = it },
                    label = stringResource(R.string.quick_label),
                    placeholder = stringResource(R.string.quick_example_1),
                    modifier = Modifier.focusRequester(focus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    visualTransformation = highlight,
                )
                Eyebrow(stringResource(R.string.quick_preview))
                if (d != null && d.title.isNotBlank()) {
                    // Le jour d'abord : c'est ce que la phrase a le plus de chances de mal dire.
                    val event = d.toEvent()
                    val subtitle = listOf(formats.dayLong(d.start.toLocalDate()), event.location).filter { it.isNotBlank() }.joinToString(" · ")
                    EventRow(event, agendas, d.zone, formats, onClick = { onDetail(text) }, subtitle = subtitle)
                    d.reminders.forEach { r -> HoraText(reminderText(r, formats), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2) }
                } else {
                    HoraText(stringResource(R.string.quick_preview_empty), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                }
                Eyebrow(stringResource(R.string.quick_examples))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(R.string.quick_example_1, R.string.quick_example_2, R.string.quick_example_3).forEach { res ->
                        val example = stringResource(res)
                        Tag(example, selected = false, onClick = { viewModel.text.value = example })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoraButton(stringResource(R.string.quick_detail), onClick = { onDetail(text) }, style = ButtonStyle.Line, enabled = text.isNotBlank(), modifier = Modifier.weight(1f))
            HoraButton(stringResource(R.string.quick_create), onClick = create, enabled = canCreate, modifier = Modifier.weight(1f))
        }
    }
}
