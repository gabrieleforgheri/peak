package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.util.tr

/**
 * Conferma in un foglio basso, nello stile dell'app: titolo, tasto principale, «annulla» sotto (`Design/ConfirmSheet.swift`).
 * L'azione parte a foglio chiuso, così quello che apre subito dopo non si scontra con la chiusura.
 *
 * `title`, `confirm` e `cancel` sono chiavi di traduzione (testo italiano). `tint` colora il tasto principale: l'accento, o
 * `PeakTheme.negative` per le azioni che cancellano. Si mostra con `if (chiedi) ConfirmSheet(…, onDismiss = { chiedi = false })`.
 */
@Composable
fun ConfirmSheet(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancel: String = "Annulla",
    tint: Color = LocalAccent.current,
) {
    var confirmed by remember { mutableStateOf(false) }
    PeakSheet(onDismiss = { onDismiss(); if (confirmed) onConfirm() }, detent = SheetDetent.Height(210.dp)) { close ->
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr(title), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(tr(confirm), { confirmed = true; close() }, tint = tint)
                SecondaryButton(tr(cancel), close)
            }
        }
    }
}
