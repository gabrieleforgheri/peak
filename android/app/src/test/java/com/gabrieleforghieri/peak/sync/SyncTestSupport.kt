package com.gabrieleforghieri.peak.sync

import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.RecordService
import com.gabrieleforghieri.peak.domain.RecordService.Diff
import com.gabrieleforghieri.peak.domain.RecordService.ExerciseState
import com.gabrieleforghieri.peak.domain.RecordService.RecordHead
import com.gabrieleforghieri.peak.domain.RecordService.RecordRow
import com.gabrieleforghieri.peak.domain.RecordService.RecordStorage
import com.gabrieleforghieri.peak.domain.RecordService.SetRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Il database in memoria per le prove della sincronizzazione e dei backup: una [Snapshot] che cambia con [Snapshot.applying] (stesse
 * cascate di Room) e, dietro [RecordStorage], il vero [RecordService] che ricalcola i massimali.
 */
class FakeDb(var data: Snapshot = Snapshot(), override var isFallback: Boolean = false) : SyncStorage, RecordStorage {
    val recordService = RecordService(this)
    var commits = 0

    override suspend fun snapshot() = data
    override suspend fun commit(changes: Changes) { commits++; data = data.applying(changes) }
    override suspend fun recomputeRecords() = recordService.recomputeAll()

    override suspend fun <T> transaction(block: suspend () -> T): T = block()

    override suspend fun exerciseState(exercise: String): ExerciseState? {
        val e = data.exercises.firstOrNull { it.uuid == exercise } ?: return null
        return ExerciseState(
            exercise, Limbs.limbsOf(e.limbs, e.isUnilateral, e.catalogID, e.name, e.muscleGroup),
            data.records.filter { it.exerciseUuid == exercise }.map { RecordRow(it.uuid, it.date, it.weight, it.reps, it.isEstimated, it.note, it.singleArm) },
            data.sets.filter { it.exerciseUuid == exercise && it.workoutUuid != null }.map {
                SetRow(it.uuid, it.createdAt, it.order, it.weight, it.reps, it.isWarmup, it.excludedFromRecords, it.isPersonalRecord, it.singleArm)
            },
        )
    }

    override suspend fun allExercises() = data.exercises.map { it.uuid }
    override suspend fun nextOrder(workout: String) = (data.sets.filter { it.workoutUuid == workout }.maxOfOrNull { it.order } ?: -1) + 1

    override suspend fun insertSet(set: SetRow, workout: String, exercise: String?) {
        data = data.applying(
            Changes(sets = listOf(WorkoutSetEntity(set.uuid, workout, exercise, set.weight, set.reps, null, set.isWarmup, set.order, set.createdAt, false, set.excludedFromRecords, set.singleArm))),
        )
    }

    override suspend fun apply(exercise: String, diff: Diff) {
        data = data.copy(
            records = data.records.filter { it.uuid !in diff.deleteRecords && diff.upsertRecords.none { r -> r.uuid == it.uuid } } +
                diff.upsertRecords.map { MaxRecordEntity(it.uuid, exercise, it.date, it.weight, it.reps, it.isEstimated, it.note, it.singleArm) },
            sets = data.sets.map { s -> diff.setFlags[s.uuid]?.let { s.copy(isPersonalRecord = it) } ?: s },
        )
    }

    override suspend fun exerciseOfSet(set: String) = data.sets.firstOrNull { it.uuid == set }?.exerciseUuid
    override suspend fun deleteSet(set: String) { data = data.copy(sets = data.sets.filter { it.uuid != set }) }
    override suspend fun exercisesOfWorkout(workout: String) = data.sets.filter { it.workoutUuid == workout }.mapNotNull { it.exerciseUuid }.toSet()
    override suspend fun deleteWorkout(workout: String) { data = data.applying(Changes(gone = Gone(workouts = setOf(workout)))) }
    override suspend fun record(uuid: String) = data.records.firstOrNull { it.uuid == uuid }?.let { RecordHead(it.exerciseUuid, it.isEstimated) }
    override suspend fun deleteRecord(uuid: String) { data = data.copy(records = data.records.filter { it.uuid != uuid }) }
    override suspend fun excludeSet(uuid: String) { data = data.copy(sets = data.sets.map { if (it.uuid == uuid) it.copy(excludedFromRecords = true, isPersonalRecord = false) else it }) }
    override suspend fun personalRecordSets(workout: String) = data.sets.filter { it.workoutUuid == workout && it.isPersonalRecord }.sortedBy { it.order }.map { it.uuid }
}

/** Il server di Peak, in memoria: stessa regola di `store.push` (vince la `m` più alta, a parità chi arriva dopo; il perso torna in `rifiutati`). */
class FakeServer : PeakApi {
    class Row(var doc: WireDoc, var rev: Int)

    val rows = linkedMapOf<String, Row>()
    val photos = mutableMapOf<String, ByteArray>()
    var rev = 0
    var unauthorized = false
    var discard: (WireDoc) -> Boolean = { false }
    val imports = mutableListOf<Triple<ByteArray, Boolean, Boolean>>()
    var pushes = 0
    var accountNotEmptyOnFirstImport = false

    /** Eseguiti nel mezzo di un giro (prima della lettura) e quando arriva un backup: per simulare ciò che succede nel frattempo. */
    var beforePull: (suspend () -> Unit)? = null
    var onImport: ((ByteArray, Boolean, Boolean) -> Unit)? = null

    private fun check() { if (unauthorized) throw PeakApiException.Unauthorized() }

    override suspend fun me(): PeakMe { check(); return PeakMe("sub-1", "Prova") }

    override suspend fun pull(since: Int): PullResult {
        check()
        beforePull?.invoke()
        return PullResult(rev, rows.values.filter { it.rev > since }.sortedBy { it.rev }.map { it.doc })
    }

    override suspend fun push(docs: List<WireDoc>): PushResult {
        check()
        pushes++
        val rejected = mutableListOf<WireDoc>()
        val discarded = mutableListOf<String>()
        for (d in docs) {
            if (discard(d)) { discarded += d.key; continue }
            val row = rows[d.key]
            if (row != null && d.m < row.doc.m) { rejected += row.doc; continue }
            rows[d.key] = Row(d, ++rev)
        }
        return PushResult(rejected, discarded)
    }

    override suspend fun putPhoto(sha: String, bytes: ByteArray) { check(); photos[sha] = bytes }
    override suspend fun photo(sha: String): ByteArray { check(); return photos[sha] ?: throw PeakApiException.Server(404) }

    override suspend fun importBackup(data: ByteArray, replace: Boolean, first: Boolean) {
        check()
        if (first && accountNotEmptyOnFirstImport) throw PeakApiException.AccountNotEmpty()
        imports += Triple(data, replace, first)
        onImport?.invoke(data, replace, first)
    }

    /** Un altro dispositivo scrive un documento (con la sua `m`). */
    fun put(doc: WireDoc) { rows[doc.key] = Row(doc, ++rev) }

    fun live(kind: String) = rows.values.map { it.doc }.filter { it.kind == kind && !it.deleted }
}

class MemoryPhotos : PhotoStore {
    val files = mutableMapOf<String, ByteArray>()
    override fun has(sha: String) = sha in files
    override fun read(sha: String) = files[sha]
    override fun write(bytes: ByteArray): String = sha256Hex(bytes).also { files[it] = bytes }
}

class MemoryCredentials : CredentialStore {
    var saved: PeakCredentials? = null
    override fun load() = saved
    override fun save(credentials: PeakCredentials) { saved = credentials }
    override fun clear() { saved = null }
}

class MemoryStateFile : SyncStateFile {
    var text: String? = null
    override fun load() = text
    override fun save(text: String) { this.text = text }
    override fun clear() { text = null }
}

class MemoryFiles : BackupFiles {
    val written = mutableMapOf<String, ByteArray>()
    var failing = false
    override fun write(name: String, bytes: ByteArray) { if (failing) error("disco pieno"); written[name] = bytes }
}

/** Un telefono: database, foto, collegamento, file di stato e il motore di sincronizzazione sopra un server condiviso. */
class Phone(val server: FakeServer, val scope: TestScope, var now: Long = 1_000_000L, snapshot: Snapshot = Snapshot()) {
    val db = FakeDb(snapshot)
    val photos = MemoryPhotos()
    val credentials = MemoryCredentials()
    val stateFile = MemoryStateFile()
    val files = MemoryFiles()
    val backup = BackupService(db, photos, files) { now }
    val dispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler)
    val engine = SyncEngine(db, photos, stateFile, credentials, { server }, backup, dispatcher) { now }

    /** Collega come farebbe [ConnectFlow] dopo la scelta: server già con i dati (o vuoto), senza passare dal login. */
    suspend fun link(sub: String = "sub-1") {
        engine.connect(PeakCredentials("token-$sub", sub, "Prova"))
        engine.completeConnection()
    }

    /** Un telefono nuovo che si collega a un account che ha già dati: come `ConnectFlow.useServerData`. */
    suspend fun join(sub: String = "sub-1") {
        engine.connect(PeakCredentials("token-$sub", sub, "Prova"))
        engine.replaceLocalWithServer()
        engine.completeConnection()
    }
}
