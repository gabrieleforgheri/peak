package com.gabrieleforghieri.peak.ui.workouts

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.PeakDatabase
import com.gabrieleforghieri.peak.data.RoomRecordStorage
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.domain.RecordService
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.Uuids
import com.gabrieleforghieri.peak.domain.uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le scritture dell'area Allenamenti su Room in memoria (Robolectric): nessun telefono, nessun emulatore. */
@RunWith(RobolectricTestRunner::class)
class WorkoutsActionsTest {
    private lateinit var db: PeakDatabase
    private lateinit var actions: WorkoutsActions
    private val bench = ExerciseEntity(uuid(1), name = "Panca", muscleGroup = "petto")
    private val squat = ExerciseEntity(uuid(2), name = "Squat", muscleGroup = "gambe")

    @Before fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PeakDatabase::class.java).allowMainThreadQueries().build()
        actions = WorkoutsActions(db, RecordService(RoomRecordStorage(db)))
        db.exercises().upsert(bench)
        db.exercises().upsert(squat)
    }

    @After fun close() = db.close()

    private fun begin(id: Int = 1) = runBlocking { WorkoutEntity(uuid(100 + id), date = 1000).also { actions.begin(it) } }

    @Test fun finishingClosesOnceAndNeverTwice() = runBlocking {
        val w = begin()
        assertTrue(actions.finish(w.uuid, now = 5000))
        assertEquals(5000L, db.workouts().get(w.uuid)!!.endedAt)
        assertFalse(actions.finish(w.uuid, now = 9000))
        assertEquals(5000L, db.workouts().get(w.uuid)!!.endedAt)
    }

    @Test fun anActiveWorkoutIsTheOneWithoutAnEnd() = runBlocking {
        val w = begin()
        assertEquals(w.uuid, db.workouts().observeActive().first()?.uuid)
        actions.finish(w.uuid)
        assertNull(db.workouts().observeActive().first())
    }

    @Test fun aSetBeatingTheRecordIsFlaggedAndTheRecordIsDerived() = runBlocking {
        val w = begin()
        assertTrue("la prima serie è un massimale", actions.addSet(w.uuid, bench.uuid, 60.0, 8, false, false))
        assertFalse("pari: non è un record", actions.addSet(w.uuid, bench.uuid, 60.0, 8, false, false))
        assertTrue(actions.addSet(w.uuid, bench.uuid, 65.0, 5, false, false))
        assertEquals(listOf(true, false, true), db.sets().ofWorkout(w.uuid).map { it.isPersonalRecord })
        assertEquals(2, db.records().ofExercise(bench.uuid).size)
    }

    @Test fun warmupSetsNeverMakeARecord() = runBlocking {
        val w = begin()
        assertFalse(actions.addSet(w.uuid, bench.uuid, 100.0, 5, true, false))
        assertTrue(db.records().ofExercise(bench.uuid).isEmpty())
    }

    @Test fun correctingASetDownwardsDropsTheRecordItHadRaised() = runBlocking {
        val w = begin()
        actions.addSet(w.uuid, bench.uuid, 60.0, 8, false, false)
        actions.addSet(w.uuid, bench.uuid, 80.0, 5, false, false)
        val second = db.sets().ofWorkout(w.uuid)[1]
        assertTrue(second.isPersonalRecord)
        val stillRecord = actions.updateSet(second.uuid, 55.0, 5, false, false)
        assertFalse(stillRecord)
        assertFalse(db.sets().get(second.uuid)!!.isPersonalRecord)
        assertEquals(listOf(60.0), db.records().ofExercise(bench.uuid).map { it.weight })
        // e al rialzo, l'ultima serie torna un record
        assertTrue(actions.updateSet(second.uuid, 90.0, 5, false, false))
        assertEquals(setOf(60.0, 90.0), db.records().ofExercise(bench.uuid).map { it.weight }.toSet())
    }

    @Test fun deletingASetGivesBackThePreviousRecord() = runBlocking {
        val w = begin()
        actions.addSet(w.uuid, bench.uuid, 60.0, 8, false, false)
        actions.addSet(w.uuid, bench.uuid, 80.0, 5, false, false)
        actions.deleteSet(db.sets().ofWorkout(w.uuid)[1].uuid)
        assertEquals(listOf(60.0), db.records().ofExercise(bench.uuid).map { it.weight })
    }

    @Test fun deletingTheWorkoutRemovesItsSetsAndTheRecordsTheyMade() = runBlocking {
        val w = begin()
        actions.addSet(w.uuid, bench.uuid, 60.0, 8, false, false)
        actions.deleteWorkout(w.uuid)
        assertNull(db.workouts().get(w.uuid))
        assertTrue(db.sets().all().isEmpty())
        assertTrue(db.records().all().isEmpty())
    }

    @Test fun noteNameFocusAndPlanAreSavedWithoutTouchingTheRest() = runBlocking {
        val w = begin()
        actions.setNote(w.uuid, "Spalla ok")
        actions.setNameAndFocus(w.uuid, "Push", "petto,spalle")
        actions.setPlan(w.uuid, "[]")
        val saved = db.workouts().get(w.uuid)!!
        assertEquals(w.copy(note = "Spalla ok", name = "Push", focus = "petto,spalle", plan = "[]"), saved)
    }

    @Test fun theFirstDayCreatesTheDefaultProgramAndLaterOnesAreAppended() = runBlocking {
        val a = actions.saveTemplate(null, "Push", listOf(TemplateItem(bench.uuid, 3)), false, "", "", -1, activeSetting = "")
        val b = actions.saveTemplate(null, "Pull", emptyList(), true, "muscle:schiena", "", -1, activeSetting = "")
        assertEquals(Uuids.DEFAULT_PROGRAM, a.program)
        assertEquals(listOf("La mia scheda"), db.programs().all().map { it.name })
        assertEquals(listOf(0, 1), listOf(a, b).map { it.sortIndex })
        assertTrue(db.templates().get(b.uuid)!!.isHidden)
        assertEquals("muscle:schiena", db.templates().get(b.uuid)!!.icon)
    }

    @Test fun theFixedWeekdayCountsOnlyIfTheProgramUsesDays() = runBlocking {
        val loose = actions.saveProgram(null, "Settimana", useDays = false, weekdays = emptyMap())
        val fixed = actions.saveProgram(null, "Fissa", useDays = true, weekdays = emptyMap())
        val onLoose = actions.saveTemplate(null, "A", emptyList(), false, "", loose.uuid, 3, "")
        val onFixed = actions.saveTemplate(null, "B", emptyList(), false, "", fixed.uuid, 3, "")
        assertEquals(-1, onLoose.weekday)
        assertEquals(3, onFixed.weekday)
    }

    @Test fun editingADayKeepsItsPlaceAndNewProgramsComeLast() = runBlocking {
        val p1 = actions.saveProgram(null, "Uno", false, emptyMap())
        val p2 = actions.saveProgram(null, "Due", false, emptyMap())
        assertEquals(listOf(0, 1), listOf(p1, p2).map { it.sortIndex })
        val day = actions.saveTemplate(null, "A", emptyList(), false, "", p1.uuid, -1, "")
        val edited = actions.saveTemplate(day, "A2", listOf(TemplateItem(squat.uuid)), false, "bolt", p2.uuid, -1, "")
        assertEquals(day.uuid, edited.uuid)
        assertEquals(day.sortIndex, edited.sortIndex)
        assertEquals(p2.uuid, db.templates().get(day.uuid)!!.program)
        assertEquals(1, db.templates().all().size)
    }

    @Test fun savingAProgramAppliesTheChosenWeekdaysAndSwitchingOffClearsThem() = runBlocking {
        val p = actions.saveProgram(null, "Fissa", true, emptyMap())
        val d1 = actions.saveTemplate(null, "A", emptyList(), false, "", p.uuid, 0, "")
        val d2 = actions.saveTemplate(null, "B", emptyList(), false, "", p.uuid, 2, "")
        actions.saveProgram(p, "Fissa", true, mapOf(d1.uuid to 4, d2.uuid to -1))
        assertEquals(listOf(4, -1), listOf(d1, d2).map { db.templates().get(it.uuid)!!.weekday })
        actions.saveProgram(p, "Fissa", false, mapOf(d1.uuid to 4))
        assertEquals(listOf(-1, -1), listOf(d1, d2).map { db.templates().get(it.uuid)!!.weekday })
        assertFalse(db.programs().get(p.uuid)!!.useDays)
    }

    @Test fun deletingAProgramDeletesItsDaysAndKeepsTheWorkoutsDone() = runBlocking {
        val p = actions.saveProgram(null, "Uno", false, emptyMap())
        val other = actions.saveProgram(null, "Due", false, emptyMap())
        actions.saveTemplate(null, "A", emptyList(), false, "", p.uuid, -1, "")
        val keep = actions.saveTemplate(null, "B", emptyList(), false, "", other.uuid, -1, "")
        val w = begin()
        actions.deleteProgram(p.uuid)
        assertNull(db.programs().get(p.uuid))
        assertEquals(listOf(keep.uuid), db.templates().all().map { it.uuid })
        assertNotNull(db.workouts().get(w.uuid))
    }

    @Test fun updatingADayFromTodaysListKeepsTheTargetsItWrote() = runBlocking {
        val day = actions.saveTemplate(null, "A", listOf(TemplateItem(bench.uuid, 3, 8, 10, 60.0)), false, "", "", -1, "")
        actions.updateTemplateItems(day.uuid, listOf(TemplateItem(squat.uuid, 4), TemplateItem(bench.uuid, 3, 8, 10, 60.0)))
        val items = db.templates().get(day.uuid)!!.let { com.gabrieleforghieri.peak.domain.TemplateItems.decode(it.items) }
        assertEquals(listOf(squat.uuid, bench.uuid), items.map { it.exercise })
        assertEquals(60.0, items[1].weight!!, 0.001)
    }

    @Test fun currentProgramIsReusedWhenOneExists() = runBlocking {
        val p = actions.saveProgram(null, "Mia", false, emptyMap())
        assertEquals(p.uuid, actions.currentProgram("").uuid)
        assertEquals(1, db.programs().all().size)
    }
}
