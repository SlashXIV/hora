package com.gabrielifrim.hora

import com.gabrielifrim.hora.core.ui.component.FitText
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.navigation.toRoute
import com.gabrielifrim.hora.core.ui.component.HoraIcon
import com.gabrielifrim.hora.core.ui.component.HoraIcons
import com.gabrielifrim.hora.core.ui.component.HoraText
import com.gabrielifrim.hora.core.ui.component.Rule
import com.gabrielifrim.hora.core.ui.motion.LocalMotionLevel
import com.gabrielifrim.hora.core.ui.motion.Motion
import com.gabrielifrim.hora.core.ui.motion.rememberHaptics
import com.gabrielifrim.hora.core.ui.theme.HoraTheme
import com.gabrielifrim.hora.feature.calendar.CalendarScreen
import com.gabrielifrim.hora.feature.calendar.SearchScreen
import com.gabrielifrim.hora.feature.event.EventDetailScreen
import com.gabrielifrim.hora.feature.event.EventEditorScreen
import com.gabrielifrim.hora.feature.settings.AgendasScreen
import com.gabrielifrim.hora.feature.settings.SettingsScreen
import com.gabrielifrim.hora.feature.today.TodayScreen
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable private data object TodayRoute
@Serializable private data object CalendarRoute
@Serializable private data object SearchRoute
@Serializable private data object SettingsRoute
@Serializable private data object AgendasRoute
@Serializable private data class EventRoute(val id: Long)

/** Éditeur : id > 0 modifie, duplicateOf > 0 copie, sinon crée au jour epochDay (minute < 0 : heure par défaut). */
@Serializable private data class EditRoute(val id: Long = 0, val duplicateOf: Long = 0, val epochDay: Long = 0, val minute: Int = -1)

private enum class Tab(val route: Any, val label: Int, val icon: ImageVector) {
    TODAY(TodayRoute, R.string.tab_today, HoraIcons.Today),
    CALENDAR(CalendarRoute, R.string.tab_calendar, HoraIcons.Agenda),
    SEARCH(SearchRoute, R.string.tab_search, HoraIcons.Search),
    SETTINGS(SettingsRoute, R.string.tab_settings, HoraIcons.Settings),
}

@Composable
fun HoraApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val haptics = rememberHaptics()
    // La transition signature (l'ombre qui tourne) arrive avec les moments ; d'ici là,
    // un fondu calme, et rien du tout si les animations sont coupées.
    val fadeMs = if (LocalMotionLevel.current.animates) Motion.CALM_MS else 0
    val currentTab = Tab.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.route::class) == true }
    val create: (LocalDate, Int?) -> Unit = { date, minute -> nav.navigate(EditRoute(epochDay = date.toEpochDay(), minute = minute ?: -1)) }
    val open: (Long) -> Unit = { id -> nav.navigate(EventRoute(id)) }

    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = TodayRoute,
            modifier = Modifier.weight(1f),
            enterTransition = { fadeIn(tween(fadeMs)) },
            exitTransition = { fadeOut(tween(fadeMs)) },
        ) {
            composable<TodayRoute> { TodayScreen(onOpenEvent = open, onCreate = { create(it, null) }) }
            composable<CalendarRoute> { CalendarScreen(onOpenEvent = open, onCreate = create) }
            composable<SearchRoute> { SearchScreen(onOpenEvent = open, modifier = Modifier.statusBarsPadding()) }
            composable<SettingsRoute> { SettingsScreen(onOpenAgendas = { nav.navigate(AgendasRoute) }) }
            composable<AgendasRoute> { AgendasScreen(onBack = nav::popBackStack) }
            composable<EventRoute> { back ->
                val route = back.toRoute<EventRoute>()
                EventDetailScreen(
                    eventId = route.id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(EditRoute(id = it)) },
                    onDuplicate = { nav.navigate(EditRoute(duplicateOf = it)) },
                )
            }
            composable<EditRoute> { back ->
                val route = back.toRoute<EditRoute>()
                EventEditorScreen(
                    eventId = route.id,
                    duplicateOf = route.duplicateOf,
                    date = if (route.id == 0L && route.duplicateOf == 0L) LocalDate.ofEpochDay(route.epochDay) else LocalDate.now(),
                    minute = route.minute.takeIf { it >= 0 },
                    onClose = { nav.popBackStack() },
                    onSaved = { savedId ->
                        // Après une création, la fiche remplace l'éditeur ; après une modification, on revient à la fiche.
                        if (route.id == 0L) {
                            nav.navigate(EventRoute(savedId)) { popUpTo<EditRoute> { inclusive = true } }
                        } else {
                            nav.popBackStack()
                        }
                    },
                )
            }
        }
        // Les onglets restent sous les écrans principaux, pas sous la fiche ni l'éditeur.
        if (currentTab != null) {
            TabBar(
                selected = currentTab,
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
                    FitText(stringResource(tab.label), style = HoraTheme.type.bodySmall, color = if (on) colors.ink else colors.ink2, minFontSize = 8.sp)
                    Box(Modifier.width(18.dp).height(2.dp).background(if (on) colors.accent else colors.background, RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}
