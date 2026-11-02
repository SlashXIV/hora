package com.gabrielifrim.hora.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.ui.theme.AgendaShape
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/** Filet : ce qui sépare les choses, à la place des ombres et des cartes. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = HoraTheme.colors.rule, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

/**
 * Déborde de [horizontal] de chaque côté : annule la marge latérale d'une liste
 * pour un élément qui doit aller bord à bord.
 */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Petite ligne en capitales au-dessus d'un titre : jour, semaine, contexte.
 * Césure automatique plutôt qu'un mot coupé n'importe où à 200 %.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HoraTheme.colors.ink2,
    maxLines: Int = 2,
) {
    UppercaseText(
        text,
        modifier,
        style = HoraTheme.type.eyebrow.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
        color = color,
        maxLines = maxLines,
    )
}

/**
 * Texte affiché en capitales, mais exposé tel quel à l'accessibilité : TalkBack
 * épelle parfois une suite de capitales comme un sigle.
 */
@Composable
fun UppercaseText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    HoraText(
        text.uppercase(),
        modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        style = style,
        color = color,
        maxLines = maxLines,
    )
}

/**
 * Repère d'agenda : sa couleur ET sa forme, pour qu'un daltonien distingue deux
 * agendas sans dépendre de la teinte. Décoratif : le nom de l'agenda est lu à côté.
 */
@Composable
fun AgendaMark(shape: AgendaShape, color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Canvas(modifier.size(size)) { drawAgendaShape(shape, color) }
}

fun DrawScope.drawAgendaShape(shape: AgendaShape, color: Color) {
    val w = size.width
    val h = size.height
    val path = Path()
    when (shape) {
        AgendaShape.DOT -> drawCircle(color, radius = w * 0.38f)
        AgendaShape.SQUARE -> drawRect(color, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f))
        AgendaShape.RING -> drawCircle(color, radius = w * 0.33f, style = Stroke(w * 0.2f))
        AgendaShape.BAR -> drawRect(color, Offset(w * 0.07f, h * 0.32f), Size(w * 0.86f, h * 0.36f))
        AgendaShape.CROSS -> {
            drawRect(color, Offset(w * 0.37f, h * 0.08f), Size(w * 0.26f, h * 0.84f))
            drawRect(color, Offset(w * 0.08f, h * 0.37f), Size(w * 0.84f, h * 0.26f))
        }
        AgendaShape.DIAMOND -> drawPath(
            path.apply { moveTo(w / 2, h * 0.07f); lineTo(w * 0.93f, h / 2); lineTo(w / 2, h * 0.93f); lineTo(w * 0.07f, h / 2); close() },
            color,
        )
        AgendaShape.TRIANGLE -> drawPath(
            path.apply { moveTo(w / 2, h * 0.1f); lineTo(w * 0.93f, h * 0.88f); lineTo(w * 0.07f, h * 0.88f); close() },
            color,
        )
        AgendaShape.TRIANGLE_DOWN -> drawPath(
            path.apply { moveTo(w / 2, h * 0.9f); lineTo(w * 0.93f, h * 0.12f); lineTo(w * 0.07f, h * 0.12f); close() },
            color,
        )
    }
}
