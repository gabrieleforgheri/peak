package com.gabrieleforghieri.peak.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MachineGlyphView
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.util.tr

/**
 * Le schede dell'app, nello stesso ordine di `Features/Root/RootView.swift` (e di `AppNav.Tab`): Allenamento (si apre lì),
 * Macchinari, Me, Amici. Le impostazioni non sono una scheda: si aprono da dentro.
 */
enum class Tab(val route: String, val title: String) {
    workouts("workouts", "Allenamento"),
    machines("machines", "Macchinari"),
    me("me", "Me"),
    friends("friends", "Amici"),
}

@Composable
fun PeakRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { it.route == backStack?.destination?.route } ?: Tab.workouts
    val accent = LocalAccent.current

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = PeakTheme.bgRaised.copy(alpha = 0.92f), contentColor = PeakTheme.textSecondary) {
                for (tab in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
        NavHost(nav, startDestination = Tab.workouts.route, modifier = Modifier.fillMaxSize()) {
            for (tab in Tab.entries) composable(tab.route) { TabPlaceholder(tab, padding) }
        }
    }
}

/** L'icona della scheda. Macchinari usa il pittogramma di un macchinario a pacco pesi, lo stesso disegno che si vede nelle liste. */
@Composable
private fun TabIcon(tab: Tab) {
    when (tab) {
        Tab.machines -> MachineGlyphView(MachineGlyph.latPulldown, tint = LocalContentColorOrWhite(), size = 24.dp)
        else -> Icon(tabVector(tab), null, Modifier.size(24.dp))
    }
}

private fun tabVector(tab: Tab): ImageVector = when (tab) {
    Tab.workouts -> PeakIcons.workouts
    Tab.me -> PeakIcons.me
    Tab.friends -> PeakIcons.friends
    Tab.machines -> PeakIcons.workouts
}

@Composable
private fun LocalContentColorOrWhite(): Color = androidx.compose.material3.LocalContentColor.current

/** Il segnaposto di una scheda: la schermata vera arriva nella fase 3. */
@Composable
private fun TabPlaceholder(tab: Tab, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current)).padding(padding)) {
        Column(Modifier.statusBarsPadding().padding(PeakTheme.padding)) {
            Spacer(Modifier.height(8.dp))
            Text(tr(tab.title), Modifier.appear(0), style = PeakTheme.display(34), color = PeakTheme.textPrimary)
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.appear(1)) {
                Text(tr("In arrivo"), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(tr("Questa scheda è in costruzione."), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary)
            }
        }
    }
}
