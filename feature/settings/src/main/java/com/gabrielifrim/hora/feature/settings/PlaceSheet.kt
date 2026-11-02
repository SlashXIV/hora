package com.gabrielifrim.hora.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.domain.almanac.Place
import com.gabrielifrim.hora.core.domain.almanac.Places
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraSheet
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.HoraTextField
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/**
 * Le lieu du lever et du coucher du soleil : une ville de la liste, ou des
 * coordonnées saisies. Aucune permission de localisation.
 */
@Composable
internal fun PlaceSheet(current: Place, onPick: (Place) -> Unit, onDismiss: () -> Unit) {
    val custom = Places.FRANCE.none { it == current }
    var latitude by remember { mutableStateOf(if (custom) current.latitude.toString() else "") }
    var longitude by remember { mutableStateOf(if (custom) current.longitude.toString() else "") }
    val lat = latitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lon = longitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val customName = stringResource(R.string.place_custom)
    HoraSheet(onDismissRequest = onDismiss) {
        HoraText(stringResource(R.string.place_title), style = HoraTheme.type.heading)
        Places.FRANCE.forEach { place ->
            val on = place == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { selected = on; role = Role.RadioButton }
                    .clickable { onPick(place) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HoraText(place.name, style = HoraTheme.type.body, color = if (on) HoraTheme.colors.accent else HoraTheme.colors.ink)
            }
        }
        Rule()
        HoraText(stringResource(R.string.place_coordinates_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            HoraTextField(latitude, { latitude = it }, label = stringResource(R.string.place_latitude), modifier = Modifier.weight(1f), keyboardOptions = decimal)
            HoraTextField(longitude, { longitude = it }, label = stringResource(R.string.place_longitude), modifier = Modifier.weight(1f), keyboardOptions = decimal)
        }
        HoraButton(
            stringResource(R.string.place_use_coordinates),
            onClick = { onPick(Place(customName, lat!!, lon!!)) },
            enabled = lat != null && lon != null,
            style = ButtonStyle.Line,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
