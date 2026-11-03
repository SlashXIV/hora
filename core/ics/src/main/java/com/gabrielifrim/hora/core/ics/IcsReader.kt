package com.gabrielifrim.hora.core.ics

import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Recurrence
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.ReminderCodec
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.ImportItem
import java.time.Duration
import java.time.LocalTime
import java.time.Year
import java.time.ZoneId
import java.util.UUID

/**
 * L'agenda d'origine d'un événement. [name] nul : pas de nom exploitable (fichier
 * sans X-WR-CALNAME, agenda local de Fossify), l'interface en propose un.
 */
data class SourceAgenda(val name: String?, val argb: Int? = null, val colorKey: String? = null)

data class IcsEntry(val item: ImportItem, val agenda: SourceAgenda)

/**
 * Ce qu'un fichier contient, prêt à importer, et ce qui a dû être adapté :
 * rien n'est perdu en silence.
 */
data class IcsImport(
    val entries: List<IcsEntry>,
    /** Tâches (VTODO) datées, importées comme des événements. */
    val tasks: Int = 0,
    /** Éléments sans date ou annulés : ils ne peuvent pas entrer dans un agenda. */
    val skipped: Int = 0,
    /** Rappels placés après le début (non pris en charge), ramenés à l'heure de début. */
    val shiftedReminders: Int = 0,
    /** Fuseaux inconnus : ces heures ont été lues dans le fuseau du téléphone. */
    val unknownZones: Set<String> = emptySet(),
)

/** Lit un fichier .ics (Fossify, Google, Outlook, Thunderbird, Hora…) en événements Hora. */
object IcsReader {

    /** Fossify (ex-Simple Calendar) et Hora rangent le nom de l'agenda dans CATEGORIES. */
    private val CATEGORIES_AS_AGENDA = listOf("FOSSIFY", "SIMPLE MOBILE TOOLS", "SIMPLE CALENDAR", "HORA")

    /** Nom technique de l'agenda local de Fossify, jamais traduit dans ses exports. */
    private const val FOSSIFY_LOCAL = "calendar_displayname_local"

    fun read(bytes: ByteArray, deviceZone: ZoneId): IcsImport {
        val entries = mutableListOf<IcsEntry>()
        var tasks = 0
        var skipped = 0
        var shifted = 0
        val unknownZones = mutableSetOf<String>()

        for (calendar in IcsSyntax.parse(bytes).filter { it.name == "VCALENDAR" }) {
            val prodId = calendar.first("PRODID")?.value.orEmpty().uppercase()
            val categoriesAreAgendas = CATEGORIES_AS_AGENDA.any { it in prodId }
            val calendarName = calendar.first("X-WR-CALNAME")?.text?.trim()?.ifEmpty { null }
            val zones = ZoneResolver(calendar.children.filter { it.name == "VTIMEZONE" }, deviceZone, Year.now().value)
            val read = mutableListOf<Parsed>()

            for (component in calendar.children.filter { it.name == "VEVENT" || it.name == "VTODO" }) {
                val parsed = runCatching { parse(component, zones, deviceZone) }.getOrNull()
                if (parsed == null) {
                    skipped++
                    continue
                }
                if (component.name == "VTODO") tasks++
                shifted += parsed.shiftedReminders
                val agendaName = if (categoriesAreAgendas) {
                    component.first("CATEGORIES")?.let { splitList(it.value).firstOrNull() }?.trim()
                } else {
                    calendarName
                }
                read += parsed.copy(
                    agenda = SourceAgenda(
                        name = agendaName?.takeUnless { it.isEmpty() || it == FOSSIFY_LOCAL } ?: calendarName.takeIf { categoriesAreAgendas },
                        argb = component.first("X-FOSSIFY-CATEGORY-COLOR")?.value?.trim()?.toLongOrNull()?.toInt(),
                        colorKey = component.first("X-HORA-AGENDA-COLOR")?.value?.trim(),
                    ),
                )
            }
            unknownZones += zones.unknown

            // Une occurrence annulée devient une exception de sa série ; un événement annulé n'entre pas.
            val cancelled = read.filter { it.cancelled }
            skipped += cancelled.count { it.parentUid == null }
            val exceptionsBySeries = cancelled.filter { it.parentUid != null }.groupBy({ it.parentUid!! }, { it.event.recurrenceId!! })
            val kept = read.filterNot { it.cancelled }.map { p ->
                val extra = exceptionsBySeries[p.event.uid].orEmpty()
                val recurrence = p.event.recurrence
                if (extra.isEmpty() || recurrence == null) p else p.copy(event = p.event.copy(recurrence = recurrence.copy(exceptions = recurrence.exceptions + extra)))
            }
            // Le même UID deux fois : la dernière version l'emporte.
            entries += kept.associateBy { it.event.uid }.values.flatMap { p ->
                listOf(IcsEntry(ImportItem(p.event, p.parentUid), p.agenda)) + p.extraDates.map { IcsEntry(ImportItem(it, p.event.uid), p.agenda) }
            }
        }
        return IcsImport(entries, tasks, skipped, shifted, unknownZones)
    }

    private data class Parsed(
        val event: Event,
        val parentUid: String?,
        val cancelled: Boolean,
        val shiftedReminders: Int,
        /** Occurrences en plus (RDATE), rattachées à la série. */
        val extraDates: List<Event> = emptyList(),
        val agenda: SourceAgenda = SourceAgenda(null),
    )

    private fun parse(c: IcsComponent, zones: ZoneResolver, deviceZone: ZoneId): Parsed {
        val start = (c.first("DTSTART") ?: c.first("DUE")).let { p -> zones.read(p!!.value, p.params)!! }
        val end = (c.first("DTEND") ?: c.first("DUE")?.takeIf { c.first("DTSTART") != null })?.let { zones.read(it.value, it.params) }
        val duration = c.first("DURATION")?.value?.let(::parseDuration)
        val timing = timingOf(start, end, duration)
        val uid = c.first("UID")?.value?.trim()?.ifEmpty { null }
            ?: UUID.nameUUIDFromBytes((c.first("SUMMARY")?.value + "|" + c.first("DTSTART")?.value).toByteArray()).toString()

        val recurrenceId = c.first("RECURRENCE-ID")?.let { p -> zones.read(p.value, p.params)?.let { keyOf(it, timing) } }
        val exceptions = c.all("EXDATE").flatMap(zones::readList).map { keyOf(it, timing) }.toSet()
        val rule = c.first("RRULE")?.value?.trim()?.removePrefix("RRULE:")?.ifEmpty { null }

        var shifted = 0
        val reminders = c.children.filter { it.name == "VALARM" }.mapNotNull { alarm ->
            reminderOf(alarm, timing, deviceZone)?.also { (_, s) -> if (s) shifted++ }?.first
        }.distinct()

        val event = Event(
            uid = if (recurrenceId != null) "$uid#$recurrenceId" else uid,
            agendaId = 0,
            title = c.first("SUMMARY")?.text?.trim().orEmpty(),
            timing = timing,
            location = c.first("LOCATION")?.text?.trim().orEmpty(),
            notes = c.first("DESCRIPTION")?.text?.trim().orEmpty(),
            colorKey = c.first("X-HORA-COLOR")?.value?.trim(),
            busy = c.first("TRANSP")?.value?.trim()?.uppercase() != "TRANSPARENT",
            reminders = reminders,
            recurrence = if (recurrenceId == null && rule != null) Recurrence(rule, exceptions) else null,
            recurrenceId = recurrenceId,
        )
        val extra = if (recurrenceId != null) emptyList() else c.all("RDATE").flatMap(zones::readList).map { at ->
            val key = keyOf(at, timing)
            event.copy(uid = "$uid#$key", recurrence = null, recurrenceId = key, timing = shiftTo(timing, key))
        }
        return Parsed(
            event = event,
            parentUid = if (recurrenceId != null) uid else null,
            cancelled = c.first("STATUS")?.value?.trim()?.uppercase() == "CANCELLED",
            shiftedReminders = shifted,
            extraDates = extra,
        )
    }

    /**
     * Fin exclusive en ICS, dernier jour inclus dans Hora. Une fin avant le début
     * (Fossify écrit DTEND = DTSTART pour un anniversaire) donne un jour, ou une durée nulle.
     */
    private fun timingOf(start: IcsWhen, end: IcsWhen?, duration: Duration?): Timing = when (start) {
        is IcsWhen.Day -> {
            val last = when {
                end is IcsWhen.Day -> end.date.minusDays(1)
                end is IcsWhen.At -> end.instant.atZone(end.zone).toLocalDate()
                duration != null -> start.date.plusDays(duration.toDays() - 1)
                else -> start.date
            }
            Timing.AllDay(start.date, if (last.isBefore(start.date)) start.date else last)
        }
        is IcsWhen.At -> {
            val stop = when (end) {
                is IcsWhen.At -> end.instant
                is IcsWhen.Day -> end.date.atStartOfDay(start.zone).toInstant()
                null -> duration?.let { start.instant.plus(it) } ?: start.instant
            }
            Timing.Timed(start.instant, if (stop.isBefore(start.instant)) start.instant else stop, start.zone)
        }
    }

    /** Clé d'occurrence (voir `occurrenceKey`) d'une valeur d'EXDATE, de RDATE ou de RECURRENCE-ID. */
    private fun keyOf(value: IcsWhen, series: Timing): Long = when (series) {
        is Timing.AllDay -> when (value) {
            is IcsWhen.Day -> value.date.toEpochDay()
            is IcsWhen.At -> value.instant.atZone(value.zone).toLocalDate().toEpochDay()
        }
        is Timing.Timed -> when (value) {
            is IcsWhen.At -> value.instant.toEpochMilli()
            // Une simple date sur une série à heure fixe : ce jour-là, à l'heure de la série.
            is IcsWhen.Day -> value.date.atTime(series.start.atZone(series.zone).toLocalTime()).atZone(series.zone).toInstant().toEpochMilli()
        }
    }

    private fun shiftTo(timing: Timing, key: Long): Timing = when (timing) {
        is Timing.AllDay -> java.time.LocalDate.ofEpochDay(key).let { Timing.AllDay(it, it.plusDays(timing.last.toEpochDay() - timing.first.toEpochDay())) }
        is Timing.Timed -> java.time.Instant.ofEpochMilli(key).let { Timing.Timed(it, it.plus(Duration.between(timing.start, timing.end)), timing.zone) }
    }

    /**
     * Un VALARM en rappel Hora ; le booléen dit s'il a fallu le déplacer. Le
     * décalage est compté depuis le début (minuit du premier jour pour un événement
     * sur la journée). Sur la journée, il devient « N jours avant à telle heure » :
     * le -P2DT14H de Fossify est « 3 jours avant à 10 h ».
     */
    private fun reminderOf(alarm: IcsComponent, timing: Timing, deviceZone: ZoneId): Pair<Reminder, Boolean>? {
        alarm.first("X-HORA-REMINDER")?.value?.let { ReminderCodec.decode(it).firstOrNull() }?.let { return it to false }
        val trigger = alarm.first("TRIGGER") ?: return null
        val (start, length) = when (timing) {
            is Timing.Timed -> timing.start to Duration.between(timing.start, timing.end)
            is Timing.AllDay -> timing.first.atStartOfDay(deviceZone).toInstant() to Duration.ofDays(timing.last.toEpochDay() - timing.first.toEpochDay() + 1)
        }
        val offset = if (trigger.params["VALUE"] == "DATE-TIME") {
            val at = (ZoneResolver(emptyList(), deviceZone, 1970).read(trigger.value, trigger.params) as? IcsWhen.At)?.instant ?: return null
            Duration.between(start, at)
        } else {
            val d = parseDuration(trigger.value) ?: return null
            if (trigger.params["RELATED"] == "END") d.plus(length) else d
        }
        val minutes = offset.toMinutes()
        return when (timing) {
            is Timing.Timed -> Reminder.Before((-minutes).coerceAtLeast(0).toInt()) to (minutes > 0)
            is Timing.AllDay -> {
                if (minutes == 0L) return Reminder.Before(0) to false
                val days = Math.floorDiv(minutes, 1440L)
                val inDay = (minutes - days * 1440).toInt()
                if (days > 0) {
                    Reminder.Anchored(0, LocalTime.of(23, 59)) to true
                } else {
                    Reminder.Anchored((-days).toInt(), LocalTime.of(inDay / 60, inDay % 60)) to false
                }
            }
        }
    }

    private val DURATION = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")

    /** Une durée RFC 5545 (« -P2DT14H0M0S », « PT30M », « P1W »). */
    internal fun parseDuration(value: String): Duration? {
        val m = DURATION.matchEntire(value.trim().uppercase()) ?: return null
        val (sign, w, d, h, min, s) = m.destructured
        val seconds = (w.toLongOrNull() ?: 0) * 604_800 + (d.toLongOrNull() ?: 0) * 86_400 +
            (h.toLongOrNull() ?: 0) * 3_600 + (min.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)
        return Duration.ofSeconds(if (sign == "-") -seconds else seconds)
    }

    /** Liste TEXT séparée par des virgules non échappées. */
    private fun splitList(value: String): List<String> =
        value.split(Regex("""(?<!\\),""")).map { IcsSyntax.unescape(it) }
}
