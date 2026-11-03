package com.gabrielifrim.hora.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.ui.R
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/** La barre de saisie rapide, au-dessus des onglets : une pilule en retrait qui ouvre l'écran de saisie. */
@Composable
fun QuickEntryBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HoraTheme.colors
    val label = stringResource(R.string.quick_title)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surface)
            .border(1.dp, colors.rule, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = label; role = Role.Button }
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HoraIcon(HoraIcons.Add, contentDescription = null, tint = colors.ink2, size = 18.dp)
        HoraText(stringResource(R.string.quick_placeholder), style = HoraTheme.type.body, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.size(36.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
            HoraIcon(HoraIcons.Next, contentDescription = null, tint = colors.onAccent, size = 18.dp)
        }
    }
}
