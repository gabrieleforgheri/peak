package com.gabrieleforghieri.peak.sync

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.TestCatalog
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Gli uuid decimali del file di prova (`U(n)` in gen-backup-fixture.ts). */
private fun uuid(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

private val NOW = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()

/** `backup-server.json`: un backup nel formato dell'iPhone (BackupDTO.swift), scritto dal vero server/backup.ts (android/scripts/gen-backup-fixture.ts). */
private fun fixture(): ByteArray = checkNotNull(BackupTest::class.java.classLoader.getResource("backup-server.json")) { "manca backup-server.json" }.readBytes()

private fun tree(bytes: ByteArray) = Json.parseToJsonElement(bytes.decodeToString())

private class Fresh(snapshot: Snapshot = Snapshot()) {
    val db = FakeDb(snapshot)
    val photos = MemoryPhotos()
    val files = MemoryFiles()
    val backup = BackupService(db, photos, files) { NOW }
}

class BackupTest {
    init { TestCatalog.load() }

    private fun <T : Throwable> assertFails(type: Class<T>, block: () -> Unit): T {
        try { block() } catch (e: Throwable) { if (type.isInstance(e)) return type.cast(e)!!; throw e }
        fail("doveva lanciare ${type.simpleName}")
        error("unreachable")
    }

    // ---- andata e ritorno -------------------------------------------------------------------------------

    @Test fun aFileInTheIphoneFormatComesBackWithoutDifferences() = runTest {
        val f = Fresh()
        val original = fixture()
        f.backup.restore(original, RestoreMode.Replace)
        val exported = f.backup.data()
        assertEquals(tree(original), tree(exported))
        // e anche come testo: chiavi ordinate, due spazi, nessun «.0», nessun millisecondo, nessun null
        assertEquals(original.decodeToString().trim(), exported.decodeToString().trim())
        assertFalse(exported.decodeToString().contains("null"))
        assertFalse(Regex("""\d\.\d{3}Z""").containsMatchIn(exported.decodeToString()))
    }

    @Test fun whatTheServerComputedIsRecomputedHere() = runTest {
        val f = Fresh()
        f.backup.restore(fixture(), RestoreMode.Replace)
        val file = BackupCodec.parse(f.backup.data())
        // 5 massimali «da allenamento» nel file del server: i nostri, ricalcolati dalle serie, sono gli stessi
        val auto = BackupCodec.parse(fixture()).exercises.flatMap { it.records }.filter { it.isEstimated }
        assertTrue(auto.isNotEmpty())
        assertEquals(auto.map { it.uuid }.toSet(), file.exercises.flatMap { it.records }.filter { it.isEstimated }.map { it.uuid }.toSet())
        val flagged = file.workouts.flatMap { it.sets }.filter { it.isPersonalRecord }.map { it.uuid }.toSet()
        assertEquals(BackupCodec.parse(fixture()).workouts.flatMap { it.sets }.filter { it.isPersonalRecord }.map { it.uuid }.toSet(), flagged)
        assertTrue(flagged.isNotEmpty())
    }

    @Test fun everythingTheFileHoldsIsInTheDatabase() = runTest {
        val f = Fresh()
        f.backup.restore(fixture(), RestoreMode.Replace)
        val s = f.db.data
        assertEquals(3, s.exercises.size)
        val chest = s.exercises.first { it.uuid == uuid(1) }
        assertEquals("Chest Press", chest.name)
        assertEquals("pecDeck", chest.glyph)
        assertEquals("either", chest.limbs)
        assertEquals("chest-press-machine", chest.catalogID)
        assertEquals(0.5, chest.photoOffset, 0.0)
        assertEquals(90, chest.restSeconds)
        assertEquals(20.0, chest.barWeight, 0.0)
        assertTrue(chest.isUnilateral && chest.plateLoaded)
        // la foto: i byte su disco, lo sha256 nel database
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte(), 1, 2, 3, 4)
        assertEquals(sha256Hex(jpeg), chest.photo)
        assertEquals(jpeg.toList(), f.photos.read(chest.photo)!!.toList())
        val lat = s.exercises.first { it.uuid == uuid(2) }
        assertTrue(lat.isCustom && lat.isArchived && lat.perSide)
        assertEquals("deltoids", lat.muscles)
        assertEquals("back", lat.view)
        assertEquals("relative", lat.weightDisplay)
        assertNull(lat.limbs)
        assertTrue(s.exercises.first { it.uuid == uuid(3) }.isHidden)
        // massimali a mano: due; il singleArm resta
        assertEquals(2, s.records.count { !it.isEstimated })
        assertTrue(s.records.first { it.uuid == uuid(102) }.singleArm)
        assertEquals(2, s.workouts.size)
        assertEquals(6, s.sets.size)
        val set203 = s.sets.first { it.uuid == uuid(203) }
        assertTrue(set203.singleArm)
        assertTrue(s.sets.first { it.uuid == uuid(205) }.excludedFromRecords)
        assertEquals(8.5, s.sets.first { it.uuid == uuid(205) }.rpe!!, 0.0)
        assertNull(s.sets.first { it.uuid == uuid(204) }.exerciseUuid)
        val workout = s.workouts.first { it.uuid == uuid(10) }
        assertEquals("Push", workout.name)
        assertEquals("petto", workout.focus)
        assertEquals(uuid(400), workout.template)
        assertEquals(2, com.gabrieleforghieri.peak.domain.TemplateItems.decode(workout.plan).size)
        assertEquals(2, s.bodyWeights.size)
        val template = s.templates.single()
        assertEquals(listOf(uuid(1), uuid(2)), com.gabrieleforghieri.peak.domain.TemplateItems.decode(template.items).map { it.exercise })
        assertEquals(uuid(500), template.program)
        assertEquals(2, template.weekday)
        assertEquals("flame", template.icon)
        assertTrue(s.programs.single().useDays)
    }

    @Test fun anAndroidExportIsStableWhenReimported() = runTest {
        val a = Fresh(); a.backup.restore(fixture(), RestoreMode.Replace)
        val first = a.backup.data()
        val b = Fresh(); b.backup.restore(first, RestoreMode.Replace)
        assertEquals(tree(first), tree(b.backup.data()))
        assertEquals(a.db.data.copy(settings = emptyMap()), b.db.data.copy(settings = emptyMap()))
    }

    // ---- modalità ---------------------------------------------------------------------------------------

    @Test fun mergingTwiceDoesNotDuplicate() = runTest {
        val f = Fresh()
        f.backup.restore(fixture(), RestoreMode.Replace)
        val before = f.db.data
        f.backup.restore(fixture(), RestoreMode.Merge)
        f.backup.restore(fixture(), RestoreMode.Merge)
        assertEquals(before, f.db.data)
    }

    @Test fun mergeKeepsAnExistingMachineAndAddsOnlyTheMissingMaxes() = runTest {
        val mine = ExerciseEntity(uuid(1), "Il mio Chest Press", "petto", incrementStep = 1.0)
        val f = Fresh(Snapshot(exercises = listOf(mine)))
        f.backup.restore(fixture(), RestoreMode.Merge)
        val s = f.db.data
        assertEquals(mine, s.exercises.first { it.uuid == uuid(1) }) // non sovrascritto
        assertEquals(setOf(uuid(101), uuid(102)), s.records.filter { !it.isEstimated }.map { it.uuid }.toSet())
        assertTrue(s.records.filter { !it.isEstimated }.all { it.exerciseUuid == uuid(1) })
        assertEquals(3, s.exercises.size)
        assertEquals(2, s.workouts.size)
        // le serie del file si agganciano al macchinario già presente
        assertTrue(s.sets.first { it.uuid == uuid(201) }.exerciseUuid == uuid(1))
    }

    @Test fun mergeSkipsWorkoutsAndWeightsAlreadyThere() = runTest {
        val f = Fresh()
        f.backup.restore(fixture(), RestoreMode.Replace)
        val edited = f.db.data.copy(workouts = f.db.data.workouts.map { if (it.uuid == uuid(10)) it.copy(note = "modificato qui") else it })
        f.db.data = edited
        f.backup.restore(fixture(), RestoreMode.Merge)
        assertEquals("modificato qui", f.db.data.workouts.first { it.uuid == uuid(10) }.note)
    }

    @Test fun replaceWipesTheDataButKeepsTheSettings() = runTest {
        val stray = Snapshot(
            exercises = listOf(ExerciseEntity(uuid(900), "Macchinario di prova")),
            workouts = listOf(WorkoutEntity(uuid(901), endedAt = 5)), settings = mapOf("unit" to "\"lb\""),
        )
        val f = Fresh(stray)
        f.backup.restore(fixture(), RestoreMode.Replace)
        assertTrue(f.db.data.exercises.none { it.name == "Macchinario di prova" })
        assertTrue(f.db.data.workouts.none { it.uuid == uuid(901) })
        assertEquals(mapOf("unit" to "\"lb\""), f.db.data.settings)
    }

    @Test fun replaceIsRefusedWhileAWorkoutIsRunningAndNothingChanges() = runTest {
        val running = Snapshot(workouts = listOf(WorkoutEntity(uuid(901), endedAt = null)))
        val f = Fresh(running)
        assertFails(BackupException.WorkoutInProgress::class.java) { kotlinx.coroutines.runBlocking { f.backup.restore(fixture(), RestoreMode.Replace) } }
        assertEquals(running, f.db.data)
        assertEquals(0, f.db.commits)
        // unire invece si può
        f.backup.restore(fixture(), RestoreMode.Merge)
        assertEquals(3, f.db.data.workouts.size)
    }

    // ---- file vecchi o strani ---------------------------------------------------------------------------

    /** Come il file di un'app vecchia: niente chiavi opzionali, né pesate, schede o programmi (la stessa forma di `seedSampleData` in BackupRoundTripTests.swift). */
    private val oldFile = """
        {"version":1,"exportedAt":"2026-01-10T10:00:00Z","exercises":[
          {"uuid":"11111111-1111-4111-8111-111111111111","name":"Leg Press 45°","muscleGroup":"gambe","notes":"","incrementStep":10,"sortIndex":0,"isArchived":false,"createdAt":"2025-12-01T10:00:00Z",
           "records":[{"uuid":"AAAAAAAA-0000-4000-8000-000000000001","date":"2025-12-02T10:00:00Z","weight":180,"reps":1,"isEstimated":false,"note":""},
                      {"uuid":"AAAAAAAA-0000-4000-8000-000000000002","date":"2025-12-20T10:00:00.123Z","weight":200,"reps":3,"isEstimated":false,"note":"x"}]},
          {"uuid":"22222222-2222-4222-8222-222222222222","name":"Pressa di casa mia","muscleGroup":"boh","notes":"","incrementStep":5,"sortIndex":1,"isArchived":false,"createdAt":"2025-12-01T10:00:00Z","records":[]}],
         "workouts":[{"uuid":"33333333-3333-4333-8333-333333333333","date":"2026-01-09T10:00:00Z","note":"Gambe pesanti","sets":[
           {"uuid":"44444444-4444-4444-8444-444444444441","exerciseUUID":"11111111-1111-4111-8111-111111111111","weight":230,"reps":5,"isWarmup":false,"order":0,"createdAt":"2026-01-09T10:01:00Z","isPersonalRecord":false},
           {"uuid":"44444444-4444-4444-8444-444444444442","exerciseUUID":"22222222-2222-4222-8222-222222222222","weight":60,"reps":10,"isWarmup":true,"order":1,"createdAt":"2026-01-09T10:02:00Z","isPersonalRecord":false}]}]}
    """.trimIndent().encodeToByteArray()

    @Test fun anOldFileWithoutTheOptionalKeysIsRead() = runTest {
        val f = Fresh()
        val file = f.backup.restore(oldFile, RestoreMode.Replace)
        assertEquals(1, file.version)
        val s = f.db.data
        // un backup vecchio non ha l'id del catalogo: lo si ricava dal nome
        val legPress = s.exercises.first { it.name == "Leg Press 45°" }
        assertEquals(Catalog.match("Leg Press 45°")?.id, legPress.catalogID)
        assertTrue(legPress.catalogID.isNotEmpty())
        // e uno personalizzato tiene l'icona che il nome gli dava; un gruppo sconosciuto diventa petto
        val custom = s.exercises.first { it.name == "Pressa di casa mia" }
        assertEquals("", custom.catalogID)
        assertEquals("petto", custom.muscleGroup)
        assertTrue(custom.glyph.isNotEmpty())
        // date con i millisecondi: a secondi interi
        assertEquals(Instant.parse("2025-12-20T10:00:00Z").toEpochMilli(), s.records.first { it.uuid == "AAAAAAAA-0000-4000-8000-000000000002" }.date)
        assertEquals("Gambe pesanti", s.workouts.single().note)
        assertEquals("", s.workouts.single().focus)
        assertTrue(s.bodyWeights.isEmpty() && s.templates.isEmpty() && s.programs.isEmpty())
        // 230x5 batte i massimali a mano (180x1, 200x3) → il massimale da allenamento si ricalcola
        assertTrue(s.sets.any { it.isPersonalRecord })
    }

    @Test fun lowercaseUuidsAreAccepted() = runTest {
        val f = Fresh()
        f.backup.restore(oldFile.decodeToString().replace("AAAAAAAA", "aaaaaaaa").encodeToByteArray(), RestoreMode.Replace)
        assertTrue(f.db.data.records.any { it.uuid == "AAAAAAAA-0000-4000-8000-000000000001" })
    }

    @Test fun notABackupIsRefused() = runTest {
        val f = Fresh(sampleSnapshot())
        val before = f.db.data
        for (bad in listOf("non sono un backup", "", "[]", "{}", """{"version":1}""", """{"version":1,"exercises":[],"workouts":{}}""")) {
            assertFails(BackupException.InvalidFile::class.java) { kotlinx.coroutines.runBlocking { f.backup.restore(bad.encodeToByteArray(), RestoreMode.Merge) } }
        }
        // un campo obbligatorio o un uuid/una data rotti: tutto il file è respinto, nulla è scritto (nemmeno le foto)
        val broken = listOf(
            oldFile.decodeToString().replace("\"name\":\"Leg Press 45°\",", ""),
            oldFile.decodeToString().replace("11111111-1111-4111-8111-111111111111", "non-un-uuid"),
            oldFile.decodeToString().replace("2025-12-01T10:00:00Z", "ieri"),
            oldFile.decodeToString().replace("\"weight\":230", "\"weight\":\"pesante\""),
        )
        for (text in broken) assertFails(BackupException.InvalidFile::class.java) { kotlinx.coroutines.runBlocking { f.backup.restore(text.encodeToByteArray(), RestoreMode.Replace) } }
        assertEquals(before, f.db.data)
        assertEquals(0, f.db.commits)
        assertTrue(f.photos.files.isEmpty())
    }

    @Test fun aNewerVersionIsRefusedBeforeAnythingElse() {
        val e = assertFails(BackupException.UnsupportedVersion::class.java) { BackupCodec.parse("""{"version":2,"exercises":"boh"}""".encodeToByteArray()) }
        assertEquals(2, e.version)
        assertEquals("Backup in versione 2: serve una versione più recente dell'app.", e.message)
    }

    @Test fun unknownKeysAreIgnored() {
        val text = oldFile.decodeToString().replaceFirst("\"version\":1,", "\"version\":1,\"nuovaChiave\":{\"a\":1},")
        assertEquals(2, BackupCodec.parse(text.encodeToByteArray()).exercises.size)
    }

    // ---- scrittura --------------------------------------------------------------------------------------

    @Test fun anExerciseWithNothingSpecialDoesNotCarryEmptyKeys() = runTest {
        val f = Fresh(Snapshot(exercises = listOf(ExerciseEntity(uuid(1), "Panca", "petto", createdAt = Instant.parse("2026-01-01T10:00:00Z").toEpochMilli() + 999))))
        val ex = (tree(f.backup.data()) as JsonObject)["exercises"] as JsonArray
        assertEquals(
            setOf("uuid", "name", "muscleGroup", "notes", "incrementStep", "sortIndex", "isArchived", "createdAt", "records"),
            (ex.single() as JsonObject).keys,
        )
        assertEquals(JsonPrimitive("2026-01-01T10:00:00Z"), (ex.single() as JsonObject)["createdAt"])
        assertEquals(JsonPrimitive(2.5), (ex.single() as JsonObject)["incrementStep"])
    }

    @Test fun anEmptyDatabaseExportsAnEmptyButValidFile() = runTest {
        val f = Fresh()
        val parsed = BackupCodec.parse(f.backup.data())
        assertEquals(BackupCodec.CURRENT_VERSION, parsed.version)
        assertEquals("2026-10-14T12:00:00Z", parsed.exportedAt)
        assertTrue(parsed.exercises.isEmpty() && parsed.workouts.isEmpty() && parsed.programs == null)
        assertEquals(emptyList<BodyWeightDTO>(), parsed.bodyWeights)
    }

    @Test fun theCopyBeforeConnectingIsWrittenOrConnectingStops() = runTest {
        val f = Fresh(sampleSnapshot())
        f.backup.writePreConnectionBackup()
        assertEquals(setOf("Peak-prima-del-collegamento.json"), f.files.written.keys)
        assertEquals(tree(f.backup.data()), tree(f.files.written["Peak-prima-del-collegamento.json"]!!))
        f.files.failing = true
        try {
            f.backup.writePreConnectionBackup()
            fail("doveva lanciare")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }
    }

    @Test fun photosThatAreMissingOnDiskAreLeftOutOfTheFile() = runTest {
        val f = Fresh(Snapshot(exercises = listOf(ExerciseEntity(uuid(1), "Panca", photo = "d".repeat(64)))))
        val ex = ((tree(f.backup.data()) as JsonObject)["exercises"] as JsonArray).single() as JsonObject
        assertFalse("photo" in ex)
    }
}
