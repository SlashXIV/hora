package com.gabrielifrim.hora.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.PageHeader
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.text.rememberAppLocale
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * L'écran d'ouverture. Au socle (J1), la date seule : le prochain événement, les
 * plages, la fête et le compte à rebours arrivent avec les jalons suivants.
 *
 * @param firstDayOfWeek null : celui de la langue du téléphone.
 */
@Composable
fun TodayScreen(firstDayOfWeek: DayOfWeek?, modifier: Modifier = Modifier) {
    // La date se relit à chaque retour au premier plan : l'écran laissé ouvert la
    // veille ne doit pas afficher hier.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose {}
    }
    val locale = rememberAppLocale()
    val weekFields = firstDayOfWeek?.let { WeekFields.of(it, 4) } ?: WeekFields.of(locale)
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    val week = today.get(weekFields.weekOfWeekBasedYear())

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader(
            title = today.format(DateTimeFormatter.ofPattern("d MMMM", locale)),
            eyebrow = stringResource(R.string.today_eyebrow, weekday, week),
            poster = true,
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Rule()
            HoraText(stringResource(R.string.today_empty_title), style = HoraTheme.type.heading)
            HoraText(stringResource(R.string.today_empty_hint), style = HoraTheme.type.body, color = HoraTheme.colors.ink2)
        }
    }
}
