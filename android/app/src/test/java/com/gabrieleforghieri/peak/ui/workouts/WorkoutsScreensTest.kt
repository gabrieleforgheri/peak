package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.ui.test.junit4.createComposeRule
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.workouts.WorkoutsFixtures as F
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gli screenshot a schermo intero dell'area Allenamenti (la lista e la sessione), in italiano: `android/docs/screens/workouts-*.png`.
 * Si ricreano con `./gradlew recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h914dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class WorkoutsScreensTest {
    @get:Rule val compose = createComposeRule()

    private val exercisesByUuid = F.exercises.associateBy { it.uuid }
    private val kg = WeightUnit.kg

    private fun listData(
        active: Boolean = false, today: Boolean = false, programs: Boolean = false, history: Boolean = true,
    ) = WorkoutsListData(
        name = "Gabriele", stats = HomeStats(weekSessions = 2, streak = 4, goal = 3),
        active = F.activeWorkout.takeIf { active }, activeSets = if (active) F.activeSets else emptyList(),
        today = F.push.takeIf { today }, programs = if (programs) listOf(F.program, F.simpleProgram) else emptyList(),
        program = F.program.takeIf { programs }, days = if (programs) listOf(F.push, F.pull, F.legs) else emptyList(),
        finished = if (history) F.finished else emptyList(), setsByWorkout = F.setsByWorkout, exercises = exercisesByUuid,
    )

    @Test fun listEmpty() = compose.shot("workouts-lista-vuota") {
        WorkoutsListContent(
            listData(history = false).copy(stats = HomeStats(0, 0, 3)), kg, WorkoutsListEvents(), hour = 9, phraseIndex = 1,
        )
    }

    @Test fun listWithDaysAndHistory() = compose.shot("workouts-lista-giorni") {
        WorkoutsListContent(listData(today = true, programs = true), kg, WorkoutsListEvents(), hour = 17, phraseIndex = 3)
    }

    @Test fun listWithWorkoutInProgress() = compose.shot("workouts-lista-in-corso") {
        WorkoutsListContent(listData(active = true, programs = true), kg, WorkoutsListEvents(), hour = 19, phraseIndex = 5)
    }

    private fun session(workout: com.gabrieleforghieri.peak.data.WorkoutEntity, sets: List<com.gabrieleforghieri.peak.data.WorkoutSetEntity>, current: String? = null) =
        sessionUi(
            workout, sets, F.exercises, F.setsByWorkout.values.flatten() + F.done3Sets,
            mapOf(workout.uuid to workout.date, F.done1.uuid to F.done1.date, F.done2.uuid to F.done2.date, F.done3.uuid to F.done3.date), kg, current,
        )

    @Test fun sessionInProgressWithRecordAndRest() {
        val ui = session(F.activeWorkout, F.activeSets, current = F.bench.uuid)
        val now = F.activeWorkout.date + 47 * 60_000L
        compose.shot("workouts-sessione-in-corso") {
            WorkoutSessionContent(ui, kg, "", restEnd = now + 75_000, recordSet = null, recordTick = 0, events = SessionEvents(), now = now)
        }
    }

    @Test fun sessionJustStartedHasEmptyCards() {
        val fresh = F.activeWorkout.copy(date = F.at("2026-10-14", 18, 5))
        val ui = session(fresh, emptyList())
        compose.shot("workouts-sessione-vuota") {
            WorkoutSessionContent(ui, kg, "", restEnd = null, recordSet = null, recordTick = 0, events = SessionEvents(), now = fresh.date + 90_000L)
        }
    }

    @Test fun sessionFinishedIsReadOnlyInTheMenu() {
        val ui = session(F.done1.copy(plan = ""), F.done1Sets)
        compose.shot("workouts-sessione-conclusa") {
            WorkoutSessionContent(ui, kg, F.done1.note, restEnd = null, recordSet = null, recordTick = 0, events = SessionEvents())
        }
    }
}
