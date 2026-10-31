package com.gabrieleforghieri.peak.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.util.tr
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// I pezzi condivisi delle feste (record, fine allenamento, obiettivo, serie di settimane), da `Design/Celebration.swift`:
// coriandoli, ingressi con rimbalzo, il toast. Il linguaggio del movimento è uno solo:
// - «pop» (entrate di festa): da scala 0.6-0.9 e trasparente a pieno, con molla, mai da zero;
// - feedback piccolo: molla morbida; dissolvenze e ingressi ease-out 0,2-0,3 s;
// Con «Riduci movimento» restano solo dissolvenze brevi: niente coriandoli, scale, spostamenti, rotazioni.

/** SplitMix64: gli stessi semi danno gli stessi pezzi. */
private class SeededRandom(seed: Long) {
    private var state = seed

    /** Un numero in 0..<1. */
    fun next(): Double {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}

private class Piece(
    val vx: Double, val vy: Double, val spin: Double, val phase: Double, val flip: Double,
    val w: Float, val h: Float, val round: Boolean, val color: Color,
)

private const val BURST_SECONDS = 1.4
private const val BURST_COUNT = 96
private const val DRAG = 1.3        // attrito dell'aria: i pezzi frenano in salita
private const val GRAVITY = 1100.0  // dp/s²

private fun makePieces(shot: Long, accent: Color): List<Piece> {
    val random = SeededRandom(shot * -0x61c8864680b583ebL + 1)
    val colors = listOf(accent) + MuscleGroup.entries.map { it.tint }
    return List(BURST_COUNT) {
        // a ventaglio verso l'alto, un po' più largo dei lati
        val angle = -PI / 2 + (random.next() - 0.5) * 2.3
        val speed = 430 + random.next() * 640
        val size = 6 + random.next() * 5
        val round = random.next() < 0.3
        Piece(
            cos(angle) * speed, sin(angle) * speed, (random.next() - 0.5) * 16, random.next() * PI * 2, 6 + random.next() * 8,
            size.toFloat(), (if (round) size else size * 1.7).toFloat(), round, colors[(random.next() * colors.size).toInt() % colors.size],
        )
    }
}

/**
 * Un'esplosione di coriandoli, una volta sola. Ogni cambio di `trigger` la fa ripartire. Disegna solo mentre dura (~1,4 s); il
 * disegno è una funzione del tempo trascorso (niente stato per frame). `origin` è il punto di partenza nello spazio di questa vista,
 * in frazioni (0..1) della larghezza e dell'altezza.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, origin: Offset = Offset(0.5f, 0.2f)) {
    val reduce = LocalReduceMotion.current
    val accent = LocalAccent.current
    var pieces by remember { mutableStateOf<List<Piece>>(emptyList()) }
    val clock = remember { Animatable(0f) }
    var last by remember { mutableStateOf(trigger) }
    var shots by remember { mutableStateOf(0L) }
    LaunchedEffect(trigger) {
        if (trigger == last || reduce) { last = trigger; return@LaunchedEffect }
        last = trigger
        shots += 1
        pieces = makePieces(shots, accent)
        clock.snapTo(0f)
        clock.animateTo(BURST_SECONDS.toFloat(), tween((BURST_SECONDS * 1000).toInt(), easing = androidx.compose.animation.core.LinearEasing))
        pieces = emptyList()
    }
    Canvas(modifier.fillMaxSize()) {
        val t = clock.value.toDouble()
        if (pieces.isEmpty() || t < 0 || t >= BURST_SECONDS) return@Canvas
        // negli ultimi 30% sfuma
        val fadeStart = BURST_SECONDS * 0.7
        val alpha = if (t > fadeStart) maxOf(0.0, 1 - (t - fadeStart) / (BURST_SECONDS - fadeStart)) else 1.0
        val glide = (1 - exp(-DRAG * t)) / DRAG
        val density = density
        for (p in pieces) {
            val x = size.width * origin.x + (p.vx * glide * density).toFloat()
            val y = size.height * origin.y + ((p.vy * glide + 0.5 * GRAVITY * t * t) * density).toFloat()
            val color = p.color.copy(alpha = alpha.toFloat())
            translate(x, y) {
                rotate(Math.toDegrees(p.phase + p.spin * t).toFloat(), Offset.Zero) {
                    val w = p.w * density
                    val h = p.h * density
                    if (p.round) {
                        drawCircle(color, w / 2, Offset.Zero)
                    } else {
                        // il foglietto che gira su sé stesso: si schiaccia e si riapre
                        val squash = (0.25 + 0.75 * abs(cos(p.flip * t))).toFloat()
                        drawRect(color, Offset(-w / 2, -h * squash / 2), Size(w, h * squash))
                    }
                }
            }
        }
    }
}

// ---- ingressi e rimbalzi -------------------------------------------------------------------

/** Ingresso di festa: da scala `from` (mai da zero) e trasparente a pieno, con rimbalzo. Si accende quando `shown` diventa vero. */
@Composable
fun Modifier.pop(shown: Boolean, from: Float = 0.7f, rotation: Float = 0f, delayMs: Int = 0): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        if (reduce) progress.animateTo(if (shown) 1f else 0f, tween(Motion.FADE, delayMs))
        else progress.animateTo(if (shown) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.65f, stiffness = 200f))
    }
    return this.graphicsLayer {
        val p = progress.value
        scaleX = if (reduce) 1f else from + (1f - from) * p
        scaleY = scaleX
        rotationZ = if (reduce) 0f else rotation * (1f - p)
        alpha = p.coerceIn(0f, 1f)
    }
}

/** Ingresso morbido per le schede e i testi: dissolve e sale da `offset` dp. `delayMs` per la cascata (50-80 ms a fratello). */
@Composable
fun Modifier.enter(shown: Boolean, offset: Float = 12f, delayMs: Int = 0): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(shown) { progress.animateTo(if (shown) 1f else 0f, tween(if (reduce) Motion.FADE else Motion.ENTER, delayMs, Motion.easeOut)) }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = if (reduce) 0f else (1f - progress.value) * offset.dp.toPx()
    }
}

/**
 * Un colpo di scala (e, con `wobble` in gradi, un'oscillazione che si assesta) ogni volta che `trigger` cambia. Per la fiamma della
 * serie, l'anello di oggi, il trofeo. Non parte alla comparsa.
 */
@Composable
fun Modifier.bounce(trigger: Int, peak: Float = 1.3f, wobble: Float = 0f): Modifier {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    val angle = remember { Animatable(0f) }
    var last by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger == last) return@LaunchedEffect
        last = trigger
        if (reduce) return@LaunchedEffect
        coroutineScope {
            launch { scale.animateTo(peak, tween(160)); scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 300f)) }
            launch {
                angle.animateTo(0f, keyframes {
                    durationMillis = 620
                    -wobble at 90; wobble at 220; -wobble * 0.55f at 350; wobble * 0.25f at 480; 0f at 620
                })
            }
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value; scaleY = scale.value; rotationZ = angle.value
    }
}

// ---- toast ---------------------------------------------------------------------------------

/** La capsula «Nuovo massimale!»: sale dall'alto, resta ~1,8 s, esce da dove è entrata. */
@Composable
fun CelebrationToast(text: String, modifier: Modifier = Modifier, icon: ImageVector = PeakIcons.workouts, tint: Color = PeakTheme.positive) {
    Row(
        modifier
            .shadow(14.dp, CircleShape, ambientColor = tint.copy(alpha = 0.4f), spotColor = tint.copy(alpha = 0.4f))
            .background(tint, CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = PeakTheme.bg)
        Text(tr(text), style = PeakTheme.rounded(15, FontWeight.ExtraBold), color = PeakTheme.bg)
    }
}

/** Il toast di festa in alto: appare a ogni aumento di `trigger`, sparisce da solo dopo ~1,8 s. */
@Composable
fun CelebrationToastHost(text: String, trigger: Int, modifier: Modifier = Modifier, icon: ImageVector = PeakIcons.workouts, tint: Color = PeakTheme.positive) {
    var shown by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(0) }
    LaunchedEffect(trigger) {
        // `last`: se la vista torna a comparire con un trigger vecchio, il toast non riparte
        if (trigger <= 0 || trigger == last) return@LaunchedEffect
        last = trigger
        shown = true
        delay(1800)
        shown = false
    }
    Box(modifier.fillMaxSize().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(shown, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            CelebrationToast(text, icon = icon, tint = tint)
        }
    }
}
