package com.gabrielifrim.hora

import android.app.Application
import com.gabrielifrim.hora.core.data.backup.DatabaseBackup
import com.gabrielifrim.hora.core.domain.repository.BackupRepository
import com.gabrielifrim.hora.core.domain.repository.ReminderStore
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import com.gabrielifrim.hora.core.reminders.DigestAlarms
import com.gabrielifrim.hora.core.reminders.PrayerAlarms
import com.gabrielifrim.hora.core.reminders.ReminderEngine
import com.gabrielifrim.hora.core.reminders.ReminderNotifier
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.os.UserManager
import androidx.core.content.ContextCompat
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import com.gabrielifrim.hora.feature.widget.HoraWidgets
import com.gabrielifrim.hora.feature.settings.DayIconAlarm
import com.gabrielifrim.hora.core.domain.repository.DeviceCalendarRepository
import kotlinx.coroutines.launch

@HiltAndroidApp
class HoraApplication : Application() {
    @Inject lateinit var engine: Lazy<ReminderEngine>
    @Inject lateinit var store: Lazy<ReminderStore>
    @Inject lateinit var notifier: Lazy<ReminderNotifier>
    @Inject lateinit var backup: Lazy<BackupRepository>
    @Inject lateinit var prayers: Lazy<PrayerAlarms>
    @Inject lateinit var settings: Lazy<SettingsRepository>
    @Inject lateinit var digest: Lazy<DigestAlarms>
    @Inject lateinit var widgets: Lazy<HoraWidgets>
    @Inject lateinit var device: Lazy<DeviceCalendarRepository>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Avant le premier déverrouillage, base et réglages sont chiffrés : seuls les récepteurs de LockedBoot
        // tournent. Au déverrouillage, on laisse le processus repartir à neuf (BOOT_COMPLETED suit).
        if (getSystemService(UserManager::class.java)?.isUserUnlocked == false) {
            val unlocked = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = Process.killProcess(Process.myPid())
            }
            ContextCompat.registerReceiver(this, unlocked, IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED)
            return
        }
        val engine = engine.get()
        val store = store.get()
        val backup = backup.get()
        val prayers = prayers.get()
        val settings = settings.get()
        val digest = digest.get()
        val widgets = widgets.get()
        val device = device.get()
        notifier.get().ensureChannels()
        // L'alarme quotidienne de sauvegarde se perd à l'arrêt forcé : on la réarme à chaque lancement.
        DatabaseBackup.scheduleAuto(this, backup.state.value.auto)
        // L'icône du jour : chiffre rattrapé si minuit a été manqué, alarme de minuit réarmée.
        DayIconAlarm.catchUp(this)
        // Toute écriture d'événement ou de report réarme la prochaine échéance, sans
        // qu'aucun écran n'ait à y penser. Le premier passage arme au lancement.
        scope.launch { store.changes.debounce(500).collect { engine.run() } }
        // Les notifications de prière suivent les réglages (lieu, angles, choix) dès qu'ils changent.
        scope.launch { settings.settings.map { it.place to it.prayer }.distinctUntilChanged().debounce(500).collect { prayers.run() } }
        // Le résumé du lendemain suit son heure ; armé aussi au lancement.
        scope.launch { settings.settings.map { it.digest }.distinctUntilChanged().collect { digest.run() } }
        // Les widgets suivent les événements (d'Hora et d'Android) et les réglages (palette, format des heures) ; redessiné aussi au lancement.
        scope.launch { merge(store.changes, settings.settings.map { }, device.agendas.map { }).debounce(500).collect { widgets.update() } }
    }
}
