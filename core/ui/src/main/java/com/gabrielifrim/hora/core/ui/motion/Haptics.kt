package com.gabrielifrim.hora.core.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** Réglage « Vibrations » : fourni par le thème, lu par [rememberHaptics]. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Vibrations courtes, toutes au même endroit pour garder un vocabulaire cohérent. */
class Haptics internal constructor(private val view: View, private val enabled: Boolean) {
    /** Changement de choix : vue, option, réglage. */
    fun tick() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Action confirmée : événement créé, rappel coché. */
    fun confirm() {
        if (!enabled) return
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
