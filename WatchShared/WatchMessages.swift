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
    /// Recupero dopo una serie vera, in secondi. `nil` se il timer di recupero è spento sull'iPhone
    /// (e nei messaggi di un iPhone più vecchio, che non lo mandano).
    var restSeconds: Int? = nil
    /// Recupero dopo una serie di riscaldamento. `nil`: la metà di `restSeconds`.
    var warmupRestSeconds: Int? = nil

    /// Secondi di recupero da far girare sul Watch dopo una serie; `nil` se non si deve recuperare.
    func restDuration(warmup: Bool) -> Int? {
        guard let rest = restSeconds, rest > 0 else { return nil }
        let seconds = warmup ? (warmupRestSeconds ?? rest / 2) : rest
        return seconds > 0 ? seconds : nil
    }
}

/// Una riga della lista di oggi, per la pagina «Esercizi» del Watch.
struct WatchPlanItem: Codable, Equatable, Identifiable {
    var uuid: UUID
    var name: String
    /// Serie di lavoro fatte oggi.
    var done: Int
    /// Serie previste dal piano; 0 = il piano non ne dice.
    var target: Int

    var id: UUID { uuid }
    /// Obiettivo raggiunto (solo se il piano ne ha uno).
    var reached: Bool { target > 0 && done >= target }
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
    /// La lista di oggi, nell'ordine del piano. `nil` = nessun allenamento in corso (e nei messaggi di un iPhone
    /// più vecchio); `[]` = allenamento aperto ma senza ancora esercizi.
    var plan: [WatchPlanItem]? = nil
    /// Serie di lavoro e volume (kg) dell'allenamento, per il riepilogo del Watch. `nil` nei messaggi vecchi.
    var workoutSets: Int? = nil
    var workoutVolume: Double? = nil

    /// C'è un allenamento aperto sull'iPhone (con o senza esercizio scelto).
    var isWorkoutActive: Bool { exercise != nil || plan != nil }

    static let key = "context"
}

/// Serie conclusa sul Watch, da registrare nella sessione dell'iPhone.
struct WatchSet: Codable, Equatable {
    var exerciseUUID: UUID
    var weight: Double
    var reps: Int
    var isWarmup = false
    /// Identificativo della serie e istante dell'invio: servono all'iPhone per scartare i doppioni
    /// (sendMessage che fallisce e ripiega su transferUserInfo può consegnare due volte) e per non
    /// aprire un allenamento nuovo con una serie rimasta in coda da ore. `nil` nei messaggi di un Watch vecchio.
    var id: UUID? = nil
    var sentAt: Date? = nil

    static let key = "set"
}

/// Comando dal Watch all'iPhone: scegliere l'esercizio o chiudere l'allenamento.
struct WatchCommand: Codable, Equatable {
    var action: String
    /// Solo per `select`.
    var exerciseUUID: UUID? = nil
    /// Identificativo e istante dell'invio: come in `WatchSet`, per scartare i doppioni e i comandi in ritardo.
    var id: UUID
    var sentAt: Date

    static let select = "select"
    static let end = "end"
    static let key = "command"
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
