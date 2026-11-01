package com.gabrieleforghieri.peak.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.Onboarding
import com.gabrieleforghieri.peak.domain.TestCatalog
import com.gabrieleforghieri.peak.sync.ConnectFlow.Stage
import com.gabrieleforghieri.peak.ui.screenshot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gli screenshot dell'area Avvio, in italiano (`android/docs/screens/start-*.png`): ogni scena del benvenuto, delle idee, dell'obiettivo,
 * del collegamento e del tutorial. Senza movimento (il fotogramma finale di ogni disegno). I fogli e le finestre a tutto schermo
 * (`FullScreenCover`) stanno in una finestra a parte e non entrano nello screenshot: si provano i loro contenuti.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-" + RobolectricDeviceQualifiers.Pixel7)
class StartScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun catalog() = TestCatalog.load()

    private fun shot(name: String, content: @Composable () -> Unit) = compose.screenshot("start-$name") {
        CompositionLocalProvider(LocalReduceMotion provides true) { Box(Modifier.fillMaxSize()) { content() } }
    }

    // ---- benvenuto (OnboardingView) ----

    private fun state(step: Int, build: OnboardingState.() -> Unit = {}) = OnboardingState(step).apply { heroSeen = true; late = 3; build() }

    private fun withGroups(state: OnboardingState) = state.apply {
        toggleGroup(MuscleGroup.petto); toggleGroup(MuscleGroup.schiena); toggleGroup(MuscleGroup.gambe)
        seedMachines()
    }

    @Test fun welcome() = shot("benvenuto") { OnboardingScreen(onFinish = {}, state = state(0)) }

    @Test fun days() = shot("giorni") { OnboardingScreen(onFinish = {}, state = state(1) { goal = 4 }) }

    @Test fun groups() = shot("gruppi") {
        OnboardingScreen(onFinish = {}, state = state(2) { toggleGroup(MuscleGroup.petto); toggleGroup(MuscleGroup.schiena); toggleGroup(MuscleGroup.core) })
    }

    @Test fun groupsNoneChosen() = shot("gruppi-vuoto") { OnboardingScreen(onFinish = {}, state = state(2)) }

    @Test fun machines() = shot("macchinari") {
        OnboardingScreen(onFinish = {}, state = withGroups(state(3)).apply { add(Catalog.entries.first { it.muscleGroup == MuscleGroup.braccia }) })
    }

    @Test fun ready() = shot("pronto") {
        OnboardingScreen(onFinish = {}, state = state(4) {
            composed = true
            made = listOf(MadeDay(MuscleGroup.petto, "Petto", 3), MadeDay(MuscleGroup.schiena, "Schiena", 3), MadeDay(MuscleGroup.gambe, "Gambe", 1))
        })
    }

    // ---- idee (IdeasView) ----

    @Test fun ideas1() = shot("idee-1") { IdeasScreen(onNext = {}, onSkip = {}, initialPage = 0) }
    @Test fun ideas2() = shot("idee-2") { IdeasScreen(onNext = {}, onSkip = {}, initialPage = 1) }
    @Test fun ideas3() = shot("idee-3") { IdeasScreen(onNext = {}, onSkip = {}, initialPage = 2) }
    @Test fun ideas4() = shot("idee-4") { IdeasScreen(onNext = {}, onSkip = {}, initialPage = 3) }

    // ---- obiettivo (GoalPickerView) ----

    @Test fun goalFirstTime() = shot("obiettivo") { GoalPickerContent(current = 0, suggested = 3, onClose = {}) }

    @Test fun goalChange() = shot("obiettivo-cambio") { GoalPickerContent(current = 5, suggested = 3, onClose = {}) }

    // ---- collegamento (ConnectFlowView) ----

    private fun connect(name: String, stage: Stage, firstRun: Boolean = false, notice: String? = null) =
        shot("collegamento-$name") { ConnectFlowBody(stage, firstRun, notice, Modifier.fillMaxSize()) }

    @Test fun connectWorking() = connect("accesso", Stage.Working("Accesso a yG…"))
    @Test fun connectChecking() = connect("controllo", Stage.Working("Controllo i dati su Peak…"))
    @Test fun connectCancelled() = connect("annullato", Stage.Idle)
    @Test fun connectEmptyWithData() = connect("vuoto-con-dati", Stage.EmptyServer(hasLocalData = true), notice = "Il file non è un backup di Peak.")
    @Test fun connectEmptyNoData() = connect("vuoto-senza-dati", Stage.EmptyServer(hasLocalData = false))
    @Test fun connectConflict() = connect("conflitto", Stage.Conflict)
    @Test fun connectFailed() = connect("errore", Stage.Failed("C'è un allenamento in corso. Terminalo e poi collega l'account."))
    @Test fun connectFirstRunEmpty() = connect("primo-avvio", Stage.EmptyServer(hasLocalData = false), firstRun = true)

    // ---- tutorial (TourOverlay) ----

    /** Una schermata finta sotto il riquadro: «Inizia» segnato con `tourTarget`, la barra delle schede in basso. */
    @Composable
    private fun Tour(step: TourState.Step, target: (Rect?) -> Rect?, hasDays: Boolean = true, backdrop: @Composable () -> Unit = {}) {
        TourState.hasDays = hasDays
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val size = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Allenamento", Modifier.padding(top = 24.dp), style = PeakTheme.display(34), color = PeakTheme.textPrimary)
                GlassCard { Text("Oggi: Petto", style = PeakTheme.rounded(17), color = PeakTheme.textPrimary) }
                PrimaryButton("Avvia allenamento", {}, Modifier.tourTarget("start"))
                backdrop()
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                for (t in listOf("Allenamento", "Macchinari", "Me", "Amici")) Text(t, style = PeakTheme.rounded(11), color = PeakTheme.textSecondary)
            }
            val bar = with(density) { Rect(8.dp.toPx(), size.height - 76.dp.toPx(), size.width - 8.dp.toPx(), size.height - 4.dp.toPx()) }
            TourCoach(step, target(bar), size, topInset = 0f, bottomInset = 0f, onSkip = {}, onNext = {})
        }
    }

    @Test fun tourTabs() = shot("tutorial-schede") { Tour(TourState.Step.tabs, { it }) }

    @Test fun tourStart() = shot("tutorial-inizia") { Tour(TourState.Step.start, { TourTargets.rects["start"] }) }

    @Test fun tourPickDay() = shot("tutorial-giorno") { Tour(TourState.Step.pick, { TourTargets.rects["start"] }) }

    @Test fun tourPickGroups() = shot("tutorial-gruppi") { Tour(TourState.Step.pick, { TourTargets.rects["start"] }, hasDays = false) }

    @Test fun tourSet() = shot("tutorial-serie") { Tour(TourState.Step.set, { TourTargets.rects["start"] }) }

    @Test fun tourLoad() = shot("tutorial-peso") { Tour(TourState.Step.load, { TourTargets.rects["start"] }) }

    @Test fun tourSave() = shot("tutorial-salva") { Tour(TourState.Step.save, { TourTargets.rects["start"] }) }

    @Test fun tourCheer() = shot("tutorial-festa") {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            TourCheer(with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }, topInset = 0f, onEnd = {})
        }
    }

    @Test fun machineTip() = shot("suggerimento-macchinario") {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                Text("Panca", Modifier.padding(top = 24.dp), style = PeakTheme.display(34), color = PeakTheme.textPrimary)
                PrimaryButton("Nuovo massimale", {}, Modifier.padding(top = 60.dp).tourTarget("record"))
            }
            MachineTip("record", true, "Il tuo massimale", "Qui registri il peso massimo che sollevi.", onNext = {})
        }
    }
}
