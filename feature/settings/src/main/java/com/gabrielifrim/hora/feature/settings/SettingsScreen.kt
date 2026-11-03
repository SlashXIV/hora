package com.gabrielifrim.hora.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.model.ClockFormat
import com.gabrielifrim.hora.core.domain.model.MotionPreference
import com.gabrielifrim.hora.core.domain.model.ThemePreference
import com.gabrielifrim.hora.core.domain.model.UserSettings
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.component.SegmentedChoice
import com.gabrielifrim.hora.core.ui.component.ToggleRow
import com.gabrielifrim.hora.core.ui.component.bleed
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.DayOfWeek

@Composable
fun SettingsScreen(onOpenAgendas: () -> Unit, onOpenTransfer: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    settings?.let { SettingsContent(it, viewModel::update, onOpenAgendas, onOpenTransfer, modifier) }
}

@Composable
private fun SettingsContent(
    settings: UserSettings,
    update: ((UserSettings) -> UserSettings) -> Unit,
    onOpenAgendas: () -> Unit,
    onOpenTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item { PageHeader(stringResource(R.string.settings_title), modifier = Modifier.bleed(16.dp)) }

        item {
            Section(stringResource(R.string.settings_agendas)) {
                LinkRow(stringResource(R.string.settings_agendas), stringResource(R.string.settings_agendas_hint), onOpenAgendas)
                LinkRow(stringResource(R.string.settings_transfer), stringResource(R.string.settings_transfer_hint), onOpenTransfer)
            }
        }

        item {
            Section(stringResource(R.string.settings_plages)) {
                PlagesSection(settings.plages) { list -> haptics.tick(); update { it.copy(plages = list) } }
            }
        }

        item {
            Section(stringResource(R.string.settings_reminders)) {
                ReminderReliability()
                HoraText(stringResource(R.string.settings_sets), style = HoraTheme.type.body)
                ReminderSets()
            }
        }

        item {
            Section(stringResource(R.string.settings_appearance)) {
                Choice(
                    options = listOf(R.string.theme_system, R.string.theme_midi, R.string.theme_nocturne),
                    selected = settings.theme.ordinal,
                ) { i -> haptics.tick(); update { it.copy(theme = ThemePreference.entries[i]) } }
                Choice(
                    options = listOf(R.string.motion_expressive, R.string.motion_calm),
                    selected = settings.motion.ordinal,
                    label = stringResource(R.string.motion),
                    hint = stringResource(R.string.motion_hint),
                ) { i -> haptics.tick(); update { it.copy(motion = MotionPreference.entries[i]) } }
                ToggleRow(
                    title = stringResource(R.string.haptics),
                    checked = settings.haptics,
                    onCheckedChange = { on -> update { it.copy(haptics = on) } },
                )
            }
        }

        item {
            Section(stringResource(R.string.settings_almanac)) {
                var picking by remember { mutableStateOf(false) }
                LinkRow(stringResource(R.string.place), stringResource(R.string.place_hint, settings.place.name)) { picking = true }
                if (picking) {
                    PlaceSheet(settings.place, onPick = { place -> haptics.tick(); update { it.copy(place = place) }; picking = false }, onDismiss = { picking = false })
                }
                ToggleRow(
                    title = stringResource(R.string.almanac_bridges),
                    hint = stringResource(R.string.almanac_bridges_hint),
                    checked = settings.almanac.bridges,
                    onCheckedChange = { on -> update { it.copy(almanac = it.almanac.copy(bridges = on)) } },
                )
                ToggleRow(
                    title = stringResource(R.string.almanac_alsace),
                    hint = stringResource(R.string.almanac_alsace_hint),
                    checked = settings.almanac.alsaceMoselle,
                    onCheckedChange = { on -> update { it.copy(almanac = it.almanac.copy(alsaceMoselle = on)) } },
                )
                ToggleRow(
                    title = stringResource(R.string.almanac_more_feasts),
                    hint = stringResource(R.string.almanac_more_feasts_hint),
                    checked = settings.almanac.moreFeasts,
                    onCheckedChange = { on -> update { it.copy(almanac = it.almanac.copy(moreFeasts = on)) } },
                )
                HoraText(stringResource(R.string.almanac_agendas_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
            }
        }

        item {
            Section(stringResource(R.string.settings_prayer)) {
                PrayerSection(settings.prayer, settings.place) { transform -> update { it.copy(prayer = transform(it.prayer)) } }
            }
        }

        item { Section(stringResource(R.string.settings_icon)) { IconPicker(onPicked = haptics::tick) } }

        item {
            Section(stringResource(R.string.settings_week)) {
                val days = listOf(null, DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
                Choice(
                    options = listOf(R.string.first_day_auto, R.string.first_day_monday, R.string.first_day_sunday, R.string.first_day_saturday),
                    selected = days.indexOf(settings.firstDayOfWeek).coerceAtLeast(0),
                    label = stringResource(R.string.first_day),
                ) { i -> haptics.tick(); update { it.copy(firstDayOfWeek = days[i]) } }
                ToggleRow(
                    title = stringResource(R.string.week_numbers),
                    checked = settings.showWeekNumbers,
                    onCheckedChange = { on -> update { it.copy(showWeekNumbers = on) } },
                )
                Choice(
                    options = listOf(R.string.clock_system, R.string.clock_24, R.string.clock_12),
                    selected = settings.clockFormat.ordinal,
                    label = stringResource(R.string.clock_format),
                ) { i -> haptics.tick(); update { it.copy(clockFormat = ClockFormat.entries[i]) } }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, hint: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 4.dp)) {
        HoraText(title, style = HoraTheme.type.body)
        HoraText(hint, style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(title, Modifier.semantics { heading() })
        content()
        Rule(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Choice(
    options: List<Int>,
    selected: Int,
    label: String? = null,
    hint: String? = null,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) HoraText(label, style = HoraTheme.type.body)
        SegmentedChoice(options.map { stringResource(it) }, selected, onSelect)
        if (hint != null) HoraText(hint, style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
}

/** Les quatre icônes dessinées en vignette ronde, comme dans le tiroir d'applications. */
@Composable
private fun IconPicker(onPicked: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIconSwitcher.current(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppIcon.entries.forEach { icon ->
                val selected = icon == current
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (!selected) {
                                AppIconSwitcher.choose(icon)
                                current = icon
                                onPicked()
                            }
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(60.dp)
                            .border(2.dp, if (selected) HoraTheme.colors.accent else HoraTheme.colors.background, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape),
                    ) {
                        // Vignette décorative : le nom de l'icône est lu juste dessous. Agrandie
                        // de 108/72 : le lanceur ne montre que le centre du canevas.
                        val launcherCrop = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
                        Image(painterResource(icon.background), contentDescription = null, modifier = launcherCrop)
                        Image(painterResource(icon.foreground), contentDescription = null, modifier = launcherCrop)
                    }
                    HoraText(
                        stringResource(icon.label),
                        style = HoraTheme.type.bodySmall,
                        color = if (selected) HoraTheme.colors.accent else HoraTheme.colors.ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        HoraText(stringResource(R.string.icon_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
}
