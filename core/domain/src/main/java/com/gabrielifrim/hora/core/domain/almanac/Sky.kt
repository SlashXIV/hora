package com.gabrielifrim.hora.core.domain.almanac

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Un lieu, pour le soleil (et plus tard les prières). */
data class Place(val name: String, val latitude: Double, val longitude: Double)

private fun rad(d: Double) = d * PI / 180
private fun deg(r: Double) = r * 180 / PI
private fun julianDay(instant: Instant) = instant.toEpochMilli() / 86_400_000.0 + 2_440_587.5
private fun instantOf(jd: Double) = Instant.ofEpochMilli(((jd - 2_440_587.5) * 86_400_000).roundToLong())

/**
 * Lever, coucher et midi solaire (algorithme de la NOAA, à la minute près sous nos
 * latitudes). Une hauteur quelconque servira aussi aux prières : aube, crépuscule, ombre.
 */
object Sun {
    /** Hauteur du centre du soleil au lever et au coucher : réfraction et demi-diamètre compris. */
    const val HORIZON = -0.833

    data class Day(val sunrise: Instant?, val noon: Instant, val sunset: Instant?)

    fun day(date: LocalDate, place: Place): Day = Day(
        sunrise = crossing(date, place, HORIZON, morning = true),
        noon = noon(date, place),
        sunset = crossing(date, place, HORIZON, morning = false),
    )

    fun noon(date: LocalDate, place: Place): Instant {
        var minutes = 720 - 4 * place.longitude
        repeat(2) { minutes = 720 - 4 * place.longitude - position(date, minutes).equationOfTime }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().plusMillis((minutes * 60_000).roundToLong())
    }

    /** Le soleil passe à [altitude] degrés le matin ou le soir ; null s'il n'y passe pas (été polaire, ou nuit). */
    fun crossing(date: LocalDate, place: Place, altitude: Double, morning: Boolean): Instant? {
        var minutes = 720 - 4 * place.longitude + if (morning) -360 else 360
        repeat(3) {
            val p = position(date, minutes)
            val cosH = (sin(rad(altitude)) - sin(rad(place.latitude)) * sin(p.declination)) / (cos(rad(place.latitude)) * cos(p.declination))
            if (cosH < -1 || cosH > 1) return null
            val h = deg(acos(cosH))
            minutes = 720 - 4 * (place.longitude + if (morning) h else -h) - p.equationOfTime
        }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().plusMillis((minutes * 60_000).roundToLong())
    }

    /** Hauteur du centre du soleil (degrés) à un instant : sert à caler les angles sur l'horaire d'une mosquée. */
    fun altitude(at: Instant, place: Place): Double {
        val utc = at.atZone(ZoneOffset.UTC)
        val minutes = utc.hour * 60 + utc.minute + utc.second / 60.0
        val p = position(utc.toLocalDate(), minutes)
        val hourAngle = rad((minutes + p.equationOfTime + 4 * place.longitude) / 4 - 180)
        val lat = rad(place.latitude)
        return deg(asin(sin(lat) * sin(p.declination) + cos(lat) * cos(p.declination) * cos(hourAngle)))
    }

    private class Position(val declination: Double, val equationOfTime: Double)

    /** Déclinaison (radians) et équation du temps (minutes) au jour [date], [minutes] après 0 h UTC. */
    private fun position(date: LocalDate, minutes: Double): Position {
        val jd = julianDay(date.atStartOfDay(ZoneOffset.UTC).toInstant()) + minutes / 1440
        val t = (jd - 2_451_545) / 36_525
        val l0 = (280.46646 + t * (36_000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val y = tan(rad(eps) / 2).let { it * it }
        val eot = 4 * deg(
            y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)),
        )
        return Position(asin(sin(rad(eps)) * sin(rad(lambda))), eot)
    }
}

/** Phases de la lune (Meeus, chapitre 49, termes principaux : à quelques minutes près). */
object Moon {
    fun phases(first: LocalDate, last: LocalDate, zone: ZoneId): List<AlmanacEntry> {
        val start = first.atStartOfDay(zone).toInstant()
        val end = last.plusDays(1).atStartOfDay(zone).toInstant()
        val year = first.year + (first.dayOfYear - 1) / 365.25
        var k = floor((year - 2000) * 12.3685) - 1
        val out = mutableListOf<AlmanacEntry>()
        while (true) {
            val phases = listOf(AlmanacKind.NEW_MOON, AlmanacKind.FIRST_QUARTER, AlmanacKind.FULL_MOON, AlmanacKind.LAST_QUARTER)
            for ((i, kind) in phases.withIndex()) {
                val at = phase(k + i / 4.0)
                if (!at.isBefore(end)) return out
                if (!at.isBefore(start)) out += AlmanacEntry(kind, at.atZone(zone).toLocalDate(), at)
            }
            k += 1
        }
    }

    /** k entier : nouvelle lune ; +0,25 premier quartier ; +0,5 pleine lune ; +0,75 dernier quartier. */
    fun phase(k: Double): Instant {
        val t = k / 1236.85
        val jde = 2_451_550.09766 + 29.530588861 * k + 0.00015437 * t * t - 0.000000150 * t * t * t + 0.00000000073 * t * t * t * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t * t - 0.00000011 * t * t * t)
        val mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t * t + 0.00001238 * t * t * t - 0.000000058 * t * t * t * t)
        val f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t * t - 0.00000227 * t * t * t + 0.000000011 * t * t * t * t)
        val om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t * t + 0.00000215 * t * t * t)
        val fraction = k - floor(k)
        val correction = when {
            fraction < 0.01 -> -0.40720 * sin(mp) + 0.17241 * e * sin(m) + 0.01608 * sin(2 * mp) + 0.01039 * sin(2 * f) +
                0.00739 * e * sin(mp - m) - 0.00514 * e * sin(mp + m) + 0.00208 * e * e * sin(2 * m) - 0.00111 * sin(mp - 2 * f) -
                0.00057 * sin(mp + 2 * f) + 0.00056 * e * sin(2 * mp + m) - 0.00042 * sin(3 * mp) + 0.00042 * e * sin(m + 2 * f) +
                0.00038 * e * sin(m - 2 * f) - 0.00024 * e * sin(2 * mp - m) - 0.00017 * sin(om)
            fraction in 0.49..0.51 -> -0.40614 * sin(mp) + 0.17302 * e * sin(m) + 0.01614 * sin(2 * mp) + 0.01043 * sin(2 * f) +
                0.00734 * e * sin(mp - m) - 0.00515 * e * sin(mp + m) + 0.00209 * e * e * sin(2 * m) - 0.00111 * sin(mp - 2 * f) -
                0.00057 * sin(mp + 2 * f) + 0.00056 * e * sin(2 * mp + m) - 0.00042 * sin(3 * mp) + 0.00042 * e * sin(m + 2 * f) +
                0.00038 * e * sin(m - 2 * f) - 0.00024 * e * sin(2 * mp - m) - 0.00017 * sin(om)
            else -> {
                val w = 0.00306 - 0.00038 * e * cos(m) + 0.00026 * cos(mp) - 0.00002 * cos(mp - m) + 0.00002 * cos(mp + m) + 0.00002 * cos(2 * f)
                -0.62801 * sin(mp) + 0.17172 * e * sin(m) - 0.01183 * e * sin(mp + m) + 0.00862 * sin(2 * mp) + 0.00804 * sin(2 * f) +
                    0.00454 * e * sin(mp - m) + 0.00204 * e * e * sin(2 * m) - 0.00180 * sin(mp - 2 * f) - 0.00070 * sin(mp + 2 * f) -
                    0.00040 * sin(3 * mp) - 0.00034 * e * sin(2 * mp - m) + 0.00032 * e * sin(m + 2 * f) + 0.00032 * e * sin(m - 2 * f) -
                    0.00028 * e * e * sin(mp + 2 * m) + 0.00027 * e * sin(2 * mp + m) - 0.00017 * sin(om) +
                    if (fraction < 0.5) w else -w
            }
        }
        // Temps dynamique → temps universel : ΔT ≈ 69 s dans les années 2020.
        return instantOf(jde + correction - 69.0 / 86_400)
    }
}

/** Villes proposées pour le soleil ; Bordeaux par défaut. Hors liste : coordonnées saisies. */
object Places {
    val DEFAULT = Place("Bordeaux", 44.8378, -0.5792)

    val FRANCE: List<Place> = listOf(
        Place("Aix-en-Provence", 43.5297, 5.4474), Place("Ajaccio", 41.9192, 8.7386), Place("Amiens", 49.8941, 2.2958),
        Place("Angers", 47.4784, -0.5632), Place("Annecy", 45.8992, 6.1294), Place("Avignon", 43.9493, 4.8055),
        Place("Bayonne", 43.4929, -1.4748), Place("Besançon", 47.2378, 6.0241), DEFAULT, Place("Brest", 48.3904, -4.4861),
        Place("Caen", 49.1829, -0.3707), Place("Clermont-Ferrand", 45.7772, 3.0870), Place("Dijon", 47.3220, 5.0415),
        Place("Grenoble", 45.1885, 5.7245), Place("La Rochelle", 46.1603, -1.1511), Place("Le Havre", 49.4944, 0.1079),
        Place("Le Mans", 48.0061, 0.1996), Place("Lille", 50.6292, 3.0573), Place("Limoges", 45.8336, 1.2611),
        Place("Lyon", 45.7640, 4.8357), Place("Marseille", 43.2965, 5.3698), Place("Metz", 49.1193, 6.1757),
        Place("Montpellier", 43.6108, 3.8767), Place("Mulhouse", 47.7508, 7.3359), Place("Nancy", 48.6921, 6.1844),
        Place("Nantes", 47.2184, -1.5536), Place("Nice", 43.7102, 7.2620), Place("Nîmes", 43.8367, 4.3601),
        Place("Orléans", 47.9030, 1.9093), Place("Paris", 48.8566, 2.3522), Place("Pau", 43.2951, -0.3708),
        Place("Perpignan", 42.6887, 2.8948), Place("Poitiers", 46.5802, 0.3404), Place("Reims", 49.2583, 4.0317),
        Place("Rennes", 48.1173, -1.6778), Place("Rouen", 49.4432, 1.0999), Place("Saint-Étienne", 45.4397, 4.3872),
        Place("Strasbourg", 48.5734, 7.7521), Place("Toulon", 43.1242, 5.9280), Place("Toulouse", 43.6047, 1.4442),
        Place("Tours", 47.3941, 0.6848),
    )
}
