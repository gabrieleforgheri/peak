package com.gabrieleforghieri.peak.ui.me

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.util.tr
import com.gabrieleforghieri.peak.design.ScreenPlaceholder
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Me e progressi: `Features/Progress/` (intestazione, peso corporeo, progressione, medaglie, sezioni).
 * Proprietario: l'area **me**; `PeakRoot` chiama solo questa funzione. L'area aggiunge qui le sue rotte (le pubbliche di `Routes`, con i
 * loro argomenti, e le proprie con tipi `@Serializable` suoi) e sostituisce i segnaposto con le schermate vere, nei suoi file.
 */
fun NavGraphBuilder.meDestinations(nav: PeakNav) {
    composable<Routes.MeHome> {
        // il tasto serve a raggiungere le impostazioni finché MeView non c'è; `MeView` ha l'ingranaggio in alto a destra
        ScreenPlaceholder("Me", "Qui va MeView: statistiche, peso corporeo, progressione, medaglie.") {
            PrimaryButton(tr("Impostazioni"), { nav.openSettings() })
        }
    }
}
