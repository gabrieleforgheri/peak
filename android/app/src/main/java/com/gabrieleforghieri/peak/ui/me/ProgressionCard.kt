package com.gabrieleforghieri.peak.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.drawIn
import com.gabrieleforghieri.peak.domain.ChartRange
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.util.tr
import kotlin.math.roundToInt

/**
 * L'andamento di un macchinario alla volta: il miglior 1RM stimato di ogni giorno in cui l'hai allenato (`ProgressionCard`).
 * `series` è già calcolata (`MeData.progression`): qui non si ricalcola niente.
 */
@Composable
fun ProgressionCard(series: List<Series>, unit: WeightUnit, onPick: () -> Unit = {}, now: Long = System.currentTimeMillis()) {
    val accent = LocalAccent.current
    var pickedID by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(ChartRange.all) }
    val current = series.firstOrNull { it.id == pickedID } ?: series.firstOrNull()
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in series) {
                    val picked = s.id == current?.id
                    Text(
                        s.exercise.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(if (picked) accent.copy(alpha = 0.22f) else PeakTheme.card, CircleShape)
                            .border(1.dp, if (picked) accent.copy(alpha = 0.7f) else PeakTheme.stroke, CircleShape)
                            .clickable(remember { MutableInteractionSource() }, null) { pickedID = s.id; onPick() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        style = PeakTheme.rounded(12, FontWeight.SemiBold), color = if (picked) accent else PeakTheme.textSecondary,
                    )
                }
            }
            if (current != null) {
                val available = availableRanges(current.points.minOfOrNull { it.date }, now)
                val active = activeRange(range, available)
                if (available.size > 1) RangePicker(available, active, { range = it }, accent)
                val cutoff = active.days?.let { now - (it * 86_400_000L).toLong() }
                ProgressionChart(current.points.filter { cutoff == null || it.date >= cutoff }, unit)
            }
        }
    }
}

@Composable
private fun ProgressionChart(pts: List<ProgressPoint>, unit: WeightUnit) {
    val accent = LocalAccent.current
    if (pts.size < 2) {
        NotEnoughData(120)
        return
    }
    val lo = pts.minOf { it.oneRm } * 0.95
    val hi = pts.maxOf { it.oneRm } * 1.05
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(tr("1RM stimato · un punto per allenamento"), Modifier.weight(1f), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary)
        val a = pts.first()
        val b = pts.last()
        TrendBadge("${Fmt.weight(a.oneRm, unit)} → ${Fmt.weight(b.oneRm, unit)}", b.oneRm >= a.oneRm)
    }
    val (domain, ticks) = yDomainOf(listOf(unit.fromKilograms(lo), unit.fromKilograms(hi)), 4, minStep = 1.0)
    LineChart(
        points = pts.map { ChartPoint(it.date.toDouble(), unit.fromKilograms(it.oneRm)) },
        xDomain = pts.first().date.toDouble()..pts.last().date.toDouble(),
        yDomain = domain,
        yTicks = ticks.map { AxisTick(it, it.roundToInt().toString()) },
        xTicks = dateTicks(pts.first().date, pts.last().date, 4),
        color = accent, height = 170.dp, modifier = Modifier.drawIn(), lineWidth = 2.dp, pointRadius = 3.dp,
    )
}
