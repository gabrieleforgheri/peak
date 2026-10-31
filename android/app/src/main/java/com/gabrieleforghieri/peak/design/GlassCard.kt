package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Il contenitore base dell'app: vetro, bordo sottile con un accenno di luce in alto, angoli morbidi (`Design/GlassCard.swift`).
 * Su Android il vetro smerigliato di iOS (`ultraThinMaterial`) non c'è: lo sfondo scuro dell'app + il velo bianco al 5,5 % dà lo
 * stesso effetto senza sfocare il contenuto che sta dietro.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = PeakTheme.padding,
    radius: Dp = PeakTheme.radius,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().glassSurface(radius, tint).padding(padding), content = content)
}

/** La superficie di vetro, per chi ha un contenuto suo (una riga, una cella). */
fun Modifier.glassSurface(radius: Dp = PeakTheme.radius, tint: Color? = null): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
        .clip(shape)
        .background(PeakTheme.bgRaised.copy(alpha = 0.55f))
        .background(tint?.copy(alpha = 0.12f) ?: PeakTheme.card)
        .border(
            BorderStroke(1.dp, Brush.verticalGradient(listOf(PeakTheme.strokeStrong, PeakTheme.stroke.copy(alpha = 0.35f)))),
            shape,
        )
}
