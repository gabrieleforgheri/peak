package com.gabrieleforghieri.peak.ui.friends

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gabrieleforghieri.peak.design.ScreenPlaceholder
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Amici: `Features/Friends/` (lista, classifiche, profilo, invito con QR).
 * Proprietario: l'area **friends**; `PeakRoot` chiama solo questa funzione. L'area aggiunge qui le sue rotte (le pubbliche di `Routes`, con i
 * loro argomenti, e le proprie con tipi `@Serializable` suoi) e sostituisce i segnaposto con le schermate vere, nei suoi file.
 */
fun NavGraphBuilder.friendsDestinations(nav: PeakNav) {
    composable<Routes.FriendsHome> {
        ScreenPlaceholder("Amici", "Qui va FriendsView: amici, richieste, classifica, novità.")
    }
    composable<Routes.FriendProfile> { entry ->
        val route = entry.toRoute<Routes.FriendProfile>()
        ScreenPlaceholder("Profilo", "Qui va FriendProfileView (@${route.username}).")
    }
}
