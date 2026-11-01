package com.gabrieleforghieri.peak.ui.me

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Me e progressi: `Features/Progress/` (intestazione, peso corporeo, progressione, medaglie, sezioni).
 * Proprietario: l'area **me**; `PeakRoot` chiama solo questa funzione. Tutto ciò che su iPhone è un foglio (la medaglia, la pesata)
 * sta dentro la schermata, in un `PeakSheet`: nessuna rotta in più. Le impostazioni si aprono con `PeakNav.openSettings`.
 */
fun NavGraphBuilder.meDestinations(nav: PeakNav) {
    composable<Routes.MeHome> { MeScreen() }
}
