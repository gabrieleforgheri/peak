package com.gabrieleforghieri.peak.sync

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.domain.SettingKeys
import com.gabrieleforghieri.peak.domain.uuid
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val PHOTO = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte(), 1, 2, 3, 4)

/** [sampleSnapshot] con la foto del primo macchinario vera (lo sha256 dei suoi byte). */
private fun withPhoto(): Pair<Snapshot, String> {
    val sha = sha256Hex(PHOTO)
    val s = sampleSnapshot()
    return s.copy(exercises = s.exercises.map { if (it.uuid == uuid(1)) it.copy(photo = sha) else it }) to sha
}

private fun Phone.exercise(id: Int) = db.data.exercises.first { it.uuid == uuid(id) }
private fun Phone.rename(id: Int, name: String) {
    db.data = db.data.copy(exercises = db.data.exercises.map { if (it.uuid == uuid(id)) it.copy(name = name) else it })
}

private fun serverName(server: FakeServer, id: Int) =
    (server.rows["exercise:${uuid(id)}"]!!.doc.data!!["name"] as kotlinx.serialization.json.JsonPrimitive).content

class SyncEngineTest {

    /** Il telefono A con i suoi dati, già sincronizzato; B si è collegato dopo e ha preso tutto dal server. Con `synced = false` non è successo nulla. */
    private suspend fun TestScope.pair(data: Snapshot = withPhoto().first, synced: Boolean = true): Triple<FakeServer, Phone, Phone> {
        val server = FakeServer()
        val a = Phone(server, this, now = 1_000, snapshot = data)
        val b = Phone(server, this, now = 1_000)
        a.photos.write(PHOTO)
        a.link()
        if (synced) { a.engine.syncNow(); b.join() }
        return Triple(server, a, b)
    }

    @Test fun firstSyncPushesEverythingAndAnotherPhoneGetsIt() = runTest {
        val (server, a, b) = pair(synced = false)
        a.engine.syncNow()
        val sent = SyncDocs.encodeAll(a.db.data)
        assertEquals(sent.keys, server.rows.keys)
        assertFalse(server.rows.keys.any { it.startsWith("record:${uuid(201)}") }) // il massimale automatico non viaggia
        assertTrue(server.photos.containsKey(sha256Hex(PHOTO)))
        assertEquals(0, a.engine.status.value.pendingCount)
        assertNotNull(a.engine.status.value.lastSync)

        b.join()
        assertEquals(sent, SyncDocs.encodeAll(b.db.data))
        assertEquals(PHOTO.toList(), b.photos.read(sha256Hex(PHOTO))!!.toList())
        // il massimale «da allenamento» si è ricalcolato dalle serie, e il flag PR con lui
        assertTrue(b.db.data.records.any { it.isEstimated })
        assertTrue(b.db.data.sets.any { it.isPersonalRecord })
        assertEquals(0, b.engine.status.value.pendingCount)
    }

    @Test fun anIdleRoundSendsNothing() = runTest {
        val (server, a, _) = pair()
        a.engine.syncNow()
        val pushes = server.pushes
        a.engine.syncNow()
        a.engine.syncNow()
        assertEquals(pushes, server.pushes)
        assertEquals(null, a.engine.status.value.lastError)
    }

    @Test fun anEditTravelsAsOneDocumentWithAHigherM() = runTest {
        val (server, a, b) = pair()
        a.engine.syncNow(); b.engine.syncNow()
        val before = server.rows["exercise:${uuid(1)}"]!!.doc.m
        a.now = 5_000
        a.rename(1, "Chest Press 2")
        a.engine.syncNow()
        val after = server.rows["exercise:${uuid(1)}"]!!.doc
        assertEquals(5_000, after.m)
        assertTrue(after.m > before)
        assertEquals("Chest Press 2", serverName(server, 1))
        assertEquals(1, server.rows.values.count { it.doc.m == 5_000L })
        b.engine.syncNow()
        assertEquals("Chest Press 2", b.exercise(1).name)
    }

    // ---- conflitti --------------------------------------------------------------------------------------

    @Test fun twoEditsOfTheSameDocumentTheLaterMWinsWhoeverSyncsFirst() = runTest {
        for (laterFirst in listOf(true, false)) {
            val (server, a, b) = pair()
            a.engine.syncNow(); b.engine.syncNow()
            // tutti e due modificano senza rete; A si riconnette alle 5000, B alle 4000
            a.rename(1, "da A"); b.rename(1, "da B")
            a.now = 5_000; b.now = 4_000
            if (laterFirst) { a.engine.syncNow(); b.engine.syncNow() } else { b.engine.syncNow(); a.engine.syncNow() }
            // il perdente (B) riceve subito la versione del server (rifiutati); il vincitore la rilegge al giro dopo
            a.engine.syncNow(); b.engine.syncNow()
            assertEquals("da A", serverName(server, 1))
            assertEquals("da A", a.exercise(1).name)
            assertEquals("da A", b.exercise(1).name)
            assertEquals(0, a.engine.status.value.pendingCount + b.engine.status.value.pendingCount)
        }
    }

    @Test fun aRejectedDocumentIsTakenFromTheServerAtOnce() = runTest {
        val (server, a, b) = pair()
        a.engine.syncNow(); b.engine.syncNow()
        a.rename(1, "da A"); a.now = 5_000; a.engine.syncNow()
        b.rename(1, "da B"); b.now = 4_000
        b.engine.syncNow() // push rifiutato, versione del server applicata nello stesso giro
        assertEquals("da A", b.exercise(1).name)
        assertEquals("da A", serverName(server, 1))
    }

    @Test fun aLaterRemoteEditBeatsALocalDeletion() = runTest {
        val (server, a, b) = pair()
        a.engine.syncNow(); b.engine.syncNow()
        b.rename(2, "ancora qui"); b.now = 3_000; b.engine.syncNow()
        a.db.data = a.db.data.applying(Changes(gone = Gone(exercises = setOf(uuid(2)))))
        a.now = 2_500
        a.engine.syncNow()
        assertFalse(server.rows["exercise:${uuid(2)}"]!!.doc.deleted)
        assertEquals("ancora qui", a.exercise(2).name)
    }

    @Test fun anEditMadeDuringARoundIsNotOverwrittenByTheServerCopy() = runTest {
        val (server, a, b) = pair()
        a.engine.syncNow(); b.engine.syncNow()
        b.rename(1, "da B"); b.now = 3_000; b.engine.syncNow()
        // nel mezzo del giro di A (dopo l'istantanea, prima della lettura) l'utente modifica lo stesso macchinario
        server.beforePull = { a.rename(1, "scritto adesso"); server.beforePull = null }
        a.now = 2_000
        a.engine.syncNow()
        assertEquals("scritto adesso", a.exercise(1).name)
        assertTrue(a.engine.takeNeedsAnotherRound())
        // il giro dopo lo spinge, con una `m` più alta di quella del server
        a.now = 4_000
        a.engine.syncNow()
        assertEquals("scritto adesso", serverName(server, 1))
        assertEquals(4_000, server.rows["exercise:${uuid(1)}"]!!.doc.m)
    }

    @Test fun eachNewChangeGetsAnMStrictlyAboveThePreviousOne() = runTest {
        val (server, a, _) = pair()
        a.engine.syncNow()
        val first = server.rows["exercise:${uuid(1)}"]!!.doc.m
        // un orologio indietro non deve far perdere la modifica contro la versione di prima
        a.now = 10
        a.rename(1, "orologio indietro")
        a.engine.syncNow()
        assertEquals(first + 1, server.rows["exercise:${uuid(1)}"]!!.doc.m)
        assertEquals("orologio indietro", serverName(server, 1))
    }

    // ---- cancellazioni ----------------------------------------------------------------------------------

    @Test fun deletingAWorkoutDeletesItsSetsEverywhere() = runTest {
        val (server, a, b) = pair()
        a.engine.syncNow(); b.engine.syncNow()
        a.db.data = a.db.data.applying(Changes(gone = Gone(workouts = setOf(uuid(10)))))
        a.now = 3_000
        a.engine.syncNow()
        assertTrue(server.rows["workout:${uuid(10)}"]!!.doc.deleted)
        assertTrue(listOf(201, 202, 203).all { server.rows["set:${uuid(it)}"]!!.doc.deleted })
        b.now = 4_000
        b.engine.syncNow()
        assertTrue(b.db.data.workouts.isEmpty() && b.db.data.sets.isEmpty())
        // i massimali «da allenamento» spariscono con le loro serie
        assertTrue(b.db.data.records.none { it.isEstimated })
    }

    @Test fun aHugeWaveOfDeletionsIsHeldBackUntilConfirmed() = runTest {
        val many = Snapshot(exercises = (1..30).map { ExerciseEntity(uuid(1000 + it), "M$it") })
        val (server, a, _) = pair(many)
        a.engine.syncNow()
        a.db.data = Snapshot() // un guasto: il database è tornato vuoto
        a.now = 3_000
        a.engine.syncNow()
        assertTrue(a.engine.status.value.lastError!!.startsWith("Sincronizzazione sospesa"))
        assertTrue(server.rows.values.none { it.doc.deleted })
        a.engine.permitMassDeletion()
        a.engine.syncNow()
        assertEquals(30, server.rows.values.count { it.doc.deleted && it.doc.kind == "exercise" })
    }

    @Test fun fewDeletionsAreNotMassDeletion() = runTest {
        val many = Snapshot(exercises = (1..30).map { ExerciseEntity(uuid(1000 + it), "M$it") })
        val (server, a, _) = pair(many)
        a.engine.syncNow()
        a.db.data = a.db.data.copy(exercises = a.db.data.exercises.drop(10)) // 10 su 30
        a.now = 3_000
        a.engine.syncNow()
        assertEquals(10, server.rows.values.count { it.doc.deleted })
        assertNull(a.engine.status.value.lastError)
    }

    // ---- guardie ----------------------------------------------------------------------------------------

    @Test fun theFallbackDatabaseNeverSyncs() = runTest {
        val (server, a, _) = pair(synced = false)
        a.db.isFallback = true
        a.engine.syncNow()
        assertEquals(0, server.pushes)
        assertTrue(server.rows.isEmpty())
        assertNull(a.engine.status.value.lastSync)
    }

    @Test fun nothingHappensWithoutALink() = runTest {
        val server = FakeServer()
        val a = Phone(server, this, snapshot = sampleSnapshot())
        a.engine.syncNow()
        assertEquals(0, server.pushes)
        assertEquals(SyncEngine.State.Disconnected, a.engine.status.value.state)
    }

    @Test fun anOnboardingLinkDoesNotSyncAndIsNotSaved() = runTest {
        val server = FakeServer()
        val a = Phone(server, this, snapshot = sampleSnapshot())
        a.engine.connect(PeakCredentials("t", "sub-1", "Prova"))
        a.engine.syncNow()
        assertEquals(0, server.pushes)
        assertNull(a.credentials.saved)
        a.engine.cancelConnection()
        assertEquals(SyncEngine.State.Disconnected, a.engine.status.value.state)
        assertNull(a.engine.status.value.credentials)
    }

    @Test fun anUnauthorizedAnswerExpiresTheLinkAndKeepsTheData() = runTest {
        val (server, a, _) = pair()
        a.engine.syncNow()
        val data = a.db.data
        server.unauthorized = true
        a.engine.syncNow()
        assertEquals(SyncEngine.State.Expired, a.engine.status.value.state)
        assertEquals(data, a.db.data)
        assertTrue(a.engine.isConnected)
        // un nuovo login di un altro account non riprende; dello stesso sì
        assertFalse(a.engine.renew(PeakCredentials("t2", "altro", "X")))
        server.unauthorized = false
        assertTrue(a.engine.renew(PeakCredentials("t2", "sub-1", "Prova")))
        assertEquals("t2", a.credentials.saved!!.token)
        a.engine.syncNow()
        assertEquals(SyncEngine.State.Connected, a.engine.status.value.state)
        assertNotNull(a.engine.status.value.lastSync)
    }

    @Test fun documentsTheServerDiscardsStayDirtyAndAreNotSentAgain() = runTest {
        val (server, a, _) = pair(synced = false)
        server.discard = { it.kind == "bodyweight" }
        a.engine.syncNow()
        assertEquals(1, a.engine.status.value.pendingCount)
        assertEquals("Rifiutati dal server: 1", a.engine.status.value.lastError)
        val pushes = server.pushes
        a.engine.syncNow()
        assertEquals(pushes, server.pushes) // stessa `m`: non si rimanda
        // cambiato il peso, si riprova
        a.now = 9_000
        a.db.data = a.db.data.copy(bodyWeights = a.db.data.bodyWeights.map { it.copy(weight = 79.0) })
        server.discard = { false }
        a.engine.syncNow()
        assertEquals(0, a.engine.status.value.pendingCount)
        assertTrue(server.rows.containsKey("bodyweight:${uuid(300)}"))
    }

    @Test fun aPhotoIsUploadedBeforeTheDocumentThatUsesIt() = runTest {
        val order = mutableListOf<String>()
        val real = FakeServer()
        val spy = object : PeakApi by real {
            override suspend fun putPhoto(sha: String, bytes: ByteArray) { order += "foto"; real.putPhoto(sha, bytes) }
            override suspend fun push(docs: List<WireDoc>): PushResult { order += "documenti"; return real.push(docs) }
        }
        val a = Phone(real, this, snapshot = withPhoto().first)
        a.photos.write(PHOTO)
        val engine = SyncEngine(a.db, a.photos, a.stateFile, a.credentials, { spy }, a.backup, a.dispatcher) { a.now }
        engine.connect(PeakCredentials("t", "sub-1", "Prova")); engine.completeConnection()
        engine.syncNow()
        assertEquals(listOf("foto", "documenti"), order)
    }

    @Test fun aPhotoTheServerDoesNotHaveDoesNotBlockTheRound() = runTest {
        val (server, a, b) = pair(synced = false)
        a.engine.syncNow()
        server.photos.clear() // il caricamento era stato rifiutato (troppe foto): il documento la richiama, i byte non ci sono
        b.join()
        assertEquals("", b.exercise(1).photo)
        assertEquals(setOf(uuid(1), uuid(2)), b.db.data.exercises.map { it.uuid }.toSet())
        assertEquals(0, b.engine.status.value.pendingCount)
        assertNull(b.engine.status.value.lastError)
    }

    @Test fun aCorruptDownloadedPhotoIsNotKept() = runTest {
        val (server, a, b) = pair(synced = false)
        a.engine.syncNow()
        server.photos[sha256Hex(PHOTO)] = byteArrayOf(1, 2, 3) // il server dà byte che non tornano con lo sha
        b.join()
        assertFalse(b.photos.has(sha256Hex(PHOTO)))
        assertEquals("", b.exercise(1).photo)
    }

    // ---- stato su disco ---------------------------------------------------------------------------------

    @Test fun theStateSurvivesARestart() = runTest {
        val (server, a, _) = pair()
        a.engine.syncNow()
        val pushes = server.pushes
        val restarted = SyncEngine(a.db, a.photos, a.stateFile, a.credentials, { server }, a.backup, a.dispatcher) { a.now }
        restarted.configure()
        assertEquals(SyncEngine.State.Connected, restarted.status.value.state)
        assertEquals("token-sub-1", restarted.status.value.credentials!!.token)
        restarted.syncNow()
        assertEquals(pushes, server.pushes)
        assertEquals(0, restarted.status.value.pendingCount)
    }

    @Test fun theStateOfAnotherAccountDoesNotApply() = runTest {
        val (server, a, _) = pair()
        a.engine.syncNow()
        a.credentials.saved = PeakCredentials("t", "altro-account", "X")
        val other = SyncEngine(a.db, a.photos, a.stateFile, a.credentials, { server }, a.backup, a.dispatcher) { a.now }
        other.configure()
        other.syncNow()
        // senza lo stato dell'altro tutto risulta da spingere (e il server, che ha già le stesse `m` più basse, le accetta)
        assertTrue(server.pushes >= 2)
    }

    @Test fun disconnectingKeepsTheDataAndForgetsTheLink() = runTest {
        val (_, a, _) = pair()
        a.engine.syncNow()
        val data = a.db.data
        a.engine.disconnect()
        assertEquals(data, a.db.data)
        assertNull(a.credentials.saved)
        assertNull(a.stateFile.text)
        assertEquals(SyncEngine.Status(), a.engine.status.value)
        a.engine.syncNow()
        assertNull(a.engine.status.value.lastSync)
    }

    // ---- sostituzione dei dati --------------------------------------------------------------------------

    @Test fun replacingLocalDataWithTheServerOnesLeavesNothingToPush() = runTest {
        val (server, a, _) = pair()
        a.engine.syncNow()
        val stray = Snapshot(
            exercises = listOf(ExerciseEntity(uuid(900), "Macchinario di prova")), settings = mapOf(SettingKeys.UNIT to "\"lb\""),
        )
        val c = Phone(server, this, snapshot = stray)
        c.engine.connect(PeakCredentials("t", "sub-1", "Prova"))
        c.engine.replaceLocalWithServer()
        c.engine.completeConnection()
        assertEquals(setOf(uuid(1), uuid(2)), c.db.data.exercises.map { it.uuid }.toSet())
        assertEquals(SyncDocs.encodeAll(a.db.data).filterKeys { !it.startsWith("setting:") }, SyncDocs.encodeAll(c.db.data).filterKeys { !it.startsWith("setting:") })
        // le impostazioni non sono dati: restano quelle del telefono finché il server non ne ha di scelte (qui le ha: lb dal server di A)
        val pushes = server.pushes
        c.engine.syncNow()
        assertEquals(pushes, server.pushes)
        assertEquals(0, c.engine.status.value.pendingCount)
        assertNull(c.engine.status.value.lastError)
        assertNotNull(c.engine.status.value.lastSync)
    }

    @Test fun replacingFailsOnTheNetworkWithoutTouchingTheData() = runTest {
        val (server, _, _) = pair()
        val c = Phone(server, this, snapshot = sampleSnapshot())
        c.engine.connect(PeakCredentials("t", "sub-1", "Prova"))
        server.unauthorized = true
        try {
            c.engine.replaceLocalWithServer()
            error("doveva fallire")
        } catch (_: PeakApiException.Unauthorized) {
        }
        assertEquals(sampleSnapshot(), c.db.data)
    }
}
