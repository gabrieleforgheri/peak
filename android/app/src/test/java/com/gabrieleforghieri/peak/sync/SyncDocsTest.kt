package com.gabrieleforghieri.peak.sync

import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.domain.SettingKeys
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.uuid
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

/** Un database con un po' di tutto: ogni tipo di documento, un massimale automatico e un flag PR (che non devono viaggiare). */
fun sampleSnapshot(): Snapshot {
    val ex1 = uuid(1); val ex2 = uuid(2); val wk = uuid(10); val tpl = uuid(40); val prog = uuid(50)
    return Snapshot(
        exercises = listOf(
            ExerciseEntity(
                ex1, "Chest Press", "petto", "sedile 4", 5.0, 0, false, "pecDeck", "a".repeat(64), 0.5, true, 90, ms("2026-09-01T08:00:00Z"),
                "chest-press-machine", false, "", "", false, 20.0, "absolute", true, false, "either",
            ),
            ExerciseEntity(
                ex2, "Lat Machine", "schiena", "", 2.5, 1, true, "", "", 0.0, false, 0, ms("2026-09-01T08:00:00Z"),
                "", true, "deltoids,biceps", "back", true, 0.0, "relative", false, true, null,
            ),
        ),
        records = listOf(
            MaxRecordEntity(uuid(101), ex1, ms("2026-09-02T09:00:00Z"), 50.0, 5, false, "primo", false),
            MaxRecordEntity(uuid(102), ex1, ms("2026-09-03T09:00:00Z"), 30.0, 8, false, "", true),
            MaxRecordEntity(uuid(201), ex1, ms("2026-09-10T10:00:00Z"), 60.0, 5, true, "Da allenamento", false),
        ),
        workouts = listOf(
            WorkoutEntity(
                wk, ms("2026-09-10T09:30:00Z"), ms("2026-09-10T10:30:00Z"), "ok", "Push", "petto", tpl,
                TemplateItems.encode(listOf(TemplateItem(ex1, 4, 8, 10, 60.0), TemplateItem(ex2, 3, 10, 12, null))),
            ),
        ),
        sets = listOf(
            WorkoutSetEntity(uuid(201), wk, ex1, 60.0, 5, null, false, 0, ms("2026-09-10T10:00:00Z"), true, false, false),
            WorkoutSetEntity(uuid(202), wk, ex1, 20.0, 10, null, true, 1, ms("2026-09-10T10:05:00Z"), false, false, false),
            WorkoutSetEntity(uuid(203), wk, null, 35.0, 8, 8.5, false, 2, ms("2026-09-10T10:07:00Z"), false, true, true),
        ),
        bodyWeights = listOf(BodyWeightEntity(uuid(300), ms("2026-09-11T07:00:00Z"), 78.4, false)),
        templates = listOf(
            WorkoutTemplateEntity(tpl, "Push 1", TemplateItems.encode(listOf(TemplateItem(ex1, 4, 8, 10, 60.0))), 0, ms("2026-09-20T08:00:00Z"), false, "flame", prog, 2),
        ),
        programs = listOf(WorkoutProgramEntity(prog, "Push Pull Legs", 0, ms("2026-09-20T08:00:00Z"), true)),
        settings = mapOf(SettingKeys.UNIT to "\"lb\"", SettingKeys.REST_SECONDS to "90", SettingKeys.SOUNDS_ENABLED to "false"),
    )
}

fun Encoded.toWire(m: Long = 1_000) = map { (key, data) ->
    val i = key.indexOf(':')
    WireDoc(key.substring(0, i), key.substring(i + 1), data, m, false)
}

fun applyTo(base: Snapshot, docs: List<WireDoc>, hasPhoto: (String) -> Boolean = { true }): Triple<SyncDocs.Applied, Changes, Snapshot> {
    val (applied, changes) = SyncDocs.apply(docs, base, hasPhoto)
    return Triple(applied, changes, base.applying(changes))
}

class SyncDocsTest {
    @Test fun settingsThatTravelAreTheSixteenSharedOnes() {
        val docs = SyncDocs.encodeAll(sampleSnapshot())
        val settings = docs.keys.filter { it.startsWith("setting:") }.map { it.removePrefix("setting:") }
        assertEquals(SettingKeys.synced.sorted(), settings.sorted())
        // il valore scelto, e il valore di partenza per ciò che non è stato scelto
        assertEquals(JsonPrimitive("lb"), docs["setting:unit"]!!["value"])
        assertEquals(JsonPrimitive(90), docs["setting:restSeconds"]!!["value"])
        assertEquals(JsonPrimitive(50), docs["setting:warmupRestPercent"]!!["value"])
        assertEquals(JsonArray(listOf(25, 20, 15, 10, 5, 2.5, 1.25).map { JsonPrimitive(it) }), docs["setting:availablePlates"]!!["value"])
        assertFalse(docs.containsKey("setting:device.soundsEnabled"))
    }

    @Test fun computedRecordsAndPersonalRecordFlagsNeverTravel() {
        val docs = SyncDocs.encodeAll(sampleSnapshot())
        assertEquals(setOf("record:${uuid(101)}", "record:${uuid(102)}"), docs.keys.filter { it.startsWith("record:") }.toSet())
        for ((key, doc) in docs) if (key.startsWith("set:")) assertFalse("isPersonalRecord" in doc)
        assertEquals(JsonPrimitive(true), docs["set:${uuid(203)}"]!!["noRecord"])
        assertEquals(JsonNull, docs["set:${uuid(203)}"]!!["exercise"])
        assertEquals(JsonPrimitive(8.5), docs["set:${uuid(203)}"]!!["rpe"])
    }

    @Test fun limbsTravelOnlyWhenChosen() {
        val docs = SyncDocs.encodeAll(sampleSnapshot())
        assertEquals(JsonPrimitive("either"), docs["exercise:${uuid(1)}"]!!["limbs"])
        assertFalse("limbs" in docs["exercise:${uuid(2)}"]!!)
    }

    @Test fun datesAreWholeSecondsInUtc() {
        val s = Snapshot(bodyWeights = listOf(BodyWeightEntity(uuid(300), ms("2026-09-11T07:00:00Z") + 750, 80.0, false)))
        assertEquals(JsonPrimitive("2026-09-11T07:00:00Z"), SyncDocs.encodeAll(s)["bodyweight:${uuid(300)}"]!!["date"])
    }

    @Test fun anExerciseWithNoPhotoSendsAnEmptyShaAndCommaListsBecomeArrays() {
        val docs = SyncDocs.encodeAll(sampleSnapshot())
        assertEquals(JsonPrimitive(""), docs["exercise:${uuid(2)}"]!!["photo"])
        assertEquals(JsonArray(listOf(JsonPrimitive("deltoids"), JsonPrimitive("biceps"))), docs["exercise:${uuid(2)}"]!!["muscles"])
        assertEquals(JsonArray(emptyList()), docs["exercise:${uuid(1)}"]!!["muscles"])
    }

    // ---- hash -------------------------------------------------------------------------------------------

    @Test fun hashDoesNotDependOnKeyOrderButOnContent() {
        val a = JsonObject(mapOf("x" to JsonPrimitive(1), "y" to JsonObject(mapOf("b" to JsonPrimitive("t"), "a" to JsonNull))))
        val b = JsonObject(mapOf("y" to JsonObject(mapOf("a" to JsonNull, "b" to JsonPrimitive("t"))), "x" to JsonPrimitive(1)))
        assertEquals(SyncDocs.hash(a), SyncDocs.hash(b))
        assertEquals("""{"x":1,"y":{"a":null,"b":"t"}}""", SyncDocs.bytes(a).decodeToString())
        assertNotEquals(SyncDocs.hash(a), SyncDocs.hash(JsonObject(mapOf("x" to JsonPrimitive(2), "y" to a["y"]!!))))
        assertEquals(64, SyncDocs.hash(a).length)
    }

    @Test fun sameRowsAlwaysGiveTheSameHashes() {
        val first = SyncDocs.encodeAll(sampleSnapshot()).mapValues { SyncDocs.hash(it.value) }
        val second = SyncDocs.encodeAll(sampleSnapshot()).mapValues { SyncDocs.hash(it.value) }
        assertEquals(first, second)
        val edited = sampleSnapshot().let { it.copy(exercises = it.exercises.map { e -> if (e.uuid == uuid(1)) e.copy(name = "Chest Press 2") else e }) }
        val third = SyncDocs.encodeAll(edited).mapValues { SyncDocs.hash(it.value) }
        assertEquals(setOf("exercise:${uuid(1)}"), first.keys.filter { first[it] != third[it] }.toSet())
    }

    // ---- applicazione -----------------------------------------------------------------------------------

    @Test fun whatIsEncodedIsAppliedBackIdentically() {
        val original = SyncDocs.encodeAll(sampleSnapshot())
        // l'ordine dei documenti non conta: figli prima dei genitori, e deve funzionare lo stesso
        val docs = original.toWire().reversed()
        val (applied, _, after) = applyTo(Snapshot(), docs)
        assertTrue(applied.skipped.isEmpty())
        assertEquals(original.keys, applied.upserted)
        assertEquals(original, SyncDocs.encodeAll(after))
        // il massimale automatico e il flag PR non sono arrivati: li ricalcolerà RecordService
        assertTrue(after.records.none { it.isEstimated })
        assertTrue(after.sets.none { it.isPersonalRecord })
    }

    @Test fun anIncomingDocumentChangesOnlyItsRow() {
        val base = sampleSnapshot()
        val doc = SyncDocs.encodeAll(base)["exercise:${uuid(1)}"]!!
        val edited = JsonObject(doc + ("name" to JsonPrimitive("Chest Press nuovo")) + ("sortIndex" to JsonPrimitive(7)))
        val (_, changes, after) = applyTo(base, listOf(WireDoc("exercise", uuid(1), edited, 5_000, false)))
        assertEquals(listOf(uuid(1)), changes.exercises.map { it.uuid })
        assertTrue(changes.records.isEmpty() && changes.sets.isEmpty())
        assertEquals("Chest Press nuovo", after.exercises.first { it.uuid == uuid(1) }.name)
        assertEquals(7, after.exercises.first { it.uuid == uuid(1) }.sortIndex)
        // il massimale automatico dello stesso macchinario non si è perso
        assertEquals(3, after.records.size)
    }

    @Test fun documentsWithoutAParentAreSkippedNotDropped() {
        val docs = SyncDocs.encodeAll(sampleSnapshot())
        val orphanRecord = JsonObject(docs["record:${uuid(101)}"]!! + ("exercise" to JsonPrimitive(uuid(99))))
        val orphanSet = JsonObject(docs["set:${uuid(201)}"]!! + ("workout" to JsonPrimitive(uuid(98))))
        val (applied, changes, after) = applyTo(
            Snapshot(),
            listOf(
                WireDoc("record", uuid(101), orphanRecord, 1, false), WireDoc("set", uuid(201), orphanSet, 1, false),
                WireDoc("mistero", uuid(5), JsonObject(emptyMap()), 1, false), WireDoc("exercise", uuid(6), JsonObject(mapOf("name" to JsonPrimitive("senza data"))), 1, false),
            ),
        )
        assertEquals(setOf("record:${uuid(101)}", "set:${uuid(201)}", "mistero:${uuid(5)}", "exercise:${uuid(6)}"), applied.skipped)
        assertTrue(applied.upserted.isEmpty())
        assertEquals(Changes(), changes)
        assertEquals(Snapshot(), after)
    }

    @Test fun anInvalidSettingIsSkippedAndALimitIsEnforced() {
        fun setting(name: String, v: kotlinx.serialization.json.JsonElement) = WireDoc("setting", name, JsonObject(mapOf("value" to v)), 1, false)
        val (applied, changes, _) = applyTo(
            Snapshot(),
            listOf(
                setting("unit", JsonPrimitive("stone")), setting("restSeconds", JsonPrimitive(5)), setting("restSeconds", JsonPrimitive(300)),
                setting("weeklyGoal", JsonPrimitive("tre")), setting("tutorialDone", JsonPrimitive(true)),
                setting("availablePlates", JsonArray(listOf(JsonPrimitive(20), JsonPrimitive(0)))), setting("language", JsonPrimitive("en")),
                setting("barWeight", JsonPrimitive(20)), setting("device.soundsEnabled", JsonPrimitive(false)),
                setting("activeProgram", JsonPrimitive(uuid(50).lowercase())),
            ),
        )
        assertEquals(mapOf("restSeconds" to "300", "tutorialDone" to "true", "language" to "\"en\"", "activeProgram" to "\"${uuid(50)}\""), changes.settings)
        // le chiavi che non viaggiano (e quelle vecchie) non entrano mai nel database
        assertTrue("setting:barWeight" in applied.skipped && "setting:device.soundsEnabled" in applied.skipped)
        assertTrue("setting:unit" in applied.skipped && "setting:weeklyGoal" in applied.skipped && "setting:availablePlates" in applied.skipped)
    }

    @Test fun cleanSettingKnowsEveryKey() {
        assertEquals("\"kg\"", SyncDocs.cleanSetting("unit", JsonPrimitive("kg")))
        assertEquals("\"lime\"", SyncDocs.cleanSetting("accent", JsonPrimitive("lime")))
        assertNull(SyncDocs.cleanSetting("accent", JsonPrimitive("rosa")))
        assertEquals("[25,2.5]", SyncDocs.cleanSetting("availablePlates", JsonArray(listOf(JsonPrimitive(25), JsonPrimitive(2.5)))))
        assertNull(SyncDocs.cleanSetting("availablePlates", JsonArray(List(21) { JsonPrimitive(5) })))
        assertEquals("0", SyncDocs.cleanSetting("warmupRestPercent", JsonPrimitive(0)))
        assertNull(SyncDocs.cleanSetting("warmupRestPercent", JsonPrimitive(101)))
        assertNull(SyncDocs.cleanSetting("tourSeen", JsonPrimitive(0)))
        assertNull(SyncDocs.cleanSetting("repRangeMin", JsonPrimitive(2.5)))
        assertNull(SyncDocs.cleanSetting("restTimerEnabled", JsonPrimitive("true")))
        assertNull(SyncDocs.cleanSetting("activeProgram", JsonPrimitive("non-un-uuid")))
        assertEquals("\"\"", SyncDocs.cleanSetting("activeProgram", JsonPrimitive("")))
        assertNull(SyncDocs.cleanSetting("unit", JsonNull))
    }

    @Test fun deletionsRemoveRowsAndCascade() {
        val base = sampleSnapshot()
        // via il macchinario: i suoi massimali spariscono, le serie restano senza macchinario
        val (applied, changes, after) = applyTo(base, listOf(WireDoc("exercise", uuid(1), null, 9, true), WireDoc("exercise", uuid(77), null, 9, true)))
        assertEquals(setOf("exercise:${uuid(1)}", "exercise:${uuid(77)}"), applied.deleted)
        assertEquals(setOf(uuid(1)), changes.gone.exercises)
        assertEquals(listOf(uuid(2)), after.exercises.map { it.uuid })
        assertTrue(after.records.isEmpty())
        assertTrue(after.sets.all { it.exerciseUuid == null })
        // via l'allenamento: spariscono le serie
        val (_, _, noWorkout) = applyTo(base, listOf(WireDoc("workout", uuid(10), null, 9, true)))
        assertTrue(noWorkout.workouts.isEmpty() && noWorkout.sets.isEmpty())
        assertEquals(2, noWorkout.exercises.size)
    }

    @Test fun aPhotoIsKeptOnlyIfItsBytesArePresent() {
        val sha = "b".repeat(64)
        val doc = JsonObject(SyncDocs.encodeAll(sampleSnapshot())["exercise:${uuid(2)}"]!! + ("photo" to JsonPrimitive(sha)))
        val wire = listOf(WireDoc("exercise", uuid(2), doc, 1, false))
        assertEquals(sha, applyTo(Snapshot(), wire) { true }.third.exercises.single().photo)
        assertEquals("", applyTo(Snapshot(), wire) { false }.third.exercises.single().photo)
        // una foto che c'era resta, finché i byte nuovi non arrivano
        val had = Snapshot(exercises = listOf(ExerciseEntity(uuid(2), photo = "c".repeat(64))))
        assertEquals("c".repeat(64), applyTo(had, wire) { false }.third.exercises.single().photo)
        // il documento senza foto la toglie
        val none = JsonObject(doc + ("photo" to JsonPrimitive("")))
        assertEquals("", applyTo(had, listOf(WireDoc("exercise", uuid(2), none, 1, false))) { false }.third.exercises.single().photo)
    }

    @Test fun optionalExerciseFieldsStayWhenAnOldServerDoesNotSendThem() {
        val base = sampleSnapshot()
        val doc = SyncDocs.encodeAll(base)["exercise:${uuid(2)}"]!!
        val old = JsonObject(doc - "muscles" - "view" - "custom" - "barWeight" - "weightDisplay" - "plateLoaded" - "perSide" - "limbs" + ("limbs" to JsonPrimitive("sidewards")))
        val after = applyTo(base, listOf(WireDoc("exercise", uuid(2), old, 1, false))).third.exercises.first { it.uuid == uuid(2) }
        val before = base.exercises.first { it.uuid == uuid(2) }
        assertEquals(before.copy(), after)
    }

    @Test fun templatesAndPlansKeepTheirRows() {
        val docs = SyncDocs.encodeAll(sampleSnapshot())
        val plan = docs["workout:${uuid(10)}"]!!["plan"] as JsonArray
        assertEquals(2, plan.size)
        assertEquals(JsonNull, (plan[1] as JsonObject)["weight"])
        val after = applyTo(Snapshot(), docs.toWire()).third
        assertEquals(sampleSnapshot().templates, after.templates)
        assertEquals(sampleSnapshot().workouts, after.workouts)
        assertEquals(Json.parseToJsonElement(after.workouts.single().plan), plan)
    }
}
