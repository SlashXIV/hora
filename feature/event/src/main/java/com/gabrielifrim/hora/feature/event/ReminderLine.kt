package com.gabrielifrim.hora.feature.event

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.ui.component.HoraIcon
import com.gabrielifrim.hora.core.ui.component.HoraIcons
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.motion.LocalMotionLevel
import com.gabrielifrim.hora.core.ui.motion.Motion
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import kotlinx.coroutines.launch

/**
 * Rappels vus en attente : quand l'un d'eux s'affiche ensuite passé, il se coche sous nos yeux.
 *
 * shortcut: en mémoire du processus ; une fiche ouverte pour la première fois après le rappel
 * le montre déjà coché, sans moment. Suffisant tant que le journal des rappels ne garde pas « vu ».
 */
private object PendingSeen {
    private val keys = mutableSetOf<String>()

    /** Vrai si [key] était en attente la dernière fois et vient de passer. */
    fun flipped(key: String, passed: Boolean): Boolean = if (passed) keys.remove(key) else { keys.add(key); false }
}

/**
 * Un rappel sur la fiche : la cloche tant qu'il est à venir, la coche une fois passé, avec son
 * heure réelle. Au passage, « un rappel se coche » : l'ombre passe sur le repère, la cloche
 * devient une coche et le texte se barre.
 */
@Composable
internal fun ReminderLine(key: String, text: String, time: String, spokenTime: String, passed: Boolean) {
    val colors = HoraTheme.colors
    val level = LocalMotionLevel.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val plays = remember(key, passed) { PendingSeen.flipped(key, passed) } && level.animates
    val progress = remember(key, passed) { Animatable(if (plays) 0f else 1f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(if (level.decorative) Motion.TRANSITION_MS else Motion.CALM_MS, easing = LinearEasing))
            haptics.confirm()
        }
    }
    val p = if (passed) progress.value else 0f
    val full = level.decorative && progress.value < 1f
    // Repères de la planche : la cloche s'efface de 25 à 60 %, la coche paraît de 30 à 100 %.
    val bell = if (full) 1f - ((p - 0.25f) / 0.35f).coerceIn(0f, 1f) else 1f - p
    val tickIn = if (full) ((p - 0.3f) / 0.7f).coerceIn(0f, 1f) else p
    val tick = if (full) Motion.Release.transform(tickIn) else tickIn
    val spoken = text + ", " + spokenTime + if (passed) ", " + stringResource(R.string.reminder_passed) else ""
    val shadow = colors.shadow

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clearAndSetSemantics { contentDescription = spoken }
            .pointerInput(progress) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); scope.launch { progress.snapTo(1f) } } }
            .clipToBounds()
            .drawWithContent {
                drawContent()
                if (full) {
                    // L'ombre oblique traverse la ligne de gauche à droite.
                    val band = 60.dp.toPx()
                    val x = -band + Motion.Transition.transform(p) * (size.width + 2 * band)
                    val skew = size.height * 0.36f
                    val path = Path().apply {
                        moveTo(x + skew, 0f); lineTo(x + band + skew, 0f); lineTo(x + band, size.height); lineTo(x, size.height); close()
                    }
                    drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, shadow, Color.Transparent), startX = x, endX = x + band + skew))
                }
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp)) {
            if (bell > 0f) HoraIcon(HoraIcons.Bell, null, Modifier.graphicsLayer { alpha = bell }, tint = colors.accent, size = 22.dp)
            if (tick > 0f) {
                HoraIcon(
                    HoraIcons.Check, null,
                    Modifier.graphicsLayer { alpha = tickIn; val s = 0.6f + 0.4f * tick; scaleX = s; scaleY = s },
                    tint = colors.success, size = 22.dp,
                )
            }
        }
        val struck = passed && p > 0.5f
        HoraText(
            "$text · $time", // texte-technique : séparateur entre deux textes traduits
            style = HoraTheme.type.body.copy(textDecoration = if (struck) TextDecoration.LineThrough else null),
            color = lerp(colors.ink, colors.ink2, p),
            modifier = Modifier.weight(1f),
        )
    }
}
