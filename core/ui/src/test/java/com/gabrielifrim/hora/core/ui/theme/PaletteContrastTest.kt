package com.gabrielifrim.hora.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Test

/**
 * Garde-fou de lisibilité appliqué à CHAQUE palette du registre : une palette
 * ajoutée qui ne tient pas ces seuils fait échouer le build.
 *
 * Seuils WCAG 2.2 : 7:1 (AAA) pour le texte courant, 4,5:1 pour le texte
 * secondaire, l'accent et les états, 3:1 pour les couleurs d'agenda (repères
 * graphiques, toujours doublés d'une forme).
 */
class PaletteContrastTest {

    @Test
    fun `le texte courant atteint 7 pour 1 sur le fond et la surface`() = HoraPalettes.all.forEach { p ->
        assertContrast(p.id, "ink/background", p.ink, p.background, 7.0)
        assertContrast(p.id, "ink/surface", p.ink, p.surface, 7.0)
        assertContrast(p.id, "onInk/ink", p.onInk, p.ink, 7.0)
    }

    @Test
    fun `le texte secondaire, l'accent et les etats atteignent 4,5 pour 1`() = HoraPalettes.all.forEach { p ->
        listOf("background" to p.background, "surface" to p.surface).forEach { (ground, bg) ->
            assertContrast(p.id, "ink2/$ground", p.ink2, bg, 4.5)
            assertContrast(p.id, "accent/$ground", p.accent, bg, 4.5)
            assertContrast(p.id, "success/$ground", p.success, bg, 4.5)
            assertContrast(p.id, "warning/$ground", p.warning, bg, 4.5)
            assertContrast(p.id, "error/$ground", p.error, bg, 4.5)
        }
        assertContrast(p.id, "onAccent/accent", p.onAccent, p.accent, 7.0)
    }

    @Test
    fun `les couleurs d'agenda se detachent du fond`() = HoraPalettes.all.forEach { p ->
        AgendaColorKey.entries.forEach { key -> assertContrast(p.id, "agenda ${key.name}", p.agenda[key], p.background, 3.0) }
    }

    /**
     * Deux agendas doivent rester distincts pour tous : vision normale, protanopie
     * et deutéranopie simulées (Machado 2009, sévérité 1), écart OKLab × 100 ≥ 8.
     */
    @Test
    fun `deux couleurs d'agenda restent distinctes, daltonisme compris`() = HoraPalettes.all.forEach { p ->
        val keys = AgendaColorKey.entries
        for (i in keys.indices) for (j in i + 1 until keys.size) {
            listOf("normale" to IDENTITY, "protan" to PROTAN, "deutan" to DEUTAN).forEach { (kind, m) ->
                val delta = deltaE(simulate(p.agenda[keys[i]], m), simulate(p.agenda[keys[j]], m))
                assertWithMessage("${p.id} : ${keys[i]}/${keys[j]} en vision $kind, ΔE = %.1f, minimum 8".format(delta))
                    .that(delta).isAtLeast(8.0)
            }
        }
    }

    @Test
    fun `chaque couleur d'agenda a sa propre forme`() {
        assertWithMessage("formes en double").that(AgendaColorKey.entries.map { it.shape }).containsNoDuplicates()
    }

    @Test
    fun `les identifiants de palette sont uniques et il y a un clair et un sombre`() {
        assertWithMessage("identifiants en double").that(HoraPalettes.all.map { it.id }).containsNoDuplicates()
        assertWithMessage("clair et sombre").that(HoraPalettes.all.map { it.isDark }.toSet()).containsExactly(true, false)
    }

    private fun assertContrast(palette: String, pair: String, fg: Color, bg: Color, min: Double) {
        val ratio = contrast(fg, bg)
        assertWithMessage("$palette : $pair = %.2f:1, minimum $min:1".format(ratio)).that(ratio).isAtLeast(min)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun simulate(c: Color, m: Array<DoubleArray>): DoubleArray {
        val rgb = doubleArrayOf(srgbToLinear(c.red), srgbToLinear(c.green), srgbToLinear(c.blue))
        return DoubleArray(3) { i -> (m[i][0] * rgb[0] + m[i][1] * rgb[1] + m[i][2] * rgb[2]).coerceIn(0.0, 1.0) }
    }

    private fun deltaE(a: DoubleArray, b: DoubleArray): Double {
        val (la, lb) = oklab(a) to oklab(b)
        return 100 * sqrt((0..2).sumOf { (la[it] - lb[it]).pow(2) })
    }

    private fun oklab(rgb: DoubleArray): DoubleArray {
        val (r, g, b) = Triple(rgb[0], rgb[1], rgb[2])
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun srgbToLinear(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    /** Luminance relative sRGB, définition WCAG. */
    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private companion object {
        val IDENTITY = arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0))
        val PROTAN = arrayOf(
            doubleArrayOf(0.152286, 1.052583, -0.204868),
            doubleArrayOf(0.114503, 0.786281, 0.099216),
            doubleArrayOf(-0.003882, -0.048116, 1.051998),
        )
        val DEUTAN = arrayOf(
            doubleArrayOf(0.367322, 0.860646, -0.227968),
            doubleArrayOf(0.280085, 0.672501, 0.047413),
            doubleArrayOf(-0.011820, 0.042940, 0.968881),
        )
    }
}
