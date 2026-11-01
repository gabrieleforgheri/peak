package com.gabrieleforghieri.peak.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/** Una curva morbida per i punti (Catmull-Rom scritta come Bézier cubiche); nei punti `corners` resta uno spigolo netto. */
internal class Smooth(val path: Path, val segs: List<List<Offset>>)

internal fun smooth(pts: List<Offset>, corners: List<Int> = emptyList()): Smooth {
    fun f(n: Float) = (n * 10).roundToInt() / 10f
    val path = Path()
    val segs = mutableListOf<List<Offset>>()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        val p0 = if (i == 0 || i in corners) a else pts[i - 1]
        val p3 = if (i + 2 >= pts.size || (i + 1) in corners) b else pts[i + 2]
        val c1 = Offset(f(a.x + (b.x - p0.x) / 6), f(a.y + (b.y - p0.y) / 6))
        val c2 = Offset(f(b.x - (p3.x - a.x) / 6), f(b.y - (p3.y - a.y) / 6))
        segs += listOf(a, c1, c2, b)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
    }
    return Smooth(path, segs)
}

private fun bezierAt(s: List<Offset>, t: Float): Offset {
    val u = 1 - t
    val w = floatArrayOf(u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t)
    return Offset(s.indices.sumOf { (s[it].x * w[it]).toDouble() }.toFloat(), s.indices.sumOf { (s[it].y * w[it]).toDouble() }.toFloat())
}

/** Il punto della curva alla quota `y` (la quota sale o scende sempre lungo il tratto). */
internal fun bezierAtY(s: List<Offset>, y: Float): Offset {
    var lo = 0f
    var hi = 1f
    val up = s[3].y < s[0].y
    repeat(24) {
        val m = (lo + hi) / 2
        if ((bezierAt(s, m).y < y) == up) hi = m else lo = m
    }
    return bezierAt(s, (lo + hi) / 2)
}

private const val W = 360f
private const val H = 260f

// un solo monte, visto da vicino: la vetta vicina al centro (un po' a destra) a un quarto dall'alto, due fianchi lunghi fino ai bordi,
// una spalla piatta per lato e nessun zigzag. Dietro, una sagoma più grande e più pallida
private val summit = Offset(192f, 70f)
private val ridgePoints = listOf(Offset(0f, 238f), Offset(62f, 194f), Offset(112f, 170f), summit, Offset(240f, 128f), Offset(286f, 148f), Offset(360f, 228f))
private val rangePoints = listOf(Offset(0f, 216f), Offset(72f, 158f), Offset(136f, 104f), Offset(214f, 134f), Offset(290f, 160f), Offset(360f, 196f))

private fun closed(p: Path): Path = Path().apply {
    addPath(p)
    lineTo(W, H)
    lineTo(0f, H)
    close()
}

/**
 * Il grafico di un titolo che sale e si fa montagna, con la bandiera sulla vetta (circa 3 s, una volta). `still`: il fotogramma
 * finale, senza movimento (anche con «Riduci movimento»). Stesso disegno e stessa scaletta di `SummitDraw.svelte` / `SummitDrawView.swift`.
 */
@Composable
fun SummitDraw(modifier: Modifier = Modifier, still: Boolean = false) {
    val play = !still && !LocalReduceMotion.current
    var t by remember { mutableDoubleStateOf(if (play) 0.0 else 99.0) }
    if (play) LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { t = (it - start) / 1e9 }
    }
    val accent = LocalAccent.current
    val ridge = remember { smooth(ridgePoints, corners = listOf(3)) }
    val range = remember { smooth(rangePoints) }
    val ridgeFill = remember { closed(ridge.path) }
    val rangeFill = remember { closed(range.path) }
    val cap = remember {
        val yb = summit.y + 26
        val l = bezierAtY(ridge.segs[2], yb)
        val r = bezierAtY(ridge.segs[3], yb)
        val w = r.x - l.x
        Path().apply {
            moveTo(summit.x, summit.y)
            lineTo(l.x, yb)
            lineTo(l.x + w * 0.3f, yb - 3)
            lineTo(l.x + w * 0.5f, yb + 2)
            lineTo(l.x + w * 0.72f, yb - 3)
            lineTo(r.x, yb)
            close()
        }
    }
    val measure = remember { PathMeasure().also { it.setPath(ridge.path, false) } }
    Canvas(modifier.aspectRatio(W / H).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        scale(size.width / W, size.width / W, pivot = Offset.Zero) { drawSummit(t, accent, ridge.path, ridgeFill, rangeFill, cap, measure) }
    }
}

private fun DrawScope.drawSummit(t: Double, accent: Color, ridge: Path, ridgeFill: Path, rangeFill: Path, cap: Path, measure: PathMeasure) {
    val kLine = Curve.clamp((t - 0.15) / 1.6)
    val p = if (kLine < 0.5) 4 * kLine * kLine * kLine else 1 - (-2 * kLine + 2).pow(3) / 2   // ease-in-out cubica
    fun fade(delay: Double, dur: Double) = Curve.bezier(Curve.easeOut, (t - delay) / dur).toFloat()
    val grow = fade(1.7, 0.9)
    val haloOp = (0.6 - 0.25 * fade(1.7, 0.8)).toFloat()
    val flagS = Curve.bezier(Curve.spring, (t - 2.35) / 0.56).toFloat()
    // la bandiera ondeggia a regime (dal 2,9 s): su e giù, 1,8 s per verso, con ease-in-out
    val ph = maxOf(0.0, t - 2.9) / 1.8
    val tri = ph % 2
    val w = Curve.bezier(Curve.easeInOut, if (tri < 1) tri else 2 - tri)
    val angle = (-4 + 8 * w) * PI / 180
    val sx = (1 - 0.1 * w).toFloat()

    val drawn = Path()
    val len = measure.length
    measure.getSegment(0f, len * maxOf(0.0001, p).toFloat(), drawn, true)
    val head = measure.getPosition(len * maxOf(0.0001, p).toFloat())

    // il monte, sfumato ai lati
    drawRect(Color.Transparent, size = androidx.compose.ui.geometry.Size(W, H))   // fissa l'area del livello
    drawPath(rangeFill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0f)), startY = 100f, endY = 230f), alpha = fade(1.85, 0.9))
    scale(1f, maxOf(0.0001f, grow), pivot = Offset(W / 2, H)) {
        drawPath(ridgeFill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0f)), startY = 56f, endY = H), alpha = grow)
    }
    drawPath(cap, Color.White.copy(alpha = 0.72f), alpha = fade(2.25, 0.45))
    drawPath(drawn, accent.copy(alpha = haloOp * 0.35f), style = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(drawn, accent.copy(alpha = haloOp), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val b = drawn.getBounds()
    drawPath(
        drawn,
        Brush.horizontalGradient(
            0f to accent.copy(alpha = 0f), 0.35f to accent.copy(alpha = 0.7f), 0.65f to accent, 1f to accent,
            startX = b.left, endX = maxOf(b.right, b.left + 1f),
        ),
        style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    if (kLine > 0 && kLine < 1) {
        drawCircle(accent.copy(alpha = 0.5f), 8f, head)
        drawCircle(Color.White, 3.5f, head)
    }
    // sfuma i lati del monte
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 0.08f to Color.Black, 0.92f to Color.Black, 1f to Color.Transparent),
        size = androidx.compose.ui.geometry.Size(W, H), blendMode = BlendMode.DstIn,
    )

    // l'alone sulla vetta
    drawCircle(
        Brush.radialGradient(listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0f)), center = Offset(summit.x, summit.y - 14), radius = 40f),
        radius = 40f, center = Offset(summit.x, summit.y - 14), alpha = fade(2.3, 0.8),
    )

    // la bandiera
    scale(maxOf(0.0001f, flagS), pivot = summit) {
        drawLine(accent, summit, Offset(summit.x, summit.y - 24), 1.2f, StrokeCap.Round)
        val cloth = clothPath(angle, sx)
        drawPath(cloth, accent)
        drawPath(cloth, accent, style = Stroke(1f, join = StrokeJoin.Round))
    }
}

/** La stoffa: un triangolo che parte dall'asta, inclinato e stretto dall'ondeggiare (come `skewY` e `scaleX` del CSS, dal suo bordo sinistro). */
private fun clothPath(angle: Double, sx: Float): Path {
    fun pt(x: Float, y: Float): Offset {
        val x1 = x * sx
        return Offset(summit.x + x1, summit.y + y + x1 * tan(angle).toFloat())
    }
    return Path().apply {
        val a = pt(0f, -24f)
        val b = pt(16f, -18.5f)
        val c = pt(0f, -13f)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
    }
}
