package com.gabrielifrim.hora.core.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Un rappel d'événement. Autant qu'on veut par événement.
 *
 * @property alarm sonne en alarme plein écran plutôt qu'en notification.
 */
sealed interface Reminder {
    val alarm: Boolean

    /** « 10 min avant », « 2 jours avant » : une durée avant le début. 0 = à l'heure. */
    data class Before(val minutes: Int, override val alarm: Boolean = false) : Reminder {
        init {
            require(minutes >= 0) { "un rappel ne peut pas suivre le début" }
        }
    }

    /** « La veille à 20 h », « 2 jours avant à 9 h » : un jour, une heure murale. */
    data class Anchored(val daysBefore: Int, val time: LocalTime, override val alarm: Boolean = false) : Reminder {
        init {
            require(daysBefore >= 0) { "un rappel ne peut pas suivre le début" }
        }
    }

    /** Clé stable du rappel dans son événement, pour ne jamais le déclencher deux fois. */
    fun key(): String = ReminderCodec.encode(listOf(this))

    /**
     * Instant de déclenchement pour une occurrence.
     *
     * Toute la journée, l'événement « commence » à minuit dans le fuseau du téléphone.
     * Un rappel ancré se calcule dans le fuseau de l'événement : « la veille à 20 h »
     * d'un rendez-vous à Paris sonne à 20 h, heure de Paris. Une heure qui n'existe pas
     * (nuit du passage à l'heure d'été) est repoussée à l'heure qui suit ; une heure
     * qui existe deux fois prend la première.
     */
    fun triggerAt(timing: Timing, deviceZone: ZoneId): Instant {
        val (start, zone) = when (timing) {
            is Timing.Timed -> timing.start to timing.zone
            is Timing.AllDay -> timing.first.atStartOfDay(deviceZone).toInstant() to deviceZone
        }
        return when (this) {
            is Before -> start.minus(Duration.ofMinutes(minutes.toLong()))
            is Anchored -> ZonedDateTime.ofLocal(start.atZone(zone).toLocalDate().minusDays(daysBefore.toLong()).atTime(time), zone, null).toInstant()
        }
    }
}

/**
 * Écriture compacte d'une liste de rappels, pour la base et les jeux de rappels :
 * `B10` (10 min avant), `A1@20:00` (la veille à 20 h), suffixe `!` pour une alarme,
 * séparés par `;`. Une entrée illisible est ignorée, jamais fatale.
 */
object ReminderCodec {
    fun encode(reminders: List<Reminder>): String = reminders.joinToString(";") { r ->
        val body = when (r) {
            is Reminder.Before -> "B${r.minutes}"
            is Reminder.Anchored -> "A${r.daysBefore}@%02d:%02d".format(r.time.hour, r.time.minute)
        }
        if (r.alarm) "$body!" else body
    }

    fun decode(text: String?): List<Reminder> = text.orEmpty().split(';').mapNotNull { raw ->
        val alarm = raw.endsWith('!')
        val body = raw.removeSuffix("!").trim()
        runCatching {
            when (body.firstOrNull()) {
                'B' -> Reminder.Before(body.drop(1).toInt(), alarm)
                'A' -> {
                    val (days, time) = body.drop(1).split('@')
                    val (h, m) = time.split(':').map(String::toInt)
                    Reminder.Anchored(days.toInt(), LocalTime.of(h, m), alarm)
                }
                else -> null
            }
        }.getOrNull()
    }.distinct()
}
