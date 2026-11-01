package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gabrieleforghieri.peak.design.ScreenPlaceholder
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Allenamenti: `Features/Workouts/` (lista con eroe, giorni e schede, sessione, editor, selettori, riepilogo).
 * Proprietario: l'area **workouts**; `PeakRoot` chiama solo questa funzione. L'area aggiunge qui le sue rotte (le pubbliche di `Routes`, con i
 * loro argomenti, e le proprie con tipi `@Serializable` suoi) e sostituisce i segnaposto con le schermate vere, nei suoi file.
 */
fun NavGraphBuilder.workoutsDestinations(nav: PeakNav) {
    composable<Routes.WorkoutsHome> {
        ScreenPlaceholder("Allenamento", "Qui va WorkoutsListView: eroe, scheda, giorni, storico.")
    }
    composable<Routes.WorkoutSession> { entry ->
        val route = entry.toRoute<Routes.WorkoutSession>()
        ScreenPlaceholder("Sessione", "Qui va WorkoutSessionView (allenamento ${route.workoutId}).")
    }
}
