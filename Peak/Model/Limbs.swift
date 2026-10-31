import Foundation

/// Uno o due arti. Gemello di «uno o due arti» in common/domain.ts: stesse regole, stesso catalogo.
/// Il peso scritto è sempre quello sull'attrezzo (un manubrio, il perno della pila, il bilanciere intero).
/// Il macchinario dice come si fa: sempre insieme, sempre un arto alla volta, oppure tutti e due con il selettore
/// sulla serie. Solo nel terzo caso il flag `singleArm` della serie conta: apre la linea di massimali a parte.
enum Limbs: String, CaseIterable {
    case both, one, either

    /// Voci del catalogo che si fanno spesso in tutti e due i modi: lo stesso macchinario, a due o a uno.
    private static let eitherIDs: Set<String> = [
        "leg-extension", "seated-leg-curl", "lying-leg-curl", "standing-leg-curl", "leg-curl-cable",
        "leg-press-45", "leg-press-horizontal", "leg-press-vertical", "leg-press-plate-loaded",
        "dumbbell-curl", "alternating-dumbbell-curl", "hammer-curl", "incline-dumbbell-curl", "preacher-curl-dumbbell",
        "preacher-curl-machine", "curl-machine", "cable-curl", "dumbbell-row",
        "lateral-raise", "cable-lateral-raise", "front-raise", "front-raise-cable", "rear-delt-fly-cable",
        "triceps-kickback-dumbbell", "cable-kickback", "glute-kickback-cable", "overhead-triceps-extension-dumbbell"
    ]
    private static let oneName = try! NSRegularExpression(pattern: "single|one[- ]?(arm|leg)|unilateral|singol|un braccio|una gamba", options: .caseInsensitive)
    private static let handleGlyphs: Set<String> = [
        "dumbbell", "dumbbellBench", "inclineDumbbell", "cableStation", "cableCross", "cableRope", "cablePushdown", "kettlebell"
    ]
    private static let handleName = try! NSRegularExpression(pattern: "manubr|cav[io]\\b|dumbbell|cable|kettlebell", options: .caseInsensitive)

    private static func matches(_ regex: NSRegularExpression, _ text: String) -> Bool {
        regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }

    /// Come si fa di base: dal catalogo, per i personalizzati dal nome. Core sempre insieme.
    static func defaultLimbs(catalog: String, name: String, muscleGroup: String) -> Limbs {
        if muscleGroup == MuscleGroup.core.rawValue { return .both }
        if !catalog.isEmpty {
            if catalog.contains("single") || catalog == "concentration-curl" || catalog == "meadows-row" { return .one }
            return eitherIDs.contains(catalog) ? .either : .both
        }
        return matches(oneName, name) ? .one : .both
    }

    /// La scelta salvata, altrimenti il vecchio segno «un braccio» (`isUnilateral`), altrimenti quella di base.
    static func limbsOf(saved: String?, isUnilateral: Bool, catalog: String, name: String, muscleGroup: String) -> Limbs {
        if let saved, let limbs = Limbs(rawValue: saved) { return limbs }
        return isUnilateral ? .one : defaultLimbs(catalog: catalog, name: name, muscleGroup: muscleGroup)
    }

    /// La serie va sulla linea «a un braccio/una gamba»: solo se il macchinario si fa in tutti e due i modi.
    static func lineArm(_ limbs: Limbs, _ singleArm: Bool) -> Bool { limbs == .either && singleArm }

    /// Manubri, cavi, kettlebell: il peso è già quello di un manico, passando a un arto non si dimezza (la pila unica sì).
    static func twoHandles(catalog: String, name: String, glyph: String) -> Bool {
        if catalog.contains("dumbbell") || catalog.contains("cable") || catalog.contains("kettlebell") || handleGlyphs.contains(glyph) { return true }
        return catalog.isEmpty && matches(handleName, name)
    }
}

extension Exercise {
    /// Come si fa questo macchinario (vedi `Limbs`).
    var limbs: Limbs {
        Limbs.limbsOf(saved: limbsRaw, isUnilateral: isUnilateral, catalog: catalogID, name: name, muscleGroup: muscleGroupRaw)
    }

    /// Il peso non si dimezza passando a un arto (manubri, cavi).
    var twoHandles: Bool { Limbs.twoHandles(catalog: catalogID, name: name, glyph: glyph?.rawValue ?? "") }

    /// Salva la scelta; `isUnilateral` la segue (è il segno che leggono i client vecchi).
    func setLimbs(_ value: Limbs) {
        if limbsRaw != value.rawValue { limbsRaw = value.rawValue }
        let one = value == .one
        if isUnilateral != one { isUnilateral = one }
    }

    /// La serie o il massimale con questo flag stanno sulla linea a un arto di questo macchinario?
    func isOneLimbLine(_ singleArm: Bool) -> Bool { Limbs.lineArm(limbs, singleArm) }
}

extension WorkoutSet {
    /// Sulla linea a un arto, secondo il macchinario. Senza macchinario (eliminato) mai.
    var isOneLimbLine: Bool { exercise?.isOneLimbLine(singleArm) ?? false }
}

extension MaxRecord {
    var isOneLimbLine: Bool { exercise?.isOneLimbLine(singleArm) ?? false }
}
