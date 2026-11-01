package com.gabrieleforghieri.peak.ui.start

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.gabrieleforghieri.peak.domain.AppSettings

/**
 * Lo stato del tutorial (`TourState` in `Features/Root/TourOverlay.swift`) e i punti che le altre aree gli segnalano. **Firma condivisa**:
 * proprietario l'area **start**, che aggiunge a [TourState] i testi e il resto della logica; i nomi qui sotto non cambiano, perché
 * workouts, machines e friends li usano già nelle loro schermate (`Modifier.tourTarget("start")`, `TourSheet(TourState.Sheet.days)`).
 *
 * Come su iPhone è uno stato unico (`TourState.shared`): non passa dalla navigazione.
 */
object TourState {
    enum class Phase { off, ideas, marks, cheer }

    enum class Step {
        tabs, start, pick, set, load, save;

        /** Sopra il foglio che il passo apre: il riquadro non blocca i tocchi. */
        val up: Boolean get() = this == pick || this == load || this == save

        /** Il passo dopo «Avanti» (solo dove c'è il pulsante). */
        val next: Step? get() = when (this) { tabs -> start; load -> save; else -> null }

        /** I controlli da evidenziare (segnati con [tourTarget]); il primo che c'è vince. La scelta del giorno ha due varianti. */
        val targets: List<String> get() = when (this) {
            tabs -> emptyList()
            start -> listOf("start")
            pick -> listOf("days", "go")
            set -> listOf("add-set")
            load -> listOf("load")
            save -> listOf("save")
        }
    }

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

    /** Il giro comincia dalle idee (la scheda Allenamento la apre chi chiama: `LocalPeakNav.goTab`). */
    fun start() {
        step = Step.tabs
        phase = Phase.ideas
    }

    fun end() { phase = Phase.off }

    /** Alla fine o saltando: il tutorial è fatto. `update` è `rememberUpdateSettings()`. */
    fun finish(update: ((AppSettings.() -> AppSettings)) -> Unit) = update { copy(tutorialDone = true, tourSeen = 3) }

    fun skip(update: ((AppSettings.() -> AppSettings)) -> Unit) {
        finish(update)
        end()
    }

    /** Il titolo e la riga del passo, come chiavi italiane (chi mostra le passa da `tr`). */
    fun title(step: Step): String = when (step) {
        Step.tabs -> "Quattro sezioni"
        Step.start -> "Inizia da qui"
        Step.pick -> if (hasDays) "Scegli un giorno" else "Scegli i gruppi, poi Inizia"
        Step.set -> "Aggiungi una serie"
        Step.load -> "Peso e ripetizioni"
        Step.save -> "Salva la serie"
    }

    fun text(step: Step): String? = when (step) {
        Step.tabs -> "Allenamento, Macchinari, Me, Amici."
        Step.start -> "Tocca il pulsante per iniziare."
        Step.set -> "Tocca + Serie."
        Step.load -> "Usa − e + per scegliere."
        Step.pick, Step.save -> null
    }
}

/** I controlli segnati con [tourTarget], in coordinate della finestra (stessa cosa dell'`anchorPreference` di SwiftUI). */
object TourTargets {
    val rects = mutableStateMapOf<String, Rect>()
}

/** Segna un controllo che il tutorial può evidenziare (`.tourTarget("start")`). Sparisce con il controllo, come una preferenza di SwiftUI. */
fun Modifier.tourTarget(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { TourTargets.rects.remove(id) } }
    onGloballyPositioned { TourTargets.rects[id] = it.boundsInRoot() }
}

/**
 * Un foglio (o la sessione) che il tutorial sa riconoscere: finché è composto, il passo che gli appartiene sta lì dentro (`.tourSheet`).
 * Dentro un foglio (`PeakSheet`) mette anche il riquadro e la carta dei suoi passi (`.tourMarks`): il foglio è una finestra a parte,
 * sopra quella dell'app. Chiamarlo dal contenuto del foglio, o della sessione; `whenActive` = `false` lo spegne.
 */
@Composable
fun TourSheet(kind: TourState.Sheet, whenActive: Boolean = true) {
    DisposableEffect(kind, whenActive) {
        if (whenActive) TourState.sheet = kind
        onDispose { if (whenActive && TourState.sheet == kind) TourState.sheet = null }
    }
    if (whenActive) SheetMarks(kind)
}
