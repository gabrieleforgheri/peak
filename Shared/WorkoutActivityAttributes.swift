import ActivityKit
import Foundation

/// Dati della Live Activity dell'allenamento, condivisi tra app ed estensione.
/// Tutto arriva già pronto da mostrare: l'estensione non conosce il modello.
struct WorkoutActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// Ultimo esercizio toccato, vuoto a inizio sessione.
        var exercise: String
        var sets: Int
        var volume: String
        /// Fine del recupero in corso, se c'è.
        var restEnd: Date? = nil
        /// Il prossimo esercizio della lista di oggi, ancora senza serie. `nil` se non c'è.
        var next: String? = nil
    }

    var startDate: Date
}
