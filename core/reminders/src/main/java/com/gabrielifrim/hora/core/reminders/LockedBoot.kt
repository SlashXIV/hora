package com.gabrielifrim.hora.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Avant le premier déverrouillage qui suit un redémarrage (mise à jour de nuit), la base
 * et les réglages sont encore chiffrés : le moteur ne peut pas tourner. Il laisse donc la
 * prochaine échéance dans le stockage lisible dès le démarrage ; ces deux récepteurs la
 * réarment et, si elle tombe avant le déverrouillage, présentent un rappel sans titre.
 * Au déverrouillage, le moteur reprend la main et présente le vrai rappel.
 */
internal object LockedBoot {
    private const val PREFS = "prochaine_echeance"
    private const val AT = "at"
    private const val ALARM = "alarm"
    const val NOTIFICATION_ID = 0x4C42

    private fun prefs(context: Context) = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remember(context: Context, at: Long?, alarm: Boolean) = prefs(context).edit().putLong(AT, at ?: -1).putBoolean(ALARM, alarm).apply()

    fun next(context: Context): Pair<Long, Boolean>? = prefs(context).let { p -> p.getLong(AT, -1).takeIf { it >= 0 }?.let { it to p.getBoolean(ALARM, false) } }

    fun locked(context: Context): Boolean = context.getSystemService(UserManager::class.java)?.isUserUnlocked == false
}

/** Démarrage avant déverrouillage : réarme l'échéance gardée par le moteur. */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val (at, alarm) = LockedBoot.next(context) ?: return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, LockedAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Échéance passée pendant que le téléphone était éteint : tout de suite.
        val time = maxOf(at, System.currentTimeMillis())
        val exact = ReminderPermissions.canScheduleExact(context)
        when {
            alarm && exact -> {
                val show = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pending)
            }
            exact -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
            else -> manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
        }
    }
}

/** L'échéance tombe avant le déverrouillage : un rappel sans titre, la base étant illisible. */
class LockedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Déjà déverrouillé : le moteur a été relancé au démarrage et présente le vrai rappel.
        if (!LockedBoot.locked(context) || !ReminderPermissions.canNotify(context)) return
        val alarm = LockedBoot.next(context)?.second == true
        val notification = NotificationCompat.Builder(context, if (alarm) ReminderNotifier.CHANNEL_ALARMS else ReminderNotifier.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.locked_title))
            .setContentText(context.getString(R.string.locked_text))
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        @Suppress("MissingPermission") // vérifiée par canNotify
        NotificationManagerCompat.from(context).notify(LockedBoot.NOTIFICATION_ID, notification)
    }
}
