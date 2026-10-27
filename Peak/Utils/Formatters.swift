import Foundation

enum Fmt {

    /// Peso formattato nell'unità scelta, es. "82,5 kg".
    static func weight(_ kilograms: Double, unit: WeightUnit, includeSymbol: Bool = true) -> String {
        let value = unit.fromKilograms(kilograms)
        // Due decimali: un disco da 1,25 kg deve leggersi 1,25, non 1,3.
        let number = value.formatted(.number.precision(.fractionLength(0...2)))
        return includeSymbol ? "\(number) \(unit.symbol)" : number
    }

    /// Solo il numero, senza simbolo, per i display grandi.
    static func weightValue(_ kilograms: Double, unit: WeightUnit) -> String {
        weight(kilograms, unit: unit, includeSymbol: false)
    }

    /// Volume totale: sopra la tonnellata passa a "12,4 t" per non riempire la riga di cifre.
    static func volume(_ kilograms: Double, unit: WeightUnit) -> String {
        let value = unit.fromKilograms(kilograms)
        if value >= 1000 {
            let tons = value / 1000
            return "\(tons.formatted(.number.precision(.fractionLength(0...1)))) \(unit == .kg ? "t" : "klb")"
        }
        return "\(value.formatted(.number.precision(.fractionLength(0)))) \(unit.symbol)"
    }

    /// Durata in minuti e secondi, es. "1:30".
    static func duration(seconds: Int) -> String {
        Duration.seconds(seconds).formatted(.time(pattern: .minuteSecond))
    }

    /// Variazione percentuale con segno, es. "+4,2%".
    static func delta(_ percent: Double) -> String {
        let sign = percent > 0 ? "+" : ""
        return sign + percent.formatted(.number.precision(.fractionLength(0...1))) + "%"
    }

    static func date(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.abbreviated).year())
    }

    static func shortDate(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.abbreviated))
    }

    static func dateTime(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.abbreviated).hour().minute())
    }

    /// Data relativa e leggibile: "Oggi", "Ieri", altrimenti la data breve.
    static func relativeDay(_ date: Date, calendar: Calendar = .current) -> String {
        if calendar.isDateInToday(date) { return "Oggi" }
        if calendar.isDateInYesterday(date) { return "Ieri" }
        return date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
    }

    static func duration(_ interval: TimeInterval) -> String {
        let minutes = max(0, Int(interval.rounded()) / 60)
        if minutes < 60 { return "\(minutes) min" }
        return "\(minutes / 60) h \(minutes % 60) min"
    }

    /// Il segno di una serie a un braccio: «1 br» (in inglese «1 arm»).
    static var armMarker: String { "1 br".tr }

    /// "80 kg × 5" — la riga base di una serie; a un braccio "80 kg × 5 · 1 br".
    static func setLine(weight: Double, reps: Int, unit: WeightUnit, singleArm: Bool = false) -> String {
        "\(Self.weight(weight, unit: unit)) × \(reps)" + (singleArm ? " · \(armMarker)" : "")
    }

    /// Riga da taccuino: serie consecutive con lo stesso peso (e lo stesso numero di bracci) raggruppate, ripetizioni
    /// unite da "·", gruppi separati da " · ", es. "71,5 kg 8·6 · 54 kg 8". Un gruppo a un braccio comincia con
    /// «1 br »: "1 br 40 kg 8·7 · 70 kg 10". Come `notebookLine` in common/format.ts.
    static func notebookLine(_ sets: [(weight: Double, reps: Int, singleArm: Bool)], unit: WeightUnit) -> String {
        var groups: [(weight: Double, singleArm: Bool, reps: [Int])] = []
        for s in sets {
            if let last = groups.last, last.weight == s.weight, last.singleArm == s.singleArm {
                groups[groups.count - 1].reps.append(s.reps)
            } else {
                groups.append((s.weight, s.singleArm, [s.reps]))
            }
        }
        return groups
            .map { ($0.singleArm ? "\(armMarker) " : "") + "\(weight($0.weight, unit: unit)) \($0.reps.map(String.init).joined(separator: "·"))" }
            .joined(separator: " · ")
    }
}
