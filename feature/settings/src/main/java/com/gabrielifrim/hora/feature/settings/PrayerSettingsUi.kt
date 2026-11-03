package com.gabrielifrim.hora.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.domain.almanac.Place
import com.gabrielifrim.hora.core.domain.almanac.Prayer
import com.gabrielifrim.hora.core.domain.almanac.PrayerSettings
import com.gabrielifrim.hora.core.domain.almanac.Prayers
import com.gabrielifrim.hora.core.reminders.PrayerAlarms
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraSheet
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.HoraTextField
import com.gabrielifrim.hora.core.ui.component.SegmentedChoice
import com.gabrielifrim.hora.core.ui.component.ToggleRow
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Réglages → Pratique musulmane. Désactivée par défaut ; tout le reste n'apparaît qu'activée. */
@Composable
internal fun PrayerSection(p: PrayerSettings, place: Place, update: ((PrayerSettings) -> PrayerSettings) -> Unit) {
    var calibrating by remember { mutableStateOf(false) }
    var tuning by remember { mutableStateOf(false) }
    ToggleRow(
        title = stringResource(R.string.prayer_enable),
        hint = stringResource(R.string.prayer_enable_hint),
        checked = p.enabled,
        onCheckedChange = { on -> update { it.copy(enabled = on) } },
    )
    if (!p.enabled) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HoraText(stringResource(R.string.prayer_dawn_name), style = HoraTheme.type.body)
        SegmentedChoice(
            listOf(stringResource(PrayerAlarms.prayerName(Prayer.SOBH, false)), stringResource(PrayerAlarms.prayerName(Prayer.SOBH, true))),
            if (p.fajrName) 1 else 0,
            { i -> update { it.copy(fajrName = i == 1) } },
        )
    }
    ToggleRow(stringResource(R.string.prayer_notify_start), p.notifyStart, { on -> update { it.copy(notifyStart = on) } })
    ToggleRow(stringResource(R.string.prayer_notify_end), p.notifyPreferredEnd, { on -> update { it.copy(notifyPreferredEnd = on) } })
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { calibrating = true }.padding(vertical = 4.dp)) {
        HoraText(stringResource(R.string.prayer_calibrate), style = HoraTheme.type.body)
        HoraText(stringResource(R.string.prayer_calibrate_hint, format(p.dawnAngle), format(p.ishaAngle)), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { tuning = true }.padding(vertical = 4.dp)) {
        HoraText(stringResource(R.string.prayer_tuning), style = HoraTheme.type.body)
        HoraText(stringResource(R.string.prayer_tuning_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
    if (calibrating) CalibrateSheet(p, place, onApply = { dawn, isha -> update { it.copy(dawnAngle = dawn, ishaAngle = isha) }; calibrating = false }, onDismiss = { calibrating = false })
    if (tuning) TuningSheet(p, onApply = { t -> update { t }; tuning = false }, onDismiss = { tuning = false })
}

private fun format(angle: Double) = "%.1f".format(angle).replace('.', ',') // texte-technique : virgule décimale

/** « 06:13 », « 6h13 », « 6.13 », ou les chiffres seuls (« 0613 ») : le clavier numérique n'a pas de « : ». */
private fun parseTime(text: String): LocalTime? = Regex("""^\s*(\d{1,2})\s*[:hH.]?\s*(\d{2})\s*$""").matchEntire(text)?.destructured
    ?.let { (h, m) -> runCatching { LocalTime.of(h.toInt(), m.toInt()) }.getOrNull() }

/** L'heure de Sobh et de 'Isha d'aujourd'hui lues sur l'horaire de la mosquée donnent les deux angles. */
@Composable
private fun CalibrateSheet(p: PrayerSettings, place: Place, onApply: (Double, Double) -> Unit, onDismiss: () -> Unit) {
    var sobh by remember { mutableStateOf("") }
    var isha by remember { mutableStateOf("") }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val result = parseTime(sobh)?.let { s -> parseTime(isha)?.let { i -> Prayers.calibrate(today.atTime(s).atZone(zone).toInstant(), today.atTime(i).atZone(zone).toInstant(), place, p) } }
        ?.takeIf { (d, i) -> d in 6.0..22.0 && i in 6.0..22.0 }
    HoraSheet(
        onDismissRequest = onDismiss,
        actions = {
            HoraButton(stringResource(R.string.prayer_calibrate_apply), onClick = { result?.let { onApply(it.first, it.second) } }, enabled = result != null, modifier = Modifier.fillMaxWidth())
        },
    ) {
        HoraText(stringResource(R.string.prayer_calibrate), style = HoraTheme.type.heading)
        HoraText(stringResource(R.string.prayer_calibrate_explain, place.name), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        val time = KeyboardOptions(keyboardType = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoraTextField(sobh, { sobh = it }, label = stringResource(PrayerAlarms.prayerName(Prayer.SOBH, p.fajrName)), placeholder = "0613", modifier = Modifier.weight(1f), keyboardOptions = time) // texte-technique : exemple d'heure
            HoraTextField(isha, { isha = it }, label = stringResource(PrayerAlarms.prayerName(Prayer.ISHA, false)), placeholder = "2151", modifier = Modifier.weight(1f), keyboardOptions = time) // texte-technique : exemple d'heure
        }
        when {
            result != null -> HoraText(stringResource(R.string.prayer_calibrate_result, format(result.first), format(result.second)), style = HoraTheme.type.body)
            // Deux heures lisibles mais un soleil levé (ou bien trop bas) : on dit pourquoi le bouton reste grisé.
            parseTime(sobh) != null && parseTime(isha) != null ->
                HoraText(stringResource(R.string.prayer_calibrate_invalid), style = HoraTheme.type.body, color = HoraTheme.colors.error)
        }
    }
}

/** Angles, décalages par prière, durées estimées et ajustement de l'hégire. */
@Composable
private fun TuningSheet(p: PrayerSettings, onApply: (PrayerSettings) -> Unit, onDismiss: () -> Unit) {
    var dawn by remember { mutableStateOf(format(p.dawnAngle)) }
    var isha by remember { mutableStateOf(format(p.ishaAngle)) }
    val offsets = remember { Prayer.entries.associateWith { mutableStateOf(p.offset(it).toString()) } }
    var isfar by remember { mutableStateOf(p.isfarMinutes.toString()) }
    var isfirar by remember { mutableStateOf(p.isfirarMinutes.toString()) }
    var maghrib by remember { mutableStateOf(p.maghribMinutes.toString()) }
    var hijri by remember { mutableIntStateOf(p.hijriAdjust) }
    fun angle(s: String) = s.replace(',', '.').toDoubleOrNull()?.takeIf { it in 6.0..22.0 }
    fun minutes(s: String, range: IntRange) = s.trim().toIntOrNull()?.takeIf { it in range }
    val tuned = run {
        p.copy(
            dawnAngle = angle(dawn) ?: return@run null,
            ishaAngle = angle(isha) ?: return@run null,
            offsets = Prayer.entries.associateWith { minutes(offsets.getValue(it).value, -30..30) ?: return@run null },
            isfarMinutes = minutes(isfar, 0..90) ?: return@run null,
            isfirarMinutes = minutes(isfirar, 0..90) ?: return@run null,
            maghribMinutes = minutes(maghrib, 0..90) ?: return@run null,
            hijriAdjust = hijri,
        )
    }
    val number = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    HoraSheet(
        onDismissRequest = onDismiss,
        actions = { HoraButton(stringResource(R.string.save), onClick = { tuned?.let(onApply) }, enabled = tuned != null, modifier = Modifier.fillMaxWidth()) },
    ) {
        HoraText(stringResource(R.string.prayer_tuning), style = HoraTheme.type.heading)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoraTextField(dawn, { dawn = it }, label = stringResource(R.string.prayer_dawn_angle), modifier = Modifier.weight(1f), keyboardOptions = number)
            HoraTextField(isha, { isha = it }, label = stringResource(R.string.prayer_isha_angle), modifier = Modifier.weight(1f), keyboardOptions = number)
        }
        HoraText(stringResource(R.string.prayer_offsets_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        Prayer.entries.forEach { prayer ->
            val state = offsets.getValue(prayer)
            HoraTextField(state.value, { state.value = it }, label = stringResource(PrayerAlarms.prayerName(prayer, p.fajrName)), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
        }
        HoraText(stringResource(R.string.prayer_estimates_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        HoraTextField(isfar, { isfar = it }, label = stringResource(R.string.prayer_isfar), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        HoraTextField(isfirar, { isfirar = it }, label = stringResource(R.string.prayer_isfirar), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        HoraTextField(maghrib, { maghrib = it }, label = stringResource(R.string.prayer_maghrib_minutes), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        HoraText(stringResource(R.string.prayer_hijri_adjust), style = HoraTheme.type.body)
        SegmentedChoice(listOf("−1", "0", "+1"), hijri.coerceIn(-1, 1) + 1, { hijri = it - 1 }) // texte-technique : décalage en jours
        HoraText(stringResource(R.string.prayer_hijri_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    }
}
