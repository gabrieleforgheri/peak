package com.gabrieleforghieri.peak.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MachineGlyphView
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.ui.friends.friendsDestinations
import com.gabrieleforghieri.peak.ui.machines.machinesDestinations
import com.gabrieleforghieri.peak.ui.me.meDestinations
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.PeakEnvironment
import com.gabrieleforghieri.peak.ui.nav.RealPeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes
import com.gabrieleforghieri.peak.ui.nav.Tab
import com.gabrieleforghieri.peak.ui.settings.settingsDestinations
import com.gabrieleforghieri.peak.ui.start.GoalOverlay
import com.gabrieleforghieri.peak.ui.start.TutorialOverlay
import com.gabrieleforghieri.peak.ui.start.WelcomeOverlay
import com.gabrieleforghieri.peak.ui.start.startDestinations
import com.gabrieleforghieri.peak.ui.workouts.PendingSummaryOverlay
import com.gabrieleforghieri.peak.ui.workouts.workoutsDestinations
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Il guscio dell'app (`Features/Root/RootView.swift`): la barra delle schede, la navigazione e gli agganci sopra tutto. **Non si
 * modifica più dopo la base della fase 3** (come `ui/nav/`): ogni area vive nella sua `<Area>Destinations` (`ui/<area>/`), e gli agganci
 * sono `WelcomeOverlay`, `TutorialOverlay`, `GoalOverlay` (start) e `PendingSummaryOverlay` (workouts). Vedi `android/AREE.md`.
 */
@Composable
fun PeakRoot() {
    PeakEnvironment(LocalContext.current.applicationContext as PeakApplication) { PeakShell() }
}

@Composable
private fun PeakShell() {
    val controller = rememberNavController()
    val backStack by controller.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val tabFlow = remember { MutableStateFlow(Tab.workouts) }
    // le schermate spinte (sessione, dettaglio…) appartengono alla scheda da cui sono partite: la barra tiene quella
    LaunchedEffect(destination) {
        Tab.entries.firstOrNull { destination?.hasRoute(it.home::class) == true }?.let { tabFlow.value = it }
    }
    val nav = remember(controller) { RealPeakNav(controller, tabFlow) }
    val current by tabFlow.collectAsState()
    val accent = LocalAccent.current
    // le impostazioni sono a schermo intero, senza barra (su iPhone un `fullScreenCover`)
    val showBar = destination?.hasRoute<Routes.Settings>() != true

    CompositionLocalProvider(LocalPeakNav provides nav) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBar) NavigationBar(containerColor = PeakTheme.bgRaised.copy(alpha = 0.92f), contentColor = PeakTheme.textSecondary) {
                    for (tab in Tab.entries) {
                        NavigationBarItem(
                            selected = tab == current,
                            onClick = { nav.goTab(tab) },
                            icon = { TabIcon(tab) },
                            label = { Text(tr(tab.title), style = PeakTheme.rounded(11, FontWeight.SemiBold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accent, selectedTextColor = accent,
                                unselectedIconColor = PeakTheme.textSecondary, unselectedTextColor = PeakTheme.textSecondary,
                                indicatorColor = accent.copy(alpha = 0.16f),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            // lo spazio della barra vale per tutte le schermate; senza barra (impostazioni) se lo prende la schermata
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                NavHost(controller, startDestination = Routes.WorkoutsHome, modifier = Modifier.fillMaxSize()) {
                    workoutsDestinations(nav)
                    machinesDestinations(nav)
                    meDestinations(nav)
                    friendsDestinations(nav)
                    startDestinations(nav)
                    settingsDestinations(nav)
                }
                WelcomeOverlay()
                TutorialOverlay()
                GoalOverlay()
                PendingSummaryOverlay()
            }
        }
    }
}

/** L'icona della scheda. Macchinari usa il pittogramma di un macchinario a pacco pesi, lo stesso disegno che si vede nelle liste. */
@Composable
private fun TabIcon(tab: Tab) {
    when (tab) {
        Tab.machines -> MachineGlyphView(MachineGlyph.latPulldown, tint = LocalContentColor.current, size = 24.dp)
        else -> Icon(tabVector(tab), null, Modifier.size(24.dp))
    }
}

private fun tabVector(tab: Tab): ImageVector = when (tab) {
    Tab.workouts -> PeakIcons.workouts
    Tab.me -> PeakIcons.me
    Tab.friends -> PeakIcons.friends
    Tab.machines -> PeakIcons.workouts
}
