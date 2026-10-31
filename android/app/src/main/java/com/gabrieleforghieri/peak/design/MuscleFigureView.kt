package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.MuscleId
import kotlin.math.max
import kotlin.math.min

enum class AnatomyView { front, back }

/** Lo zoom sulla zona che lavora: il rettangolo dei muscoli attivi, con margine e una misura minima. Come `zoomFor` in common/muscles.ts. */
object AnatomyZoom {
    private const val MIN_SIDE = 240.0

    val full = Rect(0f, 0f, AnatomyShapes.WIDTH, AnatomyShapes.HEIGHT)

    fun rect(muscles: List<MuscleId>, view: AnatomyView): Rect {
        val found = muscles.mapNotNull { AnatomyShapes.boxes["${view.name}.${it.name}"] }
        if (found.isEmpty()) return full
        var x0 = found[0][0]; var y0 = found[0][1]; var x1 = found[0][2]; var y1 = found[0][3]
        for (b in found.drop(1)) { x0 = min(x0, b[0]); y0 = min(y0, b[1]); x1 = max(x1, b[2]); y1 = max(y1, b[3]) }
        val padX = max((x1 - x0) * 0.15, 30.0)
        val padY = max((y1 - y0) * 0.15, 30.0)
        x0 -= padX; x1 += padX; y0 -= padY; y1 += padY
        val w = max(x1 - x0, MIN_SIDE)
        val h = max(y1 - y0, MIN_SIDE)
        val cx = (x0 + x1) / 2
        val cy = (y0 + y1) / 2
        return Rect((cx - w / 2).toFloat(), (cy - h / 2).toFloat(), (cx + w / 2).toFloat(), (cy + h / 2).toFloat())
    }
}

/** I percorsi del corpo letti una volta sola: il parsing costa, e la figura compare in molte liste. */
private object AnatomyCache {
    class Piece(val muscle: MuscleId?, val path: Path)

    private fun parse(pieces: List<AnatomyPiece>) = pieces.flatMap { piece -> piece.paths.map { Piece(piece.muscle, PathParser().parsePathString(it).toPath()) } }

    val front: List<Piece> by lazy { parse(AnatomyShapes.front) }
    val back: List<Piece> by lazy { parse(AnatomyShapes.back) }

    fun pieces(view: AnatomyView) = if (view == AnatomyView.front) front else back
}

/** La vista che mostra più dei muscoli dati; a parità, quella di fronte. Come `viewFor` in common/muscles.ts. */
fun anatomyView(muscles: List<MuscleId>): AnatomyView {
    fun count(view: AnatomyView): Int {
        val here = AnatomyShapes.run { if (view == AnatomyView.front) front else back }.mapNotNull { it.muscle }.toSet()
        return muscles.count { it in here }
    }
    return if (count(AnatomyView.front) >= count(AnatomyView.back)) AnatomyView.front else AnatomyView.back
}

private val OFF = Color(0.29f, 0.31f, 0.35f).copy(alpha = 0.55f)
private val OUTLINE = Color(0.11f, 0.12f, 0.14f)
private val SKIN = listOf(Color(0.18f, 0.2f, 0.23f), Color(0.49f, 0.51f, 0.56f), Color(0.7f, 0.73f, 0.76f), Color(0.49f, 0.51f, 0.56f), Color(0.18f, 0.2f, 0.23f))
private val SHEEN = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0f), Color.Black.copy(alpha = 0.25f))

/**
 * La figura dei muscoli, come nelle foto dei macchinari: solo la zona che lavora, i muscoli colorati per gruppo. Stessi disegni
 * e stessa regola dello zoom del web (MuscleFigure.svelte, common/muscles.ts). `onTap` per scegliere i muscoli a mano.
 */
@Composable
fun MuscleFigureView(
    muscles: List<MuscleId>,
    modifier: Modifier = Modifier,
    view: AnatomyView? = null,
    zoom: Boolean = true,
    box: Rect? = null,
    onTap: ((MuscleId) -> Unit)? = null,
) {
    val side = view ?: anatomyView(muscles)
    val area = box ?: if (zoom) AnatomyZoom.rect(muscles, side) else AnatomyZoom.full
    val hot = muscles.toSet()
    val pieces = remember(side) { AnatomyCache.pieces(side) }
    fun matrix(w: Float, h: Float): Matrix {
        val s = min(w / area.width, h / area.height)
        return Matrix().apply {
            translate(w / 2 - area.center.x * s, h / 2 - area.center.y * s)
            scale(s, s)
        }
    }
    Canvas(
        modifier
            .aspectRatio(area.width / area.height)
            .clipToBounds()
            .then(
                if (onTap == null) Modifier else Modifier.pointerInput(side, area) {
                    detectTapGestures { tap ->
                        val m = matrix(size.width.toFloat(), size.height.toFloat())
                        // dall'ultimo disegnato: quello sopra vince
                        val hit = pieces.lastOrNull { piece ->
                            piece.muscle != null && Path().also { it.addPath(piece.path); it.transform(m) }.let { contains(it, tap) }
                        }
                        hit?.muscle?.let(onTap)
                    }
                },
            ),
    ) {
        val m = matrix(size.width, size.height)
        for (piece in pieces) {
            val path = Path().also { it.addPath(piece.path); it.transform(m) }
            val muscle = piece.muscle
            if (muscle == null) {
                drawPath(path, Brush.horizontalGradient(SKIN))
                drawPath(path, Brush.verticalGradient(SHEEN))
            } else {
                val on = muscle in hot
                drawPath(path, if (on) muscle.group.tint else OFF)
                drawPath(path, Brush.verticalGradient(SHEEN))
                drawPath(path, OUTLINE, style = Stroke(1f))
            }
        }
    }
}

private fun contains(path: Path, point: Offset): Boolean {
    val region = android.graphics.Region()
    val bounds = android.graphics.RectF().also { path.asAndroidPath().computeBounds(it, true) }
    val clip = android.graphics.Region(bounds.left.toInt() - 1, bounds.top.toInt() - 1, bounds.right.toInt() + 1, bounds.bottom.toInt() + 1)
    region.setPath(path.asAndroidPath(), clip)
    return region.contains(point.x.toInt(), point.y.toInt())
}

/** La figura di un macchinario in un riquadro, col colore del suo gruppo; niente se non ha muscoli. */
@Composable
fun ExerciseFigure(muscles: List<MuscleId>, group: MuscleGroup, modifier: Modifier = Modifier, view: AnatomyView? = null, size: Dp = 44.dp) {
    if (muscles.isEmpty()) return
    val shape = RoundedCornerShape(size * 0.3f)
    MuscleFigureView(
        muscles, modifier.size(size).clip(shape).background(group.tint.copy(alpha = 0.2f)).padding(size * 0.08f), view,
    )
}
