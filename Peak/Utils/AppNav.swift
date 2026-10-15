import Foundation
import Observation

/// Dove sta l'app e cosa le si chiede dall'esterno di una scheda (la Home che avvia una scheda, il tutorial che cambia tab).
@MainActor
@Observable
final class AppNav {
    static let shared = AppNav()

    enum Tab: String, Hashable, CaseIterable {
        case home, workouts, progress, machines, friends, settings
    }

    var tab: Tab = .home
    /// Una scheda da avviare appena si apre la scheda Allenamento.
    var templateToStart: UUID?
    /// Aprire la sessione in corso.
    var resumeActive = false
}
