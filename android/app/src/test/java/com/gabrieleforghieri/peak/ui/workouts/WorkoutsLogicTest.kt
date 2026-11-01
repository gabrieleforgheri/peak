package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.ui.geometry.Offset
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.MuscleId
import com.gabrieleforghieri.peak.domain.Plan
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.domain.uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private const val DAY = 86_400_000L
private val zone = ZoneId.systemDefault()
private fun at(date: String, hour: Int = 10): Long = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

private val squat = ExerciseEntity(uuid(1), name = "Squat", muscleGroup = "gambe")
private val bench = ExerciseEntity(uuid(2), name = "Panca", muscleGroup = "petto")
private val curl = ExerciseEntity(uuid(3), name = "Curl", muscleGroup = "braccia")
private val all = listOf(squat, bench, curl)
private val exists: (String) -> Boolean = { id -> all.any { it.uuid == id } }

private fun set(n: Int, exercise: ExerciseEntity?, workout: String, weight: Double = 100.0, reps: Int = 5, warmup: Boolean = false, at: Long = n * 1000L, record: Boolean = false, arm: Boolean = false) =
    WorkoutSetEntity(uuid(100 + n), workout, exercise?.uuid, weight, reps, isWarmup = warmup, order = n, createdAt = at, isPersonalRecord = record, singleArm = arm)

private fun workout(n: Int, date: Long, ended: Long? = date + 3_600_000, plan: List<TemplateItem> = emptyList(), template: String = "", focus: String = "") =
    WorkoutEntity(uuid(200 + n), date = date, endedAt = ended, plan = TemplateItems.encode(plan), template = template, focus = focus)

class WorkoutsLogicTest {

    // ---- la lista di oggi ----

    @Test fun todayListIsThePlanFollowedByExercisesDoneOutsideIt() {
        val w = workout(1, 0, ended = null, plan = listOf(TemplateItem(bench.uuid, sets = 3), TemplateItem(squat.uuid)))
        val sets = listOf(set(1, curl, w.uuid), set(2, bench, w.uuid))
        assertEquals(listOf(bench.uuid, squat.uuid, curl.uuid), sessionItems(w, sets, exists).map { it.exercise })
    }

    @Test fun todayListDropsExercisesThatNoLongerExist() {
        val ghost = uuid(99)
        val w = workout(1, 0, ended = null, plan = listOf(TemplateItem(ghost), TemplateItem(bench.uuid)))
        assertEquals(listOf(bench.uuid), sessionItems(w, emptyList(), exists).map { it.exercise })
    }

    @Test fun editedPlanWritesOutOfPlanExercisesAsRows() {
        val w = workout(1, 0, ended = null, plan = listOf(TemplateItem(bench.uuid)))
        val sets = listOf(set(1, curl, w.uuid))
        val plan = TemplateItems.decode(editedPlan(w, sets, exists) { Plan.moving(it, curl.uuid, -1) })
        assertEquals(listOf(curl.uuid, bench.uuid), plan.map { it.exercise })
    }

    // ---- lo stato della sessione ----

    @Test fun sessionStateOfAnActiveWorkout() {
        val w = workout(1, at("2026-10-05"), ended = null, plan = listOf(TemplateItem(bench.uuid, sets = 4, repsMin = 8, repsMax = 10, weight = 60.0), TemplateItem(squat.uuid)))
        val sets = listOf(
            set(1, bench, w.uuid, 40.0, 10, warmup = true), set(2, bench, w.uuid, 60.0, 8, record = true), set(3, bench, w.uuid, 60.0, 7),
        )
        val ui = sessionUi(w, sets, all, sets, mapOf(w.uuid to w.date), WeightUnit.kg, currentExercise = bench.uuid)
        assertEquals(listOf(bench.uuid, squat.uuid), ui.cards.map { it.exercise.uuid })
        assertEquals(2, ui.workingSets)
        assertEquals(60.0 * 8 + 60.0 * 7, ui.volume, 0.001)
        assertEquals(1, ui.recordCount)
        val first = ui.cards[0]
        assertEquals("2/4 serie · 8–10 rip · 60 kg", first.goal)
        assertTrue(first.isCurrent)
        // con delle serie fatte non si sostituisce e non si toglie; si sposta sempre (a sessione in corso)
        assertTrue(first.canMove); assertFalse(first.canReplace); assertFalse(first.canRemove)
        val second = ui.cards[1]
        assertTrue(second.canMove); assertTrue(second.canReplace); assertTrue(second.canRemove)
        assertEquals("", second.goal)
    }

    @Test fun aFinishedWorkoutOffersNoMenuChanges() {
        val w = workout(1, at("2026-10-05"), plan = listOf(TemplateItem(bench.uuid)))
        val ui = sessionUi(w, emptyList(), all, emptyList(), mapOf(w.uuid to w.date), WeightUnit.kg, null)
        val card = ui.cards.single()
        assertFalse(card.canMove); assertFalse(card.canReplace); assertFalse(card.canRemove)
    }

    @Test fun setsOfADeletedExerciseAreShownAsOrphansAndCount() {
        val w = workout(1, 0, ended = null)
        val sets = listOf(set(1, null, w.uuid, 50.0, 10), set(2, bench, w.uuid, 60.0, 5))
        val ui = sessionUi(w, sets, all, sets, mapOf(w.uuid to 0), WeightUnit.kg, null)
        assertEquals(listOf(uuid(101)), ui.orphans.map { it.uuid })
        assertEquals(2, ui.workingSets)
        assertEquals(50.0 * 10 + 60.0 * 5, ui.volume, 0.001)
    }

    // ---- «Scorso» e l'ultima serie ----

    @Test fun previousDaysAreTheLastThreeOtherWorkoutsMostRecentFirstWithoutWarmup() {
        val dates = (1..5).associate { uuid(200 + it) to at("2026-09-0$it") } + (uuid(206) to at("2026-10-01"))
        val history = (1..5).map { set(it, bench, uuid(200 + it), 50.0 + it, 5) } + set(9, bench, uuid(201), 30.0, 10, warmup = true) + set(10, bench, uuid(206), 90.0, 3)
        val days = previousDays(history, uuid(206), dates)
        assertEquals(listOf(at("2026-09-05"), at("2026-09-04"), at("2026-09-03")), days.map { it.date })
        assertTrue(days.all { d -> d.sets.none { it.isWarmup } })
        // «Scorso»: le serie di lavoro dell'ultimo altro allenamento
        assertEquals(listOf(uuid(105)), previousWorkingSets(history, uuid(206), dates).map { it.uuid })
    }

    @Test fun previousSetsIgnoreWorkoutsThatNoLongerExist() {
        val history = listOf(set(1, bench, uuid(250)), set(2, bench, uuid(251)))
        assertTrue(previousWorkingSets(history, uuid(252), mapOf(uuid(251) to 10L)).all { it.workoutUuid == uuid(251) })
        assertTrue(previousDays(history, uuid(252), emptyMap()).isEmpty())
    }

    @Test fun lastSetPrefersTodayThenTheLatestEverThenAWarmup() {
        val today = listOf(set(1, bench, "W", 40.0, 10, warmup = true), set(2, bench, "W", 60.0, 8))
        val ever = listOf(set(5, bench, "OLD", 80.0, 5, at = 5000), set(6, bench, "OLD", 70.0, 6, at = 9000))
        assertEquals(uuid(102), lastSet(today, ever)?.uuid)
        assertEquals(uuid(106), lastSet(emptyList(), ever)?.uuid)
        assertEquals(uuid(101), lastSet(today.take(1), emptyList())?.uuid)
        assertNull(lastSet(emptyList(), emptyList()))
    }

    // ---- la scheda di partenza ----

    private fun template(items: List<TemplateItem>, id: Int = 1) = WorkoutTemplateEntity(uuid(300 + id), name = "Push", items = TemplateItems.encode(items))

    @Test fun startingADayCopiesItsRowsAndTakesTheGroupsFromTheExercises() {
        val t = template(listOf(TemplateItem(bench.uuid, 3), TemplateItem(uuid(99), 2), TemplateItem(curl.uuid)))
        val w = workoutFromTemplate(t, all, now = 1234L, uuid = "NEW")
        assertEquals("Push", w.name)
        assertEquals(t.uuid, w.template)
        assertEquals(listOf(bench.uuid, curl.uuid), w.planItems.map { it.exercise })
        assertEquals(setOf(MuscleGroup.petto, MuscleGroup.braccia), w.focusGroups)
        assertEquals(1234L, w.date)
        assertNull(w.endedAt)
    }

    @Test fun startingByGroupsKeepsNameAndFocusAndHasNoList() {
        val w = workoutFromFocus("Gambe", setOf(MuscleGroup.gambe, MuscleGroup.core), 5L, "X")
        assertEquals("gambe,core", w.focus)
        assertEquals(emptyList<TemplateItem>(), w.planItems)
    }

    @Test fun theTemplateIsOfferedForUpdateOnlyWhenExercisesOrOrderChange() {
        val t = template(listOf(TemplateItem(bench.uuid, 3, 8, 10, 60.0), TemplateItem(squat.uuid)))
        val same = workout(1, 0, template = t.uuid, plan = listOf(TemplateItem(bench.uuid, 5), TemplateItem(squat.uuid)))
        assertNull("gli obiettivi non contano", changedTemplate(same, emptyList(), listOf(t), exists))
        val reordered = same.copy(plan = TemplateItems.encode(listOf(TemplateItem(squat.uuid), TemplateItem(bench.uuid))))
        assertEquals(t.uuid, changedTemplate(reordered, emptyList(), listOf(t), exists)?.uuid)
        val extra = same.copy(plan = TemplateItems.encode(listOf(TemplateItem(bench.uuid), TemplateItem(squat.uuid), TemplateItem(curl.uuid))))
        assertNotNull(changedTemplate(extra, emptyList(), listOf(t), exists))
        // una scheda sparita non si offre
        assertNull(changedTemplate(extra, emptyList(), emptyList(), exists))
        assertNull(changedTemplate(extra.copy(template = ""), emptyList(), listOf(t), exists))
    }

    // ---- l'eroe, le schede, i giorni ----

    @Test fun homeStatsCountThisWeekTheStreakAndTheChosenGoal() {
        val now = at("2026-10-15") // giovedì
        val finished = listOf(at("2026-10-13"), at("2026-10-12"), at("2026-10-06"), at("2026-09-29"))
        val stats = homeStats(finished, chosenGoal = 4, now = now)
        assertEquals(2, stats.weekSessions)
        assertEquals(3, stats.streak)
        assertEquals(4, stats.goal)
        assertEquals(3, homeStats(emptyList(), 0, now).goal)
    }

    @Test fun theActiveProgramFollowsTheSettingElseTheFirstAndDaysBelongToIt() {
        val a = WorkoutProgramEntity(uuid(401), name = "A")
        val b = WorkoutProgramEntity(uuid(402), name = "B", useDays = true)
        assertEquals(uuid(402), activeProgram(listOf(a, b), uuid(402).lowercase())?.uuid)
        assertEquals(uuid(401), activeProgram(listOf(a, b), "")?.uuid)
        val monday = WorkoutTemplateEntity(uuid(501), name = "Push", program = b.uuid, weekday = 0)
        val loose = WorkoutTemplateEntity(uuid(502), name = "Libero", program = "")
        assertEquals(listOf(uuid(501)), daysOf(b, listOf(monday, loose)).map { it.uuid })
        assertEquals(listOf(uuid(502)), daysOf(null, listOf(monday, loose)).map { it.uuid })
        // il giorno fisso c'è solo se la scheda li usa
        assertEquals(uuid(501), todayTemplate(b, listOf(monday), at("2026-10-12"))?.uuid)
        assertNull(todayTemplate(b, listOf(monday), at("2026-10-13")))
        assertNull(todayTemplate(a.copy(useDays = false), listOf(monday.copy(program = a.uuid)), at("2026-10-12")))
    }

    // ---- l'inserimento di una serie ----

    private val bar = ExerciseEntity(uuid(7), name = "Panca piana bilanciere", muscleGroup = "petto", barWeight = 20.0, weightDisplay = "relative", catalogID = "")

    @Test fun relativeLoadHidesTheBarAndSideLoadHalvesTheRest() {
        val rel = LoadMode.of(bar, plateCalculatorEnabled = true)
        assertTrue(rel.relative)
        assertEquals(80.0, rel.fieldKg(100.0), 0.001)
        assertEquals(100.0, rel.totalKg(80.0), 0.001)
        assertEquals(0.0, rel.fieldKg(10.0), 0.001)

        val side = LoadMode.of(bar.copy(perSide = true), true)
        assertEquals(30.0, side.fieldKg(80.0), 0.001)
        assertEquals(80.0, side.totalKg(30.0), 0.001)

        val off = LoadMode.of(bar, plateCalculatorEnabled = false)
        assertFalse(off.relative); assertNull(off.plateBase)
        assertEquals(100.0, off.fieldKg(100.0), 0.001)
    }

    @Test fun thePlateCalculatorAppearsOnBarbellsAndNotOnMachinesWithoutBar() {
        assertEquals(20.0, LoadMode.plateBase(bar, true)!!, 0.001)
        assertNull(LoadMode.plateBase(ExerciseEntity(uuid(8), name = "Leg extension", muscleGroup = "gambe"), true))
        // plate loaded: calcolatore senza sbarra
        assertEquals(0.0, LoadMode.plateBase(ExerciseEntity(uuid(9), name = "Chest press plate loaded", muscleGroup = "petto", plateLoaded = true), true)!!, 0.001)
    }

    private fun record(n: Int, weight: Double, reps: Int, date: Long = n * 1000L, arm: Boolean = false) =
        MaxRecordEntity(uuid(600 + n), bench.uuid, date, weight, reps, singleArm = arm)

    @Test fun aSetBeatsTheRecordByLoadThenByRepsAndNeverAsWarmup() {
        val ref = referenceRecord(bench, listOf(record(1, 80.0, 5), record(2, 85.0, 3)), singleArm = false, editing = null)
        assertEquals(uuid(602), ref?.uuid)
        assertTrue(beatsRecord(85.0, 4, false, ref))
        assertFalse(beatsRecord(85.0, 3, false, ref))
        assertFalse(beatsRecord(90.0, 3, true, ref))
        assertTrue("senza massimale vince qualsiasi serie", beatsRecord(10.0, 1, false, null))
    }

    @Test fun whenEditingTheRecordOfTheSetItselfDoesNotCountAgainstIt() {
        val records = listOf(record(1, 80.0, 5), record(2, 85.0, 3))
        val ref = referenceRecord(bench, records, singleArm = false, editing = uuid(602))
        assertEquals(uuid(601), ref?.uuid)
    }

    @Test fun oneArmRecordsAreAnotherLineOnlyWhereTheMachineAllowsBothWays() {
        val either = ExerciseEntity(uuid(10), name = "Lateral raise", muscleGroup = "spalle", limbs = "either")
        val two = MaxRecordEntity(uuid(701), either.uuid, 1, 20.0, 8, singleArm = false)
        val one = MaxRecordEntity(uuid(702), either.uuid, 2, 12.0, 8, singleArm = true)
        assertEquals(uuid(701), referenceRecord(either, listOf(two, one), singleArm = false, editing = null)?.uuid)
        assertEquals(uuid(702), referenceRecord(either, listOf(two, one), singleArm = true, editing = null)?.uuid)
    }

    @Test fun weightDeltaIsInTheShownUnitAndIgnoresNoise() {
        assertNull(weightDelta(60.0, 60.004, WeightUnit.kg))
        assertEquals(2.5, weightDelta(62.5, 60.0, WeightUnit.kg)!!, 0.001)
        assertNull(weightDelta(60.0, null, WeightUnit.kg))
        assertEquals(-5.0, weightDelta(100.0, 102.27, WeightUnit.lb)!!, 0.1)
    }

    @Test fun theFormStartsFromTheLastSetThenTheLatestRecordThenFourSteps() {
        val last = set(1, bench, "W", 72.5, 6)
        assertEquals(72.5 to 6, initialLoad(last, record(1, 80.0, 3), 2.5))
        assertEquals(80.0 to 5, initialLoad(null, record(1, 80.0, 3), 2.5))
        assertEquals(80.0 to 8, initialLoad(null, record(1, 80.0, 8), 2.5))
        assertEquals(10.0 to 8, initialLoad(null, null, 2.5))
    }

    // ---- l'editor dei giorni ----

    @Test fun templateRowsAreClampedAndOneRepFigureIsEnough() {
        val rows = listOf(
            TemplateRow(bench.uuid, sets = "50", repsMin = "", repsMax = "10", weight = "62,5"),
            TemplateRow(squat.uuid, sets = "", repsMin = "12", repsMax = "8", weight = ""),
            TemplateRow(uuid(99), sets = "3"),
            TemplateRow(curl.uuid, repsMin = "abc", weight = "-5"),
        )
        val items = templateItems(rows, exists, WeightUnit.kg)
        assertEquals(listOf(bench.uuid, squat.uuid, curl.uuid), items.map { it.exercise })
        assertEquals(TemplateItem(bench.uuid, 20, 10, 10, 62.5), items[0])
        assertEquals(TemplateItem(squat.uuid, 0, 12, 12, null), items[1])
        assertEquals(TemplateItem(curl.uuid, 0, 0, 0, null), items[2])
    }

    @Test fun templateWeightsAreTypedInTheShownUnitAndStoredInKilograms() {
        val rows = templateRows(listOf(TemplateItem(bench.uuid, 3, 8, 10, 45.359237)), WeightUnit.lb)
        assertEquals("100", rows[0].weight)
        val back = templateItems(rows, exists, WeightUnit.lb)
        assertEquals(45.359237, back[0].weight!!, 0.01)
        assertEquals("", templateRows(listOf(TemplateItem(bench.uuid)), WeightUnit.kg)[0].sets)
    }

    @Test fun swappedMovesOnlyInsideTheList() {
        assertEquals(listOf(2, 1, 3), listOf(1, 2, 3).swapped(0, 1))
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).swapped(0, -1))
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).swapped(2, 3))
    }

    // ---- il selettore ----

    @Test fun recentExercisesAreAtMostSixDistinctAndVisible() {
        val many = (1..8).map { ExerciseEntity(uuid(800 + it), name = "E$it", muscleGroup = "petto") }
        val byUuid = many.associateBy { it.uuid }
        val sets = (listOf(1, 2, 1, 3, 4, 5, 6, 7, 8)).mapIndexed { i, n -> set(i, many[n - 1], "W") }
        assertEquals((1..6).map { uuid(800 + it) }, recentExercises(sets, byUuid, many).map { it.uuid })
        assertEquals(listOf(uuid(802)), recentExercises(sets, byUuid, many.filter { it.uuid == uuid(802) }).map { it.uuid })
    }

    @Test fun currentMaxIsTheRealHeaviestTwoArmLoad() {
        val maxes = currentMaxes(listOf(bench), listOf(record(1, 80.0, 5), record(2, 85.0, 3), record(3, 85.0, 6), record(4, 120.0, 1, arm = true)))
        // il singolo braccio non conta se il macchinario non si fa in tutti e due i modi
        assertEquals(120.0, maxes[bench.uuid]!!, 0.001)
        val either = ExerciseEntity(uuid(10), name = "Raise", muscleGroup = "spalle", limbs = "either")
        val r = listOf(MaxRecordEntity(uuid(1), either.uuid, 1, 20.0, 8), MaxRecordEntity(uuid(2), either.uuid, 2, 30.0, 8, singleArm = true))
        assertEquals(20.0, currentMaxes(listOf(either), r)[either.uuid]!!, 0.001)
    }

    @Test fun glyphIsDeducedFromTheNameInOrder() {
        assertEquals("barbell", suggestedGlyph("Rematore bilanciere", MuscleGroup.schiena).raw)
        assertEquals("seatedRow", suggestedGlyph("Rematore al macchinario", MuscleGroup.schiena).raw)
        assertEquals("legLever", suggestedGlyph("Qualcosa", MuscleGroup.gambe).raw)
    }

    // ---- il riposo ----

    @Test fun restTimerStartsShiftsAndStops() {
        RestTimer.stop()
        assertNull(RestTimer.end.value)
        RestTimer.start(90, now = 1_000)
        assertEquals(91_000L, RestTimer.end.value)
        RestTimer.shift(15, now = 2_000)
        assertEquals(106_000L, RestTimer.end.value)
        RestTimer.shift(-200, now = 2_000)
        assertNull("spostato nel passato si ferma", RestTimer.end.value)
        RestTimer.shift(15, now = 2_000)
        assertNull(RestTimer.end.value)
    }

    // ---- lo zoom dei muscoli ----

    @Test fun zoomIsClampedAndTheBodyStaysCenteredWhenItAllFits() {
        val w = 360.0; val h = 640.0
        val kMin = ZoomCam.kMin(w, h)
        val far = ZoomCam.clamped(ZoomCam(-500.0, 5000.0, 100.0), w, h)
        assertEquals(kMin * ZoomCam.MAX_ZOOM, far.k, 1e-9)
        assertTrue(far.cx >= (w / far.k) / 2 - 1e-9 && far.cy <= ZoomCam.FULL_HEIGHT - (h / far.k) / 2 + 1e-9)
        val whole = ZoomCam.clamped(ZoomCam(10.0, 10.0, 0.0001), w, h)
        assertEquals(kMin, whole.k, 1e-9)
        assertEquals(ZoomCam.FULL_WIDTH / 2, whole.cx, 1e-9)
    }

    @Test fun doubleTapAlternatesBetweenTheFrameOnTheMusclesAndTheWholeBody() {
        val w = 360.0; val h = 640.0
        val muscles = listOf(MuscleId.chest)
        val frame = ZoomCam.target(false, muscles, AnatomyView.front, w, h)
        val whole = ZoomCam.target(true, muscles, AnatomyView.front, w, h)
        assertTrue(frame.k > whole.k)
        assertEquals(whole, ZoomCam.toggled(frame, muscles, AnatomyView.front, w, h))
        assertEquals(frame, ZoomCam.toggled(whole, muscles, AnatomyView.front, w, h))
    }

    @Test fun pinchKeepsThePointUnderTheFingers() {
        val w = 360.0; val h = 640.0
        val start = ZoomCam.target(false, listOf(MuscleId.chest), AnatomyView.front, w, h)
        val anchor = Offset(200f, 300f)
        fun world(c: ZoomCam) = Offset((c.cx + (anchor.x - w / 2) / c.k).toFloat(), (c.cy + (anchor.y - h / 2) / c.k).toFloat())
        val zoomed = ZoomCam.transformed(start, anchor, Offset.Zero, 1.2f, w, h)
        assertTrue(zoomed.k > start.k)
        val a = world(start); val b = world(zoomed)
        assertEquals(a.x.toDouble(), b.x.toDouble(), 1.0)
        assertEquals(a.y.toDouble(), b.y.toDouble(), 1.0)
    }

    @Test fun musclesOfASideAreOnlyThoseDrawnThere() {
        assertTrue(MuscleId.chest in musclesOnSide(listOf(MuscleId.chest, MuscleId.hamstring), AnatomyView.front))
        assertFalse(MuscleId.hamstring in musclesOnSide(listOf(MuscleId.chest, MuscleId.hamstring), AnatomyView.front))
        assertTrue(MuscleId.hamstring in musclesOnSide(listOf(MuscleId.chest, MuscleId.hamstring), AnatomyView.back))
    }

    // ---- il riepilogo ----

    @Test fun summaryCountsTheWeekTheStreakTheGoalAndTheRecords() {
        val monday = at("2026-10-12"); val wednesday = at("2026-10-14")
        val before = workout(1, at("2026-10-05"))
        val first = workout(2, monday)
        val thisOne = workout(3, wednesday)
        val later = workout(4, at("2026-10-20"))
        val sets = mapOf(
            before.uuid to listOf(set(1, bench, before.uuid, 80.0, 5)),
            first.uuid to listOf(set(2, bench, first.uuid, 82.5, 5)),
            thisOne.uuid to listOf(set(3, bench, thisOne.uuid, 85.0, 5, record = true), set(4, bench, thisOne.uuid, 60.0, 8)),
            later.uuid to listOf(set(5, bench, later.uuid, 90.0, 5)),
        )
        val records = listOf(MaxRecordEntity(uuid(901), bench.uuid, wednesday, 85.0, 5, isEstimated = true))
        val snap = summarySnapshot(thisOne, listOf(before, first, thisOne, later), sets, all, records, weeklyGoal = 3)
        assertEquals(setOf(0, 2), snap.days)
        assertEquals(2, snap.today)
        assertEquals(2, snap.thisWeek)
        assertEquals(3, snap.goal)
        assertEquals(2, snap.streakNow)
        assertEquals(2, snap.streakBefore)
        assertEquals(listOf(uuid(103)), snap.records.map { it.uuid })
        assertTrue("un allenamento in più fa salire almeno la costanza o il volume", snap.moved.isNotEmpty())
    }

    @Test fun summaryStreakGrowsWhenThisWorkoutOpensTheWeek() {
        val last = workout(1, at("2026-10-07"))
        val thisOne = workout(2, at("2026-10-13"))
        val snap = summarySnapshot(thisOne, listOf(last, thisOne), emptyMap(), all, emptyList(), weeklyGoal = 0)
        assertEquals(2, snap.streakNow)
        assertEquals(1, snap.streakBefore)
        assertEquals(3, snap.goal)
        assertTrue(snap.records.isEmpty())
    }

    @Test fun summaryMedalsReportTheTierThatThisWorkoutRaised() {
        // 4 allenamenti prima, il quinto fa scattare il bronzo di «Costanza» (5)
        val past = (1..4).map { workout(it, at("2026-09-0$it")) }
        val fifth = workout(5, at("2026-09-10"))
        val snap = summarySnapshot(fifth, past + fifth, emptyMap(), all, emptyList(), 3)
        val sessions = snap.moved.first { it.after.kind.name == "sessions" }
        assertEquals(0, sessions.before.tier)
        assertEquals(1, sessions.after.tier)
        assertTrue(sessions.leveledUp)
    }
}
