package com.gabrielifrim.hora.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Le dernier événement écrit (créé, modifié, déplacé) : son bloc « se pose » au prochain
 * affichage, une seule fois.
 *
 * shortcut: en mémoire du processus seulement, et la première occurrence affichée d'une série
 * le consomme ; suffisant tant que l'éditeur ramène directement à la fiche puis à la liste.
 */
object Landing {
    private var pending: Long? = null

    fun mark(eventId: Long) {
        pending = eventId
    }

    internal fun take(eventId: Long): Boolean = (pending == eventId && eventId != 0L).also { if (it) pending = null }
}

/** Avancement du moment « un événement se pose » ; 1 = posé. */
@Stable
class LandingState internal constructor(internal val progress: Animatable<Float, *>, private val scope: CoroutineScope, private val full: Boolean) {
    /** Opacité de l'ombre de cadran : elle se forme sur la fin, une fois le bloc arrivé. */
    val shadow: Float get() = if (full) ((progress.value - 0.6f) / 0.4f).coerceIn(0f, 1f) else 1f

    internal val rise: Float get() = if (full) progress.value else 1f

    internal fun finish() {
        if (progress.value < 1f) scope.launch { progress.snapTo(1f) }
    }
}

@Composable
fun rememberLanding(eventId: Long): LandingState {
    val level = LocalMotionLevel.current
    val scope = rememberCoroutineScope()
    val plays = remember(eventId) { Landing.take(eventId) } && level.animates
    val state = remember(eventId) { LandingState(Animatable(if (plays) 0f else 1f), scope, level.decorative) }
    LaunchedEffect(state) {
        if (state.progress.value < 1f) {
            state.progress.animateTo(1f, if (level.decorative) tween(Motion.RELEASE_MS, easing = Motion.Release) else tween(Motion.CALM_MS))
        }
    }
    return state
}

/** Le bloc monte de 12 dp et grandit de 0,98 à 1 ; en Sobre, un fondu. Un appui termine le moment. */
@Composable
fun Modifier.landing(state: LandingState): Modifier {
    val offsetPx = with(LocalDensity.current) { 12.dp.toPx() }
    return this
        .pointerInput(state) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); state.finish() } }
        .graphicsLayer {
            val p = state.progress.value.coerceIn(0f, 1f)
            alpha = (p / 0.6f).coerceAtMost(1f)
            translationY = (1f - state.rise) * offsetPx
            val scale = 0.98f + 0.02f * state.rise
            scaleX = scale
            scaleY = scale
        }
}
