package com.gabrielifrim.hora.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrielifrim.hora.core.ui.motion.LocalMotionLevel
import com.gabrielifrim.hora.core.ui.motion.Motion
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/**
 * Étiquette de choix : une pilule au filet doux, cerclée d'accent quand elle est
 * choisie. La cible tactile fait 48 dp de haut, la pilule en dessine 34.
 */
@Composable
fun Tag(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    /** Choix multiple (jours d'une plage) : lu comme une case à cocher. */
    multiple: Boolean = false,
) {
    val colors = HoraTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .then(
                if (multiple) Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
                else Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 34.dp)
                .clip(Pill)
                .border(1.dp, if (selected) colors.accent else colors.rule, Pill)
                .then(if (selected) Modifier.background(colors.accent.copy(alpha = 0.10f)) else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            HoraText(text, style = HoraTheme.type.bodySmall, color = if (selected) colors.accent else colors.ink)
        }
    }
}

/** Choix exclusif entre quelques options : une pilule, l'option choisie à l'encre. */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HoraTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(Pill)
            .border(1.dp, colors.rule, Pill)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, option ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp)
                    .fillMaxHeight()
                    .clip(Pill)
                    .then(if (selected) Modifier.background(colors.ink) else Modifier)
                    .selectable(selected = selected, onClick = { onSelect(i) }, role = Role.RadioButton)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Une ligne qui rétrécit plutôt qu'un mot coupé (« Semai/ne ») à 200 %.
                FitText(option, style = HoraTheme.type.bodySmall, color = if (selected) colors.onInk else colors.ink2, minFontSize = 8.sp)
            }
        }
    }
}

/**
 * Ligne de réglage avec interrupteur : toute la ligne se coche, TalkBack lit le
 * libellé, l'aide et l'état d'un seul tenant.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            HoraText(title, style = HoraTheme.type.body)
            if (hint != null) HoraText(hint, style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        }
        Switch(checked)
    }
}

/** Interrupteur seul, quand le libellé est ailleurs sur la ligne ; [label] est lu par TalkBack. */
@Composable
fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = label }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Switch(checked) }
}

/** L'interrupteur dessiné, sans interaction propre : c'est la ligne qui se coche. */
@Composable
private fun Switch(checked: Boolean) {
    val colors = HoraTheme.colors
    val animates = LocalMotionLevel.current.animates
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(if (animates) Motion.CALM_MS else 0, easing = Motion.Enter),
        label = "interrupteur",
    )
    Box(
        Modifier
            .width(40.dp)
            .height(24.dp)
            .clip(Pill)
            .background(if (checked) colors.accent else colors.rule),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset((3.dp + 16.dp * position).roundToPx(), 0) }
                .size(18.dp)
                .background(if (checked) colors.onAccent else colors.background, CircleShape),
        )
    }
}

private val Pill = RoundedCornerShape(50)
