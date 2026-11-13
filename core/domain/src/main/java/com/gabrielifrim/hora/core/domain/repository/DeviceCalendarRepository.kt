package com.gabrielifrim.hora.core.domain.repository

import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow

/**
 * Les agendas d'Android (DAVx5, un compte, l'appareil), lus sur demande et en lecture
 * seule. Ils ne passent jamais par la base : ni export, ni sauvegarde, ni rappel d'Hora
 * (l'app d'origine les fait sonner). Un événement dont l'UID existe déjà dans Hora est masqué.
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
