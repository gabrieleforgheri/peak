import Foundation
import Observation
import SwiftUI

/// Unità di misura mostrata a schermo. Sul disco i pesi sono **sempre** in kg.
enum WeightUnit: String, CaseIterable, Identifiable, Codable {
    case kg
    case lb

    var id: String { rawValue }
    var symbol: String { rawValue }
    var title: String { self == .kg ? "Chilogrammi (kg)" : "Libbre (lb)" }

    private static let poundsPerKilogram = 2.204622621848776

    /// Da kg (storage) al valore mostrato.
    func fromKilograms(_ kg: Double) -> Double {
        self == .kg ? kg : kg * Self.poundsPerKilogram
    }

    /// Dal valore inserito dall'utente ai kg da salvare.
    func toKilograms(_ value: Double) -> Double {
        self == .kg ? value : value / Self.poundsPerKilogram
    }

    /// Passo di default degli stepper quando l'esercizio non ne ha uno suo.
    var defaultStep: Double { self == .kg ? 2.5 : 5 }

    private static let poundStepForKilogramStep: [Double: Double] = [1.25: 2.5, 2: 5, 2.5: 5, 5: 10, 10: 20]

    /// Il passo del macchinario (salvato in kg) nel passo nativo dell'unità: in lb 2,5 kg diventa 5 lb,
    /// non 5,51. Stessa tabella del web (`displayStep` in common/domain.ts).
    func displayStep(forKilograms step: Double) -> Double {
        guard self == .lb else { return step }
        return Self.poundStepForKilogramStep[step] ?? max(2.5, (step * Self.poundsPerKilogram / 2.5).rounded() * 2.5)
    }

    /// Passo della pesata corporea.
    var bodyWeightStep: Double { self == .kg ? 0.1 : 0.2 }
}

/// Tinte selezionabili per l'accento dell'app.
enum AccentPalette: String, CaseIterable, Identifiable, Codable {
    case lime
    case ember
    case ice
    case violet
    case coral

    var id: String { rawValue }

    var title: String {
        switch self {
        case .lime: return "Lime"
        case .ember: return "Brace"
        case .ice: return "Ghiaccio"
        case .violet: return "Viola"
        case .coral: return "Corallo"
        }
    }

    var color: Color {
        switch self {
        case .lime: return Color(red: 0.72, green: 0.94, blue: 0.30)
        case .ember: return Color(red: 1.00, green: 0.58, blue: 0.24)
        case .ice: return Color(red: 0.45, green: 0.80, blue: 1.00)
        case .violet: return Color(red: 0.68, green: 0.55, blue: 1.00)
        case .coral: return Color(red: 1.00, green: 0.45, blue: 0.45)
        }
    }
}

/// Preferenze dell'utente, persistite su `UserDefaults`.
@Observable
final class AppSettings {

    private enum Key {
        static let unit = "settings.unit"
        static let accent = "settings.accent"
        static let lastBackup = "settings.lastBackupDate"
        static let health = "settings.healthSyncEnabled"
        static let restEnabled = "settings.restTimerEnabled"
        static let restSeconds = "settings.restSeconds"
        static let progression = "settings.progressionEnabled"
        static let repMin = "settings.repRangeMin"
        static let repMax = "settings.repRangeMax"
        static let plates = "settings.plateCalculatorEnabled"
        static let barWeight = "settings.barWeight"
        static let availablePlates = "settings.availablePlates"
        static let didSeeWelcome = "settings.didSeeWelcome"
        static let didLinkCatalog = "settings.didLinkCatalog"
        static let tutorialDone = "settings.tutorialDone"
        static let weeklyGoal = "settings.weeklyGoal"
    }

    private let defaults: UserDefaults

    var unit: WeightUnit {
        didSet {
            defaults.set(unit.rawValue, forKey: Key.unit)
            guard oldValue != unit else { return }
            // Dischi e bilanciere ancora «di serie» passano a quelli di serie della nuova unità (45 lb, dischi americani).
            let old = Plates.defaults(for: oldValue)
            if availablePlates.count == old.count, zip(availablePlates.sorted(), old.sorted()).allSatisfy({ abs($0 - $1) < 0.001 }) {
                availablePlates = Plates.defaults(for: unit)
            }
            if abs(barWeight - Plates.defaultBar(for: oldValue)) < 0.001 { barWeight = Plates.defaultBar(for: unit) }
        }
    }

    var accent: AccentPalette {
        didSet { defaults.set(accent.rawValue, forKey: Key.accent) }
    }

    /// Sincronizzazione con l'app Salute: peso in entrata, allenamenti in uscita.
    var healthSyncEnabled: Bool {
        didSet { defaults.set(healthSyncEnabled, forKey: Key.health) }
    }

    /// Timer di recupero dopo ogni serie.
    var restTimerEnabled: Bool {
        didSet { defaults.set(restTimerEnabled, forKey: Key.restEnabled) }
    }

    /// Recupero predefinito in secondi; ogni macchinario può avere il suo.
    var restSeconds: Int {
        didSet { defaults.set(restSeconds, forKey: Key.restSeconds) }
    }

    /// Suggerimento del prossimo carico (doppia progressione).
    var progressionEnabled: Bool {
        didSet { defaults.set(progressionEnabled, forKey: Key.progression) }
    }

    var repRangeMin: Int {
        didSet { defaults.set(repRangeMin, forKey: Key.repMin) }
    }

    var repRangeMax: Int {
        didSet { defaults.set(repRangeMax, forKey: Key.repMax) }
    }

    /// Mai vuoto: se gli estremi si incrociano vale il minimo.
    var repRange: ClosedRange<Int> { repRangeMin...max(repRangeMin, repRangeMax) }

    /// Calcolatore dischi per bilanciere e plate loaded.
    var plateCalculatorEnabled: Bool {
        didSet { defaults.set(plateCalculatorEnabled, forKey: Key.plates) }
    }

    /// Peso del bilanciere scarico, in kg.
    var barWeight: Double {
        didSet { defaults.set(barWeight, forKey: Key.barWeight) }
    }

    /// Dischi che la tua palestra ha davvero, in kg.
    var availablePlates: [Double] {
        didSet { defaults.set(availablePlates, forKey: Key.availablePlates) }
    }

    /// La pagina «Collega a yG» si mostra una volta sola, anche a chi aggiorna dalla 1.x. Resta di questo iPhone.
    var didSeeWelcome: Bool {
        didSet { defaults.set(didSeeWelcome, forKey: Key.didSeeWelcome) }
    }

    /// Gli esercizi già presenti sono stati collegati al catalogo (per nome), una volta sola.
    var didLinkCatalog: Bool {
        didSet { defaults.set(didLinkCatalog, forKey: Key.didLinkCatalog) }
    }

    /// Il tutorial l'hai già fatto (su un dispositivo qualsiasi). Segue l'account.
    var tutorialDone: Bool {
        didSet { defaults.set(tutorialDone, forKey: Key.tutorialDone) }
    }

    /// Allenamenti a settimana che ti sei dato come obiettivo; 0 = non l'hai ancora scelto. Segue l'account.
    var weeklyGoal: Int {
        didSet { defaults.set(weeklyGoal, forKey: Key.weeklyGoal) }
    }

    var lastBackupDate: Date? {
        didSet { defaults.set(lastBackupDate, forKey: Key.lastBackup) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.unit = WeightUnit(rawValue: defaults.string(forKey: Key.unit) ?? "") ?? .kg
        self.accent = AccentPalette(rawValue: defaults.string(forKey: Key.accent) ?? "") ?? .lime
        self.healthSyncEnabled = defaults.bool(forKey: Key.health)
        self.lastBackupDate = defaults.object(forKey: Key.lastBackup) as? Date
        self.restTimerEnabled = defaults.object(forKey: Key.restEnabled) as? Bool ?? true
        self.restSeconds = defaults.object(forKey: Key.restSeconds) as? Int ?? 120
        self.progressionEnabled = defaults.object(forKey: Key.progression) as? Bool ?? true
        self.repRangeMin = defaults.object(forKey: Key.repMin) as? Int ?? 8
        self.repRangeMax = defaults.object(forKey: Key.repMax) as? Int ?? 12
        self.plateCalculatorEnabled = defaults.object(forKey: Key.plates) as? Bool ?? true
        self.barWeight = defaults.object(forKey: Key.barWeight) as? Double ?? 20
        self.availablePlates = defaults.array(forKey: Key.availablePlates) as? [Double]
            ?? [25, 20, 15, 10, 5, 2.5, 1.25]
        self.didSeeWelcome = defaults.bool(forKey: Key.didSeeWelcome)
        self.didLinkCatalog = defaults.bool(forKey: Key.didLinkCatalog)
        self.tutorialDone = defaults.bool(forKey: Key.tutorialDone)
        self.weeklyGoal = defaults.integer(forKey: Key.weeklyGoal)
    }

    var accentColor: Color { accent.color }
}
