package com.gabrieleforghieri.peak.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le query di `SettingsDao`: ripristino di un archiviato e «Cancella tutti i dati». */
@RunWith(RobolectricTestRunner::class)
class SettingsDaoTest {
    private lateinit var db: PeakDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PeakDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun restoringBringsAnArchivedExerciseBackAndLeavesTheOthers() = runBlocking {
        db.exercises().upsert(ExerciseEntity("A", name = "Squat", isArchived = true))
        db.exercises().upsert(ExerciseEntity("B", name = "Panca", isArchived = true))
        db.areaSettings().unarchive("A")
        val byId = db.exercises().observeAll().first().associateBy { it.uuid }
        assertFalse(byId.getValue("A").isArchived)
        assertTrue(byId.getValue("B").isArchived)
    }

    @Test fun eraseAllDeletesTheDataButKeepsTheSettings() = runBlocking {
        db.exercises().upsert(ExerciseEntity("E", name = "Panca"))
        db.workouts().upsert(WorkoutEntity("W", date = 1))
        db.sets().upsert(WorkoutSetEntity("S", workoutUuid = "W", exerciseUuid = "E", weight = 80.0, reps = 5))
        db.records().upsert(MaxRecordEntity("R", exerciseUuid = "E", date = 1, weight = 100.0, reps = 1))
        db.bodyWeights().upsert(BodyWeightEntity("P", date = 1, weight = 80.0))
        db.templates().upsert(WorkoutTemplateEntity("T", name = "Push"))
        db.programs().upsert(WorkoutProgramEntity("G", name = "Scheda"))
        db.settings().put(SettingEntity("unit", "\"lb\""))

        db.areaSettings().eraseAll()

        assertTrue(db.exercises().all().isEmpty())
        assertTrue(db.workouts().observeAll().first().isEmpty())
        assertEquals(0, db.sets().ofWorkout("W").size)
        assertTrue(db.records().observeAll().first().isEmpty())
        assertTrue(db.bodyWeights().observeAll().first().isEmpty())
        assertTrue(db.templates().observeAll().first().isEmpty())
        assertTrue(db.programs().observeAll().first().isEmpty())
        assertEquals(listOf("unit"), db.settings().observeAll().first().map { it.key })
    }
}
