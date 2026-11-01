package com.gabrieleforghieri.peak.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** L'altezza di un foglio, come i `presentationDetents` di SwiftUI. */
sealed interface SheetDetent {
    /** `.large`: quasi tutto lo schermo (la maggior parte dei fogli con una lista o un modulo). */
    data object Large : SheetDetent

    /** `.medium`: a metà schermo, e si può tirare su. */
    data object Medium : SheetDetent

    /** `.height(n)`: alto quanto il contenuto, con questa altezza come minimo (la conferma sta in 210). */
    data class Height(val min: Dp = 0.dp) : SheetDetent
}

/**
 * Il foglio in basso di tutta l'app, al posto di `.sheet` (`ConfirmSheet`, `AddSetSheet`, `FocusPickerSheet`…): sfondo `bgRaised`,
 * angoli di 28 dp, maniglia visibile. **Un solo componente per tutte le aree**: non si usa `ModalBottomSheet` direttamente.
 *
 * Si apre mostrandolo (`if (aperto) PeakSheet(onDismiss = { aperto = false }) { … }`). `onDismiss` parte quando il foglio si è
 * chiuso: con un gesto, toccando fuori, col tasto indietro o con `close()`, che il contenuto riceve e che lo chiude con
 * l'animazione. Quello che va fatto «a foglio chiuso» (l'azione di `ConfirmSheet`, l'apertura di un altro foglio) si fa in `onDismiss`.
 *
 * Dentro un foglio se ne può aprire un altro (come su iPhone): il secondo sta sopra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeakSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    detent: SheetDetent = SheetDetent.Large,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = detent != SheetDetent.Medium)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = PeakTheme.bgRaised,
        contentColor = PeakTheme.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = PeakTheme.textTertiary) },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        val size = when (detent) {
            SheetDetent.Large -> Modifier.fillMaxHeight()
            SheetDetent.Medium -> Modifier.fillMaxHeight(0.5f)
            is SheetDetent.Height -> Modifier.padding(0.dp)
        }
        Column(
            modifier.fillMaxWidth().then(size).windowInsetsPadding(WindowInsets.navigationBars),
            content = { content(close) },
        )
    }
}
