package com.gabrielifrim.hora.core.ics

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneOffsetTransitionRule

/** Une valeur DATE ou DATE-TIME lue. */
internal sealed interface IcsWhen {
    data class Day(val date: LocalDate) : IcsWhen

    /** [zone] : celui du TZID, ou celui du téléphone pour une heure UTC ou « flottante ». */
    data class At(val instant: Instant, val zone: ZoneId) : IcsWhen
}

/**
 * Résout les fuseaux d'un fichier. Un TZID est d'abord pris pour un nom IANA
 * (« Europe/Paris », ou « /mozilla.org/…/Europe/Paris ») ; sinon (Outlook :
 * « Romance Standard Time »), on cherche un fuseau aux mêmes décalages d'hiver et
 * d'été que le VTIMEZONE du fichier, celui du téléphone d'abord.
 */
internal class ZoneResolver(vtimezones: List<IcsComponent>, private val deviceZone: ZoneId, private val year: Int) {
    private val definitions = vtimezones.associateBy { it.first("TZID")?.value?.trim().orEmpty() }
    private val cache = mutableMapOf<String, ZoneId?>()

    /** Les TZID qu'on n'a pas su résoudre : leurs heures sont lues dans le fuseau du téléphone. */
    val unknown = mutableSetOf<String>()

    fun zone(tzid: String): ZoneId = cache.getOrPut(tzid) {
        byName(tzid) ?: definitions[tzid]?.let { def -> def.first("X-LIC-LOCATION")?.value?.let(::byName) ?: byOffsets(def) }
    } ?: deviceZone.also { unknown += tzid }

    private fun byName(name: String): ZoneId? {
        val segments = name.trim().trim('"').split('/').filter { it.isNotBlank() }
        return segments.indices.firstNotNullOfOrNull { i ->
            runCatching { ZoneId.of(segments.drop(i).joinToString("/")) }.getOrNull()
        }
    }

    private fun byOffsets(def: IcsComponent): ZoneId? {
        fun offsetOf(kind: String) = def.children.firstOrNull { it.name == kind }?.first("TZOFFSETTO")?.value?.let(::parseOffset)
        val standard = offsetOf("STANDARD") ?: return null
        val wanted = setOf(standard, offsetOf("DAYLIGHT") ?: standard)
        val january = LocalDate.of(year, 1, 15).atStartOfDay().toInstant(ZoneOffset.UTC)
        val july = LocalDate.of(year, 7, 15).atStartOfDay().toInstant(ZoneOffset.UTC)
        fun matches(z: ZoneId) = z.rules.let { setOf(it.getOffset(january), it.getOffset(july)) } == wanted
        return sequenceOf(deviceZone).plus(ZoneId.getAvailableZoneIds().sorted().asSequence().map(ZoneId::of)).firstOrNull(::matches)
    }

    private fun parseOffset(v: String): ZoneOffset? = runCatching {
        val s = v.trim()
        ZoneOffset.ofHoursMinutesSeconds(
            (if (s[0] == '-') -1 else 1) * s.substring(1, 3).toInt(),
            (if (s[0] == '-') -1 else 1) * s.substring(3, 5).toInt(),
            (if (s[0] == '-') -1 else 1) * (s.substring(5).takeIf { it.length == 2 }?.toInt() ?: 0),
        )
    }.getOrNull()

    /** Une valeur de DTSTART, DTEND, EXDATE… ; null si illisible. */
    fun read(value: String, params: Map<String, String>): IcsWhen? = runCatching {
        val v = value.trim()
        if (params["VALUE"] == "DATE" || v.length == 8) {
            IcsWhen.Day(LocalDate.parse(v.take(8), BASIC_DATE))
        } else {
            val local = LocalDateTime.parse(v.take(15).padEnd(15, '0'), BASIC_DATE_TIME)
            when {
                v.endsWith("Z") -> IcsWhen.At(local.toInstant(ZoneOffset.UTC), deviceZone)
                params["TZID"] != null -> zone(params.getValue("TZID")).let { z -> IcsWhen.At(local.atZone(z).toInstant(), z) }
                else -> IcsWhen.At(local.atZone(deviceZone).toInstant(), deviceZone) // heure « flottante »
            }
        }
    }.getOrNull()

    /** Toutes les valeurs d'une propriété à liste (EXDATE, RDATE) ; une PERIOD « début/fin » garde son début. */
    fun readList(p: IcsProperty): List<IcsWhen> = p.value.split(',').mapNotNull { read(it.substringBefore('/'), p.params) }
}

internal val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
internal val BASIC_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

/**
 * Le VTIMEZONE d'un fuseau, tiré de ses règles Java : exigé par RFC 5545 pour
 * tout TZID. « Le dernier dimanche de mars » s'écrit BYDAY=SU;BYMONTHDAY=25..31 :
 * exact quelle que soit la règle du fuseau.
 */
internal fun vtimezone(zone: ZoneId): List<String> {
    val rules = zone.rules
    val lines = mutableListOf("BEGIN:VTIMEZONE", "TZID:${zone.id}")
    val transitions = rules.transitionRules
    if (transitions.isEmpty()) {
        val offset = rules.getOffset(Instant.now())
        lines += listOf("BEGIN:STANDARD", "DTSTART:19700101T000000", "TZOFFSETFROM:${offset.ics()}", "TZOFFSETTO:${offset.ics()}", "END:STANDARD")
    } else {
        for (rule in transitions) {
            val kind = if (rule.offsetAfter.totalSeconds > rule.standardOffset.totalSeconds) "DAYLIGHT" else "STANDARD"
            lines += "BEGIN:$kind"
            lines += "DTSTART:" + BASIC_DATE_TIME.format(rule.createTransition(1970).dateTimeBefore)
            lines += "RRULE:FREQ=YEARLY;BYMONTH=${rule.month.value};" + rule.byDayRule()
            lines += "TZOFFSETFROM:${rule.offsetBefore.ics()}"
            lines += "TZOFFSETTO:${rule.offsetAfter.ics()}"
            lines += "END:$kind"
        }
    }
    lines += "END:VTIMEZONE"
    return lines
}

private fun ZoneOffsetTransitionRule.byDayRule(): String {
    val dom = dayOfMonthIndicator
    val dow = dayOfWeek ?: return "BYMONTHDAY=$dom"
    // Indicateur négatif : compté depuis la fin du mois (-1 = le dernier jour).
    val days = if (dom > 0) (dom..minOf(dom + 6, month.maxLength())) else ((dom - 6)..dom)
    return "BYDAY=${dow.name.take(2)};BYMONTHDAY=${days.joinToString(",")}"
}

private fun ZoneOffset.ics(): String {
    val total = totalSeconds
    val sign = if (total < 0) "-" else "+"
    val abs = kotlin.math.abs(total)
    val base = "%s%02d%02d".format(sign, abs / 3600, abs / 60 % 60)
    return if (abs % 60 != 0) base + "%02d".format(abs % 60) else base
}
