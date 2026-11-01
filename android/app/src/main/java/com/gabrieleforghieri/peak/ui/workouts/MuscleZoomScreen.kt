package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.gabrieleforghieri.peak.design.pressable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gabrieleforghieri.peak.design.AnatomyShapes
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.design.AnatomyZoom
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.MuscleFigureView
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.domain.MuscleId
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * La vista e le sue regole (`MuscleZoomView.Cam` e i suoi calcoli): il centro nel disegno e i punti per unità del disegno. La zona visibile
 * ha sempre la forma dello schermo. Pura, senza Compose: si prova sul JVM.
 */
data class ZoomCam(val cx: Double, val cy: Double, val k: Double) {
    companion object {
        const val FULL_WIDTH = 724.0
        const val FULL_HEIGHT = 1448.0
        const val MAX_ZOOM = 6.0

        /** Lo zoom minimo: il corpo intero entra nello schermo. */
        fun kMin(width: Double, height: Double): Double = if (width > 0) min(width / FULL_WIDTH, height / FULL_HEIGHT) else 1.0

        /** Zoom entro i limiti e centro che tiene la figura sullo schermo: se sta tutta, resta centrata. */
        fun clamped(c: ZoomCam, width: Double, height: Double): ZoomCam {
            val kMin = kMin(width, height)
            val k = min(max(c.k, kMin), kMin * MAX_ZOOM)
            fun lim(v: Double, view: Double, full: Double) = if (view >= full) full / 2 else min(max(v, view / 2), full - view / 2)
            return ZoomCam(lim(c.cx, width / k, FULL_WIDTH), lim(c.cy, height / k, FULL_HEIGHT), k)
        }

        /** Le due inquadrature: i muscoli che lavorano su questo lato (senza muscoli, il corpo intero) o tutto il corpo. */
        fun target(whole: Boolean, muscles: List<MuscleId>, side: AnatomyView, width: Double, height: Double): ZoomCam {
            val b = if (whole) Rect(0f, 0f, FULL_WIDTH.toFloat(), FULL_HEIGHT.toFloat()) else AnatomyZoom.rect(muscles, side)
            return clamped(ZoomCam(b.center.x.toDouble(), b.center.y.toDouble(), min(width / b.width, height / b.height)), width, height)
        }

        /** Il doppio tocco: dall'inquadratura sui muscoli al corpo intero, e viceversa. */
        fun toggled(cam: ZoomCam, muscles: List<MuscleId>, side: AnatomyView, width: Double, height: Double): ZoomCam {
            val frame = target(false, muscles, side, width, height)
            val atFrame = abs(cam.k / frame.k - 1) < 0.03 && abs(cam.cx - frame.cx) < 8 && abs(cam.cy - frame.cy) < 8
            return if (atFrame) target(true, muscles, side, width, height) else frame
        }

        /** Il pizzico e il trascinamento: il punto del disegno sotto le dita resta lì. `anchor` in pixel dello schermo. */
        fun transformed(cam: ZoomCam, anchor: Offset, pan: Offset, zoom: Float, width: Double, height: Double): ZoomCam {
            val kMin = kMin(width, height)
            val nk = min(max(cam.k * zoom, kMin), kMin * MAX_ZOOM)
            val wx = cam.cx + (anchor.x - width / 2) / cam.k
            val wy = cam.cy + (anchor.y - height / 2) / cam.k
            val panned = ZoomCam(wx - (anchor.x - width / 2) / nk - pan.x / nk, wy - (anchor.y - height / 2) / nk - pan.y / nk, nk)
            return clamped(panned, width, height)
        }
    }
}

/** I muscoli di questo lato, per le targhette. */
fun musclesOnSide(muscles: List<MuscleId>, side: AnatomyView): List<MuscleId> {
    val here = (if (side == AnatomyView.front) AnatomyShapes.front else AnatomyShapes.back).mapNotNull { it.muscle }.toSet()
    return muscles.filter { it in here }
}

/** La figura a tutto schermo, già inquadrata sui muscoli che lavorano (`MuscleZoomView`). Pizzico e trascinamento, doppio tocco per alternare. */
@Composable
fun MuscleZoomScreen(muscles: List<MuscleId>, side: AnatomyView, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        MuscleZoomContent(muscles, side, onClose)
    }
}

@Composable
fun MuscleZoomContent(muscles: List<MuscleId>, initialSide: AnatomyView, onClose: () -> Unit) {
    var side by remember { mutableStateOf(initialSide) }
    var width by remember { mutableStateOf(0.0) }
    var height by remember { mutableStateOf(0.0) }
    var cam by remember { mutableStateOf(ZoomCam(ZoomCam.FULL_WIDTH / 2, ZoomCam.FULL_HEIGHT / 2, 1.0)) }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current

    // all'apertura, cambiando lato o dimensione: inquadra i muscoli di quel lato (senza, il corpo intero)
    LaunchedEffect(width, height, side) { if (width > 0) cam = ZoomCam.target(false, muscles, side, width, height) }

    Column(Modifier.fillMaxSize().background(PeakTheme.bg).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.width(200.dp).background(PeakTheme.cardStrong, RoundedCornerShape(9.dp)).padding(2.dp)) {
                for ((value, label) in listOf(AnatomyView.front to "Fronte", AnatomyView.back to "Retro")) {
                    val selected = side == value
                    Text(
                        tr(label),
                        Modifier.weight(1f).background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(7.dp))
                            .pressable { side = value }.padding(vertical = 6.dp),
                        style = PeakTheme.rounded(13, FontWeight.SemiBold), color = if (selected) PeakTheme.textPrimary else PeakTheme.textTertiary, textAlign = TextAlign.Center,
                    )
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                val closeLabel = tr("Chiudi")
                Box(
                    Modifier.size(36.dp).background(PeakTheme.cardStrong, CircleShape).pressable(onClick = onClose).semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) { Icon(PeakIcons.close, null, Modifier.size(16.dp), tint = PeakTheme.textPrimary) }
            }
        }

        val label = muscles.joinToString(", ") { if (Lang.isItalian()) it.title else it.titleEn }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onSizeChanged { width = it.width.toDouble(); height = it.height.toDouble() }
                .pointerInput(muscles, side, width, height) {
                    detectTapGestures(onDoubleTap = {
                        val to = ZoomCam.toggled(cam, muscles, side, width, height)
                        if (reduce) cam = to else scope.launch {
                            val from = cam
                            Animatable(0f).animateTo(1f, tween(250)) {
                                val t = value.toDouble()
                                cam = ZoomCam(from.cx + (to.cx - from.cx) * t, from.cy + (to.cy - from.cy) * t, from.k + (to.k - from.k) * t)
                            }
                        }
                    })
                }
                .pointerInput(muscles, side, width, height) {
                    detectTransformGestures { centroid, pan, zoom, _ -> cam = ZoomCam.transformed(cam, centroid, pan, zoom, width, height) }
                }
                .semantics { contentDescription = label },
        ) {
            if (width > 0) {
                val w = width / cam.k
                val h = height / cam.k
                MuscleFigureView(
                    muscles, Modifier.fillMaxSize(), view = side,
                    box = Rect((cam.cx - w / 2).toFloat(), (cam.cy - h / 2).toFloat(), (cam.cx + w / 2).toFloat(), (cam.cy + h / 2).toFloat()),
                )
            }
        }

        val mine = musclesOnSide(muscles, side)
        if (mine.isNotEmpty()) MuscleChipGrid(mine, Modifier.padding(16.dp))
    }
}
