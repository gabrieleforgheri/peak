package com.gabrieleforghieri.peak.domain

import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** I vettori condivisi con il web e con Swift: `common/fixtures/records.json` (come `records.test.ts` e `scripts/check_records.sh`). */
class RecordRulesFixturesTest {
    private fun ms(text: String) = Instant.parse(text).toEpochMilli()

    @Test fun sharedVectorsGiveTheSameRecords() {
        val text = checkNotNull(javaClass.classLoader.getResource("records.json")).readText()
        val vectors = Json.parseToJsonElement(text).jsonArray
        assertTrue(vectors.size >= 10)
        for (v in vectors) {
            val o = v.jsonObject
            val name = o.getValue("name").jsonPrimitive.content
            val live = o.getValue("workouts").jsonArray.map { it.jsonPrimitive.content }.toSet()
            val manual = o.getValue("manual").jsonArray.map { it.jsonObject }
            val sets = o.getValue("sets").jsonArray.map { it.jsonObject }.filter { it["exercise"]?.jsonPrimitive?.contentOrNull != null && it.getValue("workout").jsonPrimitive.content in live }
            val exercises = (manual.map { it.getValue("exercise").jsonPrimitive.content } + sets.map { it.getValue("exercise").jsonPrimitive.content }).distinct()
            val pr = exercises.flatMap { ex ->
                RecordRules.personalRecordSets(
                    manual.filter { it.getValue("exercise").jsonPrimitive.content == ex }.map {
                        RecordRules.ManualRecord(
                            it.getValue("uuid").jsonPrimitive.content, ms(it.getValue("at").jsonPrimitive.content),
                            it.getValue("weight").jsonPrimitive.double, it.getValue("reps").jsonPrimitive.int, it.flag("singleArm"),
                        )
                    },
                    sets.filter { it.getValue("exercise").jsonPrimitive.content == ex }.map {
                        RecordRules.SetEntry(
                            it.getValue("uuid").jsonPrimitive.content, ms(it.getValue("at").jsonPrimitive.content), it.getValue("order").jsonPrimitive.int,
                            it.getValue("weight").jsonPrimitive.double, it.getValue("reps").jsonPrimitive.int, it.flag("isWarmup"), it.flag("noRecord"), it.flag("singleArm"),
                        )
                    },
                )
            }
            val expected = o.getValue("pr").jsonArray.map { it.jsonPrimitive.content }
            assertEquals("vettore «$name»", expected.sorted(), pr.sorted())
        }
    }

    private fun JsonObject.flag(key: String) = this[key]?.jsonPrimitive?.booleanOrNull == true
}

/** RecordServiceTests.swift, sullo stesso servizio con il database in memoria. */
class RecordServiceTest {
    private val storage = InMemoryStorage()
    private val service = RecordService(storage)
    private var clock = 1_800_000_000_000L
    private fun tick(): Long { clock += 600_000; return clock }

    private fun workout(id: String = "W${storage.workouts.size + 1}") = id.also { storage.addWorkout(it) }
    private fun exercise(id: String, limbs: Limbs = Limbs.both) = id.also { storage.addExercise(it, limbs) }

    private fun add(w: String, e: String?, weight: Double, reps: Int, warmup: Boolean = false, singleArm: Boolean = false) =
        runBlocking { service.addSet(w, e, weight, reps, warmup, singleArm, now = tick(), uuid = "S${storage.sets.size + 1}") }

    @Test fun firstWorkingSetCreatesRecord() {
        val e = exercise("E1")
        val w = workout()
        assertTrue(add(w, e, 120.0, 5))
        assertEquals(1, storage.recordsOf(e).size)
        val record = storage.recordsOf(e)[0]
        assertEquals("il massimale è il carico vero, non la stima", 120.0, record.weight, 0.01)
        assertTrue(record.isEstimated)
        assertEquals("il record automatico porta l'uuid della sua serie", storage.setsOf(w)[0].row.uuid, record.uuid)
    }

    @Test fun weakerSetDoesNotCreateRecord() {
        val e = exercise("E1")
        storage.addManual("M1", e, 90.0, 1)
        assertFalse(add(workout(), e, 60.0, 8))
        assertEquals(1, storage.recordsOf(e).size)
    }

    @Test fun warmupSetIsIgnored() {
        val e = exercise("E1")
        assertFalse(add(workout(), e, 200.0, 5, warmup = true))
        assertTrue(storage.recordsOf(e).isEmpty())
    }

    @Test fun setsAreAppendedInOrder() {
        val e = exercise("E1")
        val w = workout()
        add(w, e, 50.0, 5); add(w, e, 40.0, 5); add(w, null, 10.0, 5)
        assertEquals(listOf(0, 1, 2), storage.setsOf(w).map { it.row.order })
        // senza macchinario non c'è nulla da ricalcolare
        assertFalse(storage.setsOf(w).last().row.isPersonalRecord)
    }

    @Test fun deletingASetRemovesItsAutomaticRecord() {
        val e = exercise("E1")
        val w = workout()
        add(w, e, 50.0, 6)
        assertEquals(1, storage.recordsOf(e).size)
        runBlocking { service.deleteSet(storage.setsOf(w)[0].row.uuid) }
        assertTrue(storage.recordsOf(e).isEmpty())
    }

    @Test fun manualRecordIsNeverRemoved() {
        val e = exercise("E1")
        storage.addManual("M1", e, 40.0, 3)
        val w = workout()
        add(w, e, 40.0, 3)
        runBlocking { service.deleteSet(storage.setsOf(w)[0].row.uuid) }
        assertEquals("un record inserito a mano non va toccato", 1, storage.recordsOf(e).size)
        assertFalse(storage.recordsOf(e)[0].isEstimated)
    }

    /** Prima l'eliminazione di un allenamento lasciava i suoi record: ora spariscono con lui. */
    @Test fun deletingAWorkoutRemovesItsAutomaticRecords() {
        val e = exercise("E1")
        val w = workout()
        add(w, e, 100.0, 8)
        assertEquals(1, storage.recordsOf(e).size)
        runBlocking { service.deleteWorkout(w) }
        assertTrue(storage.recordsOf(e).isEmpty())
        assertTrue(storage.sets.isEmpty())
    }

    /** Abbassando la prima serie, quelle dopo diventano record: si ricontrollano tutte. */
    @Test fun editingASetRechecksTheLaterOnes() {
        val e = exercise("E1")
        val w = workout()
        for (weight in listOf(100.0, 90.0, 95.0)) add(w, e, weight, 5)
        runBlocking { service.recomputeAll() }
        assertEquals(listOf(true, false, false), storage.setsOf(w).map { it.row.isPersonalRecord })

        val first = storage.setsOf(w)[0]
        first.row = first.row.copy(weight = 80.0)
        runBlocking { service.recompute(e) }

        assertEquals(listOf(true, true, true), storage.setsOf(w).map { it.row.isPersonalRecord })
        assertEquals(3, storage.recordsOf(e).size)
    }

    /** Un record da allenamento eliminato dallo storico: la serie resta, ma non conta più. */
    @Test fun deletingAnAutomaticRecordExcludesItsSet() {
        val e = exercise("E1")
        val w = workout()
        add(w, e, 70.0, 8)
        val automatic = storage.recordsOf(e)[0]
        runBlocking { service.deleteRecord(automatic.uuid) }
        assertTrue(storage.recordsOf(e).isEmpty())
        assertEquals("la serie resta nell'allenamento", 1, storage.setsOf(w).size)
        assertTrue(storage.setsOf(w)[0].row.excludedFromRecords)
        assertFalse(storage.setsOf(w)[0].row.isPersonalRecord)
    }

    @Test fun deletingAManualRecordRemovesIt() {
        val e = exercise("E1")
        storage.addManual("M1", e, 40.0, 3)
        runBlocking { service.deleteRecord("M1") }
        assertTrue(storage.recordsOf(e).isEmpty())
    }

    @Test fun editedSetUpdatesItsRecord() {
        val e = exercise("E1")
        val w = workout()
        add(w, e, 80.0, 5)
        storage.setsOf(w)[0].let { it.row = it.row.copy(weight = 85.0, reps = 6) }
        runBlocking { service.recompute(e) }
        val r = storage.recordsOf(e).single()
        assertEquals(85.0, r.weight, 0.0)
        assertEquals(6, r.reps)
        assertEquals("Da allenamento", r.note)
    }

    /** Le due linee (a due e a un braccio) non si mescolano, solo sui macchinari «a scelta». */
    @Test fun singleArmLineIsSeparateOnlyForEitherMachines() {
        val either = exercise("EITHER", Limbs.either)
        val w = workout()
        assertTrue(add(w, either, 100.0, 5))
        assertTrue("60 a un braccio non si confronta con 100 a due", add(w, either, 60.0, 5, singleArm = true))
        assertEquals(setOf(false, true), storage.recordsOf(either).map { it.singleArm }.toSet())

        val both = exercise("BOTH", Limbs.both)
        assertTrue(add(w, both, 100.0, 5))
        assertFalse("su un macchinario «insieme» il segno non apre una linea", add(w, both, 60.0, 5, singleArm = true))
        assertEquals(listOf(false), storage.recordsOf(both).map { it.singleArm })
    }

    @Test fun personalRecordsOfAWorkout() {
        val e = exercise("E1")
        val w = workout()
        add(w, e, 100.0, 5); add(w, e, 90.0, 5); add(w, e, 105.0, 3)
        assertEquals(listOf("S1", "S3"), runBlocking { service.personalRecords(w) })
    }

    /** `editingSetReferenceRecordCalculation`: il riferimento senza la serie che si modifica. */
    @Test fun bestWithoutTheEditedSet() {
        val e = exercise("E1")
        val w = workout()
        add(w, e, 80.0, 5); add(w, e, 75.0, 5)
        val without2 = storage.recordsOf(e).filter { it.uuid != "S2" }
        val ref = MaxRanking.best(without2, { it.weight }, { it.reps }, { it.date })!!
        assertEquals(80.0, ref.weight, 0.0)
        assertTrue(MaxRanking.beats(85.0, 5, MaxRanking.Performance(ref.weight, ref.reps)))
        assertNull(MaxRanking.best(storage.recordsOf(e).filter { it.uuid != "S1" && it.uuid != "S2" }, { it.weight }, { it.reps }, { it.date }))
    }

    /** `previousWorkingSets`: le serie dell'allenamento precedente senza riscaldamento. */
    @Test fun previousWorkingSetsIgnoreWarmup() {
        val e = exercise("E1")
        val past = workout("PAST")
        add(past, e, 60.0, 10, warmup = true); add(past, e, 100.0, 8); add(past, e, 105.0, 6)
        val today = workout("TODAY")
        add(today, e, 110.0, 5)
        val previous = storage.setsOf("PAST").map { it.row }.filter { !it.isWarmup }
        assertEquals(listOf(100.0, 105.0), previous.map { it.weight })
        assertEquals(listOf(8, 6), previous.map { it.reps })
    }
}
