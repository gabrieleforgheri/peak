import Foundation
import Observation
import SwiftData

/// Sincronizzazione con il server di Peak (peak.yrb4g.com), per chi ha collegato l'account yG.
///
/// COME FUNZIONA. Niente `modifiedAt` nei modelli e niente "tabella delle cancellazioni": ogni
/// documento (macchinario, serie, pesata…) si codifica in JSON, se ne calcola l'hash e lo si
/// confronta con quello della volta prima (`SyncStore.entries`). Un hash cambiato = modificato;
/// un documento che non c'è più = cancellato (anche quando a sparire è una serie per via di un
/// allenamento eliminato). Si spinge ciò che è cambiato, poi si legge ciò che è cambiato altrove.
/// Su un conflitto vince l'ultima modifica (`m`, in millisecondi, sempre crescente).
///
/// I massimali "da allenamento" non viaggiano: si ricalcolano dalle serie (RecordService).
///
/// SICUREZZA DEI DATI. Prima di cancellare o sostituire qualcosa in locale si scrive
/// `Documents/Peak-prima-del-collegamento.json`, e la rete si usa PRIMA delle modifiche locali:
/// se manca la connessione a metà, i dati dell'iPhone non sono toccati. Se lo store non si apre
/// (contenitore in memoria di emergenza) la sincronizzazione resta spenta, per non spingere
/// al server un "tutto cancellato".
@MainActor
@Observable
final class SyncService {

    static let shared = SyncService()

    enum State: Equatable {
        /// Nessun account collegato: l'app funziona in locale, come sempre.
        case disconnected
        /// Login fatto ma scelta non ancora completata (server vuoto o in conflitto): il token non è
        /// salvato e non si sincronizza, così un'interruzione a metà non unisce i dati a caso.
        case onboarding
        case connected
        /// Il server non riconosce più questo iPhone: i dati restano qui, serve collegare di nuovo.
        case expired
    }

    private(set) var state: State = .disconnected
    private(set) var credentials: PeakCredentials?
    private(set) var isSyncing = false
    private(set) var lastSync: Date?
    private(set) var lastError: String?
    private(set) var pendingCount = 0

    // MARK: - Stato su disco

    struct Entry: Codable {
        var h: String
        var m: Int64
        var dirty: Bool
        var deleted: Bool
        /// Documento arrivato ma non applicabile (manca il genitore): non va mai trattato come cancellato.
        var skipped = false
    }

    struct SyncStore: Codable {
        var sub = ""
        var cursor = 0
        var entries: [String: Entry] = [:]
        var uploadedPhotos: Set<String> = []
    }

    private var store = SyncStore()
    private var container: ModelContainer?
    private var settings: AppSettings?
    private var debounce: Task<Void, Never>?
    private var allowMassDeletion = false
    private var photoCache: [UUID: (count: Int, hash: Int, sha: String)] = [:]

    private static var stateURL: URL {
        let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent("peak-sync.json")
    }

    private var context: ModelContext { container!.mainContext }
    private var api: PeakAPI? { credentials.map { PeakAPI(token: $0.token) } }
    /// Per le chiamate agli amici, che stanno fuori dalla sincronizzazione.
    var peakAPI: PeakAPI? { api }

    var isConnected: Bool { state == .connected || state == .expired }

    // MARK: - Avvio

    func configure(container: ModelContainer, settings: AppSettings) {
        self.container = container
        self.settings = settings
        credentials = PeakKeychain.load()
        if let credentials {
            store = (try? JSONDecoder().decode(SyncStore.self, from: Data(contentsOf: Self.stateURL))) ?? SyncStore()
            // Un altro account sullo stesso telefono: lo stato dell'altro non vale.
            if store.sub != credentials.sub { store = SyncStore(sub: credentials.sub) }
            state = .connected
            updatePending()
        }
        NotificationCenter.default.addObserver(forName: ModelContext.didSave, object: nil, queue: .main) { _ in
            Task { @MainActor in
                if !SyncService.shared.isSyncing { SyncService.shared.requestSync() }
            }
        }
    }

    // MARK: - Quando sincronizzare

    /// Dopo una modifica si aspetta un attimo: una raffica di salvataggi fa un solo giro.
    func requestSync() {
        guard isConnected, !AppStore.isFallback else { return }
        debounce?.cancel()
        debounce = Task {
            try? await Task.sleep(for: .seconds(2))
            if !Task.isCancelled { await syncNow() }
        }
    }

    func syncNow() async {
        guard state == .connected, !AppStore.isFallback, !isSyncing, let api, container != nil else { return }
        isSyncing = true
        defer { isSyncing = false; updatePending(); persist() }
        do {
            try detect()
            try await uploadPhotos(api)
            try detect()
            try await push(api)
            try await pull(api)
            lastSync = Date()
            lastError = nil
        } catch PeakAPIError.unauthorized {
            state = .expired
        } catch {
            lastError = error.localizedDescription
        }
    }

    // MARK: - Cosa è cambiato

    private func sha(of exercise: Exercise) -> String {
        guard let photo = exercise.photo, !photo.isEmpty else { return "" }
        let hash = photo.hashValue
        if let cached = photoCache[exercise.uuid], cached.count == photo.count, cached.hash == hash { return cached.sha }
        let value = PeakAPI.sha256(photo)
        photoCache[exercise.uuid] = (photo.count, hash, value)
        return value
    }

    private func encode() throws -> SyncDocs.Encoded {
        try SyncDocs.encodeAll(context: context, settings: settings!, photoSha: { sha(of: $0) })
    }

    private static func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// Confronta i documenti di adesso con quelli dell'ultima volta e segna cosa va spinto.
    func detect() throws {
        let encoded = try encode()
        let now = Self.nowMs()

        for (key, data) in encoded {
            let h = SyncDocs.hash(data)
            if let entry = store.entries[key], entry.h == h, !entry.deleted { continue }
            let previous = store.entries[key]?.m ?? 0
            store.entries[key] = Entry(h: h, m: max(now, previous + 1), dirty: true, deleted: false)
        }

        let gone = store.entries.filter { encoded[$0.key] == nil && !$0.value.deleted && !$0.value.skipped }
        // Un'ondata di cancellazioni non voluta (un contenitore svuotato per un guasto) non deve
        // arrivare al server: si ferma finché non la si conferma (erase all).
        let live = store.entries.values.filter { !$0.deleted }.count
        if gone.count > 20, gone.count * 2 > live, !allowMassDeletion {
            lastError = "Sincronizzazione sospesa: molte voci risultano cancellate. Se è voluto, usa «Cancella tutti i dati»."
            throw SyncGuardError.massDeletion
        }
        for (key, entry) in gone {
            store.entries[key] = Entry(h: "", m: max(now, entry.m + 1), dirty: true, deleted: true)
        }
        allowMassDeletion = false
    }

    private enum SyncGuardError: Error { case massDeletion }

    private func updatePending() {
        pendingCount = store.entries.values.filter(\.dirty).count
    }

    private func persist() {
        guard credentials != nil, let data = try? JSONEncoder().encode(store) else { return }
        try? data.write(to: Self.stateURL, options: .atomic)
    }

    // MARK: - Foto

    /// Le foto nuove salgono PRIMA del documento che le richiama.
    private func uploadPhotos(_ api: PeakAPI) async throws {
        for exercise in try context.fetch(FetchDescriptor<Exercise>()) {
            guard let data = exercise.photo, !data.isEmpty else { continue }
            let digest = sha(of: exercise)
            guard !store.uploadedPhotos.contains(digest) else { continue }
            do {
                try await api.putPhoto(sha: digest, data: data)
                store.uploadedPhotos.insert(digest)
            } catch PeakAPIError.accountNotEmpty {
                store.uploadedPhotos.insert(digest) // troppe foto sul server: il macchinario resta senza, non si blocca tutto
            }
        }
    }

    private func downloadPhotos(for docs: [WireDoc], _ api: PeakAPI) async throws -> [String: Data] {
        var local = Set<String>()
        for exercise in try context.fetch(FetchDescriptor<Exercise>()) { local.insert(sha(of: exercise)) }
        var out: [String: Data] = [:]
        for doc in docs where doc.kind == "exercise" && !doc.deleted {
            guard let digest = doc.data?["photo"] as? String, !digest.isEmpty, !local.contains(digest), out[digest] == nil else { continue }
            out[digest] = try await api.photo(sha: digest)
            store.uploadedPhotos.insert(digest)
        }
        return out
    }

    // MARK: - Push e pull

    private func push(_ api: PeakAPI) async throws {
        let encoded = try encode()
        let dirty = store.entries.filter { $0.value.dirty }.sorted { $0.key < $1.key }
        let batchSize = 500
        var index = 0
        while index < dirty.count {
            let chunk = Array(dirty[index..<min(index + batchSize, dirty.count)])
            index += batchSize
            let docs: [WireDoc] = chunk.compactMap { key, entry in
                let parts = key.split(separator: ":", maxSplits: 1).map(String.init)
                guard parts.count == 2 else { return nil }
                if entry.deleted { return WireDoc(kind: parts[0], uuid: parts[1], data: nil, m: entry.m, deleted: true) }
                guard let data = encoded[key] else { return nil }
                return WireDoc(kind: parts[0], uuid: parts[1], data: data, m: entry.m, deleted: false)
            }
            let answer = try await api.push(docs)
            let lost = Set(answer.rejected.map(\.key))
            for (key, entry) in chunk where !lost.contains(key) {
                if var current = store.entries[key], current.m == entry.m { current.dirty = false; store.entries[key] = current }
            }
            for key in answer.discarded { store.entries[key]?.dirty = false }
            // perso contro una versione più nuova: vale quella del server
            if !answer.rejected.isEmpty { try await applyRemote(answer.rejected, api: api, force: true) }
        }
    }

    private func pull(_ api: PeakAPI) async throws {
        let answer = try await api.pull(since: store.cursor)
        try await applyRemote(answer.docs, api: api, force: false)
        store.cursor = answer.rev
    }

    /// Applica documenti arrivati dal server. Un documento locale modificato dopo vince (e partirà
    /// al prossimo giro), a meno di `force`; uno uguale a quello che abbiamo già si salta.
    private func applyRemote(_ docs: [WireDoc], api: PeakAPI, force: Bool) async throws {
        let wanted = docs.filter { doc in
            guard !force, let entry = store.entries[doc.key] else { return true }
            if entry.dirty && entry.m > doc.m { return false }
            if !entry.dirty && entry.m == doc.m && entry.deleted == doc.deleted { return false }
            return true
        }
        guard !wanted.isEmpty else { return }
        let photos = try await downloadPhotos(for: wanted, api)
        try commit(wanted, photos: photos)
    }

    /// La parte che tocca i modelli: sincrona, dopo che la rete ha finito.
    private func commit(_ docs: [WireDoc], photos: [String: Data]) throws {
        let applied = try SyncDocs.apply(docs, context: context, settings: settings!, photos: photos)
        try context.save()
        RecordService.recomputeAll(context: context)

        let encoded = try encode()
        let times = Dictionary(docs.map { ($0.key, $0.m) }, uniquingKeysWith: { _, last in last })
        for key in applied.upserted {
            guard let data = encoded[key] else { continue }
            store.entries[key] = Entry(h: SyncDocs.hash(data), m: times[key] ?? Self.nowMs(), dirty: false, deleted: false)
        }
        for key in applied.deleted { store.entries[key] = Entry(h: "", m: times[key] ?? Self.nowMs(), dirty: false, deleted: true) }
        for key in applied.skipped { store.entries[key] = Entry(h: "", m: times[key] ?? Self.nowMs(), dirty: false, deleted: false, skipped: true) }
    }

    // MARK: - Collegare e scollegare

    /// Dopo il login: il token è dell'iPhone, e lo stato di sincronizzazione riparte da zero. Resta in
    /// memoria (stato `onboarding`) finché `completeConnection()` non lo salva nel portachiavi.
    func connect(_ credentials: PeakCredentials) {
        self.credentials = credentials
        store = SyncStore(sub: credentials.sub)
        state = .onboarding
        lastError = nil
    }

    /// La scelta è andata a buon fine: da qui in poi l'iPhone sincronizza.
    func completeConnection() {
        guard state == .onboarding, let credentials else { return }
        PeakKeychain.save(credentials)
        state = .connected
        persist()
        updatePending()
    }

    /// Annullato (o fallito) a metà: l'iPhone torna com'era, senza account.
    func cancelConnection() {
        guard state == .onboarding else { return }
        credentials = nil
        store = SyncStore()
        state = .disconnected
    }

    /// Scollega questo iPhone: i dati restano com'erano, come prima del collegamento. Il token
    /// sul server resta finché non lo si revoca da yrb4g.com/account/peak.
    func disconnect() {
        debounce?.cancel()
        PeakKeychain.clear()
        credentials = nil
        state = .disconnected
        store = SyncStore()
        lastSync = nil
        lastError = nil
        pendingCount = 0
        try? FileManager.default.removeItem(at: Self.stateURL)
    }

    /// Dopo la scadenza: un nuovo login dello stesso account riprende da dove si era, senza domande.
    func renew(_ credentials: PeakCredentials) -> Bool {
        guard state == .expired, credentials.sub == store.sub else { return false }
        PeakKeychain.save(credentials)
        self.credentials = credentials
        state = .connected
        return true
    }

    /// Alla cancellazione di tutto i dati spariscono davvero anche dal server: la guardia non deve fermarla.
    func permitMassDeletion() { allowMassDeletion = true }

    // MARK: - Il primo collegamento

    struct ServerInfo {
        /// Ha già macchinari, allenamenti, massimali o pesate (le sole impostazioni non contano).
        let hasData: Bool
    }

    func inspectServer() async throws -> ServerInfo {
        guard let api else { throw PeakAPIError.unauthorized }
        let answer = try await api.pull(since: 0)
        return ServerInfo(hasData: answer.docs.contains { !$0.deleted && $0.kind != "setting" })
    }

    /// Ha l'iPhone qualcosa di suo (allenamenti, massimali o pesate), oltre al catalogo di serie?
    func localHasUserData() -> Bool {
        let workouts = (try? context.fetchCount(FetchDescriptor<Workout>())) ?? 0
        let records = (try? context.fetchCount(FetchDescriptor<MaxRecord>())) ?? 0
        let weights = (try? context.fetchCount(FetchDescriptor<BodyWeightEntry>())) ?? 0
        return workouts + records + weights > 0
    }

    var localHasExercises: Bool {
        ((try? context.fetchCount(FetchDescriptor<Exercise>())) ?? 0) > 0
    }

    var hasActiveWorkout: Bool {
        ((try? context.fetch(FetchDescriptor<Workout>())) ?? []).contains { $0.endedAt == nil }
    }

    /// Prima di toccare qualcosa: una copia leggibile nell'app File, accanto al backup automatico.
    func writePreConnectionBackup() throws {
        let data = try BackupService.data(context: context)
        try data.write(to: BackupService.documentsURL.appendingPathComponent("Peak-prima-del-collegamento.json"), options: .atomic)
    }

    /// Carica i dati dell'iPhone sul server (`first`: la prima scelta su un account vuoto).
    func uploadLocalData(replace: Bool, first: Bool) async throws {
        guard let api else { throw PeakAPIError.unauthorized }
        try await api.importBackup(BackupService.data(context: context), replace: replace, first: first)
    }

    func uploadBackup(_ data: Data, replace: Bool, first: Bool) async throws {
        guard let api else { throw PeakAPIError.unauthorized }
        try await api.importBackup(data, replace: replace, first: first)
    }

    /// Sostituisce i dati dell'iPhone con quelli del server. La rete si usa per prima: se cade, i dati
    /// locali non sono stati toccati.
    func replaceLocalWithServer() async throws {
        guard let api else { throw PeakAPIError.unauthorized }
        let answer = try await api.pull(since: 0)
        let photos = try await downloadPhotos(for: answer.docs, api)

        try context.delete(model: WorkoutSet.self)
        try context.delete(model: Workout.self)
        try context.delete(model: MaxRecord.self)
        try context.delete(model: Exercise.self)
        try context.delete(model: BodyWeightEntry.self)
        try context.delete(model: WorkoutTemplate.self)
        try context.save()
        photoCache = [:]
        store.entries = [:]

        try commit(answer.docs, photos: photos)
        store.cursor = answer.rev
        persist()
        updatePending()
        lastSync = Date()
    }
}
