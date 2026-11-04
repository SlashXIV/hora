package com.gabrielifrim.hora

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import com.gabrielifrim.hora.core.ui.text.LocalUserSettings
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import com.gabrielifrim.hora.core.ui.theme.paletteFor
import com.gabrielifrim.hora.feature.settings.AppIconSwitcher
import com.gabrielifrim.hora.feature.settings.DayIconAlarm
import com.gabrielifrim.hora.reminders.AppReminderLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Activité unique : elle porte le thème et la navigation, rien d'autre. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var agendaRepository: AgendaRepository

    @Volatile
    private var settingsLoaded = false

    /** Événement à ouvrir, venu d'une notification de rappel (id, occurrence). */
    private val opening = MutableStateFlow<Pair<Long, Long>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Avant super.onCreate. Le démarrage reste affiché jusqu'à la lecture des
        // réglages : l'ambiance du mouvement doit être connue avant la première entrée.
        installSplashScreen().setKeepOnScreenCondition { !settingsLoaded }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Il faut toujours un agenda où ranger un événement : Perso, Taff, Famille au premier lancement.
        lifecycleScope.launch { agendaRepository.ensureDefaults() }
        if (savedInstanceState == null) readOpening(intent)
        setContent {
            val settings by remember { settingsRepository.settings }.collectAsStateWithLifecycle(initialValue = null)
            val loaded = settings ?: return@setContent
            settingsLoaded = true
            val dark = paletteFor(loaded.theme, isSystemInDarkTheme()).isDark
            // Sans cet appel, les icônes des barres système suivraient le téléphone et
            // non l'apparence choisie : icônes claires sur Midi, par exemple.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            HoraTheme(theme = loaded.theme, motion = loaded.motion, haptics = loaded.haptics) {
                CompositionLocalProvider(LocalUserSettings provides loaded) {
                    Box(Modifier.fillMaxSize().background(HoraTheme.colors.background)) {
                        val open by opening.collectAsStateWithLifecycle()
                        HoraApp(opening = open, onOpened = { opening.value = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOpening(intent)
    }

    private fun readOpening(intent: Intent) {
        val id = intent.getLongExtra(AppReminderLauncher.EXTRA_EVENT, -1)
        if (id > 0) opening.value = id to intent.getLongExtra(AppReminderLauncher.EXTRA_OCCURRENCE, -1)
    }

    override fun onStop() {
        super.onStop()
        // Pas pendant une rotation : l'activité revient aussitôt, l'arrêter serait visible.
        if (!isChangingConfigurations) {
            AppIconSwitcher.applyPending(applicationContext)
            DayIconAlarm.arm(applicationContext)
        }
    }
}
