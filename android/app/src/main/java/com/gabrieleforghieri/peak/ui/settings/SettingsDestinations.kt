package com.gabrieleforghieri.peak.ui.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Impostazioni: `Features/Settings/` senza Watch (categorie: Generale, Allenamento, Account yG, App Salute, Backup, Dati). Proprietario:
 * l'area **settings**; `PeakRoot` chiama solo questa funzione. La barra in basso è nascosta su questa rotta (su iPhone è un `fullScreenCover`).
 *
 * Le sottopagine si gestiscono dentro [SettingsScreen] (con `category` si apre direttamente quella), con il proprio stato e
 * `BackHandler`; `nav.back()` chiude le impostazioni.
 */
fun NavGraphBuilder.settingsDestinations(nav: PeakNav) {
    composable<Routes.Settings> { entry ->
        val route = entry.toRoute<Routes.Settings>()
        SettingsScreen(route.category, onClose = { nav.back() })
    }
}
