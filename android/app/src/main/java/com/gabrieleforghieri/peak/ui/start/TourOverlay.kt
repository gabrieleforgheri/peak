package com.gabrieleforghieri.peak.ui.start

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.Motion
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.util.tr
import kotlin.math.max
import kotlin.math.min

private val CardBg = Color(0xFF151A28)
private val Veil = Color(4 / 255f, 7 / 255f, 14 / 255f)

// MARK: - Il riquadro e la carta

/**
 * Il riquadro luminoso con la carta del passo (`TourCoach`). `target` è il controllo (nelle coordinate di `size`, in pixel); senza,
 * c'è solo la carta in basso. I passi che vivono dentro un foglio (`up`) non bloccano i tocchi: il resto del foglio resta leggibile
 * e toccabile.
 */
@Composable
fun TourCoach(
    step: TourState.Step,
    target: Rect?,
    size: Size,
    topInset: Float,
    bottomInset: Float,
    onSkip: () -> Unit,
    onNext: (TourState.Step) -> Unit,
    modifier: Modifier = Modifier,
    fly: Boolean = false,
) {
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val accent = LocalAccent.current
    val up = step.up
    fun px(dp: Float) = with(density) { dp.dp.toPx() }

    val raw = target?.takeIf { it.width > 0 }?.let { r ->
        val x = max(px(3f), r.left - px(6f))
        val y = max(px(3f), r.top - px(6f))
        Rect(x, y, max(x, min(size.width - px(3f), r.right + px(6f))), max(y, min(size.height - px(3f), r.bottom + px(6f))))
    }
    // il riquadro vola da un controllo all'altro (solo nel cambio di passo, non mentre la pagina scorre)
    var last by remember { mutableStateOf(Rect.Zero) }
    if (raw != null) last = raw
    val animated by animateRectAsState(last, if (fly && !reduce) Motion.out(450) else snap(), label = "tour-hole")
    val hole = if (raw != null) animated else null

    Box(modifier.size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })) {
        if (hole != null) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = px(16f)
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, this@Canvas.size))
                    addRoundRect(RoundRect(hole, CornerRadius(radius)))
                }
                drawPath(path, Veil.copy(alpha = if (up) 0.55f else 0.78f))
            }
            if (!up) Blockers(hole, size)
            Ring(hole, accent, reduce, density.density)
        }
        if (!up || hole != null) Card(step, hole, size, topInset, bottomInset, up, onSkip, onNext)
    }
}

/** Quattro fasce attorno al buco: bloccano i tocchi fuori, e dentro il buco arrivano al controllo vero. */
@Composable
private fun Blockers(h: Rect, size: Size) {
    val density = LocalDensity.current
    fun band(l: Float, t: Float, r: Float, b: Float): @Composable () -> Unit = {
        if (r > l && b > t) Box(
            Modifier.offset { IntOffset(l.toInt(), t.toInt()) }
                .size(with(density) { (r - l).toDp() }, with(density) { (b - t).toDp() })
                .pointerInput(Unit) { awaitEachGesture { while (true) { val e = awaitPointerEventAny(); e.changes.forEach { it.consume() } } } },
        )
    }
    band(0f, 0f, size.width, h.top)()
    band(0f, h.bottom, size.width, size.height)()
    band(0f, h.top, h.left, h.bottom)()
    band(h.right, h.top, size.width, h.bottom)()
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPointerEventAny() = awaitPointerEvent(PointerEventPass.Initial)

@Composable
private fun Ring(hole: Rect, accent: Color, reduce: Boolean, density: Float) {
    val p = if (reduce) 1f else {
        val transition = rememberInfiniteTransition(label = "tour-ring")
        val v by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)), RepeatMode.Restart), label = "p")
        v
    }
    Canvas(Modifier.fillMaxSize()) { drawRing(hole, accent, p, reduce, density) }
}

internal fun DrawScope.drawRing(hole: Rect, accent: Color, p: Float, reduce: Boolean, density: Float) {
    val r = 16f * density
    if (!reduce) {
        val grow = 16f * density * p
        drawRoundRect(
            accent.copy(alpha = 0.55f * (1 - p)), Offset(hole.left - grow / 2, hole.top - grow / 2), Size(hole.width + grow, hole.height + grow),
            CornerRadius(r + 8f * density * p), style = Stroke(grow),
        )
    }
    drawRoundRect(accent.copy(alpha = 0.18f), hole.topLeft, hole.size, CornerRadius(r), style = Stroke(8f * density))
    drawRoundRect(accent, Offset(hole.left + density, hole.top + density), Size(hole.width - 2 * density, hole.height - 2 * density), CornerRadius(r), style = Stroke(2f * density))
}

/** Fuori dal buco: sotto se c'è posto, sopra altrimenti, centrata sul controllo; senza controllo in basso, senza velo. */
@Composable
private fun Card(
    step: TourState.Step, hole: Rect?, size: Size, topInset: Float, bottomInset: Float, up: Boolean, onSkip: () -> Unit, onNext: (TourState.Step) -> Unit,
) {
    val density = LocalDensity.current
    fun px(dp: Float) = with(density) { dp.dp.toPx() }
    fun dp(px: Float) = with(density) { px.toDp() }
    val w = min(px(380f), size.width - px(32f))
    val body: @Composable () -> Unit = { CardBody(step, dp(w), tightFor(hole, size, topInset, bottomInset, density.density), up, onSkip, onNext) }
    if (hole == null) {
        Box(Modifier.fillMaxSize().padding(bottom = dp(bottomInset) + 24.dp), contentAlignment = Alignment.BottomCenter) { body() }
        return
    }
    val left = max(px(16f), min(hole.center.x - w / 2, size.width - px(16f) - w))
    val below = hole.bottom + px(14f)
    val top = topInset + px(8f)
    val bottom = size.height - bottomInset - px(8f)
    val roomBelow = bottom - below
    val roomAbove = hole.top - px(14f) - top
    val under = roomBelow >= px(170f) || (roomAbove < px(170f) && roomBelow >= roomAbove)
    if (under) Box(Modifier.offset { IntOffset(left.toInt(), below.toInt()) }) { body() }
    else Box(Modifier.fillMaxSize().padding(start = dp(max(0f, left)), bottom = dp(max(0f, size.height - hole.top + px(14f)))), contentAlignment = Alignment.BottomStart) { body() }
}

/** Poco posto (SE, foglio basso): la carta lascia la riga di spiegazione e si stringe, così sta comunque fuori dal riquadro. */
private fun tightFor(hole: Rect?, size: Size, topInset: Float, bottomInset: Float, density: Float): Boolean {
    hole ?: return false
    val top = topInset + 8 * density
    val bottom = size.height - bottomInset - 8 * density
    val roomBelow = bottom - (hole.bottom + 14 * density)
    val roomAbove = hole.top - 14 * density - top
    val under = roomBelow >= 170 * density || (roomAbove < 170 * density && roomBelow >= roomAbove)
    val room = max(80 * density, min(if (under) roomBelow else roomAbove, size.height - 32 * density))
    return room < 150 * density
}

/** La carta del passo: titolo, riga, «Salta» e «Avanti» (o «Fallo per continuare»). */
@Composable
fun CardBody(step: TourState.Step, width: Dp, tight: Boolean, up: Boolean, onSkip: () -> Unit, onNext: (TourState.Step) -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    val text = TourState.text(step)
    val title = tr(TourState.title(step))
    AnimatedContent(step, transitionSpec = { fadeIn(tween(Motion.FADE)) togetherWith fadeOut(tween(Motion.FADE)) }, label = "tour-card") { s ->
        val t = TourState.text(s)
        Column(
            Modifier.width(width).shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.6f), spotColor = Color.Black.copy(alpha = 0.6f))
                .background(CardBg, shape).border(1.dp, accent.copy(alpha = 0.4f), shape).padding(16.dp)
                .semantics { contentDescription = title },
        ) {
            Text(tr(TourState.title(s)), style = PeakTheme.rounded(if (tight) 16 else 18, FontWeight.Bold), color = PeakTheme.textPrimary)
            if (t != null && !tight) Text(tr(t), Modifier.padding(top = 4.dp, bottom = 12.dp), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary)
            Row(Modifier.padding(top = if (tight || t == null) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Salta"), Modifier.pressable(onClick = onSkip).heightIn(min = 44.dp).padding(end = 12.dp).padding(top = 12.dp),
                    style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textSecondary,
                )
                Box(Modifier.weight(1f))
                val next = s.next
                if (next != null) {
                    Row(
                        Modifier.background(accent, RoundedCornerShape(16.dp)).pressable { onNext(next) }.padding(horizontal = 22.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(tr("Avanti"), style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.bg)
                        Icon(StartIcons.chevronRight, null, Modifier.size(16.dp), tint = PeakTheme.bg)
                    }
                } else if (!s.up) {
                    Text(tr("Fallo per continuare"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                }
            }
        }
    }
}

// MARK: - Il giro sulle schermate vere

/**
 * Le schede e «Avvia allenamento», sopra la barra delle schede, e la sessione («+ Serie»); gli altri passi stanno nei loro fogli
 * ([TourSheet]). Sta nel guscio dell'app e riempie la finestra: la barra delle schede è in basso, fuori dallo spazio delle schermate.
 */
@Composable
fun TourOverlay(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val size = Size(window.width.toFloat(), window.height.toFloat())
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
    val step = TourState.step
    val phase = TourState.phase
    var fly by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(step) {
        fly = true
        kotlinx.coroutines.delay(520)
        fly = false
    }
    val update = rememberUpdateSettings()
    val haptics = LocalApp.current.haptics

    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }, contentAlignment = Alignment.TopStart) {
        val shows = phase == TourState.Phase.marks && (step == TourState.Step.tabs || step == TourState.Step.start || step == TourState.Step.set)
        // sposta il disegno così che le coordinate della finestra tornino: lo spazio delle schermate può non cominciare a zero
        Box(Modifier.offset { IntOffset(-origin.x.toInt(), -origin.y.toInt()).let { it } }.size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })) {
            if (shows) {
                val target = when (step) {
                    TourState.Step.tabs -> tabBar(size, bottomInset, density.density)
                    else -> TourState.step.targets.firstNotNullOfOrNull { TourTargets.rects[it] }
                }
                TourCoach(
                    step, target, size, topInset, bottomInset, fly = fly,
                    onSkip = { TourState.skip(update) },
                    onNext = { haptics.tap(); TourState.step = it },
                )
            } else if (phase == TourState.Phase.cheer) {
                TourCheer(size, topInset, onEnd = { haptics.tap(); TourState.end() })
            }
        }
    }
}

/** Dove sta la barra delle schede di Material (80 dp sopra la barra di sistema): la usa il passo «Quattro sezioni». */
private fun tabBar(size: Size, bottomInset: Float, density: Float): Rect =
    Rect(8 * density, size.height - bottomInset - 80 * density + 4 * density, size.width - 8 * density, size.height - bottomInset - 4 * density)

/** La prima serie fatta, in alto (`TourCheer`). */
@Composable
fun TourCheer(size: Size, topInset: Float, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    var drawn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { drawn = true }
    val k by animateFloatAsState(if (drawn || reduce) 1f else 0f, if (reduce) snap() else tween(500, 200, Motion.easeOut), label = "cheer-check")
    Box(modifier.size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() }), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = with(density) { topInset.toDp() } + 16.dp).widthIn(max = 380.dp)
                .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.6f), spotColor = Color.Black.copy(alpha = 0.6f))
                .background(CardBg, shape).border(1.dp, accent.copy(alpha = 0.4f), shape).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                Modifier.padding(bottom = 4.dp).size(48.dp).background(PeakTheme.positive.copy(alpha = 0.16f), CircleShape).border(2.5.dp, PeakTheme.positive, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PeakIcons.check, null, Modifier.size(22.dp).alpha(k).androidScale(0.4f + 0.6f * k), tint = PeakTheme.positive)
            }
            Text(tr("Prima serie fatta"), style = PeakTheme.rounded(18, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(tr("Ora sai come si fa."), Modifier.padding(bottom = 10.dp), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center)
            PrimaryButton(tr("Fine"), onEnd)
        }
    }
}

private fun Modifier.androidScale(s: Float): Modifier = graphicsLayer { scaleX = s; scaleY = s }

// MARK: - Dentro un foglio

/**
 * I passi del tutorial che vivono dentro un foglio (`tourMarks`): un anello attorno al controllo, dentro il foglio, e la carta in una
 * finestra leggera sopra di esso. Non c'è il velo: il foglio è una finestra a parte e non si lascia scurire dall'app; il resto del
 * foglio resta leggibile e toccabile, come sull'iPhone. Un passo senza il suo controllo (non c'è ancora) non mostra niente.
 */
@Composable
internal fun SheetMarks(kind: TourState.Sheet) {
    if (TourState.phase != TourState.Phase.marks) return
    val step = TourState.step
    val mine = if (kind == TourState.Sheet.days) listOf(TourState.Step.pick) else listOf(TourState.Step.load, TourState.Step.save)
    if (step !in mine) return
    val target = step.targets.firstNotNullOfOrNull { TourTargets.rects[it] } ?: return
    val density = LocalDensity.current
    val accent = LocalAccent.current
    val reduce = LocalReduceMotion.current
    val update = rememberUpdateSettings()
    val haptics = LocalApp.current.haptics
    var origin by remember { mutableStateOf(Offset.Zero) }
    val hole = Rect(target.left - 6 * density.density, target.top - 6 * density.density, target.right + 6 * density.density, target.bottom + 6 * density.density)
    // l'anello: un segno senza ingombro che disegna nelle coordinate della finestra del foglio
    Box(
        Modifier.size(1.dp).onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind { translate(-origin.x, -origin.y) { drawRing(hole, accent, 1f, true, density.density) } },
    )
    val card = remember { mutableStateOf(IntSize.Zero) }
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val d = density.density
                val x = max((16 * d).toInt(), min((hole.center.x - popupContentSize.width / 2).toInt(), windowSize.width - (16 * d).toInt() - popupContentSize.width))
                val below = (hole.bottom + 14 * d).toInt()
                val fitsBelow = below + popupContentSize.height <= windowSize.height - (8 * d).toInt()
                val y = if (fitsBelow) below else max((8 * d).toInt(), (hole.top - 14 * d).toInt() - popupContentSize.height)
                return IntOffset(x, y)
            }
        },
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        CardBody(step, 320.dp, tight = false, up = true, onSkip = { TourState.skip(update) }, onNext = { haptics.tap(); TourState.step = it })
    }
}

// MARK: - Suggerimenti del primo macchinario

/**
 * Un suggerimento sul controllo `target` (segnato con [tourTarget]), dentro la schermata stessa (`machineTip`): il tutorial principale
 * sta sotto il foglio, quindi i due non si sovrappongono mai. A differenza del tutorial non blocca i tocchi: solo l'anello attorno al
 * controllo e un riquadro con «Ok» (passo dopo passo) o «Fine» (ultimo), più «Salta» per spegnerli per sempre. Va messo in un `Box` che
 * riempie la schermata, sopra il resto.
 */
@Composable
fun MachineTip(target: String, show: Boolean, title: String, text: String, onNext: () -> Unit, modifier: Modifier = Modifier, last: Boolean = false) {
    val rect = TourTargets.rects[target]
    if (!show || rect == null) return
    val density = LocalDensity.current
    val accent = LocalAccent.current
    val update = rememberUpdateSettings()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.onSizeChanged { size = it }) {
        val d = density.density
        val hole = Rect(rect.left - origin.x - 8 * d, rect.top - origin.y - 8 * d, rect.right - origin.x + 8 * d, rect.bottom - origin.y + 8 * d)
        val below = hole.center.y < size.height / 2
        Canvas(Modifier.fillMaxSize()) { drawRing(hole, accent, 1f, true, d) }
        val shape = RoundedCornerShape(18.dp)
        val pad = with(density) { max(0f, if (below) hole.bottom + 12 * d else size.height - hole.top + 12 * d).toDp() }
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = if (below) pad else 0.dp, bottom = if (below) 0.dp else pad), contentAlignment = if (below) Alignment.TopCenter else Alignment.BottomCenter) {
            Column(
                Modifier.widthIn(max = 440.dp).shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
                    .background(CardBg, shape).border(1.dp, accent.copy(alpha = 0.4f), shape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary)
                Text(text, style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("Salta"), Modifier.pressable { update { copy(machineTipsSeen = true) } }.padding(vertical = 8.dp),
                        style = PeakTheme.rounded(13, FontWeight.Bold), color = accent,
                    )
                    Box(Modifier.weight(1f))
                    Text(
                        tr(if (last) "Fine" else "Ok"),
                        Modifier.background(accent, RoundedCornerShape(16.dp)).pressable { if (last) update { copy(machineTipsSeen = true) }; onNext() }.padding(horizontal = 36.dp, vertical = 12.dp),
                        style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.bg,
                    )
                }
            }
        }
    }
}
