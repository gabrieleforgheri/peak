package com.gabrieleforghieri.peak.ui.start

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
 * **Firma condivisa** (iPhone: `ConnectSheet` in `Features/Root/ConnectFlowView.swift`). Proprietario: l'area **start**, che scrive il
 * corpo con le scelte del primo collegamento (`sync/ConnectFlow.kt` c'è già); la firma non cambia.
 * Usata da: machines (banner della lista), friends (banner della lista), settings (Account yG).
 *
 * Il percorso per collegare l'account yG. Si chiude da solo a collegamento fatto o annullato; `onDismiss` parte a foglio chiuso.
 */
@Composable
fun ConnectSheet(onDismiss: () -> Unit) {
    PeakSheet(onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text(tr("Collega il tuo account yG"), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(tr("Questa schermata è in costruzione."), Modifier.padding(top = 8.dp), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary)
        }
    }
}
