package com.gabrieleforghieri.peak.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Plates
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.util.tr
import androidx.compose.ui.text.font.FontWeight

/** Colori dei dischi olimpici, così si riconoscono a colpo d'occhio (`PlateBarView.color(for:)`). Il peso è in kg. */
fun plateColor(plate: Double): Color = when {
    plate >= 25 -> Color(0.86f, 0.20f, 0.20f)
    plate >= 20 -> Color(0.20f, 0.42f, 0.90f)
    plate >= 15 -> Color(0.95f, 0.78f, 0.20f)
    plate >= 10 -> Color(0.25f, 0.70f, 0.35f)
    plate >= 5 -> Color(0.92f, 0.92f, 0.92f)
    plate >= 2.5 -> Color(0.86f, 0.30f, 0.30f)
    else -> Color(0.65f, 0.65f, 0.65f)
}

/** «2 × 20 + 5 per lato»: raggruppa per taglia mantenendo l'ordine. Le parole sono italiane: passa da `tr`. */
fun plateSummary(perSide: List<Double>, unit: WeightUnit, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    if (perSide.isEmpty()) return "Nessun disco"
    val groups = mutableListOf<Pair<Double, Int>>()
    for (p in perSide) {
        val last = groups.lastOrNull()
        if (last != null && last.first == p) groups[groups.lastIndex] = p to last.second + 1 else groups += p to 1
    }
    val parts = groups.map { (plate, count) ->
        val w = Fmt.weight(plate, unit, locale, includeSymbol = false)
        if (count > 1) "$count × $w" else w
    }
    return parts.joinToString(" + ") + " ${unit.symbol} per lato"
}

/**
 * Il bilanciere visto di lato, con i dischi su entrambi i lati: entrano a molla dall'esterno a ogni cambio di carico, così si
 * vede subito cosa aggiungere o togliere (`Design/PlateBarView.swift`).
 */
@Composable
fun PlateBarView(load: Plates.Load, unit: WeightUnit, modifier: Modifier = Modifier) {
    val slots = load.perSide.mapIndexed { i, plate -> "$i-$plate" to plate }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(6.dp).background(PeakTheme.textTertiary, CircleShape))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((id, plate) in slots.reversed()) key(id) { Plate(plate) }
            }
            Box(Modifier.width(6.dp).height(26.dp).background(PeakTheme.textSecondary, RoundedCornerShape(2.dp)))
            Box(Modifier.weight(1f).height(10.dp).background(PeakTheme.textTertiary.copy(alpha = 0.35f * 0.7f), CircleShape))
            Box(Modifier.width(6.dp).height(26.dp).background(PeakTheme.textSecondary, RoundedCornerShape(2.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((id, plate) in slots) key(id) { Plate(plate) }
            }
            Box(Modifier.width(18.dp).height(6.dp).background(PeakTheme.textTertiary, CircleShape))
        }
        Text(tr(plateSummary(load.perSide, unit)), style = PeakTheme.rounded(13), color = PeakTheme.textSecondary)
        if (load.leftover > 0) {
            Text(
                tr("Mancano %@ per lato con i dischi disponibili.", Fmt.weight(load.leftover, unit)),
                style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.negative,
            )
        }
    }
}

@Composable
private fun RowScope.Plate(plate: Double) {
    val height = (26 + 64 * minOf(plate, 25.0) / 25).toFloat()
    val width = if (plate >= 10) 14.dp else 10.dp
    val scale = remember { Animatable(0.4f) }
    // entra a molla: un disco nuovo sale da fuori, quello vecchio sparisce con la sua chiave
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 300f)) }
    val color = plateColor(plate)
    Box(
        Modifier.width(width).height(height.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = scale.value.coerceIn(0f, 1f) }
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color)), RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
    )
}
