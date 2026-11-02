package com.gabrielifrim.hora.feature.calendar

import com.gabrielifrim.hora.core.ui.component.FitText
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.calendar.DayLayout
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.ui.component.AgendaMark
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.colorKeyOf
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle

/** Une page de mois : six semaines, une marque (couleur + forme) par événement. */
@Composable
internal fun MonthPage(
    viewModel: CalendarViewModel,
    month: YearMonth,
    agendas: Map<Long, Agenda>,
    zone: ZoneId,
    formats: HoraFormats,
    onDay: (LocalDate) -> Unit,
) {
    val colors = HoraTheme.colors
    val first = formats.weekStart(month.atDay(1))
    val last = first.plusDays(41)
    val events by remember(first, zone) { viewModel.events(first, last, zone) }.collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            if (formats.showWeekNumbers) Box(Modifier.width(WEEK_COLUMN))
            (0L until 7L).forEach { i ->
                Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    FitText(first.plusDays(i).dayOfWeek.getDisplayName(TextStyle.NARROW, formats.locale), style = HoraTheme.type.label, color = colors.ink2)
                }
            }
        }
        (0 until 6).forEach { week ->
            Rule()
            Row(Modifier.fillMaxWidth()) {
                val weekStart = first.plusDays(week * 7L)
                if (formats.showWeekNumbers) {
                    Box(Modifier.width(WEEK_COLUMN).padding(top = 8.dp).clearAndSetSemantics { }, contentAlignment = Alignment.TopCenter) {
                        FitText(formats.week(weekStart).toString(), style = HoraTheme.type.hour.copy(fontSize = HoraTheme.type.label.fontSize), color = colors.ink2, minFontSize = 6.sp)
                    }
                }
                (0L until 7L).forEach { i ->
                    val day = weekStart.plusDays(i)
                    val dayEvents = events.filter { DayLayout.occursOn(it, day, zone) }
                    DayCell(day, inMonth = YearMonth.from(day) == month, isToday = day == today, dayEvents.map { colorKeyOf(it, agendas) }, formats, onDay, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    marks: List<com.gabrielifrim.hora.core.ui.theme.AgendaColorKey>,
    formats: HoraFormats,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val colors = HoraTheme.colors
    val spoken = formats.dayLong(day) + if (marks.isEmpty()) "" else ", " + pluralStringResource(R.plurals.event_count, marks.size, marks.size)
    Column(
        modifier
            .heightIn(min = 72.dp)
            .clickable { onDay(day) }
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Button }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (isToday) {
                // Aujourd'hui : un disque d'encre et son ombre de cadran, portée en bas à droite.
                Box(Modifier.size(26.dp).offset(2.dp, 2.dp).background(colors.shadow, CircleShape))
                Box(Modifier.size(26.dp).background(colors.ink, CircleShape))
            }
            FitText(
                day.dayOfMonth.toString(),
                style = HoraTheme.type.hour,
                color = when {
                    isToday -> colors.onInk
                    inMonth -> colors.ink
                    else -> colors.ink2.copy(alpha = 0.6f)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            marks.take(MAX_MARKS).forEach { AgendaMark(it.shape, it.color(), size = 8.dp) }
        }
        if (marks.size > MAX_MARKS) {
            HoraText("+${marks.size - MAX_MARKS}", style = HoraTheme.type.label, color = colors.ink2) // texte-technique : compteur
        }
    }
}

private val WEEK_COLUMN = 22.dp
private const val MAX_MARKS = 3
