package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gabrieleforghieri.peak.design.ScreenPlaceholder
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Macchinari: `Features/Maxes/` (lista, dettaglio, editor, catalogo, foto).
 * Proprietario: l'area **machines**; `PeakRoot` chiama solo questa funzione. L'area aggiunge qui le sue rotte (le pubbliche di `Routes`, con i
 * loro argomenti, e le proprie con tipi `@Serializable` suoi) e sostituisce i segnaposto con le schermate vere, nei suoi file.
 */
fun NavGraphBuilder.machinesDestinations(nav: PeakNav) {
    composable<Routes.MachinesHome> {
        ScreenPlaceholder("Macchinari", "Qui va MaxesListView: macchinari per gruppo, filtri, banner di collegamento.")
    }
    composable<Routes.ExerciseDetail> { entry ->
        val route = entry.toRoute<Routes.ExerciseDetail>()
        ScreenPlaceholder("Macchinario", "Qui va ExerciseDetailView (macchinario ${route.exerciseId}).")
    }
}
