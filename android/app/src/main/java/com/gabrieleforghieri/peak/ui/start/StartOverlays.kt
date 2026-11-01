package com.gabrieleforghieri.peak.ui.start

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gabrieleforghieri.peak.data.PeakDatabase
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.nav.Tab
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// I punti che l'iPhone apre sopra tutto, in `RootView`: il benvenuto, il tutorial, l'obiettivo. `PeakRoot` li chiama in ordine sopra
// la navigazione; ognuno è un aggancio che l'area **start** riempie (proprietario: start). Non hanno argomenti: leggono impostazioni,
// navigazione e dati dall'ambiente (`LocalAppSettings`, `LocalPeakNav`, `LocalRepository`). Le condizioni sono in `StartRules`.

/** Lo stato che `RootView` tiene in `@State` e che benvenuto e tutorial si passano: se la sincronizzazione ha già detto la sua. */
object StartState {
    var synced by mutableStateOf(false)

    /** Senza rete (o con un server lento) il tutorial non aspetta più di qualche secondo. */
    const val SYNC_WAIT_MS = 4000L
}

/**
 * Il primo avvio a tutto schermo (`OnboardingView`), e i compiti d'avvio che `RootView.task` fa una volta per apertura: i giorni
 * fuori scheda in «La mia scheda» (`Programs.adoptLoose`) e la prima sincronizzazione (poi una a ogni ritorno nell'app). Va deciso
 * una volta sola all'apertura: un'app con dei dati non lo mostra.
 *
 * Non ci sono qui il backup automatico (`backup-latest.json`, non ancora fatto su Android), il collegamento al catalogo
 * (`linkCatalogOnce`) né i pittogrammi fissati (`freezeGlyphsOnce`): rimediano a dati nati prima del catalogo, e un telefono Android
 * non ne ha. `Catalog.refresh` (il catalogo più nuovo dal server) manca in `PeakApi`.
 */
@Composable
fun WelcomeOverlay() {
    val app = LocalApp.current
    val settings = LocalAppSettings.current
    val update = rememberUpdateSettings()
    val scope = rememberCoroutineScope()
    val actions = remember(app) { StartActions(app.database) }
    var decided by remember { mutableStateOf(false) }
    var onboarding by remember { mutableStateOf(false) }

    // `decide()`: si guardano le impostazioni vere, non quelle di partenza che l'ambiente dà finché non sono lette
    LaunchedEffect(Unit) {
        val real = app.settings.settings.first()
        val fallback = PeakDatabase.isFallback
        val hasData = !real.didSeeWelcome && !fallback && actions.hasData()
        when (StartRules.firstRun(real.didSeeWelcome, fallback, hasData)) {
            StartRules.FirstRun.Nothing -> {}
            StartRules.FirstRun.MarkSeen -> update { copy(didSeeWelcome = true) }
            StartRules.FirstRun.Onboard -> onboarding = true
        }
        decided = true
    }
    LaunchedEffect(Unit) {
        scope.launch { delay(StartState.SYNC_WAIT_MS); StartState.synced = true }
        if (!PeakDatabase.isFallback) actions.adoptLoose(app.tr("La mia scheda"))
        app.sync.syncNow()
        StartState.synced = true
    }
    var firstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) { firstResume = false; return@LifecycleEventEffect }
        scope.launch { app.sync.syncNow() }
    }

    // finché non si è deciso, le schede non si vedono: il primo avvio non deve lampeggiare sopra l'app
    if (StartRules.coversTabs(decided, settings)) Box(Modifier.coverBackground(LocalAccent.current))
    if (onboarding) FullScreenCover {
        OnboardingScreen(onFinish = { update { copy(didSeeWelcome = true) }; onboarding = false })
    }
}

/**
 * Le idee di Peak a tutto schermo (`IdeasView`), poi il giro sulle schermate vere (`TourDriver`, `TourOverlay`, `TourCoach`),
 * guidato da [TourState] e dai [tourTarget] segnati dalle altre aree. Parte da solo quando serve ([StartRules.wantTutorial]) o da
 * «Rivedi il tutorial» (che rimette `tutorialDone = false`).
 */
@Composable
fun TutorialOverlay() {
    val settings = LocalAppSettings.current
    val nav = LocalPeakNav.current
    val update = rememberUpdateSettings()
    val fallback = PeakDatabase.isFallback
    val want = StartRules.wantTutorial(StartState.synced, settings, fallback)
    LaunchedEffect(want) {
        if (want && !TourState.running) { TourState.start(); nav.goTab(Tab.workouts) }
    }
    // la festa della prima serie sparisce da sola
    LaunchedEffect(TourState.phase) {
        if (TourState.phase == TourState.Phase.cheer) {
            delay(TourFlow.CHEER_MS)
            if (TourState.phase == TourState.Phase.cheer) TourState.end()
        }
    }
    if (TourState.phase == TourState.Phase.ideas) FullScreenCover {
        IdeasScreen(onNext = { TourState.phase = TourState.Phase.marks }, onSkip = { TourState.skip(update) })
    }
    if (TourState.running) TourDriver()
    if (StartRules.showsMarks(TourState.phase, fallback)) TourOverlay()
}

/** Tiene il passo dove deve stare (i fogli si aprono e si chiudono, la sessione parte) e chiude il giro alla prima serie salvata (`TourDriver`). */
@Composable
private fun TourDriver() {
    val repo = LocalRepository.current
    val nav = LocalPeakNav.current
    val update = rememberUpdateSettings()
    val haptics = LocalApp.current.haptics
    val active by repo.activeWorkout.collectAsState(null)
    val activeId = active?.uuid
    val count by remember(activeId) {
        if (activeId == null) flowOf(null) else repo.setsOfWorkout(activeId).map<_, Int?> { it.size }
    }.collectAsState(null)
    var base by remember { mutableIntStateOf(-1) }
    val phase = TourState.phase
    val step = TourState.step
    val sheet = TourState.sheet
    val inSession = TourState.inSession

    LaunchedEffect(phase, step, sheet, inSession, activeId != null) {
        val next = TourFlow.reconcile(phase, step, sheet, inSession, activeId != null, onWorkouts = nav.tab.value == Tab.workouts) ?: return@LaunchedEffect
        if (next.goWorkouts) nav.goTab(Tab.workouts)
        if (next.step != TourState.step) TourState.step = next.step
        if (next.waitForSession) {
            delay(TourFlow.SESSION_WAIT_MS)
            TourFlow.afterWait(TourState.step, TourState.sheet, TourState.inSession)?.let { TourState.step = it }
        }
    }
    LaunchedEffect(phase, step, count, activeId) {
        val w = TourFlow.watch(phase, step, count, base)
        base = w.base
        if (w.finished) {
            TourState.finish(update)
            haptics.success()
            TourState.phase = TourState.Phase.cheer
        }
    }
}

/** L'obiettivo settimanale la prima volta (`fullScreenCover { GoalPickerView }` di `RootView`): si apre quando `didSeeWelcome && weeklyGoal == 0`. Usa [GoalPickerContent]. */
@Composable
fun GoalOverlay() {
    if (!StartRules.showGoal(LocalAppSettings.current)) return
    FullScreenCover {
        Box(Modifier.coverBackground(LocalAccent.current).statusBarsPadding().navigationBarsPadding()) {
            GoalPickerContent(current = 0, suggested = 3, onClose = {}, modifier = Modifier.fillMaxSize())
        }
    }
}
