package com.gabrielifrim.hora.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gabrielifrim.hora.core.ui.R as UiR

/**
 * Les icônes au choix. Chacune est un `activity-alias` du manifeste de l'app,
 * pointant sur `MainActivity` ; un seul est activé à la fois. Le nom de classe de
 * l'alias ne se renomme jamais : c'est lui qui retient le choix, côté système.
 */
enum class AppIcon(
    val aliasClass: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val background: Int,
    @param:DrawableRes val foreground: Int,
) {
    CADRAN("com.gabrielifrim.hora.IconCadran", R.string.icon_cadran, UiR.drawable.ic_launcher_background_cadran, UiR.drawable.ic_launcher_foreground),
    OMBRE("com.gabrielifrim.hora.IconOmbre", R.string.icon_ombre, UiR.drawable.ic_launcher_background_ombre, UiR.drawable.ic_launcher_foreground),
    SEMAINE("com.gabrielifrim.hora.IconSemaine", R.string.icon_semaine, UiR.drawable.ic_launcher_background_semaine, UiR.drawable.ic_launcher_foreground),
    MIDI("com.gabrielifrim.hora.IconMidi", R.string.icon_midi, UiR.drawable.ic_launcher_background_midi, UiR.drawable.ic_launcher_foreground_midi),
}

/**
 * Désactiver l'alias qui a lancé Hora arrête l'app, même avec DONT_KILL_APP
 * (constaté sur Android 17). Le choix est donc retenu ici et appliqué quand Hora
 * passe en arrière-plan ([applyPending], appelé par l'activité) : l'arrêt passe
 * inaperçu.
 */
object AppIconSwitcher {
    @Volatile
    private var pending: AppIcon? = null

    /** Le choix en attente, sinon l'alias activé ; Cadran si aucun ne l'est explicitement. */
    fun current(context: Context): AppIcon = pending ?: AppIcon.entries.firstOrNull { icon ->
        when (context.packageManager.getComponentEnabledSetting(component(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.CADRAN
            else -> false
        }
    } ?: AppIcon.CADRAN

    internal fun choose(icon: AppIcon) {
        pending = icon
    }

    /**
     * Active d'abord la nouvelle icône, puis éteint les autres : il y a toujours au
     * moins un point d'entrée au lanceur.
     */
    fun applyPending(context: Context) {
        val icon = pending ?: return
        pending = null
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    // Le paquet porte le suffixe .debug en debug ; la classe de l'alias, jamais.
    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, icon.aliasClass)
}
