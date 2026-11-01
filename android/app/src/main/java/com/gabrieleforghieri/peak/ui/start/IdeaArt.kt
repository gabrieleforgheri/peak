package com.gabrieleforghieri.peak.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.util.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Quanto dura un giro di ciascun disegno, in secondi (`periods` di `IdeaArt`). */
val ideaPeriods = listOf(7.0, 6.0, 9.0, 7.0)

/**
 * Un disegno che si ripete ogni `T` secondi mentre la pagina è quella in vista e sfuma agli estremi, così il giro riparte pulito.
 * Tutto è una funzione del tempo `t`: le stesse scalette dei fotogrammi chiave di `Ideas.svelte` (e di `IdeaArt` in `IdeasView.swift`).
 * Fermo (o con «riduci movimento») è il fotogramma finale, a 0,9 di giro, dove tutto è a regime.
 */
@Composable
fun IdeaArt(index: Int, active: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val running = active && !reduce
    val period = ideaPeriods[index]
    var t by remember { mutableDoubleStateOf(0.9 * period) }
    LaunchedEffect(running) {
        if (!running) { t = 0.9 * period; return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9 }
    }
    val accent = LocalAccent.current
    val unit = LocalAppSettings.current.unit
    val measurer = rememberTextMeasurer()
    val ctx = IdeaCtx(
        measurer, accent, unit, running, period,
        flame = rememberVectorPainter(StartIcons.flame), star = rememberVectorPainter(StartIcons.star),
        link = rememberVectorPainter(StartIcons.link), crown = rememberVectorPainter(StartIcons.crown),
        weeks = tr("settimane di fila"), medalNames = listOf(tr("Bronzo"), tr("Argento"), tr("Oro")),
    )
    Canvas(
        modifier.fillMaxWidth().aspectRatio(320f / 240f).graphicsLayer {
            alpha = if (running) Curve.key((t % period) / period, listOf(0.0 to 0.0, 0.04 to 1.0, 0.93 to 1.0, 1.0 to 0.0), Curve.linear).toFloat() else 1f
        },
    ) {
        scale(size.width / 320f, size.width / 320f, pivot = Offset.Zero) {
            val p = (t % period) / period
            when (index) {
                0 -> ctx.mountain(this, t, p)
                1 -> ctx.record(this, p)
                2 -> ctx.medals(this, t)
                else -> ctx.friends(this, p)
            }
        }
    }
}

private class IdeaCtx(
    val measurer: TextMeasurer, val accent: Color, val unit: WeightUnit, val running: Boolean, val period: Double,
    val flame: Painter, val star: Painter, val link: Painter, val crown: Painter, val weeks: String, val medalNames: List<String>,
) {
    private val panel = Color.White.copy(alpha = 0.06f)
    private val panelLine = Color.White.copy(alpha = 0.12f)

    private fun f(v: Double) = v.toFloat()

    /** Un testo con la linea di base a `y` e il lato `anchor` (−1 = inizio, 0 = metà, 1 = fine) a `x`, come il `<text>` dell'SVG. */
    private fun DrawScope.label(s: String, x: Float, y: Float, size: Float, weight: FontWeight = FontWeight.SemiBold, color: Color, anchor: Int = -1, alpha: Float = 1f) {
        if (alpha <= 0.001f) return
        val style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.toSp(), fontWeight = weight, fontFeatureSettings = "tnum")
        val layout = measurer.measure(s, style, softWrap = false)
        val left = when (anchor) { -1 -> x; 0 -> x - layout.size.width / 2f; else -> x - layout.size.width }
        drawText(layout, color = color.copy(alpha = color.alpha * alpha), topLeft = Offset(left, y - layout.firstBaseline))
    }

    private fun poly(pts: List<Pair<Float, Float>>): Path = Path().apply {
        pts.forEachIndexed { i, q -> if (i == 0) moveTo(q.first, q.second) else lineTo(q.first, q.second) }
        close()
    }

    private fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color, stroke: Color? = null, alpha: Float = 1f) {
        if (alpha <= 0.001f) return
        drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r), alpha = alpha)
        if (stroke != null) drawRoundRect(stroke, Offset(x + 0.5f, y + 0.5f), Size(w - 1, h - 1), CornerRadius(r), style = Stroke(1f), alpha = alpha)
    }

    /** Un gruppo di disegni con una trasparenza sola. */
    private fun DrawScope.layer(alpha: Float, block: DrawScope.() -> Unit) {
        if (alpha >= 0.999f) { block(); return }
        if (alpha <= 0.001f) return
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(-2000f, -2000f, 4000f, 4000f), Paint().apply { this.alpha = alpha })
        block()
        canvas.restore()
    }

    private fun DrawScope.icon(p: Painter, x: Float, y: Float, size: Float, color: Color) {
        translate(x, y) { with(p) { draw(Size(size, size), 1f, ColorFilter.tint(color)) } }
    }

    /** La bandiera/stoffa ondeggia a regime: `skewY` e `scaleX` dal bordo sinistro. */
    private fun wave(t: Double, period: Double, skew: Double, scaleX: Double): Pair<Double, Double> {
        val ph = if (running) t / period else 0.5
        val tri = ph % 2
        val w = Curve.bezier(Curve.easeInOut, if (tri < 1) tri else 2 - tri)
        return ((-skew + 2 * skew * w) * PI / 180) to (1 - (1 - scaleX) * w)
    }

    // 1. La montagna
    fun mountain(d: DrawScope, t: Double, p: Double) = with(d) {
        fun seg(a: Double, b: Double) = f(Curve.key(p, listOf(0.0 to 0.0, a to 0.0, b to 1.0, 1.0 to 1.0), Curve.easeOut))
        val flag = f(Curve.key(p, listOf(0.0 to 0.0, 0.42 to 0.0, 0.52 to 1.0, 1.0 to 1.0), Curve.spring))
        val (angle, sx) = wave(t, 1.5, 5.0, 0.88)
        val n1 = f(Curve.key(p, listOf(0.0 to 0.0, 0.54 to 0.0, 0.58 to 1.0, 1.0 to 1.0)))
        val bump = f(Curve.key(p, listOf(0.0 to 1.0, 0.54 to 1.0, 0.6 to 1.08, 0.68 to 1.0, 1.0 to 1.0), Curve.easeOut))
        box(0f, 0f, 320f, 176f, 28f, accent.copy(alpha = 0.08f), accent.copy(alpha = 0.3f))
        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, 320f, 176f, 28f, 28f)) }) {
            drawPath(poly(listOf(0f to 112f, 36f to 86f, 74f to 104f, 118f to 62f, 160f to 92f, 206f to 52f, 252f to 86f, 290f to 68f, 320f to 84f, 320f to 176f, 0f to 176f)), Color.White.copy(alpha = 0.06f))
            drawPath(
                poly(listOf(0f to 148f, 48f to 112f, 98f to 130f, 150f to 80f, 190f to 108f, 240f to 34f, 282f to 90f, 320f to 72f, 320f to 176f, 0f to 176f)),
                Brush.verticalGradient(listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.05f)), startY = 34f, endY = 176f),
            )
            drawPath(poly(listOf(240f to 34f, 223.8f to 58f, 231f to 52f, 240f to 64f, 248f to 53f, 258f to 58f)), Color.White.copy(alpha = 0.88f))
            drawPath(poly(listOf(0f to 176f, 0f to 160f, 60f to 150f, 130f to 164f, 200f to 148f, 260f to 160f, 320f to 150f, 320f to 176f)), Color.Black.copy(alpha = 0.38f))
        }
        scale(maxOf(0.0001f, flag), pivot = Offset(240f, 34f)) {
            drawLine(accent, Offset(240f, 34f), Offset(240f, 10f), 1.8f, StrokeCap.Round)
            val cloth = Path().apply {
                fun pt(x: Float, y: Float) = Offset(240f + x * f(sx), 34f + y + x * f(sx) * tan(angle).toFloat())
                val a = pt(0f, -24f); val b = pt(22f, -17f); val c = pt(0f, -10f)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
            }
            drawPath(cloth, accent)
            drawPath(cloth, accent, style = Stroke(1.4f, join = StrokeJoin.Round))
        }
        val bars = listOf(seg(0.08, 0.16), seg(0.2, 0.28), seg(0.32, 0.4))
        for (i in 0 until 3) {
            val x = (40 + i * 84).toFloat()
            box(x, 192f, 76f, 10f, 5f, Color.White.copy(alpha = 0.14f))
            if (i == 2 && bars[i] > 0.01f) drawRoundRect(accent.copy(alpha = 0.3f), Offset(x - 2, 190f), Size(76f * bars[i] + 4, 14f), CornerRadius(7f))
            if (bars[i] > 0.01f) drawRoundRect(accent, Offset(x, 192f), Size(76f * bars[i], 10f), CornerRadius(5f))
        }
        scale(bump, pivot = Offset(160f, 225f)) {
            box(70f, 212f, 180f, 26f, 13f, Color.Black.copy(alpha = 0.35f), Color.White.copy(alpha = 0.12f))
            icon(flame, 84f, 215f, 20f, accent)
            label("3", 114f, 230f, 17f, FontWeight.ExtraBold, accent, 1, 1 - n1)
            label("4", 114f, 230f, 17f, FontWeight.ExtraBold, accent, 1, n1)
            label(weeks, 130f, 229.5f, 12f, FontWeight.SemiBold, PeakTheme.textSecondary)
        }
    }

    // 2. I record
    fun record(d: DrawScope, p: Double) = with(d) {
        val old = f(Curve.key(p, listOf(0.0 to 1.0, 0.36 to 1.0, 0.42 to 0.0, 1.0 to 0.0)))
        val new = f(Curve.key(p, listOf(0.0 to 0.0, 0.36 to 0.0, 0.44 to 1.0, 1.0 to 1.0)))
        val newY = f(Curve.key(p, listOf(0.0 to 8.0, 0.36 to 8.0, 0.44 to 0.0, 1.0 to 0.0)))
        val flash = if (running) f(Curve.key(p, listOf(0.0 to 0.0, 0.38 to 0.0, 0.46 to 1.0, 0.7 to 0.0, 1.0 to 0.0))) else 0.6f
        val pop = f(Curve.key(p, listOf(0.0 to 0.0, 0.4 to 0.0, 0.5 to 1.0, 1.0 to 1.0), Curve.spring))
        box(10f, 36f, 300f, 168f, 26f, panel, panelLine)
        label("Chest Press", 30f, 68f, 13f, FontWeight.Bold, PeakTheme.textSecondary)
        drawRect(Color.White.copy(alpha = 0.1f), Offset(30f, 112f), Size(260f, 1f))
        label("1", 30f, 100f, 15f, FontWeight.Bold, PeakTheme.textTertiary)
        label(Fmt.setLine(60.0, 8, unit), 58f, 100f, 22f, FontWeight.ExtraBold, PeakTheme.textPrimary)
        box(18f, 122f, 284f, 50f, 16f, PeakTheme.positive.copy(alpha = 0.22f), alpha = flash)
        label("2", 30f, 154f, 15f, FontWeight.Bold, PeakTheme.textTertiary)
        label(Fmt.setLine(60.0, 8, unit), 58f, 154f, 22f, FontWeight.ExtraBold, PeakTheme.textPrimary, alpha = old)
        label(Fmt.setLine(65.0, 8, unit), 58f, 154f + newY, 22f, FontWeight.ExtraBold, PeakTheme.textPrimary, alpha = new)

        val colors = listOf(PeakTheme.positive, accent, MuscleGroup.spalle.tint)
        val burstAt = Offset(262f, 147f)
        for (k in 0 until 12) {
            val a = k / 12.0 * PI * 2 + 0.2
            val r = 34 + (k % 3) * 9.0
            val dx = f(cos(a) * r)
            val dy = f(sin(a) * r)
            val ease = Curve.easeOut
            val sc = f(Curve.key(p, listOf(0.0 to 0.3, 0.7 to 0.3, 0.78 to 1.0), ease))
            val ox = f(Curve.key(p, listOf(0.0 to 0.0, 0.7 to 0.0, 0.78 to dx.toDouble()), ease))
            val oy = f(Curve.key(p, listOf(0.0 to 0.0, 0.7 to 0.0, 0.78 to dy.toDouble()), ease))
            val op = f(Curve.key(p, listOf(0.0 to 0.0, 0.419 to 0.0, 0.42 to 1.0, 0.7 to 1.0, 0.78 to 0.0), ease))
            val dot = (if (k % 2 == 1) 5.2f else 7.2f) * sc
            drawCircle(colors[k % 3].copy(alpha = op), dot / 2, burstAt + Offset(ox, oy))
        }
        scale(maxOf(0.0001f, pop), pivot = burstAt) {
            translate(burstAt.x, burstAt.y) {
                box(-24f, -13f, 48f, 26f, 13f, PeakTheme.positive.copy(alpha = 0.18f))
                label("PR", 0f, 5f, 14f, FontWeight.Black, PeakTheme.positive, 0)
            }
        }
    }

    // 3. Le medaglie
    private class Tier(val hi: Color, val lo: Color, val ink: Color, val tint: Color)

    private val tiers = listOf(
        Tier(Color(0xFFF1B27A), Color(0xFFA35F2A), Color(0xFF3A1D08), Color(0xFFE0955A)),
        Tier(Color(0xFFF4F6FA), Color(0xFF98A1B1), Color(0xFF232A36), Color(0xFFDFE4EC)),
        Tier(Color(0xFFFFE69A), Color(0xFFD39A14), Color(0xFF3D2A00), Color(0xFFFFD45E)),
    )

    fun medals(d: DrawScope, t: Double) = with(d) {
        for (i in 0 until 3) {
            val tr = tiers[i]
            // ogni livello parte con il suo ritardo (un terzo di giro); prima del ritardo resta al primo fotogramma
            val local = t - i * period / 3
            val p = if (local < 0) 0.0 else (local / period) % 1.0
            val op = f(Curve.key(p, listOf(0.0 to 0.0, 0.04 to 1.0, 0.3 to 1.0, 0.34 to 0.0, 1.0 to 0.0), Curve.spring))
            val sc = f(Curve.key(p, listOf(0.0 to 0.7, 0.04 to 1.0, 0.3 to 1.0, 0.34 to 0.92, 1.0 to 0.92), Curve.spring))
            val nameOp = f(Curve.key(p, listOf(0.0 to 0.0, 0.03 to 0.0, 0.06 to 1.0, 0.3 to 1.0, 0.34 to 0.0, 1.0 to 0.0)))
            val waveOp = f(Curve.key(p, listOf(0.0 to 0.0, 0.02 to 0.0, 0.04 to 0.8, 0.14 to 0.0, 1.0 to 0.0)))
            val waveSc = f(Curve.key(p, listOf(0.0 to 1.0, 0.02 to 1.0, 0.14 to 1.8, 1.0 to 1.8), Curve.easeOut))
            if (waveOp > 0.001f) drawCircle(tr.tint.copy(alpha = waveOp), 45f * waveSc, Offset(160f, 116f), style = Stroke(3f))
            layer(minOf(1f, op)) { scale(sc.coerceIn(0f, 1.2f), pivot = Offset(160f, 86f)) { medal(this, tr) } }
            label(medalNames[i], 160f, 214f, 20f, FontWeight.ExtraBold, tr.tint, 0, nameOp)
        }
    }

    private fun medal(d: DrawScope, tr: Tier) = with(d) {
        drawCircle(Color.Black.copy(alpha = 0.18f), 50f, Offset(160f, 124f))
        drawPath(poly(listOf(124f to 8f, 150f to 8f, 176f to 76f, 150f to 76f)), tr.lo)
        drawPath(poly(listOf(196f to 8f, 170f to 8f, 144f to 76f, 170f to 76f)), tr.tint.copy(alpha = 0.85f))
        drawCircle(Brush.linearGradient(listOf(tr.hi, tr.lo), start = Offset(112f + 96 * 0.2f, 68f + 96 * 0.1f), end = Offset(112f + 96 * 0.8f, 68f + 96 * 0.95f)), 48f, Offset(160f, 116f))
        drawCircle(tr.ink.copy(alpha = 0.28f), 37f, Offset(160f, 116f), style = Stroke(2f))
        icon(star, 139f, 95f, 42f, tr.ink.copy(alpha = 0.8f))
    }

    // 4. Gli amici
    fun friends(d: DrawScope, p: Double) = with(d) {
        val chip = f(Curve.key(p, listOf(0.0 to 0.0, 0.02 to 0.0, 0.1 to 1.0, 1.0 to 1.0), Curve.spring))
        val bar = f(Curve.key(p, listOf(0.0 to 0.0, 0.3 to 0.0, 0.62 to 1.0, 1.0 to 1.0), Curve.easeOut))
        val crown = f(Curve.key(p, listOf(0.0 to 0.0, 0.64 to 0.0, 0.74 to 1.0, 1.0 to 1.0), Curve.spring))
        val crownY = f(Curve.key(p, listOf(0.0 to -6.0, 0.64 to -6.0, 0.74 to 0.0, 1.0 to 0.0), Curve.spring))
        val board = listOf(Triple(188f, MuscleGroup.petto.tint, "A"), Triple(146f, MuscleGroup.schiena.tint, "M"), Triple(104f, MuscleGroup.gambe.tint, "G"))
        val starts = listOf(0.18 to 0.28, 0.26 to 0.36, 0.34 to 0.44)

        layer(minOf(1f, chip)) {
            scale(maxOf(0.0001f, chip), pivot = Offset(160f, 27f)) {
                box(86f, 8f, 148f, 38f, 19f, accent.copy(alpha = 0.16f), accent.copy(alpha = 0.45f))
                icon(link, 102f, 15f, 24f, accent)
                box(136f, 24f, 84f, 7f, 3.5f, Color.White.copy(alpha = 0.32f))
            }
        }
        box(10f, 62f, 300f, 168f, 26f, panel, panelLine)
        label("Chest Press", 30f, 92f, 13f, FontWeight.Bold, PeakTheme.textSecondary)
        for (i in 0 until 3) {
            val b = board[i]
            val (a, z) = starts[i]
            val op = f(Curve.key(p, listOf(0.0 to 0.0, a to 0.0, z to 1.0, 1.0 to 1.0), Curve.easeOut))
            val dx = f(Curve.key(p, listOf(0.0 to -24.0, a to -24.0, z to 0.0, 1.0 to 0.0), Curve.easeOut))
            val y = (i * 38).toFloat()
            layer(op) {
                translate(dx, 0f) {
                    drawCircle(b.second, 14f, Offset(42f, 126f + y))
                    label(b.third, 42f, 131f + y, 14f, FontWeight.Black, Color(0xFF0B0D14), 0)
                    box(68f, 120f + y, 222f, 12f, 6f, Color.White.copy(alpha = 0.08f))
                    if (bar > 0.001f) drawRoundRect(b.second, Offset(68f, 120f + y), Size(b.first * bar, 12f), CornerRadius(6f))
                }
            }
        }
        layer(minOf(1f, crown)) {
            scale(maxOf(0.0001f, crown), pivot = Offset(38f, 106f + crownY)) { icon(crown_(), 30f, 98f + crownY, 16f, MuscleGroup.spalle.tint) }
        }
    }

    private fun crown_() = crown
}
