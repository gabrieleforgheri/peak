package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.ui.nav.PeakEnvironment
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.ZoneId

/**
 * I dati finti per gli screenshot dell'area Allenamenti: date fisse (le immagini non cambiano con i giorni), macchinari veri del catalogo,
 * quindi pittogrammi e muscoli veri.
 */
object WorkoutsFixtures {
    fun at(date: String, hour: Int = 18, minute: Int = 30): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun id(n: Int) = "00000000-0000-4000-8000-%012X".format(n)

    val bench = ExerciseEntity(id(1), name = "Panca Piana Bilanciere", muscleGroup = "petto", catalogID = "bench-press-barbell", incrementStep = 2.5, barWeight = 20.0, weightDisplay = "absolute")
    val squat = ExerciseEntity(id(2), name = "Squat con Bilanciere", muscleGroup = "gambe", catalogID = "squat-barbell", incrementStep = 2.5, barWeight = 20.0)
    val pulldown = ExerciseEntity(id(3), name = "Lat Machine", muscleGroup = "schiena", catalogID = "lat-pulldown", incrementStep = 5.0)
    val curl = ExerciseEntity(id(4), name = "Curl con Manubri", muscleGroup = "braccia", catalogID = "dumbbell-curl", incrementStep = 1.0)
    val raise = ExerciseEntity(id(5), name = "Alzate Laterali", muscleGroup = "spalle", catalogID = "lateral-raise", incrementStep = 1.0, limbs = "either")
    val exercises = listOf(bench, squat, pulldown, curl, raise)

    fun set(n: Int, exercise: ExerciseEntity?, workout: String, weight: Double, reps: Int, warmup: Boolean = false, record: Boolean = false, arm: Boolean = false, date: Long = 0L) =
        WorkoutSetEntity(id(1000 + n), workout, exercise?.uuid, weight, reps, isWarmup = warmup, order = n, createdAt = date + n * 60_000L, isPersonalRecord = record, singleArm = arm)

    val push = WorkoutTemplateEntity(
        id(500), name = "Push", sortIndex = 0, program = id(400), weekday = 0,
        items = TemplateItems.encode(listOf(TemplateItem(bench.uuid, 4, 6, 8, 80.0), TemplateItem(raise.uuid, 3, 12, 15, null))),
    )
    val pull = WorkoutTemplateEntity(id(501), name = "Pull", sortIndex = 1, program = id(400), weekday = 2, items = TemplateItems.encode(listOf(TemplateItem(pulldown.uuid, 4, 8, 10, 55.0), TemplateItem(curl.uuid, 3, 10, 12, null))))
    val legs = WorkoutTemplateEntity(id(502), name = "Gambe", sortIndex = 2, program = id(400), weekday = -1, items = TemplateItems.encode(listOf(TemplateItem(squat.uuid, 5, 5, 5, 100.0))))
    val program = WorkoutProgramEntity(id(400), name = "Push Pull Legs", useDays = true)
    val simpleProgram = WorkoutProgramEntity(id(401), name = "Forza", sortIndex = 1)

    // un allenamento in corso (con un record) e tre conclusi
    val activeWorkout = WorkoutEntity(
        id(700), date = at("2026-10-14", 18, 5), name = "Push", focus = "petto,spalle", template = push.uuid,
        plan = TemplateItems.encode(listOf(TemplateItem(bench.uuid, 4, 6, 8, 80.0), TemplateItem(raise.uuid, 3, 12, 15, null), TemplateItem(curl.uuid))),
    )
    val activeSets = listOf(
        set(1, bench, activeWorkout.uuid, 40.0, 10, warmup = true, date = activeWorkout.date),
        set(2, bench, activeWorkout.uuid, 60.0, 6, warmup = true, date = activeWorkout.date),
        set(3, bench, activeWorkout.uuid, 80.0, 6, date = activeWorkout.date),
        set(4, bench, activeWorkout.uuid, 82.5, 6, record = true, date = activeWorkout.date),
        set(5, bench, activeWorkout.uuid, 82.5, 5, date = activeWorkout.date),
    )

    val done1 = WorkoutEntity(id(701), date = at("2026-10-12", 18), endedAt = at("2026-10-12", 19, 15), name = "Pull", note = "Spalla destra un po' dura, scaldare di più.")
    val done1Sets = listOf(
        set(11, pulldown, done1.uuid, 55.0, 10, date = done1.date), set(12, pulldown, done1.uuid, 55.0, 9, record = true, date = done1.date),
        set(13, curl, done1.uuid, 14.0, 10, date = done1.date),
    )
    val done2 = WorkoutEntity(id(702), date = at("2026-10-09", 7, 30), endedAt = at("2026-10-09", 8, 40), name = "Gambe")
    val done2Sets = listOf(set(21, squat, done2.uuid, 100.0, 5, date = done2.date), set(22, squat, done2.uuid, 102.5, 5, record = true, date = done2.date))
    val done3 = WorkoutEntity(id(703), date = at("2026-09-28", 18), endedAt = at("2026-09-28", 19), name = "")
    val done3Sets = listOf(set(31, bench, done3.uuid, 77.5, 6, date = done3.date), set(32, bench, done3.uuid, 77.5, 5, date = done3.date))
    val finished = listOf(done1, done2, done3)
    val setsByWorkout = mapOf(
        activeWorkout.uuid to activeSets, done1.uuid to done1Sets, done2.uuid to done2Sets, done3.uuid to done3Sets,
    )

    val benchRecords = listOf(
        MaxRecordEntity(id(900), bench.uuid, done3.date, 77.5, 6, isEstimated = true),
        MaxRecordEntity(id(901), bench.uuid, activeWorkout.date, 82.5, 6, isEstimated = true),
    )
}

/**
 * Lo screenshot di una schermata dell'area Allenamenti, in italiano (`@Config(qualifiers = "it-…")` sulla classe), senza audio né vibrazioni:
 * come `screenshot` di `ui/ScreenshotSupport.kt`, in più `advanceMs` fa correre l'orologio dell'interfaccia (per la festa del riepilogo).
 */
fun ComposeContentTestRule.shot(name: String, advanceMs: Long = 0, content: @Composable () -> Unit) {
    val app = ApplicationProvider.getApplicationContext<PeakApplication>()
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PeakEnvironment(app) {
                PeakTheme {
                    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current))) { content() }
                }
            }
        }
    }
    if (advanceMs > 0) mainClock.advanceTimeBy(advanceMs)
    waitForIdle()
    onRoot().captureRoboImage("../docs/screens/$name.png")
}
