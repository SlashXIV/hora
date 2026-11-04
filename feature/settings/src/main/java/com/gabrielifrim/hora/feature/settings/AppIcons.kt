package com.gabrielifrim.hora.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gabrielifrim.hora.core.ui.R as UiR
import com.gabrielifrim.hora.core.ui.theme.DayIconForegrounds
import java.time.LocalDate

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

    /** Le chiffre du jour : un alias par jour du mois (IconDay01 à IconDay31), basculé à minuit. */
    DAY("com.gabrielifrim.hora.IconDay", R.string.icon_day, UiR.drawable.ic_launcher_background_ombre, UiR.drawable.ic_launcher_foreground_day_01),
    ;

    /** L'avant-plan montré dans les réglages ; celui du jour pour l'icône du jour. */
    fun foregroundOn(date: LocalDate): Int = if (this == DAY) DayIconForegrounds[date.dayOfMonth - 1] else foreground
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
        aliases(icon).any { alias ->
            when (context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, alias))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.CADRAN
                else -> false
            }
        }
    } ?: AppIcon.CADRAN

    internal fun choose(icon: AppIcon) {
        pending = icon
    }

    /**
     * Active d'abord la nouvelle icône, puis éteint les autres : il y a toujours au
     * moins un point d'entrée au lanceur.
     */
    fun applyPending(context: Context, today: LocalDate = LocalDate.now()) {
        val icon = pending ?: return
        pending = null
        val pm = context.packageManager
        val wanted = alias(icon, today)
        // N'écrit que ce qui change : 35 alias à réécrire chaque nuit feraient clignoter le lanceur.
        fun set(alias: String, on: Boolean) {
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val component = ComponentName(context.packageName, alias)
            val now = pm.getComponentEnabledSetting(component)
            val isOn = now == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (now == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == AppIcon.CADRAN.aliasClass)
            if (isOn != on) pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
        set(wanted, true)
        AppIcon.entries.flatMap { aliases(it) }.filter { it != wanted }.forEach { set(it, false) }
    }

    /**
     * Minuit : l'icône du jour passe au chiffre du nouveau jour. Hora au premier plan, le
     * changement attend qu'elle passe en arrière-plan (sinon l'app s'arrêterait sous les yeux).
     */
    fun nextDay(context: Context, inForeground: Boolean) {
        if (current(context) != AppIcon.DAY) return
        pending = AppIcon.DAY
        if (!inForeground) applyPending(context)
    }

    // Le paquet porte le suffixe .debug en debug ; la classe de l'alias, jamais.
    private fun alias(icon: AppIcon, date: LocalDate): String =
        if (icon == AppIcon.DAY) "%s%02d".format(icon.aliasClass, date.dayOfMonth) else icon.aliasClass

    private fun aliases(icon: AppIcon): List<String> =
        if (icon == AppIcon.DAY) (1..31).map { "%s%02d".format(icon.aliasClass, it) } else listOf(icon.aliasClass)
}
