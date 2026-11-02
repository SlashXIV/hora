package com.gabrielifrim.hora

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gabrielifrim.hora.core.domain.model.UserSettings
import com.gabrielifrim.hora.core.ui.component.HoraIcon
import com.gabrielifrim.hora.core.ui.component.HoraIcons
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.motion.LocalMotionLevel
import com.gabrielifrim.hora.core.ui.motion.Motion
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import com.gabrielifrim.hora.feature.settings.SettingsScreen
import com.gabrielifrim.hora.feature.today.TodayScreen
import kotlinx.serialization.Serializable

@Serializable private data object TodayRoute
@Serializable private data object SettingsRoute

private enum class Tab(val route: Any, val label: Int, val icon: ImageVector) {
    TODAY(TodayRoute, R.string.tab_today, HoraIcons.Today),
    SETTINGS(SettingsRoute, R.string.tab_settings, HoraIcons.Settings),
}

@Composable
fun HoraApp(settings: UserSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val haptics = rememberHaptics()
    // La transition signature (l'ombre qui tourne) arrive avec les moments ; d'ici là,
    // un fondu calme, et rien du tout si les animations sont coupées.
    val fadeMs = if (LocalMotionLevel.current.animates) Motion.CALM_MS else 0

    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = TodayRoute,
            modifier = Modifier.weight(1f),
            enterTransition = { fadeIn(tween(fadeMs)) },
            exitTransition = { fadeOut(tween(fadeMs)) },
        ) {
            composable<TodayRoute> { TodayScreen(firstDayOfWeek = settings.firstDayOfWeek) }
            composable<SettingsRoute> { SettingsScreen() }
        }
        TabBar(
            selected = Tab.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.route::class) == true } ?: Tab.TODAY,
            onSelect = { tab ->
                haptics.tick()
                nav.navigate(tab.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

/** Barre d'onglets maison : libellé toujours visible, l'onglet actif souligné d'accent. */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = HoraTheme.colors
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
        Rule()
        Row(Modifier.fillMaxWidth().selectableGroup().padding(vertical = 4.dp)) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = on, role = Role.Tab) { if (!on) onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    HoraIcon(tab.icon, contentDescription = null, tint = if (on) colors.accent else colors.ink2, size = 22.dp)
                    HoraText(
                        stringResource(tab.label),
                        style = HoraTheme.type.bodySmall,
                        color = if (on) colors.ink else colors.ink2,
                    )
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(if (on) colors.accent else colors.background, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
}
