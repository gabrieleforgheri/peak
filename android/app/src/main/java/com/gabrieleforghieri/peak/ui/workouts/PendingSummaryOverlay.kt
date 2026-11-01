package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav

/**
 * Il riepilogo di un allenamento chiuso fuori dalla sessione (`RootView.presentPendingSummary`): su iPhone il Watch, qui la notifica o il
 * widget della fase 4. `PeakRoot` lo chiama sopra la navigazione.
 *
 * Legge `LocalPeakNav.current.requests.pendingSummary`: se la sessione di quell'allenamento è a schermo (`requests.visibleSession`) se lo
 * prende lei (`WorkoutSessionScreen`); altrimenti mostra [WorkoutSummarySheet] e svuota la richiesta a riepilogo chiuso.
 */
@Composable
fun PendingSummaryOverlay() {
    val requests = LocalPeakNav.current.requests
    val pending by requests.pendingSummary.collectAsState()
    val visible by requests.visibleSession.collectAsState()
    val id = pending ?: return
    if (visible == id) return
    WorkoutSummarySheet(id) { if (requests.pendingSummary.value == id) requests.pendingSummary.value = null }
}
