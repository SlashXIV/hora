package com.gabrielifrim.hora.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.ui.R
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.colorKeyOf
import com.gabrielifrim.hora.core.ui.text.spokenEvent
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.ZoneId

/**
 * Un événement en ligne de liste : heure gravée à gauche, repère d'agenda (couleur
 * et forme), titre et lieu. TalkBack lit la phrase complète, en un seul nœud.
 *
 * @param day jour de la liste où la ligne apparaît : l'heure affichée est celle de ce jour.
 */
@Composable
fun EventRow(
    event: Event,
    agendas: Map<Long, Agenda>,
    zone: ZoneId,
    formats: HoraFormats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    day: LocalDate? = null,
    subtitle: String? = null,
) {
    val colors = HoraTheme.colors
    val key = colorKeyOf(event, agendas)
    val spoken = spokenEvent(event, zone, formats)
    val t = event.timing
    val hour = when {
        t is Timing.AllDay -> stringResource(R.string.all_day_short)
        t is Timing.Timed && day != null && t.start.atZone(zone).toLocalDate().isBefore(day) -> stringResource(R.string.continued)
        t is Timing.Timed -> formats.time(t.start.atZone(zone).toLocalTime())
        else -> ""
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Button }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        HoraText(hour, style = HoraTheme.type.hour, color = colors.ink2, modifier = Modifier.width(52.dp).padding(top = 2.dp))
        AgendaMark(key.shape, key.color(), Modifier.padding(top = 4.dp), size = 11.dp)
        Column(Modifier.weight(1f)) {
            HoraText(event.title, style = HoraTheme.type.body.copy(fontWeight = HoraTheme.type.action.fontWeight), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val second = subtitle ?: event.location
            if (second.isNotBlank()) HoraText(second, style = HoraTheme.type.bodySmall, color = colors.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
