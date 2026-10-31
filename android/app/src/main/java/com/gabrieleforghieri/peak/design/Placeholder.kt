package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.util.tr

/**
 * Il segnaposto di una schermata che la fase 3 non ha ancora tradotto: sfondo, titolo e una scheda «In arrivo». L'area che traduce la
 * schermata lo toglie. `detail` (italiano) dice quale schermata dell'iPhone ci va; `extra` mette sotto altro, per esempio un pulsante.
 */
@Composable
fun ScreenPlaceholder(title: String, detail: String? = null, extra: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current))) {
        Column(Modifier.statusBarsPadding().padding(PeakTheme.padding)) {
            Spacer(Modifier.height(8.dp))
            Text(tr(title), Modifier.appear(0), style = PeakTheme.display(34), color = PeakTheme.textPrimary)
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.appear(1)) {
                Text(tr("In arrivo"), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(tr(detail ?: "Questa schermata è in costruzione."), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
            extra()
        }
    }
}
