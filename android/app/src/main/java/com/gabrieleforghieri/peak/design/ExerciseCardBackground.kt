package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.data.machineGlyph
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

/**
 * Sfondo della card di un esercizio: la foto del macchinario sfocata o il pittogramma sfocato, con un velo scuro a gradiente
 * (`Design/ExerciseCardBackground.swift`). Sta nella sessione di allenamento, nella card del massimale e nelle righe della scheda;
 * va messo sotto il contenuto (come `.background`), riempie il genitore e non prende i tocchi. La sfocatura vale da Android 12.
 */
@Composable
fun ExerciseCardBackground(exercise: ExerciseEntity, modifier: Modifier = Modifier) {
    val photo = rememberExercisePhoto(exercise.photo)
    Box(modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.BottomEnd) {
        if (photo != null) {
            Image(
                photo, null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }.blur(10.dp).alpha(0.65f),
                contentScale = ContentScale.Crop,
                // brightness(-0.15)
                colorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, -38f, 0f, 1f, 0f, 0f, -38f, 0f, 0f, 1f, 0f, -38f, 0f, 0f, 0f, 1f, 0f,
                ))),
            )
        } else {
            exercise.machineGlyph?.let {
                MachineGlyphView(it, Modifier.offset(20.dp, 15.dp).alpha(0.28f).blur(1.5.dp), tint = exercise.group.tint, size = 210.dp)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.horizontalGradient(
                    0f to Color(0.06f, 0.07f, 0.09f, 0.90f), 0.55f to Color(0.06f, 0.07f, 0.09f, 0.65f), 1f to Color(0.05f, 0.06f, 0.08f, 0.40f),
                ),
            )
        }
    }
}
