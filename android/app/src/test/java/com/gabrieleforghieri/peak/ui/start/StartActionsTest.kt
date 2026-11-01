package com.gabrieleforghieri.peak.ui.start

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.PeakDatabase
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.domain.AppSettings
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.Onboarding
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.TestCatalog
import com.gabrieleforghieri.peak.domain.Uuids
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Le scritture dell'area Avvio su Room (Robolectric, database in memoria): scheda del primo avvio, disfatta, `adoptLoose`, `hasData`. */
@RunWith(RobolectricTestRunner::class)
class StartActionsTest {
    private lateinit var db: PeakDatabase
    private lateinit var actions: StartActions

    @Before fun open() {
        TestCatalog.load()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PeakDatabase::class.java).allowMainThreadQueries().build()
        actions = StartActions(db) { 1_000L }
    }

    @After fun close() = db.close()

    private fun day(group: MuscleGroup, count: Int = 3) =
        Onboarding.Day(group, group.title, Onboarding.suggested.getValue(group).take(count).map { Catalog.entry(it)!! })

    @Test fun anEmptyDatabaseHasNoData() = runBlocking {
        assertFalse(actions.hasData())
        db.bodyWeights().upsert(com.gabrieleforghieri.peak.data.BodyWeightEntity("B", date = 1, weight = 80.0))
        assertTrue(actions.hasData())
    }

    @Test fun createPlanWritesTheProgramTheDaysAndTheMachines() = runBlocking {
        val plan = actions.createPlan("La mia scheda", listOf(day(MuscleGroup.petto), day(MuscleGroup.gambe, 2)), AppSettings())
        val exercises = db.exercises().all()
        assertEquals(5, exercises.size)
        assertEquals(listOf(0, 1, 2, 3, 4), exercises.map { it.sortIndex }.sorted())
        assertTrue(exercises.all { it.catalogID.isNotEmpty() && it.limbs != null && it.createdAt == 1_000L })
        val program = db.programs().get(plan.programUuid)!!
        assertEquals("La mia scheda", program.name)
        val days = db.templates().all().sortedBy { it.sortIndex }
        assertEquals(listOf("Petto", "Gambe"), days.map { it.name })
        assertEquals(listOf("muscle:petto", "muscle:gambe"), days.map { it.icon })
        assertTrue(days.all { it.program == plan.programUuid })
        val items = TemplateItems.decode(days[0].items)
        assertEquals(3, items.size)
        assertTrue("le righe non hanno serie, ripetizioni né peso", items.all { it.sets == 0 && it.repsMin == 0 && it.weight == null })
        assertEquals(exercises.filter { it.catalogID in Onboarding.suggested.getValue(MuscleGroup.petto).take(3) }.map { it.uuid }.toSet(), items.map { it.exercise }.toSet())
    }

    @Test fun aMachineYouAlreadyHaveIsReusedNotDuplicated() = runBlocking {
        val first = Onboarding.suggested.getValue(MuscleGroup.petto).first()
        val mine = com.gabrieleforghieri.peak.domain.uuid(1)
        db.exercises().upsert(ExerciseEntity(mine, name = "Chest", catalogID = first, sortIndex = 7))
        val plan = actions.createPlan("Scheda", listOf(day(MuscleGroup.petto)), AppSettings())
        assertEquals(3, db.exercises().all().size)
        assertEquals("i nuovi vanno in fondo", 9, db.exercises().all().maxOf { it.sortIndex })
        assertTrue(TemplateItems.decode(db.templates().all().single().items).any { it.exercise == mine })
        assertNotNull(plan)
    }

    @Test fun undoPlanTakesOutOnlyWhatThePlanCreated() = runBlocking {
        db.exercises().upsert(ExerciseEntity("MINE", name = "Altro", sortIndex = 0))
        db.programs().upsert(com.gabrieleforghieri.peak.data.WorkoutProgramEntity("OLD", "Vecchia", 0))
        val plan = actions.createPlan("La mia scheda", listOf(day(MuscleGroup.schiena)), AppSettings())
        assertEquals(4, db.exercises().all().size)
        actions.undoPlan(plan)
        assertEquals(listOf("MINE"), db.exercises().all().map { it.uuid })
        assertEquals(listOf("OLD"), db.programs().all().map { it.uuid })
        assertTrue(db.templates().all().isEmpty())
    }

    @Test fun adoptLooseGathersDaysOutsideAnyProgramIntoTheDefaultOne() = runBlocking {
        db.templates().upsert(WorkoutTemplateEntity("T1", "Push", program = ""))
        db.templates().upsert(WorkoutTemplateEntity("T2", "Pull", program = "OTHER"))
        actions.adoptLoose("La mia scheda")
        assertEquals(Uuids.DEFAULT_PROGRAM, db.templates().get("T1")!!.program)
        assertEquals("OTHER", db.templates().get("T2")!!.program)
        assertEquals("La mia scheda", db.programs().get(Uuids.DEFAULT_PROGRAM)!!.name)
        // una seconda volta non cambia niente
        actions.adoptLoose("Altro nome")
        assertEquals("La mia scheda", db.programs().get(Uuids.DEFAULT_PROGRAM)!!.name)
    }

    @Test fun adoptLooseDoesNothingWithoutLooseDays() = runBlocking {
        actions.adoptLoose("La mia scheda")
        assertTrue(db.programs().all().isEmpty())
    }
}
