package com.gabrielifrim.hora.feature.settings

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/**
 * L'icône du jour change de chiffre à minuit : une alarme approchée, sans réveil (personne ne
 * regarde l'écran d'accueil téléphone éteint), réarmée à chaque passage et au lancement.
 *
 * shortcut: rien ne la réarme après un redémarrage tant que Hora n'est pas ouverte ; le chiffre
 * se corrige alors à la première ouverture. Brancher RescheduleReceiver si cela se voit.
 */
object DayIconAlarm {
    fun arm(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, DayIconReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (AppIconSwitcher.current(context) != AppIcon.DAY) return manager.cancel(pending)
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.set(AlarmManager.RTC, midnight, pending)
    }

    /** Au lancement : rattrape un minuit manqué, appliqué quand Hora passe en arrière-plan. */
    fun catchUp(context: Context) {
        AppIconSwitcher.nextDay(context, inForeground = true)
        arm(context)
    }
}

class DayIconReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
        AppIconSwitcher.nextDay(context, inForeground = state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        DayIconAlarm.arm(context)
    }
}
