package com.gabrielifrim.hora.feature.today

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.motion.LocalMotionLevel
import com.gabrielifrim.hora.core.ui.motion.Motion
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import kotlin.math.floor

/**
 * « Le compte à rebours à zéro » : joué le jour J, si la ligne avait été vue avant ce jour-là.
 * Le dernier compte vu est gardé en préférences (propre au téléphone, comme le dossier de
 * sauvegarde) : le moment survit à la nuit, sans toucher la base.
 *
 * @return le compte de départ (3 au plus) et l'avancement, ou null s'il n'y a rien à jouer.
 */
@Composable
internal fun rememberZeroMoment(eventId: Long, days: Long): Pair<Int, Animatable<Float, AnimationVector1D>>? {
    val context = LocalContext.current
    val level = LocalMotionLevel.current
    val haptics = rememberHaptics()
    val from = remember(eventId, days) {
        val prefs = context.getSharedPreferences(MOMENTS_PREFS, Context.MODE_PRIVATE)
        val key = "countdown_$eventId"
        val before = prefs.getLong(key, 0L)
        if (before != days) prefs.edit { putLong(key, days) }
        if (days == 0L && before > 0L) before.coerceAtMost(3L).toInt() else 0
    }
    if (from == 0 || !level.animates) return null
    val progress = remember(eventId, days) { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(if (level.decorative) Motion.DRAW_MS else Motion.CALM_MS, easing = LinearEasing))
        haptics.confirm()
    }
    return from to progress
}

/**
 * Le chiffre du compte : au jour J joué, les chiffres roulent de J-[from] jusqu'à « Jour J »
 * (en Sobre, un fondu). [final] fixe la taille, pour que rien ne bouge autour.
 */
@Composable
internal fun RollingFigure(final: String, from: Int, progress: Float) {
    val style = HoraTheme.type.figure
    val decorative = LocalMotionLevel.current.decorative
    val steps = (from downTo 1).map { stringResource(R.string.countdown_figure, it) } + final
    Box(Modifier.clipToBounds(), contentAlignment = Alignment.CenterEnd) {
        HoraText(final, style = style, modifier = Modifier.graphicsLayer { alpha = 0f })
        if (!decorative) {
            HoraText(final, style = style, modifier = Modifier.graphicsLayer { alpha = progress })
        } else {
            // Chaque cran attend sur sa moitié, puis roule vers le suivant.
            val pos = progress * (steps.size - 1)
            val i = floor(pos).toInt().coerceAtMost(steps.size - 1)
            val roll = Motion.Transition.transform(((pos - i - 0.5f) / 0.5f).coerceIn(0f, 1f))
            HoraText(steps[i], style = style, modifier = Modifier.graphicsLayer { translationY = -roll * size.height })
            if (i + 1 < steps.size) {
                HoraText(steps[i + 1], style = style, modifier = Modifier.graphicsLayer { translationY = (1f - roll) * size.height })
            }
        }
    }
}

private const val MOMENTS_PREFS = "moments"
