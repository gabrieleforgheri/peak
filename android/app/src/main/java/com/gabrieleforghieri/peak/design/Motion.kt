package com.gabrieleforghieri.peak.design

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Il movimento di tutti i giorni, specchio dei token del web (app.css --dur-1..4, --ease-out; fx.ts) e di `Design/Motion.swift`.
 * Sobrio: breve, ease-out, pochi punti, mai rimbalzi. Con «Riduci movimento» restano solo le dissolvenze e i numeri sono subito
 * quelli finali. Le entrate partono una volta, la prima volta che il blocco entra nello schermo.
 */
object Motion {
    const val PRESS = 120      // --dur-1: tocco
    const val FADE = 200       // --dur-2: colori, dissolvenze
    const val ENTER = 300      // --dur-3: entrata di un blocco
    const val CHART = 600      // --dur-4: grafici che si disegnano

    /** cubic-bezier(0.23, 1, 0.32, 1), come --ease-out. */
    val easeOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    fun <T> out(durationMs: Int, delayMs: Int = 0) = tween<T>(durationMs, delayMs, easeOut)

    /** Ritardo della cascata: 30 ms l'uno, dall'ottavo in poi tutti insieme. */
    fun stagger(index: Int): Int = min(max(index, 0), 8) * 30

    /** Chi ha già fatto la sua entrata (chiave stabile): le righe che una lista ricrea scorrendo non la ripetono. */
    val seen: MutableSet<String> = HashSet()
}

/** Vero se l'utente ha tolto le animazioni da Impostazioni di Android (la durata delle animazioni a zero). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (_: Exception) { false }
    }
}

/** Il blocco entra (sale di 8 dp e appare) la prima volta che si vede, in cascata con gli altri per `index`. */
fun Modifier.appear(index: Int = 0, key: String? = null): Modifier = composed2 {
    val reduce = LocalReduceMotion.current
    val start = key != null && key in Motion.seen
    val progress = remember { Animatable(if (start || reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            if (key != null) Motion.seen += key
            progress.animateTo(1f, Motion.out(if (reduce) Motion.FADE else Motion.ENTER, if (reduce) 0 else Motion.stagger(index)))
        }
    }
    Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 8.dp.toPx()
    }
}

/** Disegna da sinistra a destra (linee, barre): una maschera che si apre. */
fun Modifier.drawIn(delayMs: Int = 0): Modifier = composed2 {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, Motion.out(if (reduce) Motion.FADE else Motion.CHART, if (reduce) 0 else delayMs)) }
    Modifier.drawWithContent {
        val width = size.width * progress.value
        clipRect(left = -20.dp.toPx(), top = -20.dp.toPx(), right = width, bottom = size.height + 20.dp.toPx()) {
            this@drawWithContent.drawContent()
        }
    }
}

/** Una barra che cresce dal basso la prima volta in vista; `index` per la cascata. */
fun Modifier.growUp(index: Int = 0): Modifier = composed2 {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, Motion.out(if (reduce) Motion.FADE else Motion.CHART, if (reduce) 0 else Motion.stagger(index))) }
    Modifier.graphicsLayer {
        scaleY = max(progress.value, 0.001f)
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }
}

/** Stato premuto per righe e card: scala appena, 120 ms ease-out. Sostituisce il ripple dove il bottone è una card. */
fun Modifier.pressable(scale: Float = 0.98f, onClick: () -> Unit): Modifier = composed2 {
    val reduce = LocalReduceMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by androidx.compose.animation.core.animateFloatAsState(if (pressed && !reduce) scale else 1f, Motion.out(Motion.PRESS), label = "press")
    val a by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.85f else 1f, Motion.out(Motion.PRESS), label = "pressAlpha")
    Modifier
        .graphicsLayer { scaleX = s; scaleY = s; alpha = a }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Un numero che conta fino al valore la prima volta in vista e scorre quando il dato cambia. `format` riceve il valore intermedio. */
@Composable
fun CountText(value: Double, modifier: Modifier = Modifier, style: TextStyle = PeakTheme.display(24), format: (Double) -> String = { it.roundToInt().toString() }) {
    val reduce = LocalReduceMotion.current
    val shown = remember { Animatable(if (reduce) value.toFloat() else 0f) }
    LaunchedEffect(value) {
        if (reduce) shown.snapTo(value.toFloat()) else shown.animateTo(value.toFloat(), tween(if (shown.value == 0f) Motion.CHART else 400, easing = Motion.easeOut))
    }
    Text(format(shown.value.toDouble()), modifier, style = style)
}

/** Una molla morbida, per i piccoli feedback (`.spring(duration: 0.3, bounce: 0.15)`). */
fun <T> softSpring() = spring<T>(dampingRatio = 0.85f, stiffness = 400f)

/** Una molla con rimbalzo, per le entrate di festa (`.spring(duration: 0.5, bounce: 0.35)`). */
fun <T> popSpring() = spring<T>(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow * 2)

/** `Modifier.composed` senza le avvertenze: una piccola scorciatoia per i modificatori che tengono stato. */
@Suppress("UnusedReceiverParameter")
private inline fun Modifier.composed2(crossinline factory: @Composable () -> Modifier): Modifier =
    this.then(composed { factory() })
