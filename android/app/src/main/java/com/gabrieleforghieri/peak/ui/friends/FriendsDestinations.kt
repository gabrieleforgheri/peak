package com.gabrieleforghieri.peak.ui.friends

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Amici: `Features/Friends/` (lista, classifiche, profilo, invito con QR).
 * Proprietario: l'area **friends**; `PeakRoot` chiama solo questa funzione. L'invito è un foglio (`InviteSheet`) dentro la lista, come su
 * iPhone; il profilo è la rotta pubblica [Routes.FriendProfile] (spinta dalla lista, dal podio e dalle novità).
 */
fun NavGraphBuilder.friendsDestinations(nav: PeakNav) {
    composable<Routes.FriendsHome> {
        FriendsScreen(onOpenFriend = nav::openFriend)
    }
    composable<Routes.FriendProfile> { entry ->
        val route = entry.toRoute<Routes.FriendProfile>()
        FriendProfileScreen(route.username, onBack = { nav.back() })
    }
}
