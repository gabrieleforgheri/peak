import Foundation

/// Serie di riscaldamento suggerite a partire dal carico vero dell'esercizio.
///
/// La rampa è quella classica in palestra: si sale in tre serie scaricando le
/// ripetizioni, così ci si scalda senza arrivare stanchi alla serie vera.
/// I pesi stanno **sulla griglia del macchinario**: si parte dal massimale, che è un
/// peso che hai davvero inserito, e si scende di N passi fino al più vicino a quello
/// calcolato. Così anche un pacco sfalsato (5 → 8,75 → 12,5…) dà pesi inseribili.
enum Warmup {

    struct Step: Identifiable {
        let id = UUID()
        /// Frazione del massimale, es. 0.4 per il 40%.
        let percent: Double
        let reps: Int
        /// Peso in kg, già portato sulla griglia del macchinario.
        let weight: Double

        var percentLabel: String { "\(Int((percent * 100).rounded()))%" }
    }

    /// 40% × 10 → 60% × 6 → 80% × 3.
    static let ramp: [(percent: Double, reps: Int)] = [
        (0.40, 10),
        (0.60, 6),
        (0.80, 3)
    ]

    /// Il peso della griglia `anchor − n·step` più vicino a `weight`, mai sotto zero:
    /// se il più vicino fosse ≤ 0 si prende il più piccolo positivo della griglia.
    static func nearest(_ weight: Double, anchor: Double, step: Double) -> Double {
        guard step > 0 else { return max(0, (weight * 2).rounded() / 2) }
        let n = ((anchor - weight) / step).rounded()
        var candidate = anchor - n * step
        if candidate <= 0.0001 {
            candidate = anchor - ((anchor - 0.0001) / step).rounded(.down) * step
        }
        // Via il rumore della virgola mobile: 47,4999… resta 47,5.
        return (candidate * 100).rounded() / 100
    }

    /// La rampa completa, in percentuale del carico di riferimento (il massimale
    /// vero del macchinario). Vuota se non c'è ancora niente su cui calcolarla.
    static func plan(base: Double?, step: Double) -> [Step] {
        guard let base, base > 0 else { return [] }
        return ramp.map { entry in
            Step(
                percent: entry.percent,
                reps: entry.reps,
                weight: nearest(base * entry.percent, anchor: base, step: step)
            )
        }
    }
}

/// Il peso quando si passa da due bracci a uno e viceversa (gemello di `snapToGrid` / `switchArm` in common/domain.ts).
/// I pesi sono nell'unità mostrata: in lb il passo è quello nativo (5 lb), non i 2,5 kg convertiti.
enum ArmSwitch {

    /// Il peso prima e dopo il dimezzamento: se non lo si tocca, tornando a due bracci si rimette identico.
    struct Memo {
        var before: Double
        var after: Double
    }

    /// Il peso della griglia `bar + k·step` più vicino a `weight`; a pari distanza il più basso. Mai sotto `bar`
    /// (né sotto un passo se `bar` è 0 e `weight` > 0).
    static func snapToGrid(_ weight: Double, bar: Double, step: Double) -> Double {
        guard weight > 0 else { return weight }
        guard step > 0 else { return (max(weight, bar) * 100).rounded() / 100 }
        let k = (weight - bar) / step
        var n = (k + 1e-6).rounded(.down)
        if k - n > 0.5 + 1e-6 { n += 1 }
        return ((bar + max(n, bar > 0 ? 0 : 1) * step) * 100).rounded() / 100
    }

    /// Il peso dopo un tocco sul selettore «2 | 1»: a uno la metà sulla griglia; di nuovo a due il precedente
    /// se il peso è ancora quello dimezzato, altrimenti il doppio sulla griglia.
    static func weight(toSingle: Bool, from weight: Double, memo: Memo?, bar: Double, step: Double) -> (weight: Double, memo: Memo?) {
        if toSingle {
            let after = snapToGrid(weight / 2, bar: bar, step: step)
            return (after, weight > 0 ? Memo(before: weight, after: after) : nil)
        }
        if let memo, abs(weight - memo.after) < 0.005 { return (memo.before, nil) }
        return (snapToGrid(weight * 2, bar: bar, step: step), nil)
    }
}
