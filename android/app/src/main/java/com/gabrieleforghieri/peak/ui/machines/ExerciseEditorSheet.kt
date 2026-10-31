package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.util.tr

/**
 * **Firma condivisa** (iPhone: `Features/Maxes/ExerciseEditorView.swift`). Proprietario: l'area **machines**, che ne scrive il corpo
 * (creazione guidata in tre passi, modifica, foto, collegamento al catalogo); la firma non cambia.
 * Usata da: machines (lista, dettaglio), workouts (il tasto + del selettore di esercizi).
 *
 * Crea (`exerciseId == null`) o modifica un macchinario. Salva da sé nel database; `onDismiss` parte a foglio chiuso.
 */
@Composable
fun ExerciseEditorSheet(exerciseId: String?, onDismiss: () -> Unit) {
    PeakSheet(onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text(tr(if (exerciseId == null) "Nuovo macchinario" else "Modifica macchinario"), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(tr("Questa schermata è in costruzione."), Modifier.padding(top = 8.dp), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary)
        }
    }
}
