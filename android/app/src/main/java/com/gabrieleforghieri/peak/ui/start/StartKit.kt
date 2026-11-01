package com.gabrieleforghieri.peak.ui.start

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.Motion
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.ui.nav.LocalApp

// I pezzi che l'area Avvio ha in comune fra benvenuto, idee, obiettivo e collegamento.

/**
 * Un `fullScreenCover`: una finestra a tutto schermo sopra l'app, barra delle schede compresa. Il tasto indietro lo gestisce il
 * contenuto (`BackHandler`); senza, non fa niente. Lo sfondo lo mette il contenuto (`PeakTheme.backgroundModifier`).
 */
@Composable
fun FullScreenCover(content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: (view.context as? Activity)?.window ?: return@SideEffect
            window.setDimAmount(0f)
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        }
        content()
    }
}

/** Entra dal basso (10 dp) con una dissolvenza; con «riduci movimento» compare e basta (`Rise` di OnboardingView). */
@Composable
fun Modifier.rise(on: Boolean): Modifier {
    val reduce = LocalReduceMotion.current
    val p by animateFloatAsState(if (on) 1f else 0f, if (reduce) androidx.compose.animation.core.snap() else Motion.out(600), label = "rise")
    return graphicsLayer {
        alpha = p
        translationY = if (reduce) 0f else (1f - p) * 10.dp.toPx()
    }
}

/** Il titolo grande di un passo (`title` di OnboardingView): nero, tondo, stretto. */
@Composable
fun StartTitle(text: String, width: Float, modifier: Modifier = Modifier) {
    val size = minOf(34f, maxOf(26f, width * 0.074f))
    Text(
        text, modifier,
        style = PeakTheme.rounded(size.toInt(), FontWeight.Black).copy(fontSize = size.sp, letterSpacing = (-0.03f * size).sp, lineHeight = TextUnit.Unspecified),
        color = PeakTheme.textPrimary,
    )
}

@Composable
fun StartSub(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp), style = PeakTheme.rounded(16, FontWeight.Normal), color = PeakTheme.textSecondary)
}

/** Il cerchietto della scelta (`check` di OnboardingView): vuoto col bordo, pieno con la spunta. */
@Composable
fun SelectCheck(on: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(20.dp).clip(CircleShape).then(if (on) Modifier.background(tint) else Modifier.border(1.5.dp, PeakTheme.strokeStrong, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(PeakIcons.check, null, Modifier.size(13.dp), tint = PeakTheme.bg)
    }
}

/** Il tasto secondario con un'icona davanti (`Label(…)` con `SecondaryButtonStyle`). */
@Composable
fun SecondaryIconButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().background(PeakTheme.cardStrong, shape).border(1.dp, PeakTheme.stroke, shape).pressable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = PeakTheme.textPrimary)
        Text(text, style = PeakTheme.rounded(15), color = PeakTheme.textPrimary)
    }
}

/** Lo sfondo di una schermata a tutto schermo che copre l'app. */
fun Modifier.coverBackground(accent: Color): Modifier = fillMaxSize().then(PeakTheme.backgroundModifier(accent))

@Composable
fun accent(): Color = LocalAccent.current

/** Il tocco leggero e la conferma, dal servizio dell'app. */
@Composable
fun rememberTap(): () -> Unit {
    val app = LocalApp.current
    return { app.haptics.tap() }
}
