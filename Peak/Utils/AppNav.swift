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
    /// Sale quando le impostazioni devono chiudersi da sole («Rivedi il tutorial»: il giro sta sotto il foglio).
    var closeSettings = 0
    /// L'allenamento (uuid) la cui sessione è a schermo adesso.
    var visibleSession: UUID?
    /// Un allenamento chiuso dal Watch di cui va mostrato il riepilogo. Lo prende la sessione se è a schermo,
    /// altrimenti la radice (`RootView`) con il foglio del riepilogo.
    var pendingSummary: UUID?
}
