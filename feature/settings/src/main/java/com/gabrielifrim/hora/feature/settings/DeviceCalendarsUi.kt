package com.gabrielifrim.hora.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.gabrielifrim.hora.core.domain.model.Agenda
import com.gabrielifrim.hora.core.ui.component.AgendaMark
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.Eyebrow
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraSheet
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.component.Tag
import com.gabrielifrim.hora.core.ui.component.ToggleRow
import com.gabrielifrim.hora.core.ui.component.ToggleSwitch
import com.gabrielifrim.hora.core.ui.text.agendaColorKey
import com.gabrielifrim.hora.core.ui.text.color
import com.gabrielifrim.hora.core.ui.text.labelRes
import com.gabrielifrim.hora.core.ui.theme.AgendaColorKey
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/**
 * Réglages › Agendas › Agendas du téléphone : le pont vers les agendas d'Android, en
 * lecture seule. Rien n'est lu avant le geste ; la seule permission demandée est la lecture.
 */
@Composable
internal fun DeviceCalendarsSection(
    enabled: Boolean,
    agendas: List<Agenda>,
    duplicates: Int,
    writable: Boolean,
    onEnable: (Boolean) -> Unit,
    onWritable: (Boolean) -> Unit,
    onVisible: (Agenda, Boolean) -> Unit,
    onColor: (Agenda, String) -> Unit,
) {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    var coloring by remember { mutableStateOf<Agenda?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refused = !granted
        if (granted) onEnable(true)
    }
    var writeRefused by remember { mutableStateOf(false) }
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        writeRefused = !granted
        if (granted) onWritable(true)
    }
    Eyebrow(stringResource(R.string.device_title), Modifier.padding(top = 20.dp, bottom = 4.dp))
    if (!enabled) {
        val rule = HoraTheme.colors.rule
        // Tireté : ce qui vient d'ailleurs, comme les événements d'Android dans les vues.
        Column(
            Modifier.fillMaxWidth().drawBehind {
                drawRoundRect(rule, cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HoraText(stringResource(R.string.device_explain), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
            if (refused) {
                HoraText(stringResource(R.string.device_refused), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.error)
                HoraButton(
                    stringResource(R.string.device_open_settings),
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { /* réglages absents : rien à ouvrir */ }
                    },
                    style = ButtonStyle.Line,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HoraButton(
                stringResource(R.string.device_enable),
                onClick = {
                    if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) onEnable(true)
                    else ask.launch(Manifest.permission.READ_CALENDAR)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    if (agendas.isEmpty()) HoraText(stringResource(R.string.device_none), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
    agendas.groupBy { it.account.orEmpty() }.forEach { (account, list) ->
        HoraText(account, style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2, modifier = Modifier.padding(top = 8.dp))
        list.forEach { agenda ->
            val key = agendaColorKey(agenda.colorKey)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.device_color), role = Role.Button) { coloring = agenda }.padding(vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AgendaMark(key.shape, key.color(), size = 14.dp)
                        HoraText(agenda.name, style = HoraTheme.type.body)
                    }
                    HoraText(stringResource(if (agenda.hidden) R.string.agenda_hidden else if (agenda.readOnly) R.string.device_agenda_hint else R.string.device_agenda_writable_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2, modifier = Modifier.padding(start = 26.dp))
                }
                ToggleSwitch(checked = !agenda.hidden, onCheckedChange = { onVisible(agenda, it) }, label = stringResource(R.string.agenda_visible, agenda.name))
            }
            Rule()
        }
    }
    if (duplicates > 0) {
        HoraText(pluralStringResource(R.plurals.device_duplicates, duplicates, duplicates), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2, modifier = Modifier.padding(top = 8.dp))
    }
    ToggleRow(
        title = stringResource(R.string.device_write),
        hint = stringResource(if (writeRefused) R.string.device_write_refused else R.string.device_write_hint),
        checked = writable,
        onCheckedChange = { on ->
            when {
                !on -> onWritable(false)
                context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED -> onWritable(true)
                else -> askWrite.launch(Manifest.permission.WRITE_CALENDAR)
            }
        },
        modifier = Modifier.padding(top = 8.dp),
    )
    HoraButton(stringResource(R.string.device_disable), onClick = { onEnable(false) }, style = ButtonStyle.Line, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    HoraText(stringResource(R.string.device_disable_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2, modifier = Modifier.padding(top = 4.dp))

    coloring?.let { agenda ->
        HoraSheet(onDismissRequest = { coloring = null }) {
            HoraText(agenda.name, style = HoraTheme.type.heading)
            HoraText(stringResource(R.string.device_color_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AgendaColorKey.entries.forEach { key ->
                    Tag(
                        stringResource(key.labelRes()),
                        selected = agenda.colorKey == key.name,
                        onClick = { onColor(agenda, key.name); coloring = null },
                        leading = { AgendaMark(key.shape, key.color(), size = 10.dp) },
                    )
                }
            }
        }
    }
}
