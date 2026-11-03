package com.gabrielifrim.hora.feature.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.domain.calendar.FreeSlots
import com.gabrielifrim.hora.core.domain.model.Plage
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraSheet
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.Tag
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.Duration
import java.time.LocalDateTime

/**
 * « Trouver un créneau » : dans une plage (la première par défaut) ou de 8:00 à 20:00,
 * les trois prochains trous de la durée de l'événement. Toucher l'un d'eux y déplace l'événement.
 */
@Composable
internal fun FreeSlotSheet(
    plages: List<Plage>,
    duration: Duration,
    formats: HoraFormats,
    find: suspend (Plage?) -> List<LocalDateTime>,
    onPick: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var plage by remember { mutableStateOf(plages.firstOrNull()) }
    val slots by produceState<List<LocalDateTime>?>(null, plage) { value = find(plage) }
    HoraSheet(onDismissRequest = onDismiss) {
        HoraText(stringResource(R.string.slot_title), style = HoraTheme.type.heading)
        Eyebrow(stringResource(R.string.slot_where))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            plages.forEach { p -> Tag(p.name, selected = p == plage, onClick = { plage = p }) }
            Tag(
                stringResource(R.string.slot_day, formats.time(FreeSlots.DAY_START), formats.time(FreeSlots.DAY_END)),
                selected = plage == null,
                onClick = { plage = null },
            )
        }
        Eyebrow(stringResource(R.string.slot_results))
        when {
            slots == null -> Unit
            slots!!.isEmpty() -> HoraText(stringResource(R.string.slot_none), style = HoraTheme.type.body, color = HoraTheme.colors.ink2)
            else -> Column {
                slots!!.forEach { start ->
                    val end = start.plus(duration)
                    HoraText(
                        stringResource(R.string.slot_label, formats.dayLong(start.toLocalDate()), formats.time(start.toLocalTime()), formats.time(end.toLocalTime())),
                        style = HoraTheme.type.body,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onPick(start) }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
