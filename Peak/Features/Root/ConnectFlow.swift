import AuthenticationServices
import Foundation
import Observation

/// Le scelte del primo collegamento a Peak (yG). Prima di toccare qualcosa sull'iPhone si scrive
/// `Documents/Peak-prima-del-collegamento.json`; il token si salva solo a scelta finita (SyncService).
///
///  - server vuoto: carica i dati dell'iPhone, importa un backup, oppure parti dal catalogo;
///  - server con dati e iPhone con dati propri: «Usa i dati del server» oppure «Unisci»;
///  - server con dati e iPhone solo col catalogo: si prendono i dati del server, senza domande.
@MainActor
@Observable
final class ConnectFlow {

    enum Stage: Equatable {
        case idle
        case working(String)
        /// `hasLocalData`: l'iPhone ha macchinari, allenamenti, massimali o pesate da caricare.
        case emptyServer(hasLocalData: Bool)
        case conflict
        case failed(String)
        case done
    }

    private(set) var stage: Stage = .idle
    private let sync = SyncService.shared

    /// Il login, poi la scelta. `authenticate` apre la pagina di yG e restituisce `peak://login?code=…`.
    func begin(authenticate: (URL) async throws -> URL) async {
        guard !AppStore.isFallback else {
            stage = .failed("L'archivio dell'app non si è aperto e i dati non sono al sicuro. Chiudi e riapri Peak prima di collegare l'account.")
            return
        }
        guard !sync.hasActiveWorkout else {
            stage = .failed("C'è un allenamento in corso. Terminalo e poi collega l'account.")
            return
        }
        stage = .working("Accesso a yG…")
        do {
            let login = PeakLogin.start()
            let callback = try await authenticate(login.url)
            guard let code = PeakLogin.code(from: callback) else { throw PeakAPIError.invalidAnswer }
            let credentials = try await PeakLogin.credentials(code: code, verifier: login.verifier)
            // Collegamento scaduto, stesso account: si riprende da dove si era, senza domande.
            if sync.renew(credentials) {
                await sync.syncNow()
                stage = .done
                return
            }
            // La copia di sicurezza prima di qualunque cosa: se non si riesce a scriverla, non si va avanti.
            if sync.localHasExercises { try sync.writePreConnectionBackup() }
            sync.connect(credentials)
            try await decide()
        } catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin {
            stage = .idle
        } catch {
            sync.cancelConnection()
            stage = .failed(error.localizedDescription)
        }
    }

    func cancel() {
        sync.cancelConnection()
        stage = .idle
    }

    private func decide() async throws {
        stage = .working("Controllo i dati su Peak…")
        let info = try await sync.inspectServer()
        if !info.hasData {
            stage = .emptyServer(hasLocalData: sync.localHasExercises || sync.localHasUserData())
        } else if sync.localHasUserData() {
            stage = .conflict
        } else {
            await run("Scarico i tuoi dati…") { try await self.sync.replaceLocalWithServer() }
        }
    }

    // MARK: - Le scelte

    func uploadLocalData() async {
        await run("Carico i dati di questo iPhone…") {
            try await self.sync.uploadLocalData(replace: false, first: true)
            try await self.sync.replaceLocalWithServer()
        }
    }

    func startFromCatalog() async {
        await run("Preparo il catalogo…") {
            try await self.sync.seedServerCatalog()
            try await self.sync.replaceLocalWithServer()
        }
    }

    func importBackup(_ data: Data) async {
        await run("Importo il backup…") {
            try await self.sync.uploadBackup(data, replace: false, first: true)
            try await self.sync.replaceLocalWithServer()
        }
    }

    func useServerData() async {
        await run("Scarico i dati da Peak…") { try await self.sync.replaceLocalWithServer() }
    }

    func merge() async {
        await run("Unisco i dati…") {
            try await self.sync.uploadLocalData(replace: false, first: false)
            try await self.sync.replaceLocalWithServer()
        }
    }

    /// Esegue un passo, poi chiude il collegamento. Un errore lascia l'iPhone com'era (la rete va
    /// prima delle modifiche locali) e riporta alla scelta o al messaggio.
    private func run(_ message: String, _ step: () async throws -> Void) async {
        stage = .working(message)
        do {
            try await step()
            sync.completeConnection()
            await sync.syncNow()
            stage = .done
        } catch PeakAPIError.accountNotEmpty {
            // Un altro dispositivo ha fatto la prima scelta mentre si decideva: si riparte dal confronto.
            do { try await decide() } catch { stage = .failed(error.localizedDescription) }
        } catch {
            stage = .failed(error.localizedDescription)
        }
    }
}
