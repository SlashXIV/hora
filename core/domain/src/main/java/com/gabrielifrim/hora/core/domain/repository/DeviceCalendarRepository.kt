package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.recurrence.EditScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow

/**
 * Les agendas d'Android (DAVx5, un compte, l'appareil), lus sur demande, modifiables
 * sur demande aussi. Ils ne passent jamais par la base : ni export, ni sauvegarde, ni rappel
 * d'Hora (l'app d'origine les fait sonner). Un événement dont l'UID ou la [copyKey] existe déjà dans Hora est masqué.
 *
 * Identifiants négatifs, bien au-delà de ceux des agendas calculés : voir [deviceAgendaId].
 */
interface DeviceCalendarRepository {
    /** Vrai quand la lecture est demandée et la permission accordée. */
    val enabled: Flow<Boolean>

    suspend fun setEnabled(on: Boolean)

    /** Les agendas d'Android, masqués compris ; [Agenda.account] porte le compte. */
    val agendas: Flow<List<Agenda>>

    /** Événements des agendas d'Android visibles qui touchent la période, doublons d'Hora retirés. */
    fun eventsBetween(first: LocalDate, last: LocalDate, zone: ZoneId): Flow<List<Event>>

    /** L'occurrence qui commence à [begin] (millisecondes), ou la prochaine si [begin] est null. */
    fun event(id: Long, begin: Long?): Flow<Event?>

    fun search(query: String): Flow<List<Event>>

    /** Événements d'Android masqués parce qu'Hora a déjà le même UID. */
    val duplicates: Flow<Int>

    suspend fun setHidden(agendaId: Long, hidden: Boolean)

    suspend fun setColor(agendaId: Long, colorKey: String)

    /** Vrai quand l'écriture est demandée et la permission accordée. */
    val writable: Flow<Boolean>

    suspend fun setWritable(on: Boolean)

    /**
     * Écrit [edited] dans son agenda d'Android et renvoie son id d'Hora, null si Android refuse.
     * [original] est l'occurrence affichée (null : nouvel événement) ; pour une série, [scope]
     * vaut [EditScope.THIS] (une exception d'Android) ou [EditScope.ALL].
     */
    suspend fun save(edited: Event, original: Event?, scope: EditScope): Long?

    /**
     * Vrai si Android sait changer une seule occurrence de la série [id]. Il ne le sait pas
     * pour un événement jamais synchronisé (agenda local) : l'exception y efface la série.
     */
    suspend fun canEditOccurrence(id: Long): Boolean

    /** Supprime l'occurrence [shown] ([EditScope.THIS]) ou tout l'événement. */
    suspend fun delete(shown: Event, scope: EditScope): Boolean
}

private const val DEVICE_AGENDA = 1_000_000_000_000L
private const val DEVICE_EVENT = 2_000_000_000_000L

fun deviceAgendaId(calendarId: Long): Long = -(DEVICE_AGENDA + calendarId)

/** L'id d'agenda d'Android derrière [agendaId], null pour un agenda d'Hora ou calculé. */
fun deviceCalendarOf(agendaId: Long): Long? = (-agendaId - DEVICE_AGENDA).takeIf { agendaId < 0 && it in 0 until DEVICE_AGENDA }

fun deviceEventId(eventId: Long): Long = -(DEVICE_EVENT + eventId)

fun deviceEventOf(id: Long): Long? = (-id - DEVICE_EVENT).takeIf { id < 0 && it >= 0 }

/**
 * Une journée entière d'Android va de minuit à minuit en UTC, fin exclue : du 14 à 0 h
 * au 15 à 0 h, c'est le seul 14. Une fin égale au début compte pour un jour.
 */
fun deviceAllDay(beginMs: Long, endMs: Long): Timing.AllDay {
    val first = Instant.ofEpochMilli(beginMs).atOffset(ZoneOffset.UTC).toLocalDate()
    val last = Instant.ofEpochMilli(maxOf(beginMs, endMs - 1)).atOffset(ZoneOffset.UTC).toLocalDate()
    return Timing.AllDay(first, maxOf(first, last))
}

/**
 * Clé d'une copie : même titre, même début, même journée entière. Un événement importé depuis
 * Fossify, qui synchronisait déjà Google, n'a pas l'UID d'Android : il se reconnaît ainsi.
 * [start] : jour (epoch) pour une journée entière côté Hora, millisecondes sinon ; [deviceStart]
 * convertit le DTSTART d'Android vers la même forme.
 */
fun copyKey(allDay: Boolean, start: Long, title: String): String = "${if (allDay) 'J' else 'H'}$start ${title.trim()}"

/** DTSTART d'Android ramené à la forme d'Hora : minuit UTC → jour (epoch) pour une journée entière. */
fun deviceStart(allDay: Boolean, dtstart: Long): Long = if (allDay) Math.floorDiv(dtstart, 86_400_000L) else dtstart

/** Les colonnes de temps d'un événement d'Android : une série porte une durée, pas de fin. */
data class DeviceTimes(val start: Long, val end: Long?, val duration: String?, val allDay: Boolean, val zone: String)

fun deviceTimes(timing: Timing, recurring: Boolean): DeviceTimes = when (timing) {
    is Timing.Timed -> {
        val start = timing.start.toEpochMilli()
        val end = timing.end.toEpochMilli()
        DeviceTimes(start, end.takeUnless { recurring }, "P${(end - start) / 1000}S".takeIf { recurring }, false, timing.zone.id)
    }
    // Journée entière : de minuit à minuit en UTC, fin exclue (voir [deviceAllDay]).
    is Timing.AllDay -> {
        fun utc(d: java.time.LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val days = timing.last.toEpochDay() - timing.first.toEpochDay() + 1
        DeviceTimes(utc(timing.first), utc(timing.last.plusDays(1)).takeUnless { recurring }, "P${days}D".takeIf { recurring }, true, "UTC")
    }
}

/**
 * Minutes avant le début, seule forme de rappel d'Android. « La veille à 20 h » est
 * converti pour cette occurrence-là : dans une série, il glisse d'une heure au changement d'heure.
 */
fun deviceMinutes(reminder: Reminder, timing: Timing, zone: ZoneId): Int = when (reminder) {
    is Reminder.Before -> reminder.minutes
    is Reminder.Anchored -> {
        val start = when (timing) {
            is Timing.Timed -> timing.start
            is Timing.AllDay -> timing.first.atStartOfDay(zone).toInstant()
        }
        Duration.between(reminder.triggerAt(timing, zone), start).toMinutes().toInt().coerceAtLeast(0)
    }
}

/**
 * La couleur d'Hora la plus proche d'une couleur d'Android (ARGB) : celle dont la teinte
 * est la plus proche, l'ardoise pour un gris. Hora garde ainsi ses huit couleurs testées,
 * chacune avec sa forme.
 */
fun nearestColorKey(argb: Int): String {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    if (hi == 0f || (hi - lo) / hi < 0.2f) return "SLATE"
    val d = hi - lo
    val hue = when (hi) {
        r -> 60 * (((g - b) / d) % 6)
        g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }.let { if (it < 0) it + 360 else it }
    return HUES.minBy { (_, h) -> abs(hue - h).let { min(it, 360 - it) } }.first
}

/** Teintes des couleurs d'agenda d'Hora (palette Midi), en degrés. */
private val HUES = listOf("BRICK" to 12f, "OCHRE" to 46f, "FIR" to 128f, "TEAL" to 189f, "BLUE" to 208f, "PLUM" to 276f, "RASPBERRY" to 322f)
