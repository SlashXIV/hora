package com.gabrielifrim.hora.core.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Ce que l'éditeur sait proposer ; toute autre RRULE (importée) est gardée telle quelle. */
enum class Frequency { DAILY, WEEKLY, MONTHLY_BY_DATE, MONTHLY_BY_RANK, YEARLY }

sealed interface RepeatEnd {
    data object Never : RepeatEnd
    data class After(val count: Int) : RepeatEnd
    data class Until(val lastDay: LocalDate) : RepeatEnd
}

/**
 * Une répétition éditable : « toutes les 2 semaines le mardi et le jeudi »,
 * « le 2e mardi du mois », « chaque année ». [toRule] l'écrit en RRULE ;
 * [parse] la relit quand elle tient dans ce que l'éditeur propose.
 */
data class RepeatSpec(
    val frequency: Frequency,
    val interval: Int = 1,
    /** Jours de la semaine (hebdomadaire) ; vide : le jour du début. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    val end: RepeatEnd = RepeatEnd.Never,
) {
    init {
        require(interval >= 1) { "intervalle d'au moins 1" }
    }

    /**
     * @param zone fuseau d'une série à heure fixe ; null pour une série sur la journée.
     * RFC 5545 l'exige : UNTIL est en UTC si le début a un fuseau, une simple date sinon.
     */
    fun toRule(start: LocalDate, zone: ZoneId?): String {
        val parts = mutableListOf<String>()
        parts += "FREQ=" + when (frequency) {
            Frequency.DAILY -> "DAILY"
            Frequency.WEEKLY -> "WEEKLY"
            Frequency.MONTHLY_BY_DATE, Frequency.MONTHLY_BY_RANK -> "MONTHLY"
            Frequency.YEARLY -> "YEARLY"
        }
        if (interval > 1) parts += "INTERVAL=$interval"
        when (frequency) {
            Frequency.WEEKLY -> parts += "BYDAY=" + (weekdays.ifEmpty { setOf(start.dayOfWeek) }).sorted().joinToString(",") { it.code() }
            Frequency.MONTHLY_BY_DATE -> parts += "BYMONTHDAY=${start.dayOfMonth}"
            Frequency.MONTHLY_BY_RANK -> parts += "BYDAY=${rankOf(start)}${start.dayOfWeek.code()}"
            else -> Unit
        }
        when (val e = end) {
            RepeatEnd.Never -> Unit
            is RepeatEnd.After -> parts += "COUNT=${e.count}"
            // Jusqu'au jour inclus : sa dernière seconde dans le fuseau de la série.
            is RepeatEnd.Until -> parts += "UNTIL=" + if (zone == null) {
                "%04d%02d%02d".format(e.lastDay.year, e.lastDay.monthValue, e.lastDay.dayOfMonth)
            } else {
                UNTIL_UTC.format(e.lastDay.atTime(23, 59, 59).atZone(zone).withZoneSameInstant(ZoneOffset.UTC))
            }
        }
        return parts.joinToString(";")
    }

    companion object {
        /**
         * Rang du jour dans le mois : 2 pour le 2e mardi. Un 5e jour devient « le
         * dernier » (-1) : il n'existe pas tous les mois, le dernier si.
         */
        fun rankOf(day: LocalDate): Int = ((day.dayOfMonth - 1) / 7 + 1).let { if (it >= 5) -1 else it }

        /**
         * null si la règle dépasse ce que l'éditeur sait montrer (BYSETPOS, BYHOUR…).
         * [start] : le jour de début ; il rend lisible le « FREQ=YEARLY;BYMONTH=11 » de
         * Fossify quand BYMONTH est le mois du début (c'est alors « chaque année »).
         */
        fun parse(rule: String, zone: ZoneId?, start: LocalDate? = null): RepeatSpec? {
            val parts = rule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate {
                val (k, v) = it.split('=', limit = 2).let { p -> p[0].uppercase() to p.getOrElse(1) { "" } }
                k to v
            }
            val known = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "COUNT", "UNTIL", "WKST")
            val redundantMonth = parts["FREQ"] == "YEARLY" && start != null && parts["BYMONTH"] == start.monthValue.toString()
            if (!known.containsAll(parts.keys - if (redundantMonth) setOf("BYMONTH") else emptySet())) return null
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
            val byDay = parts["BYDAY"]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            val end = when {
                parts["COUNT"] != null -> RepeatEnd.After(parts.getValue("COUNT").toIntOrNull() ?: return null)
                parts["UNTIL"] != null -> RepeatEnd.Until(parseUntilDay(parts.getValue("UNTIL"), zone) ?: return null)
                else -> RepeatEnd.Never
            }
            val frequency = when (parts["FREQ"]) {
                "DAILY" -> if (byDay.isEmpty()) Frequency.DAILY else return null
                "WEEKLY" -> Frequency.WEEKLY
                "MONTHLY" -> when {
                    byDay.isEmpty() -> Frequency.MONTHLY_BY_DATE
                    byDay.size == 1 && byDay[0].length > 2 -> Frequency.MONTHLY_BY_RANK
                    else -> return null
                }
                "YEARLY" -> if (byDay.isEmpty() && parts["BYMONTHDAY"] == null) Frequency.YEARLY else return null
                else -> return null
            }
            val weekdays = if (frequency == Frequency.WEEKLY) {
                byDay.map { dayOf(it) ?: return null }.toSet()
            } else {
                emptySet()
            }
            return RepeatSpec(frequency, interval.coerceAtLeast(1), weekdays, end)
        }

        private val UNTIL_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        /** Le dernier jour de la série ; un UNTIL en UTC est ramené au jour local de [zone]. */
        private fun parseUntilDay(v: String, zone: ZoneId?): LocalDate? = runCatching {
            if (v.endsWith("Z") && zone != null) {
                java.time.LocalDateTime.parse(v.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate()
            } else {
                LocalDate.of(v.substring(0, 4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt())
            }
        }.getOrNull()

        private fun dayOf(code: String): DayOfWeek? = DayOfWeek.entries.firstOrNull { it.code() == code.takeLast(2) }
    }
}

internal fun DayOfWeek.code(): String = name.take(2)
