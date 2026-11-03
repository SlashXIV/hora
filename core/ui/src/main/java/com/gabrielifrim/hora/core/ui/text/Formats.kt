package com.gabrielifrim.hora.core.ui.text

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.ClockFormat
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.model.UserSettings
import com.gabrielifrim.hora.core.ui.R
import com.gabrielifrim.hora.core.ui.theme.AgendaColorKey
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Réglages lus par les écrans (format des heures, premier jour…), fournis par l'activité. */
val LocalUserSettings = staticCompositionLocalOf { UserSettings() }

/** Heures et dates à l'écran, selon les réglages et la langue de l'app. */
class HoraFormats(val locale: Locale, val clock24: Boolean, val firstDayOfWeek: DayOfWeek, val showWeekNumbers: Boolean) {
    val weekFields: WeekFields = WeekFields.of(firstDayOfWeek, 4)
    private val time = DateTimeFormatter.ofPattern(if (clock24) "HH:mm" else "h:mm a", locale)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val month = DateTimeFormatter.ofPattern("MMMM", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale)
    // TalkBack lit « 14 h 30 » et « 15 h », pas « 14 deux-points 30 ».
    private val spokenTime = DateTimeFormatter.ofPattern(if (clock24) "H 'h' mm" else "h 'h' mm a", locale)
    private val spokenHour = DateTimeFormatter.ofPattern(if (clock24) "H 'h'" else "h 'h' a", locale)

    private val hourOnly = DateTimeFormatter.ofPattern("h a", locale)

    fun time(t: LocalTime): String = time.format(t)

    /** Heure gravée dans la marge de la grille : « 14 », ou « 2 PM » en 12 h. */
    fun hourLabel(hour: Int): String = if (clock24) "%02d".format(hour) else hourOnly.format(LocalTime.of(hour, 0))
    fun dayLong(d: LocalDate): String = dayLong.format(d).capitalized()

    /** Pour une phrase : « mardi 3 novembre », sans capitale ; l'année seulement si ce n'est pas celle-ci. */
    fun daySpoken(d: LocalDate): String = dayLong.format(d) + yearIfOther(d)
    fun dayShort(d: LocalDate): String = dayShort.format(d)

    /** « jeu. 26 juin 2025 » dans une liste qui mêle les années (recherche). */
    fun dayShortWithYear(d: LocalDate): String = dayShort.format(d) + yearIfOther(d)

    private fun yearIfOther(d: LocalDate): String = if (d.year != LocalDate.now().year) " ${d.year}" else ""
    fun month(d: LocalDate): String = month.format(d).capitalized()
    fun dayMonth(d: LocalDate): String = dayMonth.format(d)
    fun weekday(d: LocalDate): String = weekday.format(d).capitalized()
    fun spoken(t: LocalTime): String = (if (t.minute == 0) spokenHour else spokenTime).format(t)
    fun week(d: LocalDate): Int = d.get(weekFields.weekOfWeekBasedYear())

    /** Le lundi (ou le premier jour choisi) de la semaine de [d]. */
    fun weekStart(d: LocalDate): LocalDate = d.with(weekFields.dayOfWeek(), 1)

    private fun String.capitalized() = replaceFirstChar { it.titlecase(locale) }
}

@Composable
fun rememberFormats(): HoraFormats {
    val settings = LocalUserSettings.current
    val locale = rememberAppLocale()
    val context = LocalContext.current
    val clock24 = when (settings.clockFormat) {
        ClockFormat.H24 -> true
        ClockFormat.H12 -> false
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    val firstDay = settings.firstDayOfWeek ?: WeekFields.of(locale).firstDayOfWeek
    return remember(locale, clock24, firstDay, settings.showWeekNumbers) { HoraFormats(locale, clock24, firstDay, settings.showWeekNumbers) }
}

/** Couleur d'un événement : la sienne, sinon celle de son agenda ; une clé inconnue retombe sur le bleu. */
fun colorKeyOf(event: Event, agendas: Map<Long, Agenda>): AgendaColorKey =
    agendaColorKey(event.colorKey ?: agendas[event.agendaId]?.colorKey)

fun agendaColorKey(name: String?): AgendaColorKey = AgendaColorKey.entries.firstOrNull { it.name == name } ?: AgendaColorKey.BLUE

/** Nom de la couleur, pour le choix d'une couleur et pour TalkBack. */
fun AgendaColorKey.labelRes(): Int = when (this) {
    AgendaColorKey.BLUE -> R.string.color_blue
    AgendaColorKey.FIR -> R.string.color_fir
    AgendaColorKey.PLUM -> R.string.color_plum
    AgendaColorKey.BRICK -> R.string.color_brick
    AgendaColorKey.OCHRE -> R.string.color_ochre
    AgendaColorKey.TEAL -> R.string.color_teal
    AgendaColorKey.RASPBERRY -> R.string.color_raspberry
    AgendaColorKey.SLATE -> R.string.color_slate
}

@Composable
@ReadOnlyComposable
fun AgendaColorKey.color(): Color = HoraTheme.colors.agenda[this]

/** Plage horaire courte : « 14:30–15:00 » ou « Toute la journée ». */
@Composable
fun timeRange(event: Event, zone: ZoneId, formats: HoraFormats): String = when (val t = event.timing) {
    is Timing.AllDay -> if (t.first == t.last) {
        stringResource(R.string.all_day)
    } else {
        stringResource(R.string.date_range, formats.dayShort(t.first), formats.dayShort(t.last))
    }
    // Un instant (phase de lune) : une heure, pas « 08:02–08:02 ».
    is Timing.Timed -> if (t.start == t.end) formats.time(t.start.atZone(zone).toLocalTime()) else stringResource(
        R.string.time_range,
        formats.time(t.start.atZone(zone).toLocalTime()),
        formats.time(t.end.atZone(zone).toLocalTime()),
    )
}

/**
 * L'événement en une phrase pour TalkBack : « Dentiste, mardi 3 novembre, 14 h 30
 * à 15 h, Cabinet du Dr Martin ». Les rappels s'y ajouteront avec J3.
 */
@Composable
fun spokenEvent(event: Event, zone: ZoneId, formats: HoraFormats): String {
    val `when` = when (val t = event.timing) {
        is Timing.AllDay -> if (t.first == t.last) {
            stringResource(R.string.spoken_all_day, formats.daySpoken(t.first))
        } else {
            stringResource(R.string.spoken_date_range, formats.daySpoken(t.first), formats.daySpoken(t.last))
        }
        is Timing.Timed -> {
            val start = t.start.atZone(zone)
            val end = t.end.atZone(zone)
            if (start == end) {
                stringResource(R.string.spoken_instant, formats.daySpoken(start.toLocalDate()), formats.spoken(start.toLocalTime()))
            } else if (start.toLocalDate() == end.toLocalDate()) {
                stringResource(R.string.spoken_timed, formats.daySpoken(start.toLocalDate()), formats.spoken(start.toLocalTime()), formats.spoken(end.toLocalTime()))
            } else {
                stringResource(
                    R.string.spoken_timed_multi,
                    formats.daySpoken(start.toLocalDate()), formats.spoken(start.toLocalTime()),
                    formats.daySpoken(end.toLocalDate()), formats.spoken(end.toLocalTime()),
                )
            }
        }
    }
    // Les hachures du trajet ne se voient pas à l'oreille : l'heure de départ les remplace.
    val leave = (event.timing as? Timing.Timed)?.takeIf { event.travelMinutes > 0 }
        ?.let { stringResource(R.string.spoken_leave, formats.spoken(it.start.minusSeconds(event.travelMinutes * 60L).atZone(zone).toLocalTime())) }.orEmpty()
    return listOf(event.title, `when`, leave, event.location).filter { it.isNotBlank() }.joinToString(", ")
}
