package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.data.machineGlyph
import com.gabrieleforghieri.peak.domain.PhotoFraming
import com.gabrieleforghieri.peak.util.tr
import kotlin.math.min

/**
 * L'immagine di un macchinario: la tua foto se c'è, altrimenti il pittogramma (se ce n'è uno) (`Design/ExerciseThumbnail.swift`).
 * Un unico punto in tutta l'app, così la lista, il selettore e la sessione restano coerenti. `zoomable`: se c'è una foto, toccarla la apre intera.
 */
@Composable
fun ExerciseThumbnail(
    exercise: ExerciseEntity,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    glyphRatio: Float = 0.64f,
    zoomable: Boolean = false,
) {
    val photo = rememberExercisePhoto(exercise.photo)
    var showing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(size * 0.30f)
    val tint = exercise.group.tint
    val zoomLabel = tr("Mostra la foto di %@", exercise.displayName())
    Box(
        modifier.size(size).clip(shape)
            .then(if (photo != null) Modifier.border(1.dp, PeakTheme.stroke, shape) else Modifier)
            .then(
                if (zoomable && photo != null) Modifier
                    .clickable(remember { MutableInteractionSource() }, null) { showing = true }
                    .semantics { contentDescription = zoomLabel }
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            FramedPhoto(photo, exercise.photoOffset, Modifier.size(size))
        } else {
            Box(Modifier.size(size).background(tint.copy(alpha = 0.14f)))
            exercise.machineGlyph?.let { MachineGlyphView(it, tint = tint, size = size * glyphRatio) }
        }
    }
    if (showing && photo != null) PhotoViewer(photo, exercise.displayName()) { showing = false }
}

/** Una foto che riempie un quadrato, spostata lungo l'asse che sborda (`FramedPhoto`). Il lato è quello del `modifier`. */
@Composable
fun FramedPhoto(image: ImageBitmap, offset: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val aspect = image.width.toDouble() / image.height
        val vertical = PhotoFraming.isVertical(aspect)
        val shift = PhotoFraming.offsetPoints(offset, aspect, side.toDouble()).toFloat()
        val w = if (vertical) side else side * aspect.toFloat()
        val h = if (vertical) side / aspect.toFloat() else side
        val x = (side - w) / 2 + if (vertical) 0f else shift
        val y = (side - h) / 2 + if (vertical) shift else 0f
        clipRect(0f, 0f, side, side) {
            drawImage(image, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
        }
    }
}

/** La foto intera, per riconoscere il macchinario. Si chiude toccando, con la X o trascinando verso il basso. */
@Composable
private fun PhotoViewer(image: ImageBitmap, title: String, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var drag by remember { mutableFloatStateOf(0f) }
        val closeLabel = tr("Chiudi")
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                image, null, Modifier.fillMaxSize()
                    .graphicsLayer { translationY = drag }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, delta -> drag = (drag + delta).coerceAtLeast(0f) },
                            onDragEnd = { if (drag > 120.dp.toPx()) onClose() else drag = 0f },
                            onDragCancel = { drag = 0f },
                        )
                    }
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
                contentScale = ContentScale.Fit,
            )
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).graphicsLayer { alpha = 1f - (drag / 600f).coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, Modifier.weight(1f), style = PeakTheme.rounded(17, FontWeight.Bold), color = Color.White)
                Box(
                    Modifier.size(36.dp).background(Color.White.copy(alpha = 0.18f), CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
                        .semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) { Icon(PeakIcons.close, null, Modifier.size(18.dp), tint = Color.White) }
            }
        }
    }
}
