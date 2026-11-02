package com.gabrielifrim.hora

import android.app.Application
import com.gabrielifrim.hora.core.data.backup.DatabaseBackup
import com.gabrielifrim.hora.core.domain.repository.BackupRepository
import com.gabrielifrim.hora.core.domain.repository.ReminderStore
import com.gabrielifrim.hora.core.reminders.ReminderEngine
import com.gabrielifrim.hora.core.reminders.ReminderNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@HiltAndroidApp
class HoraApplication : Application() {
    @Inject lateinit var engine: ReminderEngine
    @Inject lateinit var store: ReminderStore
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var backup: BackupRepository

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
    }
}
