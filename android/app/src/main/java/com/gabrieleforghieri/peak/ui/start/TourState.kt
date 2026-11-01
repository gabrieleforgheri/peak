package com.gabrieleforghieri.peak.ui.start

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Lo stato del tutorial (`TourState` in `Features/Root/TourOverlay.swift`) e i punti che le altre aree gli segnalano. **Firma condivisa**:
 * proprietario l'area **start**, che aggiunge a [TourState] i testi e il resto della logica; i nomi qui sotto non cambiano, perché
 * workouts, machines e friends li usano già nelle loro schermate (`Modifier.tourTarget("start")`, `TourSheet(TourState.Sheet.days)`).
 *
 * Come su iPhone è uno stato unico (`TourState.shared`): non passa dalla navigazione.
 */
object TourState {
    enum class Phase { off, ideas, marks, cheer }

    enum class Step { tabs, start, pick, set, load, save }

    /** I fogli che il tutorial riconosce: la scelta del giorno, la serie da salvare. */
    enum class Sheet { days, save }

    var phase by mutableStateOf(Phase.off)
    var step by mutableStateOf(Step.tabs)

    /** Quale foglio è aperto. Lo scrive [TourSheet]. */
    var sheet by mutableStateOf<Sheet?>(null)

    /** La sessione è sullo schermo (la scrive la sessione, come `tour.inSession`). */
    var inSession by mutableStateOf(false)

    /** Senza giorni nella scheda la scelta è per gruppi: il riquadro punta a «Inizia». */
    var hasDays by mutableStateOf(true)

    val running: Boolean get() = phase != Phase.off
}

/** I controlli segnati con [tourTarget], in coordinate della radice (stessa cosa dell'`anchorPreference` di SwiftUI). */
object TourTargets {
    val rects = mutableStateMapOf<String, Rect>()
}

/** Segna un controllo che il tutorial può evidenziare (`.tourTarget("start")`). */
fun Modifier.tourTarget(id: String): Modifier = onGloballyPositioned { TourTargets.rects[id] = it.boundsInRoot() }

/** Un foglio (o la sessione) che il tutorial sa riconoscere: finché è composto, il passo che gli appartiene sta lì dentro (`.tourSheet`). */
@Composable
fun TourSheet(kind: TourState.Sheet, whenActive: Boolean = true) {
    DisposableEffect(kind, whenActive) {
        if (whenActive) TourState.sheet = kind
        onDispose { if (whenActive && TourState.sheet == kind) TourState.sheet = null }
    }
}
