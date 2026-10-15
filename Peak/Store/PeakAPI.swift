import CryptoKit
import Foundation

/// Un documento come viaggia fra iPhone e server: `kind` e `uuid` lo identificano, `data` è il
/// contenuto (nil per una cancellazione), `m` l'istante dell'ultima modifica in millisecondi.
struct WireDoc {
    let kind: String
    let uuid: String
    let data: [String: Any]?
    let m: Int64
    let deleted: Bool

    var key: String { "\(kind):\(uuid)" }

    init(kind: String, uuid: String, data: [String: Any]?, m: Int64, deleted: Bool) {
        self.kind = kind; self.uuid = uuid; self.data = data; self.m = m; self.deleted = deleted
    }

    init?(json: Any) {
        guard let object = json as? [String: Any],
              let kind = object["kind"] as? String,
              let uuid = object["uuid"] as? String,
              let m = (object["m"] as? NSNumber)?.int64Value else { return nil }
        self.init(kind: kind, uuid: uuid, data: object["data"] as? [String: Any], m: m, deleted: object["deleted"] as? Bool ?? false)
    }

    var json: [String: Any] {
        ["kind": kind, "uuid": uuid, "data": data ?? NSNull(), "m": m, "deleted": deleted]
    }
}

enum PeakAPIError: LocalizedError {
    /// Il token non vale più (scollegato dal sito, o l'utente è uscito dal gruppo).
    case unauthorized
    /// Nessuna rete o il server non risponde.
    case offline
    case server(Int)
    case invalidAnswer
    /// L'account ha già dei dati (la prima scelta l'ha fatta un altro dispositivo).
    case accountNotEmpty
    /// Il file di backup non è valido o è di una versione più nuova.
    case invalidBackup(String)

    var errorDescription: String? {
        switch self {
        case .unauthorized: return "Il collegamento a yG è scaduto: collega di nuovo."
        case .offline: return "Peak non è raggiungibile. Riprova con la connessione."
        case .server(let status): return "Peak ha risposto con un errore (\(status))."
        case .invalidAnswer: return "Risposta di Peak non valida."
        case .accountNotEmpty: return "Su Peak ci sono già dei dati."
        case .invalidBackup(let message): return message
        }
    }
}

/// Il server di Peak (https://peak.yrb4g.com). Un solo indirizzo fisso, come per yG Music: il
/// token del dispositivo non si manda mai altrove.
struct PeakAPI {

    static let base = URL(string: "https://peak.yrb4g.com")!

    let token: String

    // MARK: - Richieste

    func send(
        _ path: String,
        method: String = "GET",
        query: [URLQueryItem] = [],
        body: Data? = nil,
        contentType: String? = nil
    ) async throws -> Data {
        var components = URLComponents(url: Self.base.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        var request = URLRequest(url: components.url!)
        request.httpMethod = method
        request.timeoutInterval = 30
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        request.httpBody = body

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw PeakAPIError.offline
        }
        guard let http = response as? HTTPURLResponse else { throw PeakAPIError.invalidAnswer }
        switch http.statusCode {
        case 200..<300: return data
        case 401: throw PeakAPIError.unauthorized
        case 409: throw PeakAPIError.accountNotEmpty
        case 422:
            let message = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]).flatMap { object -> String? in
                if object["error"] as? String == "versione", let version = object["version"] as? Int {
                    return "Backup in versione \(version): serve una versione più recente dell'app."
                }
                return nil
            }
            throw PeakAPIError.invalidBackup(message ?? "Il file non è un backup di Massimali valido.")
        default: throw PeakAPIError.server(http.statusCode)
        }
    }

    private func object(_ data: Data) throws -> [String: Any] {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw PeakAPIError.invalidAnswer }
        return object
    }

    // MARK: - Sincronizzazione

    /// Chi è (e se il token vale ancora).
    func me() async throws -> (sub: String, name: String) {
        let answer = try object(await send("api/io"))
        guard let sub = answer["sub"] as? String else { throw PeakAPIError.invalidAnswer }
        return (sub, answer["nome"] as? String ?? "")
    }

    func pull(since cursor: Int) async throws -> (rev: Int, docs: [WireDoc]) {
        let answer = try object(await send("api/sync", query: [URLQueryItem(name: "dopo", value: String(cursor))]))
        guard let rev = (answer["rev"] as? NSNumber)?.intValue, let docs = answer["docs"] as? [Any] else { throw PeakAPIError.invalidAnswer }
        return (rev, docs.compactMap(WireDoc.init(json:)))
    }

    /// Spinge i documenti; restituisce quelli che il server ha rifiutato perché ne ha una versione
    /// più nuova (con quella del server dentro) e quanti ne ha scartati perché non validi.
    func push(_ docs: [WireDoc]) async throws -> (rejected: [WireDoc], discarded: [String]) {
        let body = try JSONSerialization.data(withJSONObject: ["docs": docs.map(\.json)])
        let answer = try object(await send("api/sync", method: "POST", body: body, contentType: "application/json"))
        let rejected = (answer["rifiutati"] as? [Any] ?? []).compactMap(WireDoc.init(json:))
        let discarded = (answer["scartati"] as? [[String: Any]] ?? []).compactMap { item -> String? in
            guard let kind = item["kind"] as? String, let uuid = item["uuid"] as? String else { return nil }
            return "\(kind):\(uuid)"
        }
        return (rejected, discarded)
    }

    // MARK: - Foto (indirizzate dal loro sha256)

    static func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    func putPhoto(sha: String, data: Data) async throws {
        _ = try await send("api/foto/\(sha)", method: "PUT", body: data, contentType: "image/jpeg")
    }

    func photo(sha: String) async throws -> Data {
        try await send("api/foto/\(sha)")
    }

    // MARK: - Operazioni in blocco

    /// Carica un backup (il formato dell'app) sul server: `unisci` aggiunge, `sostituisci` riparte
    /// dal file. `first` = la prima scelta su un account vuoto: se nel frattempo un altro dispositivo
    /// ha caricato dati, risponde `accountNotEmpty`.
    func importBackup(_ data: Data, replace: Bool, first: Bool) async throws {
        var query = [URLQueryItem(name: "modo", value: replace ? "sostituisci" : "unisci")]
        if first { query.append(URLQueryItem(name: "primo", value: "1")) }
        _ = try await send("api/importa", method: "POST", query: query, body: data, contentType: "application/json")
    }
}
