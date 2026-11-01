package com.gabrieleforghieri.peak.ui.me

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes
import com.gabrieleforghieri.peak.ui.friends.social.rememberSocialStore

/**
 * Me e progressi: `Features/Progress/` (intestazione, peso corporeo, progressione, medaglie, sezioni).
 * Proprietario: l'area **me**; `PeakRoot` chiama solo questa funzione. Tutto ciò che su iPhone è un foglio (la medaglia, la pesata)
 * sta dentro la schermata, in un `PeakSheet`: nessuna rotta in più. Le impostazioni si aprono con `PeakNav.openSettings`.
 */
fun NavGraphBuilder.meDestinations(nav: PeakNav) {
    composable<Routes.MeHome> {
        // come `social.friendCount` su iPhone: gli amici già caricati dalla scheda Amici, senza chiamare il server da qui
        val lists by rememberSocialStore().friends.collectAsState()
        MeScreen(friendCount = lists?.friends?.size ?: 0)
    }
}
