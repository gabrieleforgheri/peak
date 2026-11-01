package com.gabrieleforghieri.peak.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.RecordService
import com.gabrieleforghieri.peak.ui.machines.EditorForm
import com.gabrieleforghieri.peak.ui.machines.MachineLines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le query dell'area Macchinari e il percorso di salvataggio dell'editor e di «Aggiorna massimale», su Room in memoria. */
@RunWith(RobolectricTestRunner::class)
class MachinesDaoTest {
    private lateinit var db: PeakDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PeakDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun archiveHidesNothingInTheDatabaseButFlagsTheRow() = runBlocking {
        db.exercises().upsert(ExerciseEntity("A", name = "Squat"))
        db.records().upsert(MaxRecordEntity("R", "A", 1, 100.0, 5))
        db.areaMachines().archive("A")
        assertTrue(db.exercises().get("A")!!.isArchived)
        // lo storico resta
        assertEquals(1, db.records().ofExercise("A").size)
        // un uuid che non c'è non rompe nulla
        db.areaMachines().archive("nope")
    }

    @Test fun newMachinesGoLast() = runBlocking {
        assertEquals(1, db.areaMachines().nextSortIndex())
        db.exercises().upsert(ExerciseEntity("A", sortIndex = 4))
        db.exercises().upsert(ExerciseEntity("B", sortIndex = 2))
        assertEquals(5, db.areaMachines().nextSortIndex())
    }

    @Test fun theEditorsFormRoundTripsThroughTheDatabase() = runBlocking {
        val created = EditorForm(name = "Pressa mia", isCustom = true, step = 5.0, barWeight = 0.0).applyTo(null, "", db.areaMachines().nextSortIndex(), 1000, true, uuid = "U1")
        db.exercises().upsert(created)
        val loaded = db.exercises().get("U1")!!
        assertEquals(created, loaded)
        assertEquals(Limbs.both, MachineLines(loaded, emptyList()).limbs)
        // modifica: archivia e cambia il passo; l'uuid e la data di creazione restano
        val edited = EditorForm.of(loaded, true).copy(step = 2.5, isArchived = true).applyTo(loaded, "", 0, 2000, true)
        db.exercises().upsert(edited)
        assertEquals("U1", db.exercises().observeAll().first().single().uuid)
        assertEquals(1000, db.exercises().get("U1")!!.createdAt)
        assertTrue(db.exercises().get("U1")!!.isArchived)
    }

    @Test fun deletingAMachineTakesItsRecordsButNotItsSets() = runBlocking {
        db.exercises().upsert(ExerciseEntity("A", name = "Squat"))
        db.records().upsert(MaxRecordEntity("R", "A", 1, 100.0, 5))
        db.workouts().upsert(WorkoutEntity("W", date = 1))
        db.sets().upsert(WorkoutSetEntity("S", "W", "A", 80.0, 5))
        db.exercises().delete("A")
        assertTrue(db.records().ofExercise("A").isEmpty())
        assertNull(db.sets().get("S")!!.exerciseUuid)
    }

    @Test fun aManualMaxIsRecordedAndRecomputedWithoutTouchingTheOthers() = runBlocking {
        val service = RecordService(RoomRecordStorage(db))
        db.exercises().upsert(ExerciseEntity("A", name = "Panca"))
        // come EditMaxContent.save(): il massimale a mano, poi il ricalcolo
        db.records().upsert(MaxRecordEntity("R1", "A", 1000, 80.0, 5, isEstimated = false))
        service.recompute("A")
        db.records().upsert(MaxRecordEntity("R2", "A", 2000, 85.0, 3, isEstimated = false))
        service.recompute("A")
        val lines = MachineLines(db.exercises().get("A")!!, db.records().observeOfExercise("A").first())
        assertEquals("R2", lines.best?.uuid)
        assertEquals(2, lines.twoArm.size)
        assertFalse(lines.twoArm.any { it.isEstimated })
        // cancellarlo dallo storico (il cestino del dettaglio) rimette il precedente
        service.deleteRecord("R2")
        assertEquals("R1", MachineLines(db.exercises().get("A")!!, db.records().observeOfExercise("A").first()).best?.uuid)
    }
}
