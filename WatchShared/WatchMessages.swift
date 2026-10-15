import Foundation

/// L'esercizio aperto sull'iPhone, già pronto da mostrare: il Watch non conosce il modello.
struct WatchExercise: Codable, Equatable {
    var uuid: UUID
    var name: String
    /// Carico proposto, in kg.
    var weight: Double
    /// Passo del macchinario in kg, per la Digital Crown.
    var step: Double
    /// Obiettivo della scheda già scritto («2/4 serie · 8–10 rip · 60 kg»). `nil` se l'allenamento non ne ha.
    var target: String? = nil
}

/// Stato che l'iPhone tiene aggiornato sul Watch (application context:
/// arriva anche se l'app del Watch era chiusa, alla prossima apertura).
struct WatchContext: Codable, Equatable {
    var exercise: WatchExercise?
    /// Unità mostrata e fattore kg → unità.
    var unitSymbol: String
    var unitFactor: Double
    /// Valorizzato quando sull'iPhone si tocca "Avvia sul Watch": il Watch riparte da zero su questo esercizio.
    var startRequestedAt: Date?

    static let key = "context"
}

/// Serie conclusa sul Watch, da registrare nella sessione dell'iPhone.
struct WatchSet: Codable, Equatable {
    var exerciseUUID: UUID
    var weight: Double
    var reps: Int

    static let key = "set"
}

extension Encodable {
    /// Le API di WatchConnectivity vogliono dizionari di plist: il JSON viaggia come `Data`.
    func watchPayload(key: String) -> [String: Any] {
        guard let data = try? JSONEncoder().encode(self) else { return [:] }
        return [key: data]
    }
}

extension Decodable {
    init?(watchPayload: [String: Any], key: String) {
        guard let data = watchPayload[key] as? Data,
              let value = try? JSONDecoder().decode(Self.self, from: data) else { return nil }
        self = value
    }
}
