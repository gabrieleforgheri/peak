package com.gabrieleforghieri.peak.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Prova di Room sul JVM (Robolectric, database in memoria): è l'esempio da copiare per provare le query di un'area.
 * Il database vero e le sue query non hanno bisogno né di un telefono né di un emulatore.
 */
@RunWith(RobolectricTestRunner::class)
class RoomDaoTest {
    private lateinit var db: PeakDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PeakDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun exercisesComeBackSortedByIndexThenName() = runBlocking {
        db.exercises().upsert(ExerciseEntity("B", name = "Panca", sortIndex = 1))
        db.exercises().upsert(ExerciseEntity("A", name = "Squat", sortIndex = 0))
        db.exercises().upsert(ExerciseEntity("C", name = "Croci", sortIndex = 1))
        assertEquals(listOf("A", "C", "B"), db.exercises().observeAll().first().map { it.uuid })
    }

    @Test fun deletingAnExerciseKeepsItsSetsInTheHistory() = runBlocking {
        db.exercises().upsert(ExerciseEntity("E", name = "Panca"))
        db.workouts().upsert(WorkoutEntity("W", date = 1))
        db.sets().upsert(WorkoutSetEntity("S", workoutUuid = "W", exerciseUuid = "E", weight = 80.0, reps = 5))
        db.exercises().delete("E")
        assertNull(db.sets().get("S")?.exerciseUuid)
        assertEquals(1, db.sets().ofWorkout("W").size)
    }
}
