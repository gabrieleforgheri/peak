package com.gabrieleforghieri.peak.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gabrieleforghieri.peak.ui.AccountScreen
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Impostazioni: `Features/Settings/` senza Watch (categorie: Generale, Allenamento, Account yG, App Salute, Backup, Dati). Proprietario:
 * l'area **settings**; `PeakRoot` chiama solo questa funzione. La barra in basso è nascosta su questa rotta (su iPhone è un `fullScreenCover`).
 *
 * Per ora mostra la schermata provvisoria della fase 2 (`ui/AccountScreen.kt`: account, collega, sincronizza, backup), che l'area
 * sostituisce con la schermata vera e poi cancella. Le sottopagine si gestiscono dentro questa rotta (con `category` si apre
 * direttamente quella), con il proprio stato e `BackHandler`; `nav.back()` chiude le impostazioni.
 */
fun NavGraphBuilder.settingsDestinations(nav: PeakNav) {
    composable<Routes.Settings> { entry ->
        @Suppress("UNUSED_VARIABLE") val route = entry.toRoute<Routes.Settings>()
        AccountScreen(PaddingValues(0.dp))
    }
}
