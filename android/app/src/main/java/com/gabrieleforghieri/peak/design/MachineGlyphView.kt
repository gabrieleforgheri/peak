package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.domain.GlyphShape
import com.gabrieleforghieri.peak.domain.MachineGlyph
import kotlin.math.max
import kotlin.math.min

/** Il pittogramma di un attrezzo, disegnato dalle forme di `MachineGlyph` (`Design/MachineGlyph.swift`). */
@Composable
fun MachineGlyphView(glyph: MachineGlyph, modifier: Modifier = Modifier, tint: Color = Color.White, size: Dp = 26.dp) {
    Canvas(modifier.size(size)) {
        val s = min(this.size.width, this.size.height)
        val thin = max(1f, s * 0.055f)
        val thick = max(1.4f, s * 0.10f)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        for (shape in glyph.shapes) when (shape) {
            is GlyphShape.Line -> drawLine(tint, p(shape.x1, shape.y1), p(shape.x2, shape.y2), thin, StrokeCap.Round)
            is GlyphShape.Bar -> drawLine(tint, p(shape.x1, shape.y1), p(shape.x2, shape.y2), thick, StrokeCap.Round)
            is GlyphShape.SolidRect -> {
                val w = shape.w * s
                val h = shape.h * s
                drawRoundRect(tint, p(shape.x, shape.y), Size(w, h), CornerRadius(min(w, h) * 0.35f))
            }
            is GlyphShape.Dot -> drawCircle(tint, shape.r * s, p(shape.cx, shape.cy))
            is GlyphShape.Ring -> drawCircle(tint, shape.r * s, p(shape.cx, shape.cy), style = Stroke(thin))
        }
    }
}
