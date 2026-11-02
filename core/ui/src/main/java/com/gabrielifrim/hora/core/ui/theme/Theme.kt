package com.gabrielifrim.hora.core.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.TextStyle
import com.gabrielifrim.hora.core.domain.model.MotionPreference
import com.gabrielifrim.hora.core.domain.model.ThemePreference
import com.gabrielifrim.hora.core.ui.motion.LocalHapticsEnabled
import com.gabrielifrim.hora.core.ui.motion.LocalMotionLevel
import com.gabrielifrim.hora.core.ui.motion.rememberMotionLevel
import kotlinx.coroutines.launch

val LocalHoraColors = staticCompositionLocalOf { HoraPalettes.Midi }
val LocalHoraType = staticCompositionLocalOf { HoraType() }

/** Couleur par défaut du texte et des icônes sous ce nœud. */
val LocalContentColor = compositionLocalOf { Color.Unspecified }

/** Style par défaut de [com.gabrielifrim.hora.core.ui.component.HoraText] sous ce nœud. */
val LocalTextStyle = compositionLocalOf { TextStyle.Default }

object HoraTheme {
    val colors: HoraColors
        @Composable @ReadOnlyComposable
        get() = LocalHoraColors.current

    val type: HoraType
        @Composable @ReadOnlyComposable
        get() = LocalHoraType.current
}

/**
 * Thème de Hora, sans Material.
 *
 * Système : Midi si le téléphone est clair, Nocturne s'il est sombre.
 */
@Composable
fun HoraTheme(
    theme: ThemePreference = ThemePreference.SYSTEM,
    motion: MotionPreference = MotionPreference.EXPRESSIVE,
    haptics: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = paletteFor(theme, isSystemInDarkTheme())
    val type = HoraType()
    CompositionLocalProvider(
        LocalHoraColors provides colors,
        LocalHoraType provides type,
        LocalMotionLevel provides rememberMotionLevel(motion),
        LocalHapticsEnabled provides haptics,
        LocalContentColor provides colors.ink,
        LocalTextStyle provides type.body,
        LocalIndication provides InkPressIndication,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = colors.accent,
            backgroundColor = colors.accent.copy(alpha = 0.35f),
        ),
        content = content,
    )
}

/** Palette effective ; partagée avec l'activité, qui y accorde ses barres système. */
fun paletteFor(theme: ThemePreference, systemDark: Boolean): HoraColors = when (theme) {
    ThemePreference.MIDI -> HoraPalettes.Midi
    ThemePreference.NOCTURNE -> HoraPalettes.Nocturne
    ThemePreference.SYSTEM -> if (systemDark) HoraPalettes.Nocturne else HoraPalettes.Midi
}

/**
 * Retour de pression par défaut : un voile d'encre à 8 %, sans ondulation. Les
 * boutons maison y ajoutent leur enfoncement (`pressScale`).
 */
private object InkPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = InkPressNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = javaClass.hashCode()
}

private class InkPressNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            source.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                }
                val now = presses > 0
                if (now != pressed) {
                    pressed = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(currentValueOf(LocalHoraColors).ink.copy(alpha = PRESS_ALPHA))
    }
}

private const val PRESS_ALPHA = 0.08f
