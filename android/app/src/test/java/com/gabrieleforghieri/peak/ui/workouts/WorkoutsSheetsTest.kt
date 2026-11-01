package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.ui.test.junit4.createComposeRule
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.MuscleId
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.workouts.WorkoutsFixtures as F
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il contenuto dei fogli dell'area Allenamenti (un `PeakSheet` sta in una finestra a parte e non entra nello screenshot), in italiano, su
 * uno schermo alto così si vede tutto: `android/docs/screens/workouts-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h1500dp-normal-long-notround-any-320dpi-keyshidden-nonav")
class WorkoutsSheetsTest {
    @get:Rule val compose = createComposeRule()

    private val kg = WeightUnit.kg
    private val plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    // ---- il selettore di macchinari ----

    @Test fun pickerWithRecentAndGroups() = compose.shot("workouts-selettore") {
        ExercisePickerContent(
            F.exercises, F.activeSets.reversed() + F.done1Sets, F.benchRecords, emptySet(), kg, onSelect = {}, onClose = {}, onNew = {},
        )
    }

    @Test fun pickerFilteredByTodaysGroups() = compose.shot("workouts-selettore-filtrato") {
        ExercisePickerContent(
            F.exercises, F.activeSets, F.benchRecords, setOf(MuscleGroup.petto, MuscleGroup.spalle), kg, onSelect = {}, onClose = {}, onNew = {},
        )
    }

    // ---- cosa alleni oggi ----

    @Test fun focusPickerWithDays() = compose.shot("workouts-scelta-giorni") {
        FocusPickerContent(listOf(F.push, F.pull, F.legs), "Inizia", "", emptySet(), {}, {}, { _, _ -> })
    }

    @Test fun focusPickerByGroups() = compose.shot("workouts-scelta-gruppi") {
        FocusPickerContent(emptyList(), "Salva", "Spinta", setOf(MuscleGroup.petto, MuscleGroup.spalle), {}, {}, { _, _ -> })
    }

    // ---- una serie ----

    private val benchLast = F.activeSets.last()

    @Test fun addSetOnABarbellShowsPlatesAndWarmup() = compose.shot("workouts-serie-nuova") {
        AddSetContent(F.bench, SetMode.Add(benchLast), F.activeSets + F.done3Sets, F.benchRecords, kg, true, plates, {}, { _, _, _, _ -> })
    }

    @Test fun editingARecordSet() = compose.shot("workouts-serie-modifica") {
        AddSetContent(F.bench, SetMode.Edit(F.activeSets[3]), F.activeSets + F.done3Sets, F.benchRecords, kg, true, plates, {}, { _, _, _, _ -> })
    }

    @Test fun addSetWithOneOrTwoLimbs() = compose.shot("workouts-serie-un-braccio") {
        val last = F.set(40, F.raise, F.done1.uuid, 8.0, 12, arm = true, date = F.done1.date)
        AddSetContent(F.raise, SetMode.Add(last), listOf(last), emptyList(), kg, true, plates, {}, { _, _, _, _ -> })
    }

    // ---- i muscoli ----

    @Test fun muscleDetail() = compose.shot("workouts-muscoli") { MuscleDetailContent(F.squat, {}) }

    @Test fun muscleZoom() = compose.shot("workouts-muscoli-zoom") {
        MuscleZoomContent(listOf(MuscleId.chest, MuscleId.triceps), AnatomyView.front, {})
    }

    // ---- giorni e schede ----

    @Test fun newDayIsEmpty() = compose.shot("workouts-giorno-nuovo") {
        TemplateEditorContent(null, "", emptyList(), F.exercises, listOf(F.program), kg, F.program.uuid, {}, { _, _, _, _, _, _ -> }, {})
    }

    @Test fun editingADayShowsItsRows() = compose.shot("workouts-giorno-righe") {
        TemplateEditorContent(F.push, "", emptyList(), F.exercises, listOf(F.program, F.simpleProgram), kg, F.program.uuid, {}, { _, _, _, _, _, _ -> }, {})
    }

    @Test fun newProgram() = compose.shot("workouts-scheda-nuova") {
        ProgramEditorContent(null, emptyList(), false, {}, { _, _, _ -> }, {})
    }

    @Test fun programWithFixedDays() = compose.shot("workouts-scheda-giorni-fissi") {
        ProgramEditorContent(F.program, listOf(F.push, F.pull, F.legs), true, {}, { _, _, _ -> }, {})
    }

    // ---- il riepilogo ----

    private val finishedToday: WorkoutEntity = F.activeWorkout.copy(endedAt = F.activeWorkout.date + 71 * 60_000L)

    private fun earlier(n: Int, date: String) = WorkoutEntity(
        "00000000-0000-4000-8000-%012X".format(800 + n), date = F.at(date, 18), endedAt = F.at(date, 19),
    )

    private fun summary(name: String, workout: WorkoutEntity, sets: List<com.gabrieleforghieri.peak.data.WorkoutSetEntity>, history: List<WorkoutEntity>) {
        val all = history + workout
        val byWorkout = mapOf(workout.uuid to sets) + F.setsByWorkout.filterKeys { k -> history.any { it.uuid == k } }
        val snap = summarySnapshot(workout, all, byWorkout, F.exercises, F.benchRecords, weeklyGoal = 3)
        compose.shot(name, advanceMs = 9000) { WorkoutSummaryContent(workout, sets, F.exercises, snap, kg, onSaveAsDay = {}, onDone = {}) }
    }

    @Test fun summaryWithRecordsAndMedals() = summary(
        "workouts-riepilogo-record", finishedToday, F.activeSets,
        listOf(F.done1, F.done2, F.done3, earlier(1, "2026-10-06"), earlier(2, "2026-10-02"), earlier(3, "2026-09-20")),
    )

    @Test fun summaryWithoutRecords() = summary(
        "workouts-riepilogo-semplice", F.done1.copy(), F.done1Sets.map { it.copy(isPersonalRecord = false) }, listOf(F.done2, F.done3),
    )

}
