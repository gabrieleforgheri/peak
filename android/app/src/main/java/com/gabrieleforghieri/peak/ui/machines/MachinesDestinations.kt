package com.gabrieleforghieri.peak.ui.machines

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Macchinari: `Features/Maxes/` (lista, dettaglio, editor, catalogo, foto).
 * Proprietario: l'area **machines**; `PeakRoot` chiama solo questa funzione. L'editor, il catalogo e «Aggiorna massimale» sono fogli
 * (`PeakSheet`), non rotte: li apre la lista o il dettaglio con il suo stato.
 */
fun NavGraphBuilder.machinesDestinations(nav: PeakNav) {
    composable<Routes.MachinesHome> { MachinesListScreen() }
    composable<Routes.ExerciseDetail> { entry -> ExerciseDetailScreen(entry.toRoute<Routes.ExerciseDetail>().exerciseId) }
}
