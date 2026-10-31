package com.gabrieleforghieri.peak.sync

import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.domain.TestCatalog
import com.gabrieleforghieri.peak.domain.uuid
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryPending : PendingLogin {
    var verifier: String? = null
    override fun save(verifier: String) { this.verifier = verifier }
    override fun take() = verifier.also { verifier = null }
}

/** Le scelte del primo collegamento (ConnectFlow.swift), con server e telefono finti. */
class ConnectFlowTest {
    init { TestCatalog.load() }

    private val creds = PeakCredentials("token-sub-1", "sub-1", "Prova")

    private class Setup(val server: FakeServer, val phone: Phone, val flow: ConnectFlow, val pending: MemoryPending)

    private fun TestScope.setup(local: Snapshot = Snapshot(), server: FakeServer = FakeServer()): Setup {
        val phone = Phone(server, this, snapshot = local)
        val pending = MemoryPending()
        val flow = ConnectFlow(phone.engine, PeakHttp("http://127.0.0.1:1"), pending, { "Pixel di prova" }, { phone.db.isFallback })
        // il server vero, ricevuto un backup, ne ricava i documenti: qui si copia ciò che c'è sul telefono
        server.onImport = { _, _, _ -> SyncDocs.encodeAll(phone.db.data).forEach { (k, d) -> server.put(WireDoc(k.substringBefore(':'), k.substringAfter(':'), d, 10, false)) } }
        return Setup(server, phone, flow, pending)
    }

    private suspend fun serverWithData(scope: TestScope, data: Snapshot = sampleSnapshot()): FakeServer {
        val server = FakeServer()
        val other = Phone(server, scope, snapshot = data)
        other.link(); other.engine.syncNow()
        server.imports.clear()
        return server
    }

    @Test fun emptyServerAndPhoneWithDataOffersToUploadThenUploadsAndLinks() = runTest {
        val s = setup(sampleSnapshot())
        s.flow.connectWith(creds)
        assertEquals(ConnectFlow.Stage.EmptyServer(hasLocalData = true), s.flow.stage.value)
        assertEquals(SyncEngine.State.Onboarding, s.phone.engine.status.value.state)
        assertNull(s.phone.credentials.saved) // il token si salva solo a scelta fatta
        // prima di tutto la copia di sicurezza leggibile
        assertEquals(setOf("Peak-prima-del-collegamento.json"), s.phone.files.written.keys)

        s.flow.uploadLocalData()
        assertEquals(ConnectFlow.Stage.Done, s.flow.stage.value)
        assertEquals(listOf(Triple(false, true, true)), s.server.imports.map { Triple(it.second, it.third, true) }.map { Triple(it.first, it.second, it.third) })
        assertEquals(SyncEngine.State.Connected, s.phone.engine.status.value.state)
        assertNotNull(s.phone.credentials.saved)
        // i dati del telefono sono ancora tutti lì, dopo essere passati dal server
        assertEquals(sampleSnapshot().exercises.map { it.uuid }.toSet(), s.phone.db.data.exercises.map { it.uuid }.toSet())
        assertEquals(sampleSnapshot().workouts.size, s.phone.db.data.workouts.size)
        assertEquals(0, s.phone.engine.status.value.pendingCount)
    }

    @Test fun emptyServerAndEmptyPhoneJustLinks() = runTest {
        val s = setup()
        s.flow.connectWith(creds)
        assertEquals(ConnectFlow.Stage.EmptyServer(hasLocalData = false), s.flow.stage.value)
        assertTrue(s.phone.files.written.isEmpty()) // niente da salvare
        s.flow.startEmpty()
        assertEquals(ConnectFlow.Stage.Done, s.flow.stage.value)
        assertEquals(SyncEngine.State.Connected, s.phone.engine.status.value.state)
    }

    @Test fun serverWithDataAndPhoneWithOnlyTheCatalogTakesTheServerDataAtOnce() = runTest {
        val scope = this
        val server = serverWithData(scope)
        val s = setup(Snapshot(exercises = listOf(ExerciseEntity(uuid(900), "Solo catalogo"))), server)
        s.flow.connectWith(creds)
        assertEquals(ConnectFlow.Stage.Done, s.flow.stage.value)
        assertEquals(setOf(uuid(1), uuid(2)), s.phone.db.data.exercises.map { it.uuid }.toSet())
        assertEquals(1, s.phone.files.written.size) // il catalogo di prima non si perde: copia di sicurezza lo stesso
    }

    @Test fun bothSidesWithDataAsksAndTheChoicesWork() = runTest {
        val scope = this
        val own = Snapshot(
            exercises = listOf(ExerciseEntity(uuid(900), "Mio")),
            bodyWeights = listOf(BodyWeightEntity(uuid(901), 1_700_000_000_000, 80.0, false)),
        )
        // usa i dati del server: i miei spariscono
        run {
            val s = setup(own, serverWithData(scope))
            s.flow.connectWith(creds)
            assertEquals(ConnectFlow.Stage.Conflict, s.flow.stage.value)
            s.flow.useServerData()
            assertEquals(ConnectFlow.Stage.Done, s.flow.stage.value)
            assertTrue(s.phone.db.data.exercises.none { it.uuid == uuid(900) })
            assertEquals(1, s.phone.files.written.size) // ma la copia di sicurezza li ha
        }
        // unisci: caricati sul server (unisci, non prima scelta), poi tutto insieme
        run {
            val s = setup(own, serverWithData(scope))
            s.flow.connectWith(creds)
            s.flow.merge()
            assertEquals(ConnectFlow.Stage.Done, s.flow.stage.value)
            assertEquals(listOf(false to false), s.server.imports.map { it.second to it.third })
        }
    }

    @Test fun cancellingTheChoiceLeavesThePhoneAsItWas() = runTest {
        val own = Snapshot(exercises = listOf(ExerciseEntity(uuid(900), "Mio")), bodyWeights = listOf(BodyWeightEntity(uuid(901), 1_700_000_000_000, 80.0, false)))
        val s = setup(own, serverWithData(this))
        s.flow.connectWith(creds)
        assertEquals(ConnectFlow.Stage.Conflict, s.flow.stage.value)
        s.flow.cancel()
        assertEquals(ConnectFlow.Stage.Idle, s.flow.stage.value)
        assertEquals(SyncEngine.State.Disconnected, s.phone.engine.status.value.state)
        assertNull(s.phone.credentials.saved)
        assertEquals(own, s.phone.db.data)
    }

    @Test fun anErrorOnTheNetworkKeepsTheDataAndShowsTheMessage() = runTest {
        val scope = this
        val own = Snapshot(exercises = listOf(ExerciseEntity(uuid(900), "Mio")), bodyWeights = listOf(BodyWeightEntity(uuid(901), 1_700_000_000_000, 80.0, false)))
        val s = setup(own, serverWithData(scope))
        s.flow.connectWith(creds)
        s.server.unauthorized = true
        s.flow.useServerData()
        assertEquals(ConnectFlow.Stage.Failed(PeakApiException.Unauthorized().message!!), s.flow.stage.value)
        assertEquals(own, s.phone.db.data)
        assertNull(s.phone.credentials.saved)
        s.flow.dismiss()
        assertEquals(ConnectFlow.Stage.Idle, s.flow.stage.value)
    }

    @Test fun anotherDeviceTakingTheFirstChoiceRestartsFromTheComparison() = runTest {
        val s = setup(sampleSnapshot())
        s.flow.connectWith(creds)
        assertEquals(ConnectFlow.Stage.EmptyServer(true), s.flow.stage.value)
        // nel frattempo un altro dispositivo carica i suoi dati: «prima scelta» non vale più
        s.server.accountNotEmptyOnFirstImport = true
        s.server.put(WireDoc("exercise", uuid(77), SyncDocs.encodeAll(Snapshot(exercises = listOf(ExerciseEntity(uuid(77), "Altro"))))["exercise:${uuid(77)}"], 5, false))
        s.flow.uploadLocalData()
        assertEquals(ConnectFlow.Stage.Conflict, s.flow.stage.value)
        assertNull(s.phone.credentials.saved)
    }

    @Test fun noBackupCopyMeansNoLinking() = runTest {
        val s = setup(sampleSnapshot())
        s.phone.files.failing = true
        try {
            s.flow.connectWith(creds)
            error("doveva lanciare")
        } catch (e: IllegalStateException) {
            assertEquals("disco pieno", e.message)
        }
        assertEquals(SyncEngine.State.Disconnected, s.phone.engine.status.value.state)
        assertEquals(sampleSnapshot(), s.phone.db.data)
    }

    // ---- il login ---------------------------------------------------------------------------------------

    @Test fun beginGivesTheLoginAddressAndKeepsTheVerifier() = runTest {
        val s = setup()
        val url = s.flow.begin()!!
        assertTrue(url.startsWith("http://127.0.0.1:1/auth/login?app="))
        assertTrue(url.endsWith("&nome=Pixel%20di%20prova"))
        assertEquals(ConnectFlow.Stage.Working("Accesso a yG…"), s.flow.stage.value)
        assertEquals(43, s.pending.verifier!!.length)
        s.flow.cancel()
        assertNull(s.pending.verifier)
    }

    @Test fun beginRefusesWithAWorkoutRunningOrADamagedDatabase() = runTest {
        val running = setup(Snapshot(workouts = listOf(WorkoutEntity(uuid(5), endedAt = null))))
        assertNull(running.flow.begin())
        assertEquals(ConnectFlow.Stage.Failed("C'è un allenamento in corso. Terminalo e poi collega l'account."), running.flow.stage.value)
        assertNull(running.pending.verifier)

        val damaged = setup(sampleSnapshot())
        damaged.phone.db.isFallback = true
        assertNull(damaged.flow.begin())
        assertTrue((damaged.flow.stage.value as ConnectFlow.Stage.Failed).message.startsWith("L'archivio dell'app non si è aperto"))
        assertTrue(damaged.phone.files.written.isEmpty())
    }

    @Test fun aCallbackThatIsNotOursIsIgnoredAndOneWithoutAVerifierFails() = runTest {
        val s = setup()
        s.flow.handleCallback("https://esempio.it/?code=abc")
        s.flow.handleCallback(null)
        assertEquals(ConnectFlow.Stage.Idle, s.flow.stage.value)
        s.flow.handleCallback("peak://login?code=abc") // nessun verifier: il login non è partito da qui
        assertEquals(ConnectFlow.Stage.Failed("Il collegamento è scaduto: riprova."), s.flow.stage.value)
        assertFalse(s.phone.engine.isConnected)
    }

    @Test fun anExpiredLinkOfTheSameAccountResumesWithoutQuestions() = runTest {
        val scope = this
        val server = serverWithData(scope)
        val s = setup(sampleSnapshot(), server)
        s.phone.link()
        s.phone.engine.syncNow()
        server.unauthorized = true
        s.phone.engine.syncNow()
        assertEquals(SyncEngine.State.Expired, s.phone.engine.status.value.state)
        server.unauthorized = false
        s.flow.connectWith(PeakCredentials("nuovo-token", "sub-1", "Prova"))
        assertEquals(ConnectFlow.Stage.Done, s.flow.stage.value)
        assertEquals("nuovo-token", s.phone.credentials.saved!!.token)
        assertEquals(SyncEngine.State.Connected, s.phone.engine.status.value.state)
    }
}
