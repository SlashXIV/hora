package com.gabrielifrim.hora.core.domain.almanac

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Les agendas calculés, jamais stockés ni exportés : ils se masquent comme un agenda. */
enum class AlmanacGroup { HOLIDAYS, FEASTS, MOON, CLOCK }

/** Tout ce que l'almanach sait placer ; le libellé vient de l'interface. */
enum class AlmanacKind(val group: AlmanacGroup) {
    NEW_YEAR(AlmanacGroup.HOLIDAYS),
    GOOD_FRIDAY(AlmanacGroup.HOLIDAYS),
    EASTER_MONDAY(AlmanacGroup.HOLIDAYS),
    LABOUR_DAY(AlmanacGroup.HOLIDAYS),
    VICTORY_1945(AlmanacGroup.HOLIDAYS),
    ASCENSION(AlmanacGroup.HOLIDAYS),
    WHIT_MONDAY(AlmanacGroup.HOLIDAYS),
    BASTILLE_DAY(AlmanacGroup.HOLIDAYS),
    ASSUMPTION(AlmanacGroup.HOLIDAYS),
    ALL_SAINTS(AlmanacGroup.HOLIDAYS),
    ARMISTICE(AlmanacGroup.HOLIDAYS),
    CHRISTMAS(AlmanacGroup.HOLIDAYS),
    SAINT_STEPHEN(AlmanacGroup.HOLIDAYS),
    BRIDGE(AlmanacGroup.HOLIDAYS),
    MOTHERS_DAY(AlmanacGroup.FEASTS),
    FATHERS_DAY(AlmanacGroup.FEASTS),
    CANDLEMAS(AlmanacGroup.FEASTS),
    VALENTINE(AlmanacGroup.FEASTS),
    GRANDMOTHERS_DAY(AlmanacGroup.FEASTS),
    EASTER(AlmanacGroup.FEASTS),
    PENTECOST(AlmanacGroup.FEASTS),
    MUSIC_DAY(AlmanacGroup.FEASTS),
    HALLOWEEN(AlmanacGroup.FEASTS),
    NEW_MOON(AlmanacGroup.MOON),
    FIRST_QUARTER(AlmanacGroup.MOON),
    FULL_MOON(AlmanacGroup.MOON),
    LAST_QUARTER(AlmanacGroup.MOON),
    SUMMER_TIME(AlmanacGroup.CLOCK),
    WINTER_TIME(AlmanacGroup.CLOCK),
}

/** Une entrée : un jour (fériés, fêtes), ou un instant précis (lune, changement d'heure). */
data class AlmanacEntry(val kind: AlmanacKind, val date: LocalDate, val at: Instant? = null)

data class AlmanacOptions(
    /** Vendredi saint et Saint-Étienne, fériés en Alsace et en Moselle. */
    val alsaceMoselle: Boolean = false,
    /** Le lundi d'un férié un mardi, le vendredi d'un férié un jeudi. */
    val bridges: Boolean = true,
    /** Au-delà des fêtes des mères et des pères : Chandeleur, Saint-Valentin, Halloween… */
    val moreFeasts: Boolean = false,
)

/** Fériés et fêtes de France, ponts, lune et changements d'heure, calculés pour une période. */
object Almanac {

    fun entries(first: LocalDate, last: LocalDate, zone: ZoneId, options: AlmanacOptions, groups: Set<AlmanacGroup>): List<AlmanacEntry> {
        val out = mutableListOf<AlmanacEntry>()
        for (year in first.year..last.year) {
            if (AlmanacGroup.HOLIDAYS in groups) out += holidays(year, options)
            if (AlmanacGroup.FEASTS in groups) out += feasts(year, options.moreFeasts)
        }
        if (AlmanacGroup.MOON in groups) out += Moon.phases(first, last, zone)
        if (AlmanacGroup.CLOCK in groups) out += clockChanges(first, last, zone)
        return out.filter { !it.date.isBefore(first) && !it.date.isAfter(last) }.sortedBy { it.date }
    }

    /** Dimanche de Pâques (grégorien, algorithme de Meeus / Jones / Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    fun holidays(year: Int, options: AlmanacOptions = AlmanacOptions()): List<AlmanacEntry> {
        val easter = easter(year)
        val days = buildList {
            add(AlmanacKind.NEW_YEAR to LocalDate.of(year, 1, 1))
            if (options.alsaceMoselle) add(AlmanacKind.GOOD_FRIDAY to easter.minusDays(2))
            add(AlmanacKind.EASTER_MONDAY to easter.plusDays(1))
            add(AlmanacKind.LABOUR_DAY to LocalDate.of(year, 5, 1))
            add(AlmanacKind.VICTORY_1945 to LocalDate.of(year, 5, 8))
            add(AlmanacKind.ASCENSION to easter.plusDays(39))
            add(AlmanacKind.WHIT_MONDAY to easter.plusDays(50))
            add(AlmanacKind.BASTILLE_DAY to LocalDate.of(year, 7, 14))
            add(AlmanacKind.ASSUMPTION to LocalDate.of(year, 8, 15))
            add(AlmanacKind.ALL_SAINTS to LocalDate.of(year, 11, 1))
            add(AlmanacKind.ARMISTICE to LocalDate.of(year, 11, 11))
            add(AlmanacKind.CHRISTMAS to LocalDate.of(year, 12, 25))
            if (options.alsaceMoselle) add(AlmanacKind.SAINT_STEPHEN to LocalDate.of(year, 12, 26))
        }
        val holidayDates = days.map { it.second }.toSet()
        val bridges = if (!options.bridges) emptyList() else days.mapNotNull { (_, day) ->
            when (day.dayOfWeek) {
                DayOfWeek.TUESDAY -> day.minusDays(1)
                DayOfWeek.THURSDAY -> day.plusDays(1)
                else -> null
            }?.takeIf { it !in holidayDates }
        }.distinct()
        return days.map { (kind, day) -> AlmanacEntry(kind, day) } + bridges.map { AlmanacEntry(AlmanacKind.BRIDGE, it) }
    }

    fun feasts(year: Int, more: Boolean = false): List<AlmanacEntry> {
        val easter = easter(year)
        val pentecost = easter.plusDays(49)
        // Dernier dimanche de mai, repoussé au premier de juin s'il tombe à la Pentecôte (Code de l'action sociale).
        val lastMaySunday = LocalDate.of(year, 5, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val mothers = if (lastMaySunday == pentecost) lastMaySunday.plusWeeks(1) else lastMaySunday
        val fathers = LocalDate.of(year, 6, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY))
        val base = listOf(AlmanacEntry(AlmanacKind.MOTHERS_DAY, mothers), AlmanacEntry(AlmanacKind.FATHERS_DAY, fathers))
        if (!more) return base
        return base + listOf(
            AlmanacEntry(AlmanacKind.CANDLEMAS, LocalDate.of(year, 2, 2)),
            AlmanacEntry(AlmanacKind.VALENTINE, LocalDate.of(year, 2, 14)),
            AlmanacEntry(AlmanacKind.GRANDMOTHERS_DAY, LocalDate.of(year, 3, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))),
            AlmanacEntry(AlmanacKind.EASTER, easter),
            AlmanacEntry(AlmanacKind.PENTECOST, pentecost),
            AlmanacEntry(AlmanacKind.MUSIC_DAY, LocalDate.of(year, 6, 21)),
            AlmanacEntry(AlmanacKind.HALLOWEEN, LocalDate.of(year, 10, 31)),
        )
    }

    /** Passages à l'heure d'été et d'hiver du fuseau, à l'instant exact. */
    fun clockChanges(first: LocalDate, last: LocalDate, zone: ZoneId): List<AlmanacEntry> {
        val end = last.plusDays(1).atStartOfDay(zone).toInstant()
        var t = zone.rules.nextTransition(first.atStartOfDay(zone).toInstant().minusSeconds(1)) ?: return emptyList()
        val out = mutableListOf<AlmanacEntry>()
        while (t.instant.isBefore(end)) {
            val kind = if (t.isGap) AlmanacKind.SUMMER_TIME else AlmanacKind.WINTER_TIME
            out += AlmanacEntry(kind, t.instant.atZone(zone).toLocalDate(), t.instant)
            t = zone.rules.nextTransition(t.instant) ?: break
        }
        return out
    }
}
