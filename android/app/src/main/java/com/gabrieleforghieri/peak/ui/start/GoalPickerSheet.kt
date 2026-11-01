package com.gabrieleforghieri.peak.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.util.tr

/**
 * **Firma condivisa** (iPhone: `Features/Root/GoalPickerView.swift`). Proprietario: l'area **start**, che ne riscrive il corpo (la curva
 * della vetta, il suggerimento); la firma non cambia. Usata da: workouts (cambiare l'obiettivo), start (`GoalOverlay`, la prima volta).
 *
 * Sceglie quanti allenamenti a settimana. Salva `weeklyGoal` da sé; `onClose` parte a foglio chiuso. `current` = l'obiettivo di adesso
 * (0 = nessuno), `suggested` = quello che l'app propone (`Stats.weeklyGoal`).
 */
@Composable
fun GoalPickerSheet(current: Int, suggested: Int, onClose: () -> Unit) {
    val update = rememberUpdateSettings()
    PeakSheet(onClose) { close ->
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("Quanti allenamenti a settimana?"), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(tr("Consigliati: %lld", suggested), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in 1..7) {
                    PrimaryButton("$n", { update { copy(weeklyGoal = n) }; close() }, Modifier.weight(1f), tint = if (n == current) PeakTheme.positive else com.gabrieleforghieri.peak.design.LocalAccent.current)
                }
            }
        }
    }
}
