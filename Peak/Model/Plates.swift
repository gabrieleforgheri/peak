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
        // «Curl Machine»: una macchina non ha il bilanciere, qualunque sia il pittogramma (il Multipower ha il suo)
        if (n.contains("machine") || n.contains("macchina")) && glyph != .smithMachine && !n.contains("smith") && !n.contains("multipower") { return 0 }
        if glyph == .plateLoaded || n.contains("plate loaded") { return 0 }
        if glyph == .ezBar || n.contains("ez") { return 10 }
        if glyph == .trapBar || n.contains("trap bar") || n.contains("quadra") { return 25 }
        if n.contains("multipower") || n.contains("smith") { return 15 }
        if glyph == .barbell || glyph == .benchFlat || glyph == .benchIncline || glyph == .squatRack || glyph == .hipThrust
            || n.contains("bilanciere") || n.contains("barbell") { return 20 }
        return 0
    }

    /// «plate» all'inizio di una parola nel nome (plated, plate loaded), non «template».
    private static func isPlateNamed(_ lowercasedName: String) -> Bool {
        lowercasedName.range(of: "\\bplate", options: .regularExpression) != nil
    }

    /// Euristica iniziale del flag «Carico a dischi» (nuovo esercizio o scelta dal catalogo).
    static func guessPlateLoaded(glyph: MachineGlyph?, name: String) -> Bool {
        glyph == .plateLoaded || isPlateNamed(name.lowercased())
    }

    /// Il calcolatore compare dove c'è un bilanciere (anche panche, rack, hip thrust, EZ, trap bar) e per le
    /// plate loaded (senza). Non con i manubri: un vecchio «Panca piana manubri» personalizzato ha il pittogramma della panca.
    /// `nil` = niente calcolatore. Stessa regola del web (`plateMode` in common/domain.ts).
    static func barBase(glyph: MachineGlyph, name: String, barWeight: Double, plateLoaded: Bool = false) -> Double? {
        let name = name.lowercased()
        if barWeight > 0 { return barWeight }
        /// L'euristica sul nome è in OR con il flag, quindi l'interruttore non può spegnere il calcolatore di un macchinario con «plate» nel nome.
        if plateLoaded || glyph == .plateLoaded || isPlateNamed(name) { return 0 }
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
