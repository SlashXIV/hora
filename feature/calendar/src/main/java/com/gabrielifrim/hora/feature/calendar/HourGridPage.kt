package com.gabrielifrim.hora.feature.calendar

import com.gabrielifrim.hora.core.ui.component.FitText
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabrielifrim.hora.core.domain.calendar.DayLayout
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Plage
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.colorKeyOf
import com.gabrielifrim.hora.core.ui.text.spokenEvent
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import kotlinx.coroutines.delay

/**
 * Grille horaire d'un jour ou d'une semaine : heures gravées dans la marge, une
 * colonne par jour, la ligne « maintenant » et son ombre oblique. Toucher une
 * heure libre crée un événement à cette heure ; le bouton + fait de même.
 *
 * @param onDay ouvre la vue jour quand on touche l'en-tête d'un jour (semaine seulement).
 */
@Composable
internal fun HourGridPage(
    viewModel: CalendarViewModel,
    days: List<LocalDate>,
    agendas: Map<Long, Agenda>,
    zone: ZoneId,
    formats: HoraFormats,
    onOpenEvent: (Long, Long?) -> Unit,
    onCreate: (LocalDate, Int?) -> Unit,
    onDay: ((LocalDate) -> Unit)?,
) {
    val colors = HoraTheme.colors
    val events by remember(days.first(), days.size, zone) { viewModel.events(days.first(), days.last(), zone) }
        .collectAsStateWithLifecycle(emptyList())
    val plages by viewModel.plages.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val scroll = rememberScrollState()
    val hourPx = with(LocalDensity.current) { HOUR.toPx() }
    // On ouvre la grille vers 7 h, ou une heure avant « maintenant » si c'est plus tard.
    LaunchedEffect(Unit) { scroll.scrollTo((maxOf(7, LocalTime.now().hour - 1) * hourPx).toInt()) }

    Column(Modifier.fillMaxSize()) {
        if (onDay != null) {
            Row(Modifier.fillMaxWidth().padding(start = formats.gutter)) {
                days.forEach { day ->
                    val isToday = day == today
                    Column(
                        Modifier.weight(1f).heightIn(min = 48.dp).clickable { onDay(day) }
                            .clearAndSetSemantics { contentDescription = formats.dayLong(day); role = Role.Button },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FitText(day.dayOfWeek.getDisplayName(TextStyle.SHORT, formats.locale), style = HoraTheme.type.label, color = if (isToday) colors.accent else colors.ink2, minFontSize = 7.sp)
                        FitText(day.dayOfMonth.toString(), style = HoraTheme.type.hour, color = if (isToday) colors.accent else colors.ink)
                    }
                }
            }
        }
        AllDayRow(days, events, agendas, zone, formats, onOpenEvent)
        Rule()
        Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
            HourGutter(formats)
            days.forEach { day ->
                // Le nom d'une plage : en entier dans la vue jour, sur son premier jour en semaine.
                val bands = plages.filter { it.isOn(day.dayOfWeek) }.map { p ->
                    p to when {
                        days.size == 1 -> stringResource(R.string.plage_label, p.name, formats.time(p.start), formats.time(p.end))
                        days.first { p.isOn(it.dayOfWeek) } == day -> p.name
                        else -> null
                    }
                }
                DayColumn(day, events, agendas, zone, formats, isToday = day == today, bands, onOpenEvent, onCreate, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AllDayRow(days: List<LocalDate>, events: List<Event>, agendas: Map<Long, Agenda>, zone: ZoneId, formats: HoraFormats, onOpenEvent: (Long, Long?) -> Unit) {
    val perDay = days.map { DayLayout.allDayOn(events, it) }
    if (perDay.all { it.isEmpty() }) return
    Row(Modifier.fillMaxWidth().padding(start = formats.gutter, end = 2.dp, bottom = 4.dp)) {
        perDay.forEach { list ->
            Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                list.forEach { event -> Block(event, agendas, zone, formats, onOpenEvent, compact = days.size > 1, Modifier.fillMaxWidth().heightIn(min = 24.dp)) }
            }
        }
    }
}

@Composable
private fun HourGutter(formats: HoraFormats) {
    Column(Modifier.width(formats.gutter)) {
        (0 until 24).forEach { h ->
            Box(Modifier.height(HOUR).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                if (h > 0) {
                    FitText(
                        formats.hourLabel(h),
                        style = HoraTheme.type.hour.copy(fontSize = HoraTheme.type.label.fontSize),
                        color = HoraTheme.colors.ink2,
                        minFontSize = 7.sp,
                        modifier = Modifier.offset(y = (-7).dp).padding(end = 6.dp).clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    events: List<Event>,
    agendas: Map<Long, Agenda>,
    zone: ZoneId,
    formats: HoraFormats,
    isToday: Boolean,
    bands: List<Pair<Plage, String?>>,
    onOpenEvent: (Long, Long?) -> Unit,
    onCreate: (LocalDate, Int?) -> Unit,
    modifier: Modifier,
) {
    val colors = HoraTheme.colors
    val placed = remember(events, day, zone) { DayLayout.place(DayLayout.timedSlices(events, day, zone)) }
    BoxWithConstraints(
        modifier
            .height(HOUR * 24)
            .drawBehind {
                val hour = size.height / 24
                for (h in 1 until 24) drawLine(colors.rule, Offset(0f, h * hour), Offset(size.width, h * hour), 1f)
                drawLine(colors.rule, Offset(0f, 0f), Offset(0f, size.height), 1f)
            }
            .pointerInput(day) {
                detectTapGestures { offset ->
                    // Arrondi au quart d'heure, comme on choisirait à la main.
                    val minute = ((offset.y / size.height * 24 * 60) / 15).toInt() * 15
                    onCreate(day, minute)
                }
            },
    ) {
        val width = maxWidth
        bands.forEach { (plage, label) -> PlageBand(plage, label) }
        placed.forEach { p ->
            val top = HOUR * (p.slice.startMinute / 60f)
            val height = HOUR * (maxOf(p.slice.endMinute - p.slice.startMinute, DayLayout.MIN_VISIBLE_MINUTES) / 60f)
            val colWidth = width / p.columns
            Block(
                p.slice.event, agendas, zone, formats, onOpenEvent, compact = width < 80.dp,
                Modifier.offset(x = colWidth * p.column, y = top).width(colWidth).height(height).padding(1.dp),
            )
        }
        if (isToday) NowLine(width)
    }
}

/** Une plage : bande d'ombre pâle sous les événements, bordée de pointillés. */
@Composable
private fun PlageBand(plage: Plage, label: String?) {
    val colors = HoraTheme.colors
    val top = HOUR * (plage.start.toSecondOfDay() / 3600f)
    val height = HOUR * ((plage.end.toSecondOfDay() - plage.start.toSecondOfDay()) / 3600f)
    Box(
        Modifier.offset(y = top).fillMaxWidth().height(height).background(colors.band).drawBehind {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            drawLine(colors.rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = dash)
            drawLine(colors.rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(), pathEffect = dash)
        },
    ) {
        // Une colonne de semaine est étroite : le nom rapetisse plutôt que d'être coupé (police à 200 %).
        if (label != null) {
            FitText(
                label.uppercase(),
                Modifier.padding(start = 4.dp, top = 2.dp, end = 2.dp).clearAndSetSemantics { text = AnnotatedString(label) },
                style = HoraTheme.type.eyebrow,
                color = colors.ink2,
                minFontSize = 7.sp,
            )
        }
    }
}

/** Un événement posé dans la grille : fond teinté, filet de couleur, ombre de cadran. */
@Composable
private fun Block(event: Event, agendas: Map<Long, Agenda>, zone: ZoneId, formats: HoraFormats, onOpenEvent: (Long, Long?) -> Unit, compact: Boolean, modifier: Modifier) {
    val colors = HoraTheme.colors
    val c = colorKeyOf(event, agendas).color()
    val spoken = spokenEvent(event, zone, formats)
    Box(
        modifier
            .drawBehind {
                // L'ombre dure du cadran, portée en bas à droite, sous le bloc.
                drawRoundRect(colors.shadow, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
            .clip(RoundedCornerShape(3.dp))
            .background(c.copy(alpha = 0.16f).compositeOver(colors.background))
            .drawBehind { drawRect(c, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
            .clickable { onOpenEvent(event.id, event.occurrence) }
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Button }
            .padding(start = 6.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
    ) {
        HoraText(
            event.title,
            style = if (compact) HoraTheme.type.label.copy(letterSpacing = HoraTheme.type.bodySmall.letterSpacing) else HoraTheme.type.bodySmall,
            maxLines = if (compact) 3 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * La ligne « maintenant » : un filet bleu cadran et l'ombre oblique qu'il porte,
 * comme l'ombre du style sur la table du cadran. Elle avance d'elle-même chaque
 * minute, sans animation continue.
 */
@Composable
private fun NowLine(width: Dp) {
    val colors = HoraTheme.colors
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            now = LocalTime.now()
        }
    }
    val y = HOUR * (now.toSecondOfDay() / 3600f)
    Canvas(Modifier.offset(y = y - 1.dp).width(width).height(18.dp)) {
        val line = 2.dp.toPx()
        val shadow = Path().apply {
            moveTo(0f, line); lineTo(size.width, line); lineTo(size.width + 14.dp.toPx(), size.height); lineTo(14.dp.toPx(), size.height); close()
        }
        drawPath(shadow, Brush.verticalGradient(listOf(colors.shadow, Color.Transparent), startY = line, endY = size.height))
        drawRect(colors.accent, size = androidx.compose.ui.geometry.Size(size.width, line))
        drawCircle(colors.accent, radius = 4.dp.toPx(), center = Offset(0f, line / 2))
    }
}

private val HOUR = 56.dp
private val GUTTER = 40.dp
// « 10 PM » ne tient pas dans 40 dp.
private val GUTTER_12H = 52.dp
private val HoraFormats.gutter get() = if (clock24) GUTTER else GUTTER_12H
