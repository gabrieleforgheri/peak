package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.util.tr

/**
 * **Firma condivisa** (iPhone: `Features/Workouts/WorkoutSummaryView.swift`). Proprietario: l'area **workouts**, che ne scrive il corpo
 * (durata, volume, medaglie, record, «salva come giorno»); la firma non cambia. Usata da: workouts (fine della sessione) e da
 * `PendingSummaryOverlay` (un allenamento chiuso fuori dalla sessione).
 *
 * Il riepilogo di un allenamento chiuso. `onDone` parte a foglio chiuso, anche con un gesto.
 */
@Composable
fun WorkoutSummarySheet(workoutId: String, onDone: () -> Unit) {
    PeakSheet(onDone) { close ->
        Column(Modifier.padding(20.dp)) {
            Text(tr("Riepilogo"), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(tr("Questa schermata è in costruzione."), Modifier.padding(vertical = 8.dp), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary)
            PrimaryButton(tr("Fatto"), close)
        }
    }
}
