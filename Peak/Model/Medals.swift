import Foundation

/// Le medaglie: sette traguardi con tre livelli (bronzo, argento, oro). Si ricavano dai numeri che l'app ha già, niente da
/// salvare. Stessa logica di `common/medals.ts`.
enum MedalID: String, CaseIterable, Identifiable {
    case sessions, records, streak, volume, strength, variety, friends

    var id: String { rawValue }

    var title: String {
        switch self {
        case .sessions: return "Costanza"
        case .records: return "Cacciatore di record"
        case .streak: return "In serie"
        case .volume: return "Tonnellate"
        case .strength: return "Forza"
        case .variety: return "Esploratore"
        case .friends: return "Squadra"
        }
    }

    var symbol: String {
        switch self {
        case .sessions: return "dumbbell.fill"
        case .records: return "trophy.fill"
        case .streak: return "flame.fill"
        case .volume: return "scalemass.fill"
        case .strength: return "bolt.fill"
        case .variety: return "safari.fill"
        case .friends: return "person.2.fill"
        }
    }

    /// Soglie di bronzo, argento, oro.
    var tiers: [Double] {
        switch self {
        case .sessions: return [5, 25, 100]
        case .records: return [1, 10, 50]
        case .streak: return [2, 6, 12]
        case .volume: return [1_000, 25_000, 250_000]
        case .strength: return [60, 100, 140]
        case .variety: return [5, 12, 25]
        case .friends: return [1, 3, 10]
        }
    }
}

extension MedalID {
    /// Cosa misura, in una riga (la stessa del web).
    var info: String {
        switch self {
        case .sessions: return "Ogni allenamento concluso conta."
        case .records: return "Ogni massimale battuto conta."
        case .streak: return "Settimane di seguito con almeno un allenamento."
        case .volume: return "Tutto il peso sollevato, sommato."
        case .strength: return "Il tuo miglior 1RM stimato, su un macchinario."
        case .variety: return "Quanti macchinari diversi hai usato."
        case .friends: return "Quanti amici hai collegato."
        }
    }
}

struct MedalInput {
    var sessions = 0
    var records = 0
    var streak = 0
    var volumeKg = 0.0
    var bestOneRm = 0.0
    var machines = 0
    var friends = 0

    func value(_ id: MedalID) -> Double {
        switch id {
        case .sessions: return Double(sessions)
        case .records: return Double(records)
        case .streak: return Double(streak)
        case .volume: return volumeKg
        case .strength: return bestOneRm
        case .variety: return Double(machines)
        case .friends: return Double(friends)
        }
    }
}

struct Medal: Identifiable {
    let kind: MedalID
    let value: Double
    /// 0 = ancora niente, 1 bronzo, 2 argento, 3 oro.
    let tier: Int
    /// Verso il livello dopo, da 0 a 1 (1 = oro già preso).
    let progress: Double
    let next: Double?

    var id: String { kind.rawValue }
}

enum Medals {
    static func list(_ input: MedalInput) -> [Medal] {
        MedalID.allCases.map { kind in
            let value = input.value(kind)
            let tiers = kind.tiers
            let tier = tiers.filter { value >= $0 }.count
            let next: Double? = tier < 3 ? tiers[tier] : nil
            let base = tier == 0 ? 0 : tiers[tier - 1]
            let progress = next.map { max(0, min(1, (value - base) / ($0 - base))) } ?? 1
            return Medal(kind: kind, value: value, tier: tier, progress: progress, next: next)
        }
    }

    static func totals(_ list: [Medal]) -> (earned: Int, total: Int) {
        (list.reduce(0) { $0 + $1.tier }, list.count * 3)
    }
}

/// Le frasi della Home: una diversa ogni giorno, poi a rotazione.
enum Motivation {
    static let phrases = [
        "Oggi è un buon giorno per battere il tuo record.",
        "Ogni vetta comincia con un primo passo.",
        "La costanza batte il talento.",
        "Il peso non si alza da solo.",
        "Niente scuse, solo serie.",
        "Sei più forte di ieri. Domani lo sarai di più.",
        "Fatica oggi, orgoglio domani.",
        "Un record nasce da una serie normale.",
        "Respira, spingi, ripeti.",
        "La vetta non si vede dal divano.",
        "Il tuo unico avversario è la scheda di ieri.",
        "Piccoli passi, grandi carichi.",
        "Ogni ripetizione conta.",
        "Chi si allena oggi ringrazia domani.",
        "Riposa se serve, ma non mollare.",
        "Un allenamento alla volta."
    ]

    static func startIndex(now: Date = Date(), calendar: Calendar = .current) -> Int {
        let day = calendar.ordinality(of: .day, in: .era, for: now) ?? 0
        return day % phrases.count
    }
}
