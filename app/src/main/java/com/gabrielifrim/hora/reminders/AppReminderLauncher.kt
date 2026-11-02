package com.gabrielifrim.hora.reminders

import android.content.Context
import android.content.Intent
import com.gabrielifrim.hora.MainActivity
import com.gabrielifrim.hora.core.reminders.ReminderLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Les écrans que le moteur de rappels ouvre : la fiche d'un événement, l'alarme plein écran. */
class AppReminderLauncher @Inject constructor() : ReminderLauncher {
    override fun openEvent(context: Context, eventId: Long, occurrence: Long): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_EVENT, eventId)
            .putExtra(EXTRA_OCCURRENCE, occurrence)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    override fun fullScreenAlarm(context: Context, eventId: Long, occurrence: Long): Intent =
        Intent(context, AlarmActivity::class.java)
            .putExtra(EXTRA_EVENT, eventId)
            .putExtra(EXTRA_OCCURRENCE, occurrence)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    override fun openApp(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val EXTRA_EVENT = "hora.event"
        const val EXTRA_OCCURRENCE = "hora.occurrence"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReminderLauncherModule {
    @Binds
    abstract fun launcher(impl: AppReminderLauncher): ReminderLauncher
}
