package com.gabrielifrim.hora.core.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.ui.motion.pressScale
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

enum class ButtonStyle {
    /** Aplat bleu cadran : l'action principale de l'écran, une seule. */
    Accent,

    /** Contour d'encre : action secondaire. */
    Line,

    /** Texte seul : annuler, actions tertiaires. */
    Text,
}

/**
 * Bouton de Hora : une pilule, libellé en minuscules, enfoncement élastique à la
 * pression au lieu d'une ondulation.
 */
@Composable
fun HoraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Accent,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
) {
    val colors = HoraTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val fg = when {
        contentColor != Color.Unspecified -> contentColor
        style == ButtonStyle.Accent -> colors.onAccent
        else -> colors.ink
    }
    Row(
        modifier = modifier
            .pressScale(interaction)
            // Tout le bouton s'estompe d'un bloc : une couche alpha posée sous clickable ne
            // redessinait plus le fond ni le contour au passage de désactivé à activé.
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .defaultMinSize(minHeight = 48.dp)
            .clip(Shape)
            .then(
                when (style) {
                    ButtonStyle.Accent -> Modifier.background(colors.accent)
                    ButtonStyle.Line -> Modifier.border(1.5.dp, colors.ink, Shape)
                    ButtonStyle.Text -> Modifier
                },
            )
            .clickable(interaction, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (style == ButtonStyle.Text) 12.dp else 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) HoraIcon(icon, contentDescription = null, tint = fg, size = 20.dp)
        // Deux lignes plutôt qu'un libellé coupé : à 200 %, un demi-bouton perd son
        // dernier mot. Un bouton texte reste aligné sur son icône, les autres se centrent.
        HoraText(
            text,
            style = HoraTheme.type.action.copy(
                textAlign = if (style == ButtonStyle.Text) TextAlign.Start else TextAlign.Center,
            ),
            color = fg,
            maxLines = 2,
        )
    }
}

/** Action en icône seule, avec une cible tactile de 48 dp. */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = HoraTheme.colors.ink,
    /** Appui long, annoncé par TalkBack avec [onLongClickLabel]. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(50))
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        HoraIcon(icon, contentDescription, tint = tint)
    }
}

private val Shape = RoundedCornerShape(50)
internal const val DISABLED_ALPHA = 0.38f
