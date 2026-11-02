package com.gabrielifrim.hora.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gabrielifrim.hora.core.domain.repository.ReminderSet
import com.gabrielifrim.hora.core.domain.repository.ReminderSetRepository
import com.gabrielifrim.hora.core.reminders.BatteryOptimization
import com.gabrielifrim.hora.core.reminders.ReminderPermissions
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraIcons
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.IconAction
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(private val sets: ReminderSetRepository) : ViewModel() {
    val reminderSets: StateFlow<List<ReminderSet>> = sets.sets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { sets.delete(id) }
    }
}

/**
 * « Fiabilité des rappels » : tout ce qui peut empêcher un rappel de sonner, dit en
 * clair, avec le bouton qui ouvre le bon écran. Relu à chaque retour dans l'app.
 */
@Composable
internal fun ReminderReliability() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    // Lu à chaque passage : `refresh` force la relecture après un retour des réglages Android.
    val notify = refresh.let { ReminderPermissions.canNotify(context) }
    val exact = refresh.let { ReminderPermissions.canScheduleExact(context) }
    val fullScreen = refresh.let { ReminderPermissions.canFullScreen(context) }
    val battery = refresh.let { BatteryOptimization.isUnrestricted(context) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HoraText(stringResource(R.string.reliability_hint), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        Status(stringResource(R.string.reliability_notifications), notify, stringResource(R.string.reliability_allow)) {
            if (Build.VERSION.SDK_INT >= 33) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                BatteryOptimization.open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
        if (Build.VERSION.SDK_INT in 31..32) {
            Status(stringResource(R.string.reliability_exact), exact, stringResource(R.string.reliability_allow)) {
                BatteryOptimization.open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            Status(stringResource(R.string.reliability_full_screen), fullScreen, stringResource(R.string.reliability_allow)) {
                BatteryOptimization.open(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()))
            }
        }
        Status(stringResource(R.string.reliability_battery), battery, stringResource(R.string.reliability_unrestrict)) {
            BatteryOptimization.open(context, BatteryOptimization.requestIntent(context))
        }
        // MIUI et les autres surcouches : démarrage automatique et économie de batterie propres au fabricant.
        HoraButton(
            stringResource(R.string.reliability_manufacturer),
            onClick = { BatteryOptimization.open(context, BatteryOptimization.manufacturerIntent(context)) },
            style = ButtonStyle.Text,
        )
    }
}

@Composable
private fun Status(title: String, ok: Boolean, action: String, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            HoraText(title, style = HoraTheme.type.body)
            HoraText(
                stringResource(if (ok) R.string.reliability_ok else R.string.reliability_blocked),
                style = HoraTheme.type.bodySmall,
                color = if (ok) HoraTheme.colors.success else HoraTheme.colors.warning,
            )
        }
        if (!ok) HoraButton(action, onClick = onFix, style = ButtonStyle.Line)
    }
}

/** Les jeux de rappels enregistrés depuis l'éditeur, qu'on peut supprimer ici. */
@Composable
internal fun ReminderSets(viewModel: ReminderSettingsViewModel = hiltViewModel()) {
    val sets by viewModel.reminderSets.collectAsStateWithLifecycle()
    Column {
        if (sets.isEmpty()) {
            HoraText(stringResource(R.string.sets_empty), style = HoraTheme.type.bodySmall, color = HoraTheme.colors.ink2)
        }
        sets.forEach { set ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                HoraText(stringResource(R.string.set_line, set.name, set.reminders.size), style = HoraTheme.type.body, modifier = Modifier.weight(1f))
                IconAction(HoraIcons.Close, stringResource(R.string.set_delete, set.name), onClick = { viewModel.delete(set.id) }, tint = HoraTheme.colors.ink2)
            }
        }
    }
}
