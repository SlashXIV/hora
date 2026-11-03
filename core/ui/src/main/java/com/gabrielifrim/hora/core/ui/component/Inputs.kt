package com.gabrielifrim.hora.core.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/**
 * Champ de saisie : libellé en capitales au-dessus, un filet dessous qui s'épaissit
 * et passe au bleu cadran au focus. Le libellé est dans la décoration du champ,
 * donc dans son nœud d'accessibilité : TalkBack l'annonce avec la saisie.
 */
@Composable
fun HoraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = HoraTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = HoraTheme.type.body.copy(color = colors.ink),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                UppercaseText(label, style = HoraTheme.type.label, color = if (focused) colors.accent else colors.ink2)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val w = (if (focused) 2.dp else 1.dp).toPx()
                            drawLine(if (focused) colors.accent else colors.rule, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                        }
                        .padding(vertical = 10.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) HoraText(placeholder, color = colors.ink2)
                    inner()
                }
            }
        },
    )
}
