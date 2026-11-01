package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.runtime.Composable

/**
 * Il riepilogo di un allenamento chiuso fuori dalla sessione (`RootView.presentPendingSummary`): su iPhone il Watch, qui la notifica o il
 * widget della fase 4. Aggancio già pronto, vuoto, che l'area **workouts** riempie (proprietario: workouts). `PeakRoot` lo chiama sopra la
 * navigazione.
 *
 * Legge `LocalPeakNav.current.requests.pendingSummary`: se la sessione di quell'allenamento è a schermo (`requests.visibleSession`) se lo
 * prende lei; altrimenti, con l'app in primo piano, mostra [WorkoutSummarySheet] e svuota la richiesta.
 */
@Composable
fun PendingSummaryOverlay() {}
