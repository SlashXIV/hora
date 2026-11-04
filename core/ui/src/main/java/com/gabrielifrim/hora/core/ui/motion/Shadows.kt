package com.gabrielifrim.hora.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/**
 * Ombre du style, portée depuis le milieu du bas : un secteur de 20° qui pivote vers [angleDeg]
 * (0 = droit vers le haut). Elle tourne au changement d'angle, jamais au premier affichage ;
 * hors ambiance expressive, elle se pose sans tourner.
 */
fun Modifier.gnomonShadow(angleDeg: Float): Modifier = composed {
    val level = LocalMotionLevel.current
    val color = HoraTheme.colors.shadow
    val angle = remember { Animatable(angleDeg) }
    LaunchedEffect(angleDeg) {
        if (level.decorative) angle.animateTo(angleDeg, tween(Motion.TRANSITION_MS, easing = Motion.Transition))
        else angle.snapTo(angleDeg)
    }
    drawBehind {
        val radius = size.height * 0.8f
        val pivot = Offset(size.width / 2, size.height)
        drawArc(
            // L'ombre s'estompe en s'éloignant du style, comme au soleil.
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = pivot, radius = radius),
            startAngle = -90f + angle.value - SECTOR_DEG / 2,
            sweepAngle = SECTOR_DEG,
            useCenter = true,
            topLeft = pivot - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
        )
    }
}

/** Angle de l'ombre pour un mois (1 à 12) : un cran de 8° par mois, de -44° en janvier à +44° en décembre. */
fun monthShadowAngle(month: Int): Float = (month - 6.5f) * 8f

private const val SECTOR_DEG = 20f
