import Foundation

/// Quali dischi mettere per lato per arrivare a un carico.
enum Plates {

    /// Tutti i dischi selezionabili nelle impostazioni, dal più pesante.
    static let catalog: [Double] = [25, 20, 15, 10, 5, 2.5, 1.25, 0.5]

    private static let kilogramsPerPound = 0.45359237
    /// I dischi americani, in kg (si salva sempre in kg): 45 / 35 / 25 / 10 / 5 / 2,5 lb.
    static let poundCatalog: [Double] = [45, 35, 25, 10, 5, 2.5].map { $0 * kilogramsPerPound }

    /// I tagli che le impostazioni mostrano nell'unità scelta.
    static func catalog(for unit: WeightUnit) -> [Double] { unit == .kg ? catalog : poundCatalog }
    /// Dischi di serie dell'unità.
    static func defaults(for unit: WeightUnit) -> [Double] { unit == .kg ? [25, 20, 15, 10, 5, 2.5, 1.25] : poundCatalog }
    /// Bilanciere di serie: 20 kg, 45 lb.
    static func defaultBar(for unit: WeightUnit) -> Double { unit == .kg ? 20 : 45 * kilogramsPerPound }

    /// Peso predefinito della sbarra in base a pittogramma o nome (come defaultBarWeightFor nel web).
    static func defaultBar(for glyph: MachineGlyph?, name: String) -> Double {
        let n = name.lowercased()
        if n.contains("manubri") || n.contains("dumbbell") { return 0 }
        if glyph == .plateLoaded || n.contains("plate loaded") { return 0 }
        if glyph == .ezBar || n.contains("ez") { return 10 }
        if glyph == .trapBar || n.contains("trap bar") || n.contains("quadra") { return 25 }
        if n.contains("multipower") || n.contains("smith") { return 15 }
        if glyph == .barbell || glyph == .benchFlat || glyph == .benchIncline || glyph == .squatRack || glyph == .hipThrust
            || n.contains("bilanciere") || n.contains("barbell") { return 20 }
        return 0
    }

    /// Il calcolatore compare dove c'è un bilanciere (anche panche, rack, hip thrust, EZ, trap bar) e per le
    /// plate loaded (senza). Non con i manubri: un vecchio «Panca piana manubri» personalizzato ha il pittogramma della panca.
    /// `nil` = niente calcolatore. Stessa regola del web (`plateMode` in common/domain.ts).
    static func barBase(glyph: MachineGlyph, name: String, barWeight: Double) -> Double? {
        let name = name.lowercased()
        if glyph == .plateLoaded || name.contains("plate loaded") { return 0 }
        if barWeight > 0 { return barWeight }
        switch glyph {
        case .barbell, .benchFlat, .benchIncline, .squatRack, .hipThrust, .ezBar, .trapBar:
            return name.contains("manubri") ? nil : barWeight
        default:
            return nil
        }
    }

    struct Load: Equatable {
        /// Dischi su un lato, dal più pesante (il primo infilato) al più leggero.
        let perSide: [Double]
        /// Peso per lato che con i dischi disponibili non si riesce a caricare.
        let leftover: Double
    }

    /// Avido dal disco più pesante: con i tagli standard è anche la soluzione con meno dischi.
    /// `bar` è il peso già presente senza dischi (bilanciere, 0 per le plate loaded).
    static func load(total: Double, bar: Double, plates: [Double]) -> Load {
        var remaining = max(0, (total - bar) / 2)
        var perSide: [Double] = []
        for plate in plates.sorted(by: >) where plate > 0 {
            while remaining + 0.0001 >= plate {
                perSide.append(plate)
                remaining -= plate
            }
        }
        return Load(perSide: perSide, leftover: remaining < 0.0001 ? 0 : (remaining * 100).rounded() / 100)
    }
}
