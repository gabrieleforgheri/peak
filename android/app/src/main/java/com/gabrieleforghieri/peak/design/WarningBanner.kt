package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.util.tr

/** L'arancione di sistema di iOS, il colore di base di [WarningBanner]. */
val WarningOrange = Color(0xFFFF9500)

/**
 * Avviso in cima alla schermata, per le cose che possono farti perdere i dati: la firma che scade, il backup che non fai da troppo
 * (`Design/WarningBanner.swift`). `title` e `message` sono chiavi di traduzione (testo italiano); con `actionTitle` e `onAction` c'è il tasto.
 */
@Composable
fun WarningBanner(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tint: Color = WarningOrange,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    GlassCard(modifier, padding = 14.dp, tint = tint) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(30.dp).background(tint.copy(alpha = 0.16f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = tint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr(title), style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary)
                Text(tr(message), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary)
                if (actionTitle != null && onAction != null) {
                    Text(
                        tr(actionTitle), Modifier.padding(top = 4.dp).pressable(onClick = onAction),
                        style = PeakTheme.rounded(13, FontWeight.Bold), color = tint,
                    )
                }
            }
        }
    }
}
