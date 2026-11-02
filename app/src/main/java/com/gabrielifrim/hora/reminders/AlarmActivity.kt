package com.gabrielifrim.hora.reminders

import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.gabrielifrim.hora.R
import com.gabrielifrim.hora.core.domain.model.Event
import com.gabrielifrim.hora.core.domain.model.Timing
import com.gabrielifrim.hora.core.domain.repository.ReminderStore
import com.gabrielifrim.hora.core.domain.model.ThemePreference
import com.gabrielifrim.hora.core.reminders.ReminderNotifier
import com.gabrielifrim.hora.core.reminders.SnoozeReceiver
import com.gabrielifrim.hora.core.ui.component.ButtonStyle
import com.gabrielifrim.hora.core.ui.component.HoraButton
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.text.rememberFormats
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * L'alarme plein écran : allume l'écran par-dessus le verrouillage, sonne comme un
 * réveil (canal alarme, volume des alarmes) et vibre jusqu'à « Arrêter » ou
 * « Reporter ». Elle s'arrête d'elle-même après 10 minutes.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var store: ReminderStore
    @Inject lateinit var notifier: ReminderNotifier

    private var ringtone: Ringtone? = null
    private var event by mutableStateOf<Event?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // Android 8.0 : l'ancienne manière, par les drapeaux de la fenêtre.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val eventId = intent.getLongExtra(AppReminderLauncher.EXTRA_EVENT, -1)
        val occurrence = intent.getLongExtra(AppReminderLauncher.EXTRA_OCCURRENCE, -1)
        lifecycleScope.launch { event = store.event(eventId) }
        ring()
        lifecycleScope.launch {
            delay(AUTO_STOP_MS)
            stop(eventId, occurrence)
        }
        setContent {
            // Toujours Nocturne : un écran qui s'allume la nuit ne doit pas éblouir.
            HoraTheme(theme = ThemePreference.NOCTURNE) {
                val formats = rememberFormats()
                val zone = ZoneId.systemDefault()
                val e = event
                // L'heure affichée avance avec la minute, sans animation.
                var now by remember { mutableStateOf(LocalTime.now()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(60_000L - (System.currentTimeMillis() % 60_000L))
                        now = LocalTime.now()
                    }
                }
                Column(
                    Modifier.fillMaxSize().background(HoraTheme.colors.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.height(32.dp))
                        HoraText(stringResource(R.string.alarm_eyebrow), style = HoraTheme.type.eyebrow, color = HoraTheme.colors.ink2)
                        HoraText(formats.time(now), style = HoraTheme.type.figure.copy(fontSize = HoraTheme.type.figure.fontSize * 2.4f))
                        if (e != null) {
                            HoraText(
                                e.title, style = HoraTheme.type.poster.copy(fontSize = HoraTheme.type.heading.fontSize * 1.4f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            )
                            val start = (e.timing as? Timing.Timed)?.start?.atZone(zone)?.toLocalTime()
                            if (start != null) HoraText(formats.time(start), style = HoraTheme.type.hour, color = HoraTheme.colors.ink2)
                        }
                    }
                    Gnomon()
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HoraButton(stringResource(R.string.alarm_stop), onClick = { stop(eventId, occurrence) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
                        HoraButton(
                            stringResource(R.string.alarm_snooze),
                            onClick = {
                                sendBroadcast(
                                    Intent(this@AlarmActivity, SnoozeReceiver::class.java).setAction(SnoozeReceiver.TEN_MINUTES)
                                        .putExtra(SnoozeReceiver.EXTRA_EVENT, eventId)
                                        .putExtra(SnoozeReceiver.EXTRA_OCCURRENCE, occurrence)
                                        .putExtra(SnoozeReceiver.EXTRA_ALARM, true),
                                )
                                silence()
                                finish()
                            },
                            style = ButtonStyle.Line,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        )
                    }
                }
            }
        }
    }

    private fun ring() {
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
        vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    private fun silence() {
        ringtone?.stop()
        vibrator()?.cancel()
    }

    private fun stop(eventId: Long, occurrence: Long) {
        silence()
        notifier.cancel(eventId, occurrence)
        finish()
    }

    override fun onDestroy() {
        silence()
        super.onDestroy()
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
    }

    private companion object {
        const val AUTO_STOP_MS = 10 * 60_000L
    }
}

/** Le style du cadran et son ombre, rappel de l'icône, entre l'heure et les boutons. */
@androidx.compose.runtime.Composable
private fun Gnomon() {
    val colors = HoraTheme.colors
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val base = size.height - 4.dp.toPx()
        val cx = size.width / 2
        drawLine(colors.accent, Offset(cx - 90.dp.toPx(), base), Offset(cx + 90.dp.toPx(), base), 2.dp.toPx())
        drawPath(Path().apply { moveTo(cx, base); lineTo(cx - 70.dp.toPx(), base - 28.dp.toPx()); lineTo(cx - 70.dp.toPx(), base - 20.dp.toPx()); close() }, colors.shadow)
        drawLine(colors.ink, Offset(cx, base), Offset(cx, base - 48.dp.toPx()), 3.dp.toPx())
    }
}
