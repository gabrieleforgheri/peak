package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.domain.AccentPalette
import com.gabrieleforghieri.peak.domain.MuscleGroup
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * I token visivi dell'app, tradotti da `Design/Theme.swift`. L'interfaccia è solo scura, come su iPhone: Material 3 fa da base
 * (componenti, accessibilità), ma colori, forme e numeri sono quelli di Peak.
 */
object PeakTheme {
    // Superfici
    val bg = Color(0xFF0B0D14)
    val bgRaised = Color(0xFF12151F)
    val card = Color.White.copy(alpha = 0.055f)
    val cardStrong = Color.White.copy(alpha = 0.09f)
    val stroke = Color.White.copy(alpha = 0.10f)
    val strokeStrong = Color.White.copy(alpha = 0.18f)

    // Testo
    val textPrimary = Color.White.copy(alpha = 0.95f)
    val textSecondary = Color.White.copy(alpha = 0.58f)
    val textTertiary = Color.White.copy(alpha = 0.35f)

    // Semantica
    val positive = Color(0xFF5FD98A)
    val negative = Color(0xFFFF6B6B)

    // I metalli delle medaglie
    val bronze = Color(0xFFE0955A)
    val silver = Color(0xFFDFE4EC)
    val gold = Color(0xFFFFD45E)

    // Geometria
    val radius = 20.dp
    val radiusSmall = 14.dp
    val padding = 16.dp

    /** Il secondo alone dello sfondo, in alto a destra. */
    val glowBlue = Color(0xFF4A5BFF)

    // Font: Peak usa il «tondo» di sistema con le cifre tabellari, così i numeri non ballano. Su Android la famiglia è quella
    // di sistema; le cifre tabellari si chiedono con `tnum`.
    private const val TABULAR = "tnum"

    /** Font dei numeri grandi: largo, bold, tabellare. */
    fun display(size: Int): TextStyle =
        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR)

    fun rounded(size: Int, weight: FontWeight = FontWeight.SemiBold): TextStyle =
        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, fontWeight = weight)

    /** Per i testi in cui le cifre non devono ballare (timer, pesi). */
    fun tabular(size: Int, weight: FontWeight = FontWeight.SemiBold): TextStyle = rounded(size, weight).copy(fontFeatureSettings = TABULAR)

    /** Sfondo dell'app: nero bluastro con due aloni dell'accento in alto. */
    fun backgroundModifier(accent: Color): Modifier = Modifier
        .background(bg)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.15f, -size.height * 0.05f), radius = 420.dp.toPx(),
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(glowBlue.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.08f), radius = 380.dp.toPx(),
                ),
            )
        }
}

/** I colori degli accenti selezionabili (`AccentPalette.color` in AppSettings.swift). */
val AccentPalette.color: Color
    get() = when (this) {
        AccentPalette.lime -> Color(0.72f, 0.94f, 0.30f)
        AccentPalette.ember -> Color(1.00f, 0.58f, 0.24f)
        AccentPalette.ice -> Color(0.45f, 0.80f, 1.00f)
        AccentPalette.violet -> Color(0.68f, 0.55f, 1.00f)
        AccentPalette.coral -> Color(1.00f, 0.45f, 0.45f)
    }

/** La tinta di un gruppo muscolare, usata per chip, icone e grafici. */
val MuscleGroup.tint: Color
    get() = when (this) {
        MuscleGroup.petto -> Color(0.98f, 0.42f, 0.36f)
        MuscleGroup.schiena -> Color(0.36f, 0.72f, 0.98f)
        MuscleGroup.gambe -> Color(0.55f, 0.85f, 0.42f)
        MuscleGroup.spalle -> Color(0.99f, 0.75f, 0.31f)
        MuscleGroup.braccia -> Color(0.78f, 0.54f, 0.98f)
        MuscleGroup.core -> Color(0.40f, 0.87f, 0.80f)
    }

/** L'accento in uso, per i componenti che non lo ricevono come parametro (come `settings.accentColor` su iPhone). */
val LocalAccent = compositionLocalOf { AccentPalette.lime.color }

/** Vero se l'utente ha tolto le animazioni di sistema («Riduci movimento»). Vedi `rememberReduceMotion`. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun PeakTheme(accent: AccentPalette = AccentPalette.lime, reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val accentColor = accent.color
    val scheme = darkColorScheme(
        primary = accentColor,
        onPrimary = PeakTheme.bg,
        secondary = accentColor,
        background = PeakTheme.bg,
        onBackground = PeakTheme.textPrimary,
        surface = PeakTheme.bgRaised,
        onSurface = PeakTheme.textPrimary,
        surfaceVariant = PeakTheme.bgRaised,
        onSurfaceVariant = PeakTheme.textSecondary,
        outline = PeakTheme.strokeStrong,
        outlineVariant = PeakTheme.stroke,
        error = PeakTheme.negative,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(PeakTheme.radiusSmall),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(PeakTheme.radius),
        extraLarge = RoundedCornerShape(28.dp),
    )
    CompositionLocalProvider(LocalAccent provides accentColor, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
    }
}
