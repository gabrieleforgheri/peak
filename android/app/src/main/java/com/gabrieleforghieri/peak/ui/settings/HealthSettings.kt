package com.gabrieleforghieri.peak.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.util.tr

/**
 * Impostazioni → App Salute (`HealthSettingsView`). Su Android è Health Connect e arriva nella fase 4: per ora la pagina dice che non
 * c'è, senza interruttori (l'iPhone mostra l'interruttore solo se `HealthService.isAvailable`, e altrimenti questo stato).
 */
@Composable
fun HealthSettingsPage() {
    SettingsPage {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("App Salute")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SettingsIcons.health, null, Modifier.size(22.dp), tint = PeakTheme.textTertiary)
                    Text(tr("Non ancora disponibile su Android"), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                }
                Hint(
                    tr("Il collegamento con Health Connect, per leggere il peso corporeo e registrare gli allenamenti, arriverà con un prossimo aggiornamento. Per ora i tuoi dati restano in Peak."),
                    color = PeakTheme.textSecondary, size = 12,
                )
            }
        }
    }
}
