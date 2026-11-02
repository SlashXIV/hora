package com.gabrielifrim.hora.core.domain.almanac

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt
import kotlin.math.tan

enum class Prayer { SOBH, DHOHR, ASR, MAGHRIB, ISHA }

/** Avis retenu pour Sobh et Maghrib : mashhour (le plus répandu) ou rajih (le plus fort). */
enum class Opinion { MASHHOUR, RAJIH }

/**
 * Réglages de la pratique musulmane. Valeurs de départ calées sur l'horaire publié de
 * la mosquée de Talence (Mawaqit) : aube et 'Isha à 12,8°, Dhohr au zénith + 5 min,
 * Maghrib au coucher + 4 min, 'Asr à l'ombre × 1 (malikite).
 */
data class PrayerSettings(
    val enabled: Boolean = false,
    val opinion: Opinion = Opinion.MASHHOUR,
    val dawnAngle: Double = 12.8,
    val ishaAngle: Double = 12.8,
    /** Décalage en minutes ajouté au début de chaque prière, comme le font les mosquées. */
    val offsets: Map<Prayer, Int> = mapOf(Prayer.DHOHR to 5, Prayer.MAGHRIB to 4),
    /** Isfar : fin du préférable de Sobh, N minutes avant le lever (mashhour). Estimé. */
    val isfarMinutes: Int = 30,
    /** Isfirar : fin du préférable de 'Asr, N minutes avant le coucher (soleil orangé). Estimé. */
    val isfirarMinutes: Int = 20,
    /** Fin du préférable de Maghrib, N minutes après son début (mashhour). */
    val maghribMinutes: Int = 15,
    /** « Fajr » plutôt que « Sobh » pour la prière de l'aube. */
    val fajrName: Boolean = false,
    val notifyStart: Boolean = false,
    /** « Fin du temps préférable dans 15 min ». */
    val notifyPreferredEnd: Boolean = false,
    /** L'hégire calculé peut avoir un jour d'écart avec l'observation locale. */
    val hijriAdjust: Int = 0,
) {
    fun offset(p: Prayer): Int = offsets[p] ?: 0
}

/**
 * Une prière du jour : son début, la fin du temps préférable (ikhtiyari) et la fin du
 * temps de nécessité (darouri). [estimated] : la fin du préférable ne se mesure pas.
 */
data class PrayerWindow(val prayer: Prayer, val start: Instant, val preferredEnd: Instant, val necessaryEnd: Instant, val estimated: Boolean)

/** Fenêtres de prière selon le fiqh malikite (voir la feuille de route, J5). */
object Prayers {

    /** Les cinq prières de [date] à [place] ; null si le soleil n'atteint pas les angles (latitudes extrêmes). */
    fun day(date: LocalDate, place: Place, s: PrayerSettings): List<PrayerWindow>? {
        fun shift(p: Prayer, t: Instant) = t.plus(Duration.ofMinutes(s.offset(p).toLong()))
        val dawn = Sun.crossing(date, place, -s.dawnAngle, morning = true) ?: return null
        val nextDawn = Sun.crossing(date.plusDays(1), place, -s.dawnAngle, morning = true) ?: return null
        val sunrise = Sun.crossing(date, place, Sun.HORIZON, morning = true) ?: return null
        val sunset = Sun.crossing(date, place, Sun.HORIZON, morning = false) ?: return null
        val noon = Sun.noon(date, place)
        val ishaAt = Sun.crossing(date, place, -s.ishaAngle, morning = false) ?: return null
        val asrAt = Sun.crossing(date, place, asrAltitude(Sun.altitude(noon, place)), morning = false) ?: return null

        val sobh = shift(Prayer.SOBH, dawn)
        val dhohr = shift(Prayer.DHOHR, noon)
        val asr = shift(Prayer.ASR, asrAt)
        val maghrib = shift(Prayer.MAGHRIB, sunset)
        val isha = shift(Prayer.ISHA, ishaAt)
        // La nuit va du coucher à l'aube ; le préférable de 'Isha finit au premier tiers.
        val firstThird = sunset.plus(Duration.between(sunset, nextDawn).dividedBy(3))
        val rajih = s.opinion == Opinion.RAJIH
        return listOf(
            PrayerWindow(Prayer.SOBH, sobh, if (rajih) sunrise else sunrise.minus(Duration.ofMinutes(s.isfarMinutes.toLong())), sunrise, estimated = !rajih),
            PrayerWindow(Prayer.DHOHR, dhohr, asr, sunset, estimated = false),
            PrayerWindow(Prayer.ASR, asr, sunset.minus(Duration.ofMinutes(s.isfirarMinutes.toLong())), sunset, estimated = true),
            PrayerWindow(Prayer.MAGHRIB, maghrib, if (rajih) isha else maghrib.plus(Duration.ofMinutes(s.maghribMinutes.toLong())), nextDawn, estimated = false),
            PrayerWindow(Prayer.ISHA, isha, maxOf(firstThird, isha), nextDawn, estimated = false),
        )
    }

    /**
     * 'Asr : l'ombre d'un objet égale sa taille, plus l'ombre qu'il avait au zénith.
     * Hauteur du soleil à cet instant, depuis sa hauteur à midi.
     */
    fun asrAltitude(noonAltitude: Double): Double = Math.toDegrees(atan(1 / (1 + tan(Math.toRadians(90 - noonAltitude)))))

    /**
     * Angles d'aube et de 'Isha tirés de l'horaire d'une mosquée : la hauteur du soleil
     * aux heures publiées, au dixième de degré. Les décalages réglés sont retirés d'abord.
     */
    fun calibrate(sobh: Instant, isha: Instant, place: Place, s: PrayerSettings): Pair<Double, Double> {
        fun angle(at: Instant, p: Prayer) = (abs(Sun.altitude(at.minus(Duration.ofMinutes(s.offset(p).toLong())), place)) * 10).roundToInt() / 10.0
        return angle(sobh, Prayer.SOBH) to angle(isha, Prayer.ISHA)
    }

    /** Date de l'hégire (Umm al-Qura) : jour, mois (1 à 12), année. */
    fun hijri(date: LocalDate, adjust: Int = 0): Triple<Int, Int, Int> {
        val h = HijrahDate.from(date.plusDays(adjust.toLong()))
        return Triple(h.get(ChronoField.DAY_OF_MONTH), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.YEAR))
    }
}

/**
 * Les réglages des prières tiennent dans une colonne : « clé=valeur » séparés par
 * des « ; ». Une clé inconnue ou illisible garde sa valeur par défaut.
 */
object PrayerCodec {
    fun encode(s: PrayerSettings): String = listOf(
        "on" to s.enabled, "opinion" to s.opinion.name, "dawn" to s.dawnAngle, "isha" to s.ishaAngle,
        "offsets" to Prayer.entries.joinToString(",") { "${it.name}:${s.offset(it)}" },
        "isfar" to s.isfarMinutes, "isfirar" to s.isfirarMinutes, "maghrib" to s.maghribMinutes,
        "fajr" to s.fajrName, "notifyStart" to s.notifyStart, "notifyEnd" to s.notifyPreferredEnd, "hijri" to s.hijriAdjust,
    ).joinToString(";") { (k, v) -> "$k=$v" }

    fun decode(text: String?): PrayerSettings {
        val d = PrayerSettings()
        val m = text.orEmpty().split(';').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        val offsets = m["offsets"]?.split(',')?.mapNotNull { e ->
            val (name, v) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            Prayer.entries.firstOrNull { it.name == name }?.let { p -> v.toIntOrNull()?.let { p to it } }
        }?.toMap() ?: d.offsets
        return PrayerSettings(
            enabled = m["on"]?.toBooleanStrictOrNull() ?: d.enabled,
            opinion = Opinion.entries.firstOrNull { it.name == m["opinion"] } ?: d.opinion,
            dawnAngle = m["dawn"]?.toDoubleOrNull() ?: d.dawnAngle,
            ishaAngle = m["isha"]?.toDoubleOrNull() ?: d.ishaAngle,
            offsets = offsets,
            isfarMinutes = m["isfar"]?.toIntOrNull() ?: d.isfarMinutes,
            isfirarMinutes = m["isfirar"]?.toIntOrNull() ?: d.isfirarMinutes,
            maghribMinutes = m["maghrib"]?.toIntOrNull() ?: d.maghribMinutes,
            fajrName = m["fajr"]?.toBooleanStrictOrNull() ?: d.fajrName,
            notifyStart = m["notifyStart"]?.toBooleanStrictOrNull() ?: d.notifyStart,
            notifyPreferredEnd = m["notifyEnd"]?.toBooleanStrictOrNull() ?: d.notifyPreferredEnd,
            hijriAdjust = m["hijri"]?.toIntOrNull()?.coerceIn(-1, 1) ?: d.hijriAdjust,
        )
    }
}
