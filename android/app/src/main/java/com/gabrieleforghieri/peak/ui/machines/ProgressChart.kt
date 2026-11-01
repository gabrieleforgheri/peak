package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.drawIn
import com.gabrieleforghieri.peak.domain.Stats
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Il grafico «Andamento» di `ExerciseDetailView`: area sfumata, linea monotona, un punto per misurazione (più tenue se «da
 * allenamento»), l'asse dei pesi a sinistra e poche date sotto. Un passo per misurazione: i giorni senza allenamento non esistono.
 */
@Composable
fun ProgressChart(points: List<ChartPoint>, tint: Color, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 190.dp) {
    val measurer = rememberTextMeasurer()
    val labelStyle = PeakTheme.rounded(10, FontWeight.Medium).copy(color = PeakTheme.textTertiary)
    val xDomain = chartXDomain(points.size)
    val yDomain = chartYDomain(points)
    val ticks = niceTicks(yDomain)
    val xTicks = Stats.tickIndices(points.size)
    val format = DecimalFormat("0")
    Canvas(modifier.fillMaxWidth().height(height).drawIn()) {
        val leftPad = 34.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val topPad = 6.dp.toPx()
        val rightPad = 8.dp.toPx()
        val w = size.width - leftPad - rightPad
        val h = size.height - bottomPad - topPad
        fun x(i: Double) = leftPad + ((i - xDomain.start) / (xDomain.endInclusive - xDomain.start)).toFloat() * w
        fun y(v: Double) = topPad + (1f - ((v - yDomain.start) / (yDomain.endInclusive - yDomain.start)).toFloat()) * h

        for (t in ticks) {
            val ty = y(t)
            drawLine(PeakTheme.stroke, Offset(leftPad, ty), Offset(size.width - rightPad, ty), 1f)
            val text = measurer.measure(format.format(t), labelStyle)
            drawText(text, topLeft = Offset(leftPad - 6.dp.toPx() - text.size.width, ty - text.size.height / 2f))
        }
        for (i in xTicks) {
            val text = measurer.measure(MachineDates.shortDate(points[i].date), labelStyle)
            val tx = (x(i.toDouble()) - text.size.width / 2f).coerceIn(leftPad - 8.dp.toPx(), size.width - text.size.width.toFloat())
            drawText(text, topLeft = Offset(tx, size.height - text.size.height.toFloat()))
        }

        val xs = points.map { x(it.index.toDouble()) }
        val ys = points.map { y(it.value) }
        val line = monotonePath(xs, ys)
        val area = Path().apply {
            addPath(line)
            lineTo(xs.last(), topPad + h)
            lineTo(xs.first(), topPad + h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.02f)), startY = topPad, endY = topPad + h))
        drawPath(line, tint, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { i, p ->
            drawCircle(if (p.isEstimated) tint.copy(alpha = 0.55f) else tint, if (p.isEstimated) 3.dp.toPx() else 4.2.dp.toPx(), Offset(xs[i], ys[i]))
        }
    }
}

/** Una curva che passa per tutti i punti senza oscillare fra l'uno e l'altro (Fritsch–Carlson): il `.monotone` di Swift Charts. */
internal fun monotonePath(xs: List<Float>, ys: List<Float>): Path {
    val n = xs.size
    val path = Path()
    if (n == 0) return path
    path.moveTo(xs[0], ys[0])
    if (n == 1) return path
    val dx = FloatArray(n - 1) { xs[it + 1] - xs[it] }
    val slope = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / dx[it] }
    val m = FloatArray(n)
    m[0] = slope[0]
    m[n - 1] = slope[n - 2]
    for (i in 1 until n - 1) m[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
    for (i in 0 until n - 1) {
        if (slope[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / slope[i]
        val b = m[i + 1] / slope[i]
        val s = a * a + b * b
        if (s > 9f) {
            val t = 3f / kotlin.math.sqrt(s)
            m[i] = t * a * slope[i]
            m[i + 1] = t * b * slope[i]
        }
    }
    for (i in 0 until n - 1) {
        path.cubicTo(xs[i] + dx[i] / 3f, ys[i] + m[i] * dx[i] / 3f, xs[i + 1] - dx[i] / 3f, ys[i + 1] - m[i + 1] * dx[i] / 3f, xs[i + 1], ys[i + 1])
    }
    return path
}

/** Tacche «tonde» sull'asse dei pesi: quattro o cinque, a passi da 1, 2 o 5 per una potenza di dieci. */
internal fun niceTicks(domain: ClosedFloatingPointRange<Double>, target: Int = 4): List<Double> {
    val span = domain.endInclusive - domain.start
    if (span <= 0) return listOf(domain.start)
    val raw = span / target
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val first = ceil(domain.start / step) * step
    return generateSequence(first) { it + step }.takeWhile { it <= domain.endInclusive + abs(step) * 1e-9 }.toList()
}
