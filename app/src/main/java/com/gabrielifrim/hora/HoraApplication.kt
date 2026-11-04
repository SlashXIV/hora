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
import com.gabrielifrim.hora.feature.widget.TodayWidgets
import kotlinx.coroutines.launch

@HiltAndroidApp
class HoraApplication : Application() {
    @Inject lateinit var engine: ReminderEngine
    @Inject lateinit var store: ReminderStore
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var backup: BackupRepository
    @Inject lateinit var prayers: PrayerAlarms
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var digest: DigestAlarms
    @Inject lateinit var widgets: TodayWidgets

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
        // L'alarme quotidienne de sauvegarde se perd à l'arrêt forcé : on la réarme à chaque lancement.
        DatabaseBackup.scheduleAuto(this, backup.state.value.auto)
        // Toute écriture d'événement ou de report réarme la prochaine échéance, sans
        // qu'aucun écran n'ait à y penser. Le premier passage arme au lancement.
        scope.launch { store.changes.debounce(500).collect { engine.run() } }
        // Les notifications de prière suivent les réglages (lieu, angles, choix) dès qu'ils changent.
        scope.launch { settings.settings.map { it.place to it.prayer }.distinctUntilChanged().debounce(500).collect { prayers.run() } }
        // Le résumé du lendemain suit son heure ; armé aussi au lancement.
        scope.launch { settings.settings.map { it.digest }.distinctUntilChanged().collect { digest.run() } }
        // Le widget suit les événements et les réglages (palette, format des heures) ; redessiné aussi au lancement.
        scope.launch { merge(store.changes, settings.settings.map { }).debounce(500).collect { widgets.update() } }
    }
}
