package com.gabrieleforghieri.peak.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Dove si tiene lo stato della sincronizzazione (`peak-sync.json`). Un'interfaccia per le prove. */
interface SyncStateFile {
    fun load(): String?
    fun save(text: String)
    fun clear()
}

/**
 * Sincronizzazione con il server di Peak (peak.yrb4g.com), per chi ha collegato l'account yG. Traduzione di `SyncService.swift`.
 *
 * COME FUNZIONA. Niente `modifiedAt` nelle tabelle e niente «tabella delle cancellazioni»: ogni documento (macchinario, serie,
 * pesata…) si codifica in JSON, se ne calcola l'hash e lo si confronta con quello della volta prima ([SyncStore.entries]). Un hash
 * cambiato = modificato; un documento che non c'è più = cancellato (anche quando a sparire è una serie per via di un allenamento
 * eliminato). Si spinge ciò che è cambiato, poi si legge ciò che è cambiato altrove. Su un conflitto vince l'ultima modifica (`m`, in
 * millisecondi, sempre crescente).
 *
 * I massimali «da allenamento» non viaggiano: si ricalcolano dalle serie (RecordService).
 *
 * SICUREZZA DEI DATI. Prima di cancellare o sostituire qualcosa in locale si scrive la copia di sicurezza (`Peak-prima-del-collegamento.json`),
 * e la rete si usa PRIMA delle modifiche locali: se manca la connessione a metà, i dati del telefono non sono toccati. Se il database è
 * quello di ripiego (il vero non si è aperto) la sincronizzazione resta spenta, per non spingere al server un «tutto cancellato».
 *
 * THREAD. Tutti i metodi vanno chiamati dallo stesso thread (come il `@MainActor` di Swift): fra un `await` e l'altro nessun altro
 * tocca lo stato. La codifica e gli hash di tutto il database girano su [compute].
 */
class SyncEngine(
    private val storage: SyncStorage,
    private val photos: PhotoStore,
    private val stateFile: SyncStateFile,
    private val credentialStore: CredentialStore,
    private val api: (token: String) -> PeakApi,
    private val backup: BackupService,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class State {
        /** Nessun account collegato: l'app funziona in locale, come sempre. */
        Disconnected,

        /** Login fatto ma scelta non ancora completata (server vuoto o in conflitto): il token non è salvato e non si sincronizza. */
        Onboarding,
        Connected,

        /** Il server non riconosce più questo telefono: i dati restano qui, serve collegare di nuovo. */
        Expired,
    }

    data class Status(
        val state: State = State.Disconnected,
        val credentials: PeakCredentials? = null,
        val isSyncing: Boolean = false,
        val lastSync: Long? = null,
        val lastError: String? = null,
        val pendingCount: Int = 0,
    )

    @Serializable
    data class Entry(val h: String, val m: Long, val dirty: Boolean, val deleted: Boolean, val skipped: Boolean = false)

    @Serializable
    data class SyncStore(
        val sub: String = "",
        var cursor: Int = 0,
        val entries: MutableMap<String, Entry> = mutableMapOf(),
        val uploadedPhotos: MutableSet<String> = mutableSetOf(),
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private var store = SyncStore()
    private var allowMassDeletion = false

    /** Un salvataggio è arrivato durante un giro: ne serve un altro (l'istantanea potrebbe non averlo visto). */
    var needsAnotherRound = false
        private set

    /** Cresce quando lo stato cambia sotto un giro (sostituzione, collegamento): l'istantanea di prima non vale più. */
    private var generation = 0

    /** Documenti scartati dal server, con la `m` che ha rifiutato (solo in memoria, come sul web). */
    private val rejected = mutableMapOf<String, Long>()

    private val json = Json { ignoreUnknownKeys = true }

    private val state get() = _status.value.state
    private val credentials get() = _status.value.credentials
    private fun api(): PeakApi? = credentials?.let { api(it.token) }

    /** Per le chiamate che stanno fuori dalla sincronizzazione (amici). */
    val peakApi: PeakApi? get() = api()

    val isConnected get() = state == State.Connected || state == State.Expired

    /** Un salvataggio è arrivato mentre si sincronizzava: chi osserva il database lo segnala qui. */
    fun noteSaveDuringRound(): Boolean {
        if (!_status.value.isSyncing) return false
        needsAnotherRound = true
        return true
    }

    // ---- avvio -------------------------------------------------------------------------------------------

    fun configure() {
        val saved = credentialStore.load() ?: return
        var loaded = stateFile.load()?.let { try { json.decodeFromString<SyncStore>(it) } catch (_: Exception) { null } } ?: SyncStore()
        // Un altro account sullo stesso telefono: lo stato dell'altro non vale.
        if (loaded.sub != saved.sub) loaded = SyncStore(sub = saved.sub)
        store = loaded
        _status.update { it.copy(state = State.Connected, credentials = saved) }
        updatePending()
    }

    // ---- sincronizzare -----------------------------------------------------------------------------------

    /** Un giro: spinge ciò che è cambiato qui, poi legge ciò che è cambiato altrove. Non fa nulla se non si può (o è già in corso un giro). */
    suspend fun syncNow() {
        val api = api()
        if (state != State.Connected || storage.isFallback || _status.value.isSyncing || api == null) return
        _status.update { it.copy(isSyncing = true) }
        val epoch = generation
        // Il permesso vale per un solo confronto, ma solo se c'era già prima dell'istantanea.
        val permitted = allowMassDeletion
        try {
            val snapshot = takeSnapshot()
            // Nel frattempo è cambiato lo stato (sostituzione dei dati, scollegamento): l'istantanea è vecchia.
            if (state != State.Connected || epoch != generation) { needsAnotherRound = state == State.Connected; return }
            detect(snapshot)
            if (permitted) allowMassDeletion = false
            uploadPhotos(api, snapshot)
            push(api, snapshot)
            pull(api)
            _status.update {
                it.copy(
                    lastSync = clock(),
                    lastError = if (rejected.isEmpty()) null else "Rifiutati dal server: ${rejected.size}",
                )
            }
        } catch (_: PeakApiException.Unauthorized) {
            _status.update { it.copy(state = State.Expired) }
        } catch (e: MassDeletionException) {
            _status.update { it.copy(lastError = e.message) }
        } catch (e: PeakApiException) {
            _status.update { it.copy(lastError = e.message) }
        } finally {
            _status.update { it.copy(isSyncing = false) }
            updatePending()
            persist()
        }
    }

    /** Dopo la fine del giro: c'era un salvataggio non visto, ne serve un altro. Lo legge e azzera chi poi lo richiede. */
    fun takeNeedsAnotherRound(): Boolean = needsAnotherRound.also { needsAnotherRound = false }

    // ---- cosa è cambiato ---------------------------------------------------------------------------------

    /** I documenti locali a un certo istante: byte JSON (chiavi ordinate) e relativi hash. */
    private class Snap {
        val hashes = mutableMapOf<String, String>()
        val json = mutableMapOf<String, ByteArray>()

        /** Foto non ancora salite, coi byte letti insieme allo sha: documento e file non possono divergere. */
        val newPhotos = mutableMapOf<String, ByteArray>()
    }

    private suspend fun takeSnapshot(): Snap {
        val uploaded = store.uploadedPhotos.toSet()
        val db = storage.snapshot()
        return withContext(compute) {
            val snap = Snap()
            for ((key, doc) in SyncDocs.encodeAll(db)) {
                val bytes = SyncDocs.bytes(doc)
                snap.json[key] = bytes
                snap.hashes[key] = sha256Hex(bytes)
            }
            for (e in db.exercises) {
                if (e.photo.isNotEmpty() && e.photo !in uploaded) photos.read(e.photo)?.let { snap.newPhotos[e.photo] = it }
            }
            snap
        }
    }

    private fun nowMs() = clock()

    /** Confronta i documenti dell'istantanea con quelli dell'ultima volta e segna cosa va spinto. */
    private fun detect(snap: Snap) {
        val now = nowMs()
        for ((key, h) in snap.hashes) {
            val entry = store.entries[key]
            if (entry != null && entry.h == h && !entry.deleted) continue
            val previous = entry?.m ?: 0
            store.entries[key] = Entry(h, maxOf(now, previous + 1), dirty = true, deleted = false)
        }

        val gone = store.entries.filter { it.key !in snap.hashes && !it.value.deleted && !it.value.skipped }
        // Un'ondata di cancellazioni non voluta (un database svuotato per un guasto) non deve arrivare al server: si ferma finché non la si conferma.
        val live = store.entries.values.count { !it.deleted }
        if (gone.size > 20 && gone.size * 2 > live && !allowMassDeletion) {
            throw MassDeletionException("Sincronizzazione sospesa: molte voci risultano cancellate. Se è voluto, usa «Cancella tutti i dati».")
        }
        for ((key, entry) in gone) store.entries[key] = Entry("", maxOf(now, entry.m + 1), dirty = true, deleted = true)
    }

    private class MassDeletionException(message: String) : Exception(message)

    private fun updatePending() {
        _status.update { it.copy(pendingCount = store.entries.values.count { e -> e.dirty }) }
    }

    private fun persist() {
        if (credentials == null) return
        stateFile.save(json.encodeToString(SyncStore.serializer(), store))
    }

    // ---- foto --------------------------------------------------------------------------------------------

    /** Le foto nuove salgono PRIMA del documento che le richiama. */
    private suspend fun uploadPhotos(api: PeakApi, snap: Snap) {
        for ((sha, bytes) in snap.newPhotos) {
            if (sha in store.uploadedPhotos) continue
            try {
                api.putPhoto(sha, bytes)
                store.uploadedPhotos += sha
            } catch (_: PeakApiException.AccountNotEmpty) {
                store.uploadedPhotos += sha // troppe foto sul server: il macchinario resta senza, non si blocca tutto
            }
        }
    }

    /** Scarica e salva sul disco le foto che i documenti richiamano e il telefono non ha. */
    private suspend fun downloadPhotos(docs: List<WireDoc>, api: PeakApi) {
        for (doc in docs) {
            if (doc.kind != "exercise" || doc.deleted) continue
            val sha = (doc.data?.get("photo") as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            if (sha.isEmpty() || photos.has(sha)) continue
            val bytes = api.photo(sha)
            if (photos.writeAs(sha, bytes)) store.uploadedPhotos += sha // se i byte non tornano con lo sha la foto resta senza
        }
    }

    // ---- push e pull -------------------------------------------------------------------------------------

    private suspend fun push(api: PeakApi, snap: Snap) {
        // i documenti che il server ha scartato non si rimandano finché non cambiano (stessa `m`)
        val dirty = store.entries.filter { it.value.dirty && rejected[it.key] != it.value.m }.toSortedMap().entries.toList()
        for (chunk in dirty.chunked(BATCH)) {
            val docs = chunk.mapNotNull { (key, entry) ->
                val i = key.indexOf(':')
                if (i < 0) return@mapNotNull null
                val kind = key.substring(0, i)
                val uuid = key.substring(i + 1)
                if (entry.deleted) return@mapNotNull WireDoc(kind, uuid, null, entry.m, deleted = true)
                val bytes = snap.json[key] ?: return@mapNotNull null
                val data = try { Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject } catch (_: Exception) { null } ?: return@mapNotNull null
                WireDoc(kind, uuid, data, entry.m, deleted = false)
            }
            val answer = api.push(docs)
            val lost = answer.rejected.mapTo(HashSet()) { it.key }
            // pulito solo ciò che è davvero partito
            val sent = docs.mapTo(HashSet()) { it.key }
            for ((key, entry) in chunk) {
                if (key !in sent || key in lost) continue
                val current = store.entries[key]
                if (current != null && current.m == entry.m) { store.entries[key] = current.copy(dirty = false); rejected.remove(key) }
            }
            // scartati dal server: restano sporchi (non si perdono), ma non si rimandano finché non cambiano
            for (key in answer.discarded) {
                val entry = store.entries[key] ?: continue
                if (entry.dirty) rejected[key] = entry.m
            }
            // perso contro una versione più nuova: vale quella del server
            if (answer.rejected.isNotEmpty()) applyRemote(answer.rejected, api, force = true)
        }
    }

    private suspend fun pull(api: PeakApi) {
        val answer = api.pull(store.cursor)
        applyRemote(answer.docs, api, force = false)
        store.cursor = answer.rev
    }

    /**
     * Applica documenti arrivati dal server. Un documento locale modificato dopo vince (e partirà al prossimo giro), a meno di `force`;
     * uno uguale a quello che abbiamo già si salta.
     */
    private suspend fun applyRemote(docs: List<WireDoc>, api: PeakApi, force: Boolean) {
        // Una modifica fatta dopo l'istantanea non è ancora «sporca» per lo stato: si vede dal documento locale di adesso, che non ha più
        // l'hash registrato. Quel documento non si sovrascrive: parte al prossimo giro.
        val current = if (force) emptyMap() else SyncDocs.encodeAll(storage.snapshot())
        val wanted = docs.filter { doc ->
            val entry = store.entries[doc.key]
            if (force || entry == null) return@filter true
            if (entry.dirty && entry.m > doc.m) return@filter false
            if (!entry.dirty && entry.m == doc.m && entry.deleted == doc.deleted) return@filter false
            if (!entry.dirty && !entry.deleted && !doc.deleted) {
                val local = current[doc.key]
                if (local != null && SyncDocs.hash(local) != entry.h) { needsAnotherRound = true; return@filter false }
            }
            true
        }
        if (wanted.isEmpty()) return
        downloadPhotos(wanted, api)
        commit(wanted)
    }

    /** La parte che tocca il database: dopo che la rete ha finito. `base` = le righe su cui applicare (di norma quelle di adesso). */
    private suspend fun commit(docs: List<WireDoc>, base: Snapshot? = null, wipe: Boolean = false) {
        val before = base ?: storage.snapshot()
        val (applied, changes) = SyncDocs.apply(docs, before) { photos.has(it) }
        storage.commit(changes.copy(wipe = wipe))
        storage.recomputeRecords()

        val encoded = SyncDocs.encodeAll(storage.snapshot())
        val times = docs.associate { it.key to it.m }
        for (key in applied.upserted) {
            val doc = encoded[key] ?: continue
            store.entries[key] = Entry(SyncDocs.hash(doc), times[key] ?: nowMs(), dirty = false, deleted = false)
        }
        for (key in applied.deleted) store.entries[key] = Entry("", times[key] ?: nowMs(), dirty = false, deleted = true)
        for (key in applied.skipped) store.entries[key] = Entry("", times[key] ?: nowMs(), dirty = false, deleted = false, skipped = true)
    }

    // ---- collegare e scollegare --------------------------------------------------------------------------

    /** Dopo il login: il token è di questo telefono e lo stato riparte da zero. Resta in memoria (stato `Onboarding`) finché [completeConnection] non lo salva. */
    fun connect(credentials: PeakCredentials) {
        generation++
        store = SyncStore(sub = credentials.sub)
        _status.update { it.copy(credentials = credentials, state = State.Onboarding, lastError = null) }
    }

    /** La scelta è andata a buon fine: da qui in poi il telefono sincronizza. */
    fun completeConnection() {
        val c = credentials ?: return
        if (state != State.Onboarding) return
        credentialStore.save(c)
        _status.update { it.copy(state = State.Connected) }
        persist()
        updatePending()
    }

    /** Annullato (o fallito) a metà: il telefono torna com'era, senza account. */
    fun cancelConnection() {
        if (state != State.Onboarding) return
        store = SyncStore()
        _status.update { it.copy(credentials = null, state = State.Disconnected) }
    }

    /** Scollega questo telefono: i dati restano com'erano, come prima del collegamento. Il token sul server resta finché non lo si revoca da yrb4g.com/account/peak. */
    fun disconnect() {
        credentialStore.clear()
        generation++
        store = SyncStore()
        rejected.clear()
        _status.value = Status()
        stateFile.clear()
    }

    /** Dopo la scadenza: un nuovo login dello stesso account riprende da dove si era, senza domande. */
    fun renew(credentials: PeakCredentials): Boolean {
        if (state != State.Expired || credentials.sub != store.sub) return false
        credentialStore.save(credentials)
        _status.update { it.copy(credentials = credentials, state = State.Connected) }
        return true
    }

    /** Alla cancellazione di tutto i dati spariscono davvero anche dal server: la guardia non deve fermarla. */
    fun permitMassDeletion() { allowMassDeletion = true }

    // ---- il primo collegamento ---------------------------------------------------------------------------

    /** Il server ha già macchinari, allenamenti, massimali o pesate (le sole impostazioni non contano). */
    suspend fun inspectServer(): Boolean {
        val api = api() ?: throw PeakApiException.Unauthorized()
        if (storage.snapshot().hasActiveWorkout) throw BackupException.WorkoutInProgress()
        return api.pull(0).docs.any { !it.deleted && it.kind != "setting" }
    }

    suspend fun localHasUserData(): Boolean = storage.snapshot().hasUserData

    suspend fun localHasExercises(): Boolean = storage.snapshot().exercises.isNotEmpty()

    suspend fun hasActiveWorkout(): Boolean = storage.snapshot().hasActiveWorkout

    /** Prima di toccare qualcosa: una copia leggibile accanto al backup automatico. */
    suspend fun writePreConnectionBackup() = backup.writePreConnectionBackup()

    /** Carica i dati del telefono sul server (`first`: la prima scelta su un account vuoto). */
    suspend fun uploadLocalData(replace: Boolean, first: Boolean) {
        val api = api() ?: throw PeakApiException.Unauthorized()
        api.importBackup(backup.data(), replace, first)
    }

    suspend fun uploadBackup(data: ByteArray, replace: Boolean, first: Boolean) {
        val api = api() ?: throw PeakApiException.Unauthorized()
        api.importBackup(data, replace, first)
    }

    /** Sostituisce i dati del telefono con quelli del server. La rete si usa per prima: se cade, i dati locali non sono stati toccati. */
    suspend fun replaceLocalWithServer() {
        val api = api() ?: throw PeakApiException.Unauthorized()
        val answer = api.pull(0)
        downloadPhotos(answer.docs, api)

        generation++
        store.entries.clear()
        // si applica su un database vuoto (le impostazioni restano) e si scrive tutto in una transazione sola
        commit(answer.docs, base = Snapshot(settings = storage.snapshot().settings), wipe = true)
        store.cursor = answer.rev
        persist()
        updatePending()
        _status.update { it.copy(lastSync = clock()) }
    }

    private companion object {
        const val BATCH = 500
    }
}
