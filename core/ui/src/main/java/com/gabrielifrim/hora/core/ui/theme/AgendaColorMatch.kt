package com.gabrielifrim.hora.core.ui.theme

import kotlin.math.abs
import kotlin.math.min

/**
 * La couleur d'agenda Hora la plus proche d'une couleur venue d'ailleurs (ARGB de
 * Fossify, Google…), par la teinte : les couleurs Hora sont sourdes, une
 * distance RGB ferait tout tomber sur l'ardoise. Une couleur presque grise donne l'ardoise.
 */
fun nearestAgendaColor(argb: Int): AgendaColorKey {
    val (hue, saturation) = hueAndSaturation(argb)
    if (saturation < 0.15f) return AgendaColorKey.SLATE
    return AgendaColorKey.entries.filter { it != AgendaColorKey.SLATE }.minBy { key ->
        val d = abs(hueAndSaturation(HoraPalettes.Midi.agenda[key].toArgbInt()).first - hue)
        min(d, 360f - d)
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
    (alpha * 255).toInt() shl 24 or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

/** Teinte (degrés) et saturation (0..1), calculées à la main : testable hors Android. */
private fun hueAndSaturation(argb: Int): Pair<Float, Float> {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    if (delta == 0f) return 0f to 0f
    val hue = when (max) {
        r -> 60 * (((g - b) / delta) % 6)
        g -> 60 * ((b - r) / delta + 2)
        else -> 60 * ((r - g) / delta + 4)
    }.let { if (it < 0) it + 360 else it }
    return hue to delta / max
}
