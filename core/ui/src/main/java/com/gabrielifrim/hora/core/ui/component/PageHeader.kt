package com.gabrielifrim.hora.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.ui.motion.RisingText
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/**
 * En-tête d'écran : une petite ligne de contexte, puis le titre en Fraunces. Il
 * remplace la barre d'application Material.
 *
 * @param poster grand titre d'affiche (une date) plutôt qu'un titre de section.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    poster: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconAction(HoraIcons.Back, contentDescription = backLabel.orEmpty(), onClick = onBack)
            }
            Spacer(Modifier.weight(1f))
            Row(content = actions)
        }
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (eyebrow != null) Eyebrow(eyebrow)
            RisingText(
                text = title,
                style = if (poster) HoraTheme.type.poster else HoraTheme.type.heading.copy(fontSize = HoraTheme.type.heading.fontSize * 1.3f),
                color = HoraTheme.colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
