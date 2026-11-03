package com.gabrielifrim.hora.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.ui.component.EventRow
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.HoraTextField
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.text.timeRange
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf

/** Recherche dans le titre, le lieu et les notes ; résultats du plus récent au plus ancien. */
@Composable
fun SearchScreen(onOpenEvent: (Long, Long?) -> Unit, modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    var query by rememberSaveable { mutableStateOf("") }
    val agendas by viewModel.agendas.collectAsStateWithLifecycle()
    val results by remember(query) { if (query.isBlank()) flowOf(emptyList()) else viewModel.search(query) }.collectAsStateWithLifecycle(emptyList())
    val formats = rememberFormats()
    val zone = ZoneId.systemDefault()
    val sorted = remember(results) {
        results.sortedByDescending { e ->
            when (val t = e.timing) {
                is Timing.Timed -> t.start.toEpochMilli()
                is Timing.AllDay -> t.first.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
    }
    val focus = remember { FocusRequester() }
    // Clavier ouvert d’emblée : on vient ici pour taper.
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Column(modifier.fillMaxSize()) {
        PageHeader(stringResource(R.string.search_title))
        HoraTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.search_label),
            placeholder = stringResource(R.string.search_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.padding(horizontal = 16.dp).focusRequester(focus),
        )
        if (query.isNotBlank()) {
            HoraText(
                if (sorted.isEmpty()) stringResource(R.string.search_none) else pluralStringResource(R.plurals.search_count, sorted.size, sorted.size),
                style = HoraTheme.type.bodySmall,
                color = HoraTheme.colors.ink2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(sorted, key = { it.id }) { event ->
                val day = when (val t = event.timing) {
                    is Timing.Timed -> t.start.atZone(zone).toLocalDate()
                    is Timing.AllDay -> t.first
                }
                EventRow(
                    event, agendas, zone, formats,
                    onClick = { onOpenEvent(event.id, event.occurrence) },
                    subtitle = formats.dayShortWithYear(day) + " · " + timeRange(event, zone, formats),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
