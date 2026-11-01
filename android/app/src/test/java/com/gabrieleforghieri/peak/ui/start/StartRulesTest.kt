package com.gabrieleforghieri.peak.ui.start

import com.gabrieleforghieri.peak.domain.AppSettings
import com.gabrieleforghieri.peak.domain.CatalogEntry
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.Onboarding
import com.gabrieleforghieri.peak.domain.TestCatalog
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.ui.start.TourState.Phase
import com.gabrieleforghieri.peak.ui.start.TourState.Sheet
import com.gabrieleforghieri.peak.ui.start.TourState.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quando si vedono benvenuto, obiettivo e tutorial (`RootView.swift`), e la sequenza del tutorial (`TourDriver`). */
class StartRulesTest {
    private val seen = AppSettings(didSeeWelcome = true, weeklyGoal = 3)

    // ---- benvenuto ----

    @Test fun anEmptyAppThatNeverSawTheWelcomeStartsTheFirstRun() {
        assertEquals(StartRules.FirstRun.Onboard, StartRules.firstRun(didSeeWelcome = false, isFallback = false, hasData = false))
    }

    @Test fun anAppWithDataSkipsTheWelcomeAndMarksItSeen() {
        assertEquals(StartRules.FirstRun.MarkSeen, StartRules.firstRun(false, false, hasData = true))
    }

    @Test fun theWelcomeIsNeverShownTwiceOrOnTheEmergencyStore() {
        assertEquals(StartRules.FirstRun.Nothing, StartRules.firstRun(didSeeWelcome = true, isFallback = false, hasData = false))
        assertEquals(StartRules.FirstRun.Nothing, StartRules.firstRun(false, isFallback = true, hasData = false))
        assertEquals(StartRules.FirstRun.Nothing, StartRules.firstRun(false, isFallback = true, hasData = true))
    }

    @Test fun theBackgroundCoversTheTabsOnlyUntilItIsDecided() {
        assertTrue(StartRules.coversTabs(decided = false, s = AppSettings()))
        assertFalse(StartRules.coversTabs(decided = true, s = AppSettings()))
        assertFalse(StartRules.coversTabs(decided = false, s = seen))
    }

    // ---- obiettivo ----

    @Test fun theGoalIsAskedAfterTheWelcomeWhenThereIsNone() {
        assertTrue(StartRules.showGoal(AppSettings(didSeeWelcome = true, weeklyGoal = 0)))
        assertFalse(StartRules.showGoal(AppSettings(didSeeWelcome = false, weeklyGoal = 0)))
        assertFalse(StartRules.showGoal(seen))
    }

    @Test fun theGoalPickerStartsOnTheCurrentOrTheSuggestedGoalInRange() {
        assertEquals(4, initialGoalPick(current = 4, suggested = 3))
        assertEquals(3, initialGoalPick(0, 3))
        assertEquals(2, initialGoalPick(0, 1))
        assertEquals(6, initialGoalPick(0, 7))
        assertEquals(6, initialGoalPick(0, 6))
    }

    // ---- tutorial ----

    @Test fun theTutorialWaitsForTheGoalTheSyncAndTheWelcome() {
        val fresh = seen.copy(tutorialDone = false, tourSeen = 1)
        assertTrue(StartRules.wantTutorial(true, fresh, false))
        assertFalse("la sincronizzazione non ha ancora detto la sua", StartRules.wantTutorial(false, fresh, false))
        assertFalse("senza obiettivo prima c'è l'obiettivo", StartRules.wantTutorial(true, fresh.copy(weeklyGoal = 0), false))
        assertFalse("senza il benvenuto visto", StartRules.wantTutorial(true, fresh.copy(didSeeWelcome = false), false))
        assertFalse("con lo store di emergenza", StartRules.wantTutorial(true, fresh, true))
    }

    @Test fun theTutorialDoesNotComeBackOnceDoneUnlessAnOlderVersionSawIt() {
        assertFalse(StartRules.wantTutorial(true, seen.copy(tutorialDone = true, tourSeen = 3), false))
        // «Rivedi il tutorial» rimette tutorialDone a false
        assertTrue(StartRules.wantTutorial(true, seen.copy(tutorialDone = false, tourSeen = 3), false))
        // chi ha visto solo la versione 1 del giro lo rivede
        assertTrue(StartRules.wantTutorial(true, seen.copy(tutorialDone = true, tourSeen = 1), false))
        assertFalse(StartRules.wantTutorial(true, seen.copy(tutorialDone = true, tourSeen = 2), false))
    }

    @Test fun theMarksAreShownOnlyAfterTheIdeasAndNeverOnTheEmergencyStore() {
        assertFalse(StartRules.showsMarks(Phase.off, false))
        assertFalse(StartRules.showsMarks(Phase.ideas, false))
        assertTrue(StartRules.showsMarks(Phase.marks, false))
        assertTrue(StartRules.showsMarks(Phase.cheer, false))
        assertFalse(StartRules.showsMarks(Phase.marks, true))
    }

    @Test fun startingTheTourBeginsWithTheIdeasAndTheTabsStep() {
        TourState.step = Step.save
        TourState.phase = Phase.off
        TourState.start()
        assertEquals(Phase.ideas, TourState.phase)
        assertEquals(Step.tabs, TourState.step)
        assertTrue(TourState.running)
        TourState.end()
        assertFalse(TourState.running)
    }

    @Test fun skippingMarksTheTutorialDoneAndEndsIt() {
        var s = AppSettings()
        TourState.phase = Phase.marks
        TourState.skip { s = s.it() }
        assertTrue(s.tutorialDone)
        assertEquals(3, s.tourSeen)
        assertEquals(Phase.off, TourState.phase)
    }

    private fun AppSettings.it() = this

    @Test fun theStepsKnowTheirTargetsTheirNextAndWhereTheyLive() {
        assertEquals(listOf(Step.tabs, Step.start, Step.pick, Step.set, Step.load, Step.save), Step.entries)
        assertEquals(emptyList<String>(), Step.tabs.targets)
        assertEquals(listOf("start"), Step.start.targets)
        assertEquals(listOf("days", "go"), Step.pick.targets)
        assertEquals(listOf("add-set"), Step.set.targets)
        assertEquals(listOf("load"), Step.load.targets)
        assertEquals(listOf("save"), Step.save.targets)
        assertEquals(Step.start, Step.tabs.next)
        assertEquals(Step.save, Step.load.next)
        assertNull(Step.start.next)
        assertNull(Step.save.next)
        assertEquals(listOf(Step.pick, Step.load, Step.save), Step.entries.filter { it.up })
    }

    @Test fun theTitleOfThePickStepFollowsWhetherThereAreDays() {
        TourState.hasDays = true
        assertEquals("Scegli un giorno", TourState.title(Step.pick))
        TourState.hasDays = false
        assertEquals("Scegli i gruppi, poi Inizia", TourState.title(Step.pick))
        TourState.hasDays = true
        assertEquals("Quattro sezioni", TourState.title(Step.tabs))
        assertEquals("Allenamento, Macchinari, Me, Amici.", TourState.text(Step.tabs))
        assertNull(TourState.text(Step.pick))
        assertNull(TourState.text(Step.save))
    }

    // ---- la sequenza del giro ----

    private fun reconcile(phase: Phase = Phase.marks, step: Step, sheet: Sheet? = null, inSession: Boolean = false, hasActive: Boolean = false, onWorkouts: Boolean = true) =
        TourFlow.reconcile(phase, step, sheet, inSession, hasActive, onWorkouts)

    @Test fun nothingMovesOutsideTheMarksPhaseOrOnTheTabsStep() {
        assertNull(reconcile(Phase.ideas, Step.start))
        assertNull(reconcile(Phase.off, Step.set))
        assertNull(reconcile(step = Step.tabs))
    }

    @Test fun theTourWalksFromTheTabsToTheFirstSavedSet() {
        // 1. «Quattro sezioni»: Avanti porta a «Inizia da qui», che sta sulla scheda Allenamento
        assertEquals(Step.start, Step.tabs.next)
        assertEquals(TourFlow.Next(Step.start, goWorkouts = true), reconcile(step = Step.start, onWorkouts = false))
        // 2. tocca «Inizia»: si apre la scelta del giorno, e il passo diventa «Scegli un giorno»
        assertEquals(TourFlow.Next(Step.pick), reconcile(step = Step.start, sheet = Sheet.days))
        // 3. il giorno è scelto: il foglio si chiude, c'è un allenamento ma la sessione non è ancora a schermo: si aspetta
        assertEquals(TourFlow.Next(Step.pick, waitForSession = true), reconcile(step = Step.pick, hasActive = true))
        // 4. la sessione compare: «Aggiungi una serie»
        assertEquals(TourFlow.Next(Step.set), reconcile(step = Step.pick, inSession = true, hasActive = true))
        // 5. «+ Serie» apre il foglio da salvare: «Peso e ripetizioni»
        assertEquals(TourFlow.Next(Step.load), reconcile(step = Step.set, sheet = Sheet.save, inSession = true, hasActive = true))
        // 6. «Avanti» porta a «Salva la serie» e il foglio resta: non si muove più niente
        assertEquals(Step.save, Step.load.next)
        assertNull(reconcile(step = Step.save, sheet = Sheet.save, inSession = true, hasActive = true))
        assertNull(reconcile(step = Step.load, sheet = Sheet.save, inSession = true, hasActive = true))
    }

    @Test fun closingTheDaysSheetWithoutPickingGoesBackToStart() {
        // il foglio dei giorni si chiude senza un allenamento in corso: «Inizia da qui»
        assertEquals(TourFlow.Next(Step.start), reconcile(step = Step.pick, hasActive = false))
    }

    @Test fun ifTheSessionNeverComesTheTourGoesBackToStartAfterTheWait() {
        assertEquals(Step.start, TourFlow.afterWait(Step.pick, sheet = null, inSession = false))
        assertNull("la sessione è arrivata", TourFlow.afterWait(Step.pick, null, inSession = true))
        assertNull("un foglio si è riaperto", TourFlow.afterWait(Step.pick, Sheet.days, false))
        assertNull("il passo è già un altro", TourFlow.afterWait(Step.set, null, false))
    }

    @Test fun closingTheSaveSheetBringsTheTourBackToTheSetStep() {
        assertEquals(TourFlow.Next(Step.set), reconcile(step = Step.load, sheet = null, inSession = true, hasActive = true))
        assertEquals(TourFlow.Next(Step.set), reconcile(step = Step.save, sheet = null, inSession = true, hasActive = true))
    }

    @Test fun leavingTheSessionDuringTheSetStepGoesBackToStart() {
        assertEquals(TourFlow.Next(Step.start), reconcile(step = Step.set, inSession = false))
        assertNull(reconcile(step = Step.set, inSession = true, hasActive = true))
    }

    @Test fun theLastStepsFinishWhenASetAppearsInTheWorkout() {
        // il passo comincia con 2 serie: si ricorda la base
        var w = TourFlow.watch(Phase.marks, Step.set, count = 2, base = -1)
        assertEquals(TourFlow.Watched(2, false), w)
        // nessun cambiamento: ancora niente
        w = TourFlow.watch(Phase.marks, Step.load, 2, w.base)
        assertEquals(TourFlow.Watched(2, false), w)
        // una serie in più: finito
        w = TourFlow.watch(Phase.marks, Step.save, 3, w.base)
        assertEquals(TourFlow.Watched(-1, true), w)
    }

    @Test fun aDeletedSetLowersTheBaseWithoutFinishing() {
        assertEquals(TourFlow.Watched(1, false), TourFlow.watch(Phase.marks, Step.set, 1, base = 3))
    }

    @Test fun theWatchIsOffOutsideTheLastStepsOrWithoutAWorkout() {
        assertEquals(TourFlow.Watched(-1, false), TourFlow.watch(Phase.marks, Step.start, 3, 2))
        assertEquals(TourFlow.Watched(-1, false), TourFlow.watch(Phase.ideas, Step.set, 3, 2))
        assertEquals(TourFlow.Watched(-1, false), TourFlow.watch(Phase.marks, Step.set, null, 2))
    }

    // ---- primo avvio ----

    @Test fun theFirstThreeMachinesOfEachChosenGroupStartPicked() {
        TestCatalog.load()
        val s = OnboardingState()
        s.toggleGroup(MuscleGroup.petto)
        s.toggleGroup(MuscleGroup.gambe)
        s.seedMachines()
        assertEquals(Onboarding.suggested.getValue(MuscleGroup.petto).take(3), s.chosen(MuscleGroup.petto).map { it.id })
        assertEquals(6, s.total)
        // tornando indietro e riavanti non si rimettono quelli tolti
        s.toggleMachine(Onboarding.suggested.getValue(MuscleGroup.petto)[0])
        s.seedMachines()
        assertEquals(5, s.total)
    }

    @Test fun aSearchedMachineJoinsItsGroupAndAddsTheGroupEmpty() {
        TestCatalog.load()
        val s = OnboardingState()
        val listed = Catalog.entry(Onboarding.suggested.getValue(MuscleGroup.schiena).last())!!
        s.add(listed)
        assertEquals(setOf(MuscleGroup.schiena), s.groups)
        assertEquals("un macchinario già suggerito si segna soltanto", setOf(listed.id), s.on)
        assertTrue(s.extras.isEmpty())
        val other: CatalogEntry = Catalog.entries.first { it.muscleGroup == MuscleGroup.core && it.id !in Onboarding.suggested.getValue(MuscleGroup.core) }
        s.add(other)
        s.add(other)
        assertEquals(listOf(other.id), s.extras.map { it.id })
        assertEquals("il gruppo nuovo parte vuoto, senza i tre di partenza", 2, s.total)
        assertTrue(MuscleGroup.core in s.seeded)
    }

    @Test fun thePlanHasADayPerGroupInTheCanonicalOrderAndSkipsEmptyOnes() {
        TestCatalog.load()
        val s = OnboardingState()
        s.toggleGroup(MuscleGroup.gambe)
        s.toggleGroup(MuscleGroup.petto)
        s.toggleGroup(MuscleGroup.core)
        s.seedMachines()
        for (id in s.chosen(MuscleGroup.core).map { it.id }) s.toggleMachine(id)
        val days = s.days { "<$it>" }
        assertEquals(listOf(MuscleGroup.petto, MuscleGroup.gambe), days.map { it.group })
        assertEquals(listOf("<Petto>", "<Gambe>"), days.map { it.name })
        assertTrue(days.all { it.machines.size == 3 })
    }

    @Test fun removingAnExtraTakesItOut() {
        TestCatalog.load()
        val s = OnboardingState()
        val other = Catalog.entries.first { it.muscleGroup == MuscleGroup.braccia && it.id !in Onboarding.suggested.getValue(MuscleGroup.braccia) }
        s.add(other)
        s.removeExtra(other.id)
        assertTrue(s.extras.isEmpty())
    }

    // ---- curve ----

    @Test fun curvesHaveTheirEndpointsAndStayMonotonic() {
        for (c in listOf(Curve.easeOut, Curve.spring, Curve.ease, Curve.easeInOut)) {
            assertEquals(0.0, Curve.bezier(c, 0.0), 1e-9)
            assertEquals(1.0, Curve.bezier(c, 1.0), 1e-9)
        }
        assertEquals(0.5, Curve.bezier(Curve.linear, 0.5), 1e-9)
        assertEquals(0.5, Curve.bezier(Curve.easeInOut, 0.5), 1e-3)
        assertTrue("l'ease-out parte veloce", Curve.bezier(Curve.easeOut, 0.2) > 0.5)
        assertTrue("la molla supera 1 prima di assestarsi", Curve.bezier(Curve.spring, 0.5) > 1.0)
    }

    @Test fun keyframesInterpolateBetweenFrames() {
        val frames = listOf(0.0 to 0.0, 0.5 to 1.0, 1.0 to 1.0)
        assertEquals(0.0, Curve.key(0.0, frames, Curve.linear), 1e-9)
        assertEquals(0.5, Curve.key(0.25, frames, Curve.linear), 1e-9)
        assertEquals(1.0, Curve.key(0.5, frames, Curve.linear), 1e-9)
        assertEquals(1.0, Curve.key(2.0, frames, Curve.linear), 1e-9)
        assertEquals(0.0, Curve.key(-1.0, frames, Curve.linear), 1e-9)
    }
}
