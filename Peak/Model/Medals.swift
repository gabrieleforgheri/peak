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

    /// Quanto del livello dopo hai fatto, da 0 a 1 (valore / soglia): è anche quello che si legge accanto, «12 / 25». Oro preso = 1.
    var frac: Double { next.map { min(1, value / $0) } ?? 1 }
}

enum Medals {
    /// Prima le più vicine al livello dopo, poi quelle d'oro; a parità resta l'ordine di sempre.
    static func ordered(_ list: [Medal]) -> [Medal] {
        let open = list.enumerated().filter { $0.element.next != nil }
            .sorted { $0.element.frac != $1.element.frac ? $0.element.frac > $1.element.frac : $0.offset < $1.offset }
        return open.map(\.element) + list.filter { $0.next == nil }
    }

    /// Quando ogni livello già preso è scattato la prima volta: scorre gli istanti `times` (in ordine qualsiasi) e li chiede a `at`
    /// (le medaglie com'erano allora), fermandosi quando ha trovato tutto. Per i livelli non presi (`current`) o non trovati: nil.
    /// Vale il primo istante in cui il livello c'era, anche se poi è stato perso (la serie settimanale può interrompersi).
    /// Come `tierDates` in common/medals.ts.
    static func tierDates(times: [Date], at: (Date) -> [Medal], current: [Medal]) -> [MedalID: [Date?]] {
        var out = Dictionary(uniqueKeysWithValues: current.map { ($0.kind, [Date?](repeating: nil, count: 3)) })
        let cap = Dictionary(uniqueKeysWithValues: current.map { ($0.kind, $0.tier) })
        var left = current.reduce(0) { $0 + $1.tier }
        for time in Set(times).sorted() {
            if left == 0 { break }
            for m in at(time) {
                guard let c = cap[m.kind] else { continue }
                for i in 0..<min(m.tier, c) where out[m.kind]?[i] == nil { out[m.kind]?[i] = time; left -= 1 }
            }
        }
        return out
    }

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

/// Le frasi dell'eroe in Allenamento: una diversa ogni giorno, poi a rotazione.
enum Motivation {
    private static let italian = [
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
        "Un allenamento alla volta.",
        "Costruisci oggi, quello che sarai domani.",
        "La disciplina è la via, per ogni vetta.",
        "Non contare i giorni, fai contare i giorni.",
        "La forza cresce, quando pensi di non farcela.",
        "Focalizzati sull'obiettivo, dimentica il rumore.",
        "Ogni goccia di sudore, è un passo avanti.",
        "Vinci la mente, e il corpo seguirà.",
        "Il ferro non mente mai, ti dice sempre chi sei.",
        "Supera i tuoi limiti, un chilo alla volta.",
        "La determinazione, trasforma la fatica in forza.",
        "Non fermarti quando sei stanco, fermati quando hai finito.",
        "Non cercare scorciatoie, la salita fa parte del viaggio.",
        "La motivazione ti fa partire, l'abitudine ti fa andare avanti.",
        "Una serie in più oggi, un record in più domani.",
        "Fidati del processo, i risultati arriveranno.",
        "Non serve essere perfetti, basta essere costanti."
    ]

    /// Già tradotte e spezzate dopo la virgola. Si traduce PRIMA di andare a capo: la chiave di `.tr`
    /// è l'italiano, con "\n" dentro non si trova più. La vista poi non trova chiave e lascia com'è.
    static var phrases: [String] {
        italian.map { $0.tr.replacingOccurrences(of: ", ", with: ",\n") }
    }

    static func startIndex(now: Date = Date(), calendar: Calendar = .current) -> Int {
        let day = calendar.ordinality(of: .day, in: .era, for: now) ?? 0
        return day % italian.count
    }
}
