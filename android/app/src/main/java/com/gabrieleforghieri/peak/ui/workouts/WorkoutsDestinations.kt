package com.gabrieleforghieri.peak.ui.workouts

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gabrieleforghieri.peak.ui.nav.PeakNav
import com.gabrieleforghieri.peak.ui.nav.Routes

/**
 * Allenamenti: `Features/Workouts/` (lista con eroe, giorni e schede, sessione, editor, selettori, riepilogo).
 * Proprietario: l'area **workouts**; `PeakRoot` chiama solo questa funzione. La lista è la radice della scheda; la sessione si apre da lì
 * (`PeakNav.openWorkout`) o da un'altra area (`startTemplate`, `resumeActiveWorkout`). Tutto il resto dell'iPhone è un foglio.
 */
fun NavGraphBuilder.workoutsDestinations(nav: PeakNav) {
    composable<Routes.WorkoutsHome> { WorkoutsListScreen() }
    composable<Routes.WorkoutSession> { entry -> WorkoutSessionScreen(entry.toRoute<Routes.WorkoutSession>().workoutId) }
}
