package com.gabrielifrim.hora.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gabrielifrim.hora.core.domain.model.Reminder
import com.gabrielifrim.hora.core.domain.repository.ReminderSet
import com.gabrielifrim.hora.core.ui.R
import com.gabrielifrim.hora.core.ui.text.HoraFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import java.time.LocalTime

/** « 10 min avant », « 2 jours avant », « la veille à 20:00 », « à l'heure ». */
@Composable
fun reminderText(r: Reminder, formats: HoraFormats): String = when (r) {
    is Reminder.Before -> when {
        r.minutes == 0 -> stringResource(R.string.reminder_at_start)
        r.minutes % (7 * 24 * 60) == 0 -> (r.minutes / (7 * 24 * 60)).let { pluralStringResource(R.plurals.reminder_weeks_before, it, it) }
        r.minutes % (24 * 60) == 0 -> (r.minutes / (24 * 60)).let { pluralStringResource(R.plurals.reminder_days_before, it, it) }
        r.minutes % 60 == 0 -> (r.minutes / 60).let { pluralStringResource(R.plurals.reminder_hours_before, it, it) }
        else -> pluralStringResource(R.plurals.reminder_minutes_before, r.minutes, r.minutes)
    }
    is Reminder.Anchored -> when (r.daysBefore) {
        0 -> stringResource(R.string.reminder_same_day_at, formats.time(r.time))
        1 -> stringResource(R.string.reminder_eve_at, formats.time(r.time))
        else -> pluralStringResource(R.plurals.reminder_days_before_at, r.daysBefore, r.daysBefore, formats.time(r.time))
    }
}

/** La liste des rappels d'un événement (ou d'un agenda), chacun retirable, et l'ajout. */
@Composable
fun ReminderList(
    reminders: List<Reminder>,
    formats: HoraFormats,
    onRemove: (Reminder) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Eyebrow(pluralStringResource(R.plurals.reminders_count, reminders.size, reminders.size))
        reminders.forEach { r ->
            val text = reminderText(r, formats)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HoraIcon(HoraIcons.Bell, contentDescription = null, tint = HoraTheme.colors.accent, size = 18.dp)
                Column(Modifier.weight(1f)) {
                    HoraText(text, style = HoraTheme.type.body)
                    if (r.alarm) HoraText(stringResource(R.string.reminder_alarm), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
                }
                IconAction(HoraIcons.Close, stringResource(R.string.reminder_remove, text), onClick = { onRemove(r) }, tint = HoraTheme.colors.ink2)
            }
            Rule()
        }
        HoraButton(stringResource(R.string.reminder_add), onClick = onAdd, style = ButtonStyle.Text, icon = HoraIcons.Add)
    }
}

private enum class Custom { NONE, DURATION, ANCHORED }

/**
 * « Ajouter un rappel » : relatifs, ancrés, durée libre, jeux de rappels. Un choix
 * ajoute tout de suite et ferme la feuille ; l'interrupteur « Alarme plein écran »
 * s'applique à ce qu'on ajoute.
 */
@Composable
fun AddReminderSheet(
    sets: List<ReminderSet>,
    formats: HoraFormats,
    onAdd: (List<Reminder>) -> Unit,
    onDismiss: () -> Unit,
) {
    var alarm by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(Custom.NONE) }
    var amount by remember { mutableStateOf("15") }
    var unit by remember { mutableIntStateOf(0) }
    var days by remember { mutableIntStateOf(1) }
    var time by remember { mutableStateOf(LocalTime.of(20, 0)) }
    var pickingTime by remember { mutableStateOf(false) }
    val add: (Reminder) -> Unit = { onAdd(listOf(it)) }

    HoraSheet(onDismissRequest = onDismiss) {
        HoraText(stringResource(R.string.reminder_add), style = HoraTheme.type.heading)
        Eyebrow(stringResource(R.string.reminder_before_start))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 10, 30, 60, 24 * 60, 2 * 24 * 60, 3 * 24 * 60, 7 * 24 * 60).forEach { m ->
                val r = Reminder.Before(m, alarm)
                Tag(reminderText(r, formats), selected = false, onClick = { add(r) })
            }
            Tag(stringResource(R.string.reminder_custom_duration), selected = custom == Custom.DURATION, onClick = { custom = Custom.DURATION })
        }
        if (custom == Custom.DURATION) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                HoraTextField(
                    value = amount, onValueChange = { v -> amount = v.filter(Char::isDigit).take(3) },
                    label = stringResource(R.string.reminder_amount),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
                SegmentedChoice(
                    listOf(R.string.unit_minutes, R.string.unit_hours, R.string.unit_days, R.string.unit_weeks).map { stringResource(it) },
                    selectedIndex = unit, onSelect = { unit = it }, modifier = Modifier.weight(1f),
                )
            }
            val minutes = (amount.toIntOrNull() ?: 0) * listOf(1, 60, 24 * 60, 7 * 24 * 60)[unit]
            HoraButton(stringResource(R.string.reminder_add_this), onClick = { add(Reminder.Before(minutes, alarm)) }, enabled = minutes > 0, modifier = Modifier.fillMaxWidth())
        }
        Eyebrow(stringResource(R.string.reminder_at_time))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Reminder.Anchored(1, LocalTime.of(20, 0)), Reminder.Anchored(2, LocalTime.of(9, 0)), Reminder.Anchored(0, LocalTime.of(8, 0))).forEach { preset ->
                val r = preset.copy(alarm = alarm)
                Tag(reminderText(r, formats), selected = false, onClick = { add(r) })
            }
            Tag(stringResource(R.string.reminder_other), selected = custom == Custom.ANCHORED, onClick = { custom = Custom.ANCHORED })
        }
        if (custom == Custom.ANCHORED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(HoraIcons.Previous, stringResource(R.string.reminder_fewer_days), onClick = { days = (days - 1).coerceAtLeast(0) })
                val label = reminderText(Reminder.Anchored(days, time), formats)
                HoraText(label, style = HoraTheme.type.body, modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = label })
                IconAction(HoraIcons.Next, stringResource(R.string.reminder_more_days), onClick = { days = (days + 1).coerceAtMost(60) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoraButton(formats.time(time), onClick = { pickingTime = true }, style = ButtonStyle.Line)
                HoraButton(stringResource(R.string.reminder_add_this), onClick = { add(Reminder.Anchored(days, time, alarm)) }, modifier = Modifier.weight(1f))
            }
        }
        if (sets.isNotEmpty()) {
            Eyebrow(stringResource(R.string.reminder_sets))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sets.forEach { set ->
                    Tag(stringResource(R.string.reminder_set_label, set.name, set.reminders.size), selected = false, onClick = { onAdd(set.reminders) })
                }
            }
        }
        ToggleRow(
            title = stringResource(R.string.reminder_alarm_toggle),
            hint = stringResource(R.string.reminder_alarm_hint),
            checked = alarm,
            onCheckedChange = { alarm = it },
        )
    }
    if (pickingTime) TimePickerSheet(time, formats, onPick = { time = it; pickingTime = false }, onDismiss = { pickingTime = false })
}
