package com.gabrieleforghieri.peak.ui.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.domain.ChartRange
import com.gabrieleforghieri.peak.util.tr
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

// I grafici di Me: `Chart` di Swift Charts (linea monotona con i punti, asse y a sinistra con la griglia, qualche data in basso)
// e il selettore a segmenti dell'intervallo. Disegnati a mano: nessuna libreria in più.

/** Un punto del grafico; `dim` = disegnato più tenue (la pesata che viene da Salute). */
data class ChartPoint(val x: Double, val y: Double, val dim: Boolean = false)

/** Un'etichetta sull'asse. */
data class AxisTick(val value: Double, val label: String)

/** Tacche «tonde» (1, 2, 5 × 10ⁿ) che coprono `lo..hi`, circa `count`: il dominio del grafico è dalla prima all'ultima. */
fun niceTicks(lo: Double, hi: Double, count: Int = 4, minStep: Double = 0.0): List<Double> {
    if (!(hi > lo)) return listOf(lo)
    val raw = (hi - lo) / max(1, count - 1)
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw - 1e-9 }.coerceAtLeast(minStep)
    val first = floor(lo / step + 1e-9) * step
    val last = ceil(hi / step - 1e-9) * step
    return generateSequence(first) { it + step }.takeWhile { it <= last + step / 2 }.toList()
}

/** Il tratto «monotono» di `.interpolationMethod(.monotone)`: una curva che non supera mai i punti (Fritsch–Carlson). */
fun monotonePath(p: List<Offset>): Path {
    val path = Path()
    if (p.isEmpty()) return path
    path.moveTo(p[0].x, p[0].y)
    if (p.size == 1) return path
    val n = p.size
    val dx = FloatArray(n - 1) { p[it + 1].x - p[it].x }
    val slope = FloatArray(n - 1) { if (dx[it] == 0f) 0f else (p[it + 1].y - p[it].y) / dx[it] }
    val m = FloatArray(n)
    m[0] = slope[0]
    m[n - 1] = slope[n - 2]
    for (i in 1 until n - 1) m[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
    for (i in 0 until n - 1) {
        if (slope[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / slope[i]
        val b = m[i + 1] / slope[i]
        val h = a * a + b * b
        if (h > 9f) {
            val t = 3f / kotlin.math.sqrt(h)
            m[i] = t * a * slope[i]
            m[i + 1] = t * b * slope[i]
        }
    }
    for (i in 0 until n - 1) {
        val third = dx[i] / 3f
        path.cubicTo(p[i].x + third, p[i].y + m[i] * third, p[i + 1].x - third, p[i + 1].y - m[i + 1] * third, p[i + 1].x, p[i + 1].y)
    }
    return path
}

/**
 * Il grafico a linea di Me. `yTicks` vuoto = asse y nascosto; `xTicks` vuoto = niente date sotto. I punti sono in coordinate dei dati
 * (`xDomain`, `yDomain`).
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    xDomain: ClosedFloatingPointRange<Double>,
    yDomain: ClosedFloatingPointRange<Double>,
    yTicks: List<AxisTick>,
    xTicks: List<AxisTick>,
    color: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 2.5.dp,
    pointRadius: Dp = 3.dp,
) {
    val measurer = rememberTextMeasurer()
    val label = PeakTheme.rounded(10, FontWeight.Medium).copy(color = PeakTheme.textTertiary, textAlign = TextAlign.End)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gutter = if (yTicks.isEmpty()) 0f else yTicks.maxOf { measurer.measure(it.label, label).size.width } + 6.dp.toPx()
        val footer = if (xTicks.isEmpty()) 0f else 18.dp.toPx()
        val pad = max(pointRadius.toPx(), lineWidth.toPx())
        val plotLeft = gutter + pad
        val plotRight = size.width - pad
        val plotTop = pad
        val plotBottom = size.height - footer - pad
        fun px(x: Double) = plotLeft + ((x - xDomain.start) / (xDomain.endInclusive - xDomain.start).coerceAtLeast(1e-9)).toFloat() * (plotRight - plotLeft)
        fun py(y: Double) = plotBottom - ((y - yDomain.start) / (yDomain.endInclusive - yDomain.start).coerceAtLeast(1e-9)).toFloat() * (plotBottom - plotTop)

        for (t in yTicks) {
            val y = py(t.value)
            drawLine(PeakTheme.stroke, Offset(gutter, y), Offset(size.width, y), 1.dp.toPx())
            val m = measurer.measure(t.label, label)
            drawText(m, topLeft = Offset(gutter - 6.dp.toPx() - m.size.width, y - m.size.height / 2f))
        }
        for (t in xTicks) {
            val m = measurer.measure(t.label, label.copy(textAlign = TextAlign.Center))
            val x = (px(t.value) - m.size.width / 2f).coerceIn(0f, max(0f, size.width - m.size.width))
            drawText(m, topLeft = Offset(x, size.height - footer + 4.dp.toPx()))
        }
        val offsets = points.map { Offset(px(it.x), py(it.y)) }
        if (offsets.size >= 2) drawPath(monotonePath(offsets), color, style = Stroke(lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, p -> drawCircle(if (p.dim) color.copy(alpha = 0.5f) else color, pointRadius.toPx(), offsets[i]) }
    }
}

/** Le tacche dell'asse del tempo: `count` date equidistanti da `from` a `to` (ms). */
fun dateTicks(from: Long, to: Long, count: Int = 4): List<AxisTick> {
    if (to <= from) return listOf(AxisTick(from.toDouble(), shortDateText(from)))
    return (0 until count).map { i ->
        val t = from + (to - from) * i / (count - 1)
        AxisTick(t.toDouble(), shortDateText(t))
    }
}

/** Il selettore a segmenti dell'intervallo (`Picker("Intervallo")` con `.segmented`). */
@Composable
fun RangePicker(ranges: List<ChartRange>, selected: ChartRange, onSelect: (ChartRange) -> Unit, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.fillMaxWidth().background(PeakTheme.card, shape).border(1.dp, PeakTheme.stroke, shape).padding(2.dp)) {
        for (r in ranges) {
            val on = r == selected
            Box(
                Modifier.weight(1f).height(30.dp)
                    .background(if (on) accent.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(r) },
                contentAlignment = Alignment.Center,
            ) {
                Text(tr(r.title), style = PeakTheme.rounded(12, if (on) FontWeight.Bold else FontWeight.Medium), color = if (on) accent else PeakTheme.textSecondary, maxLines = 1)
            }
        }
    }
}

/** Il dominio y di un grafico con `padding` attorno ai dati (come `chartYScale(domain:)`), arrotondato alle tacche. */
fun yDomainOf(values: List<Double>, ticks: Int = 4, minStep: Double = 0.0): Pair<ClosedFloatingPointRange<Double>, List<Double>> {
    val lo = values.minOrNull() ?: 0.0
    val hi = values.maxOrNull() ?: 1.0
    val t = niceTicks(lo, if (abs(hi - lo) < 1e-9) lo + 1 else hi, ticks, minStep)
    return (t.first()..t.last()) to t
}
