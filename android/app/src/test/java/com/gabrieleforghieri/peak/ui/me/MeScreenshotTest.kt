package com.gabrieleforghieri.peak.ui.me

import androidx.compose.ui.test.junit4.createComposeRule
import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.domain.Medal
import com.gabrieleforghieri.peak.domain.MedalID
import com.gabrieleforghieri.peak.domain.Medals
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Gli screenshot di Me, in italiano (`android/docs/screens/me-*.png`): vuota, con i dati, con un solo peso, e i contenuti dei fogli
 * (la pesata, la medaglia), che nello screenshot di una schermata non entrano. Una viewport alta per vedere la scheda intera.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h3000dp-xhdpi")
class MeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val dayMs = 86_400_000L
    private val now = System.currentTimeMillis()
    private fun ago(d: Int) = now - d * dayMs

    private val exercises = listOf(
        ExerciseEntity("BENCH", name = "Panca piana", muscleGroup = "petto"),
        ExerciseEntity("SQUAT", name = "Squat", muscleGroup = "gambe"),
        ExerciseEntity("ROW", name = "Rematore", muscleGroup = "schiena"),
        ExerciseEntity("PRESS", name = "Military press", muscleGroup = "spalle"),
        ExerciseEntity("CURL", name = "Curl con bilanciere", muscleGroup = "braccia"),
    )

    /** Quarantotto allenamenti in quattordici settimane, a volte due a settimana, con carichi che crescono. */
    private fun history(): Triple<List<WorkoutEntity>, List<WorkoutSetEntity>, List<MaxRecordEntity>> {
        val workouts = mutableListOf<WorkoutEntity>()
        val sets = mutableListOf<WorkoutSetEntity>()
        val records = mutableListOf<MaxRecordEntity>()
        for (i in 0 until 28) {
            val d = 2 + i * 3 + (i % 2)
            val w = WorkoutEntity("W$i", date = ago(d), endedAt = ago(d) + 3_600_000)
            workouts += w
            for ((k, e) in exercises.withIndex()) {
                if ((i + k) % 2 == 1) continue
                val base = 40.0 + k * 12
                sets += WorkoutSetEntity("S$i-$k-w", w.uuid, e.uuid, base * 0.5, 10, isWarmup = true, createdAt = w.date)
                for (n in 0 until 3 + k % 2) sets += WorkoutSetEntity("S$i-$k-$n", w.uuid, e.uuid, base + (28 - i), 8, createdAt = w.date + n)
                if (i % 4 == 0) records += MaxRecordEntity("R$i-$k", e.uuid, w.date, base + (28 - i) * 1.0, 5 - (i % 3))
            }
        }
        return Triple(workouts, sets, records)
    }

    private fun weights(n: Int) = (0 until n).map { BodyWeightEntity("B$it", ago(it * 6), 82.4 - it * 0.45 + (it % 3) * 0.2) }

    private val noDates: suspend (MedalID) -> List<Long?> = { List(3) { null } }

    private fun content(data: MeData?, weights: List<BodyWeightEntity>, friends: Int = 0, name: String = "", unit: WeightUnit = WeightUnit.kg): @androidx.compose.runtime.Composable () -> Unit = {
        MeContent(name, friends, data, weights, unit, {}, {}, {}, {}, noDates)
    }

    @Test fun withData() {
        val (w, s, r) = history()
        compose.screenshot("me-con-dati", content(MeData.compute(w, s, exercises, r, 2), weights(14), friends = 2, name = "Gabriele Forghieri"))
    }

    @Test fun withDataInPounds() {
        val (w, s, r) = history()
        compose.screenshot("me-con-dati-lb", content(MeData.compute(w, s, exercises, r, 0), weights(3), unit = WeightUnit.lb))
    }
}
