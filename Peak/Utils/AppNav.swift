import Foundation
import Observation

/// Dove sta l'app e cosa le si chiede dall'esterno di una scheda (la scheda Allenamento che avvia un giorno, il tutorial che cambia tab).
@MainActor
@Observable
final class AppNav {
    static let shared = AppNav()

    enum Tab: String, Hashable, CaseIterable {
        case workouts, machines, me, friends
    }

    var tab: Tab = .workouts
    /// Una scheda da avviare appena si apre la scheda Allenamento.
    var templateToStart: UUID?
    /// Aprire la sessione in corso.
    var resumeActive = false
}
