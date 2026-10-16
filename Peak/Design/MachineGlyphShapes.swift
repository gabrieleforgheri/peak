// Generato da scripts/gen-glyphs.ts a partire da common/glyph.ts: non modificarlo a mano.

import CoreGraphics

/// Pittogrammi degli attrezzi, uno per tipo di attrezzo del catalogo, più quelli generici di prima.
enum MachineGlyph: String, Codable, CaseIterable, Identifiable {
    case seatedPush
    case pecDeck
    case latPulldown
    case seatedRow
    case cableStation
    case cableCross
    case benchFlat
    case benchIncline
    case barbell
    case dumbbell
    case plateLoaded
    case pullUpBar
    case legPress
    case legLever
    case squatRack
    case smithMachine
    case hyperBench
    case calfPlatform
    case abBench
    case romanChair
    case bodyweight
    case machine
    case legCurlLying
    case legCurlSeated
    case hackSquat
    case squatMachine
    case hipThrust
    case gluteMachine
    case hipAdductor
    case calfSeated
    case ghd
    case kettlebell
    case trapBar
    case ezBar
    case landmine
    case preacherBench
    case dipStation
    case assistMachine
    case rowMachine
    case cableRope
    case cablePushdown
    case sled
    case dumbbellBench
    case inclineDumbbell
    case abMachine
    case deltMachine
    case floorMat
    case plate

    var id: String { rawValue }

    var title: String {
        switch self {
        case .seatedPush: return "Spinta da seduto"
        case .pecDeck: return "Butterfly"
        case .latPulldown: return "Lat machine"
        case .seatedRow: return "Rematore / pulley"
        case .cableStation: return "Cavi"
        case .cableCross: return "Croci ai cavi"
        case .benchFlat: return "Panca piana"
        case .benchIncline: return "Panca inclinata"
        case .barbell: return "Bilanciere"
        case .dumbbell: return "Manubri"
        case .plateLoaded: return "Plate loaded"
        case .pullUpBar: return "Sbarra / parallele"
        case .legPress: return "Pressa"
        case .legLever: return "Leva per gambe"
        case .squatRack: return "Rack"
        case .smithMachine: return "Multipower"
        case .hyperBench: return "Panca iperestensioni"
        case .calfPlatform: return "Pedana calf"
        case .abBench: return "Panca addominali"
        case .romanChair: return "Sedia romana"
        case .bodyweight: return "Corpo libero"
        case .machine: return "Macchina generica"
        case .legCurlLying: return "Leg curl sdraiato"
        case .legCurlSeated: return "Leg curl seduto"
        case .hackSquat: return "Hack squat"
        case .squatMachine: return "Squat a leva"
        case .hipThrust: return "Hip thrust"
        case .gluteMachine: return "Glute machine"
        case .hipAdductor: return "Adduttori / abduttori"
        case .calfSeated: return "Calf seduto"
        case .ghd: return "Glute ham"
        case .kettlebell: return "Kettlebell"
        case .trapBar: return "Trap bar"
        case .ezBar: return "Bilanciere EZ"
        case .landmine: return "Landmine"
        case .preacherBench: return "Panca Scott"
        case .dipStation: return "Parallele"
        case .assistMachine: return "Trazioni assistite"
        case .rowMachine: return "Rematore machine"
        case .cableRope: return "Cavo con corda"
        case .cablePushdown: return "Cavo alto con barra"
        case .sled: return "Slitta"
        case .dumbbellBench: return "Panca e manubri"
        case .inclineDumbbell: return "Panca inclinata e manubri"
        case .abMachine: return "Macchina addominali"
        case .deltMachine: return "Alzate laterali machine"
        case .floorMat: return "A terra"
        case .plate: return "Disco"
        }
    }

    var shapes: [GlyphShape] {
        switch self {
        case .seatedPush: return [
            .solidRect(0.06, 0.26, 0.14, 0.54),
            .line(0.06, 0.395, 0.2, 0.395),
            .line(0.06, 0.53, 0.2, 0.53),
            .line(0.06, 0.665, 0.2, 0.665),
            .line(0.28, 0.14, 0.28, 0.88),
            .solidRect(0.37, 0.3, 0.075, 0.32),
            .solidRect(0.37, 0.62, 0.26, 0.07),
            .line(0.46, 0.3, 0.76, 0.3),
            .line(0.46, 0.44, 0.76, 0.44),
            .dot(0.8, 0.3, 0.055),
            .dot(0.8, 0.44, 0.055),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .pecDeck: return [
            .solidRect(0.06, 0.3, 0.13, 0.5),
            .line(0.06, 0.425, 0.19, 0.425),
            .line(0.06, 0.55, 0.19, 0.55),
            .line(0.06, 0.675, 0.19, 0.675),
            .line(0.3, 0.16, 0.3, 0.88),
            .solidRect(0.38, 0.6, 0.24, 0.07),
            .line(0.44, 0.24, 0.74, 0.34),
            .line(0.44, 0.52, 0.74, 0.42),
            .dot(0.78, 0.34, 0.05),
            .dot(0.78, 0.42, 0.05),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .latPulldown: return [
            .solidRect(0.06, 0.32, 0.12, 0.48),
            .line(0.06, 0.44, 0.18, 0.44),
            .line(0.06, 0.56, 0.18, 0.56),
            .line(0.06, 0.68, 0.18, 0.68),
            .line(0.24, 0.12, 0.24, 0.88),
            .line(0.24, 0.13, 0.68, 0.13),
            .ring(0.64, 0.19, 0.05),
            .line(0.64, 0.24, 0.64, 0.44),
            .bar(0.38, 0.46, 0.92, 0.46),
            .solidRect(0.46, 0.7, 0.26, 0.07),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .seatedRow: return [
            .solidRect(0.06, 0.3, 0.12, 0.5),
            .line(0.06, 0.425, 0.18, 0.425),
            .line(0.06, 0.55, 0.18, 0.55),
            .line(0.06, 0.675, 0.18, 0.675),
            .line(0.24, 0.16, 0.24, 0.88),
            .line(0.24, 0.52, 0.64, 0.52),
            .dot(0.68, 0.52, 0.055),
            .solidRect(0.36, 0.64, 0.26, 0.07),
            .bar(0.84, 0.52, 0.84, 0.8),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .cableStation: return [
            .solidRect(0.14, 0.12, 0.1, 0.76),
            .ring(0.19, 0.22, 0.045),
            .line(0.19, 0.265, 0.19, 0.38),
            .line(0.19, 0.38, 0.62, 0.46),
            .dot(0.66, 0.47, 0.055),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .cableCross: return [
            .solidRect(0.07, 0.14, 0.09, 0.74),
            .solidRect(0.84, 0.14, 0.09, 0.74),
            .ring(0.115, 0.24, 0.04),
            .ring(0.885, 0.24, 0.04),
            .line(0.115, 0.28, 0.5, 0.54),
            .line(0.885, 0.28, 0.5, 0.54),
            .dot(0.5, 0.57, 0.055),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .benchFlat: return [
            .bar(0.12, 0.32, 0.88, 0.32),
            .dot(0.17, 0.32, 0.085),
            .dot(0.83, 0.32, 0.085),
            .solidRect(0.24, 0.54, 0.52, 0.09),
            .line(0.32, 0.63, 0.32, 0.86),
            .line(0.68, 0.63, 0.68, 0.86)
        ]
        case .benchIncline: return [
            .bar(0.12, 0.26, 0.88, 0.26),
            .dot(0.17, 0.26, 0.075),
            .dot(0.83, 0.26, 0.075),
            .bar(0.26, 0.74, 0.66, 0.46),
            .line(0.3, 0.74, 0.3, 0.87),
            .line(0.63, 0.5, 0.63, 0.87),
            .solidRect(0.2, 0.72, 0.16, 0.07)
        ]
        case .barbell: return [
            .bar(0.08, 0.5, 0.92, 0.5),
            .dot(0.2, 0.5, 0.145),
            .dot(0.8, 0.5, 0.145),
            .dot(0.34, 0.5, 0.085),
            .dot(0.66, 0.5, 0.085)
        ]
        case .dumbbell: return [
            .bar(0.33, 0.5, 0.67, 0.5),
            .solidRect(0.14, 0.33, 0.14, 0.34),
            .solidRect(0.72, 0.33, 0.14, 0.34)
        ]
        case .plateLoaded: return [
            .line(0.22, 0.14, 0.22, 0.88),
            .solidRect(0.28, 0.58, 0.24, 0.07),
            .solidRect(0.28, 0.3, 0.07, 0.28),
            .line(0.38, 0.4, 0.64, 0.4),
            .ring(0.76, 0.5, 0.155),
            .ring(0.76, 0.5, 0.05),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .pullUpBar: return [
            .line(0.14, 0.18, 0.86, 0.18),
            .line(0.16, 0.18, 0.16, 0.88),
            .line(0.84, 0.18, 0.84, 0.88),
            .dot(0.38, 0.18, 0.05),
            .dot(0.62, 0.18, 0.05),
            .line(0.38, 0.22, 0.5, 0.4),
            .line(0.62, 0.22, 0.5, 0.4),
            .dot(0.5, 0.46, 0.075),
            .line(0.5, 0.53, 0.5, 0.74)
        ]
        case .legPress: return [
            .bar(0.2, 0.78, 0.72, 0.34),
            .bar(0.64, 0.2, 0.86, 0.44),
            .solidRect(0.1, 0.62, 0.22, 0.08),
            .ring(0.48, 0.58, 0.09),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .legLever: return [
            .solidRect(0.2, 0.5, 0.3, 0.08),
            .solidRect(0.16, 0.24, 0.08, 0.26),
            .line(0.5, 0.58, 0.78, 0.42),
            .dot(0.83, 0.39, 0.075),
            .line(0.34, 0.58, 0.34, 0.88),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .squatRack: return [
            .bar(0.12, 0.3, 0.88, 0.3),
            .dot(0.17, 0.3, 0.09),
            .dot(0.83, 0.3, 0.09),
            .line(0.22, 0.14, 0.22, 0.88),
            .line(0.78, 0.14, 0.78, 0.88),
            .line(0.22, 0.38, 0.3, 0.33),
            .line(0.78, 0.38, 0.7, 0.33),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .smithMachine: return [
            .line(0.26, 0.1, 0.26, 0.88),
            .line(0.74, 0.1, 0.74, 0.88),
            .solidRect(0.22, 0.4, 0.08, 0.09),
            .solidRect(0.7, 0.4, 0.08, 0.09),
            .bar(0.1, 0.445, 0.9, 0.445),
            .dot(0.15, 0.445, 0.08),
            .dot(0.85, 0.445, 0.08),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .hyperBench: return [
            .bar(0.24, 0.68, 0.62, 0.32),
            .line(0.44, 0.52, 0.44, 0.86),
            .dot(0.72, 0.68, 0.06),
            .dot(0.82, 0.68, 0.06),
            .line(0.66, 0.68, 0.66, 0.86),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .calfPlatform: return [
            .solidRect(0.74, 0.36, 0.12, 0.34),
            .line(0.74, 0.445, 0.86, 0.445),
            .line(0.74, 0.53, 0.86, 0.53),
            .line(0.74, 0.615, 0.86, 0.615),
            .solidRect(0.18, 0.68, 0.5, 0.09),
            .line(0.28, 0.18, 0.28, 0.68),
            .solidRect(0.22, 0.22, 0.3, 0.08),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .abBench: return [
            .bar(0.2, 0.72, 0.68, 0.42),
            .line(0.28, 0.7, 0.28, 0.86),
            .line(0.62, 0.46, 0.62, 0.86),
            .dot(0.76, 0.38, 0.06),
            .dot(0.76, 0.52, 0.06),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .romanChair: return [
            .line(0.28, 0.12, 0.28, 0.88),
            .solidRect(0.28, 0.24, 0.07, 0.26),
            .solidRect(0.34, 0.34, 0.26, 0.07),
            .dot(0.5, 0.2, 0.075),
            .line(0.5, 0.44, 0.5, 0.62),
            .line(0.5, 0.62, 0.68, 0.6),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .bodyweight: return [
            .dot(0.5, 0.22, 0.095),
            .line(0.5, 0.32, 0.5, 0.58),
            .line(0.28, 0.42, 0.72, 0.42),
            .line(0.5, 0.58, 0.34, 0.8),
            .line(0.5, 0.58, 0.66, 0.8)
        ]
        case .machine: return [
            .solidRect(0.1, 0.26, 0.16, 0.56),
            .line(0.1, 0.4, 0.26, 0.4),
            .line(0.1, 0.54, 0.26, 0.54),
            .line(0.1, 0.68, 0.26, 0.68),
            .line(0.34, 0.14, 0.34, 0.88),
            .solidRect(0.42, 0.56, 0.28, 0.08),
            .solidRect(0.42, 0.3, 0.07, 0.26),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .legCurlLying: return [
            .solidRect(0.1, 0.46, 0.56, 0.08),
            .line(0.2, 0.54, 0.2, 0.88),
            .line(0.56, 0.54, 0.56, 0.88),
            .line(0.66, 0.5, 0.82, 0.3),
            .dot(0.84, 0.27, 0.07),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .legCurlSeated: return [
            .solidRect(0.16, 0.24, 0.08, 0.3),
            .solidRect(0.2, 0.54, 0.3, 0.08),
            .solidRect(0.3, 0.4, 0.2, 0.07),
            .line(0.5, 0.58, 0.8, 0.5),
            .dot(0.82, 0.58, 0.07),
            .line(0.34, 0.62, 0.34, 0.88),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .hackSquat: return [
            .line(0.16, 0.88, 0.58, 0.12),
            .bar(0.38, 0.66, 0.58, 0.32),
            .dot(0.64, 0.3, 0.06),
            .bar(0.36, 0.8, 0.68, 0.74),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .squatMachine: return [
            .line(0.18, 0.36, 0.18, 0.88),
            .bar(0.18, 0.36, 0.7, 0.28),
            .dot(0.46, 0.39, 0.055),
            .ring(0.78, 0.27, 0.11),
            .solidRect(0.3, 0.8, 0.42, 0.07),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .hipThrust: return [
            .solidRect(0.06, 0.42, 0.26, 0.1),
            .line(0.12, 0.52, 0.12, 0.88),
            .line(0.26, 0.52, 0.26, 0.88),
            .ring(0.62, 0.64, 0.22),
            .dot(0.62, 0.64, 0.06),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .gluteMachine: return [
            .solidRect(0.06, 0.3, 0.12, 0.5),
            .line(0.06, 0.425, 0.18, 0.425),
            .line(0.06, 0.55, 0.18, 0.55),
            .line(0.06, 0.675, 0.18, 0.675),
            .line(0.26, 0.14, 0.26, 0.88),
            .solidRect(0.32, 0.3, 0.22, 0.07),
            .line(0.42, 0.58, 0.78, 0.42),
            .bar(0.72, 0.32, 0.86, 0.54),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .hipAdductor: return [
            .solidRect(0.4, 0.16, 0.2, 0.32),
            .solidRect(0.28, 0.5, 0.44, 0.08),
            .bar(0.42, 0.6, 0.18, 0.78),
            .bar(0.58, 0.6, 0.82, 0.78),
            .line(0.5, 0.58, 0.5, 0.88),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .calfSeated: return [
            .solidRect(0.1, 0.56, 0.28, 0.08),
            .line(0.24, 0.64, 0.24, 0.88),
            .solidRect(0.36, 0.42, 0.22, 0.07),
            .line(0.47, 0.44, 0.76, 0.38),
            .ring(0.82, 0.37, 0.09),
            .solidRect(0.5, 0.78, 0.16, 0.07),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .ghd: return [
            .line(0.1, 0.74, 0.88, 0.74),
            .line(0.18, 0.74, 0.18, 0.88),
            .line(0.8, 0.74, 0.8, 0.88),
            .bar(0.2, 0.36, 0.2, 0.6),
            .line(0.2, 0.6, 0.2, 0.74),
            .dot(0.29, 0.42, 0.05),
            .dot(0.29, 0.56, 0.05),
            .solidRect(0.48, 0.46, 0.32, 0.16),
            .line(0.64, 0.62, 0.64, 0.74),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .kettlebell: return [
            .ring(0.5, 0.3, 0.15),
            .dot(0.5, 0.6, 0.25)
        ]
        case .trapBar: return [
            .bar(0.3, 0.5, 0.4, 0.32),
            .bar(0.4, 0.32, 0.6, 0.32),
            .bar(0.6, 0.32, 0.7, 0.5),
            .bar(0.7, 0.5, 0.6, 0.68),
            .bar(0.6, 0.68, 0.4, 0.68),
            .bar(0.4, 0.68, 0.3, 0.5),
            .bar(0.06, 0.5, 0.3, 0.5),
            .bar(0.7, 0.5, 0.94, 0.5),
            .solidRect(0.12, 0.32, 0.08, 0.36),
            .solidRect(0.8, 0.32, 0.08, 0.36),
            .line(0.42, 0.44, 0.42, 0.56),
            .line(0.58, 0.44, 0.58, 0.56)
        ]
        case .ezBar: return [
            .bar(0.06, 0.5, 0.3, 0.5),
            .bar(0.3, 0.5, 0.37, 0.4),
            .bar(0.37, 0.4, 0.44, 0.58),
            .bar(0.44, 0.58, 0.5, 0.48),
            .bar(0.5, 0.48, 0.56, 0.58),
            .bar(0.56, 0.58, 0.63, 0.4),
            .bar(0.63, 0.4, 0.7, 0.5),
            .bar(0.7, 0.5, 0.94, 0.5),
            .solidRect(0.1, 0.32, 0.08, 0.36),
            .solidRect(0.82, 0.32, 0.08, 0.36)
        ]
        case .landmine: return [
            .solidRect(0.06, 0.8, 0.16, 0.08),
            .bar(0.14, 0.82, 0.74, 0.3),
            .dot(0.78, 0.27, 0.13),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .preacherBench: return [
            .solidRect(0.1, 0.58, 0.24, 0.08),
            .line(0.22, 0.66, 0.22, 0.88),
            .bar(0.4, 0.32, 0.66, 0.54),
            .line(0.52, 0.44, 0.52, 0.88),
            .dot(0.75, 0.6, 0.06),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .dipStation: return [
            .line(0.24, 0.32, 0.24, 0.88),
            .line(0.76, 0.32, 0.76, 0.88),
            .bar(0.12, 0.32, 0.38, 0.32),
            .bar(0.62, 0.32, 0.88, 0.32),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .assistMachine: return [
            .solidRect(0.06, 0.34, 0.12, 0.46),
            .line(0.06, 0.455, 0.18, 0.455),
            .line(0.06, 0.57, 0.18, 0.57),
            .line(0.06, 0.685, 0.18, 0.685),
            .line(0.26, 0.1, 0.26, 0.88),
            .line(0.26, 0.12, 0.76, 0.12),
            .dot(0.76, 0.12, 0.05),
            .bar(0.5, 0.4, 0.78, 0.4),
            .solidRect(0.44, 0.62, 0.28, 0.07),
            .line(0.26, 0.66, 0.44, 0.66),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .rowMachine: return [
            .solidRect(0.06, 0.3, 0.12, 0.5),
            .line(0.06, 0.425, 0.18, 0.425),
            .line(0.06, 0.55, 0.18, 0.55),
            .line(0.06, 0.675, 0.18, 0.675),
            .line(0.24, 0.16, 0.24, 0.88),
            .line(0.24, 0.4, 0.4, 0.4),
            .dot(0.43, 0.4, 0.055),
            .solidRect(0.54, 0.28, 0.07, 0.3),
            .solidRect(0.54, 0.62, 0.3, 0.07),
            .line(0.68, 0.69, 0.68, 0.88),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .cableRope: return [
            .solidRect(0.1, 0.12, 0.1, 0.76),
            .line(0.15, 0.14, 0.6, 0.14),
            .ring(0.6, 0.2, 0.05),
            .line(0.6, 0.25, 0.6, 0.46),
            .line(0.6, 0.46, 0.5, 0.68),
            .line(0.6, 0.46, 0.7, 0.68),
            .dot(0.49, 0.71, 0.05),
            .dot(0.71, 0.71, 0.05),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .cablePushdown: return [
            .solidRect(0.1, 0.12, 0.1, 0.76),
            .line(0.15, 0.14, 0.6, 0.14),
            .ring(0.6, 0.2, 0.05),
            .line(0.6, 0.25, 0.6, 0.52),
            .bar(0.44, 0.54, 0.76, 0.54),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .sled: return [
            .bar(0.1, 0.82, 0.74, 0.82),
            .line(0.74, 0.82, 0.88, 0.7),
            .bar(0.24, 0.8, 0.14, 0.2),
            .line(0.58, 0.82, 0.58, 0.34),
            .solidRect(0.4, 0.6, 0.36, 0.09),
            .solidRect(0.4, 0.46, 0.36, 0.09)
        ]
        case .dumbbellBench: return [
            .solidRect(0.2, 0.58, 0.6, 0.09),
            .line(0.3, 0.67, 0.3, 0.88),
            .line(0.7, 0.67, 0.7, 0.88),
            .bar(0.4, 0.32, 0.6, 0.32),
            .solidRect(0.29, 0.21, 0.1, 0.22),
            .solidRect(0.61, 0.21, 0.1, 0.22)
        ]
        case .inclineDumbbell: return [
            .bar(0.24, 0.76, 0.6, 0.48),
            .line(0.28, 0.76, 0.28, 0.88),
            .line(0.56, 0.52, 0.56, 0.88),
            .solidRect(0.16, 0.74, 0.16, 0.07),
            .bar(0.62, 0.22, 0.8, 0.22),
            .solidRect(0.53, 0.12, 0.09, 0.2),
            .solidRect(0.8, 0.12, 0.09, 0.2)
        ]
        case .abMachine: return [
            .solidRect(0.06, 0.3, 0.12, 0.5),
            .line(0.06, 0.425, 0.18, 0.425),
            .line(0.06, 0.55, 0.18, 0.55),
            .line(0.06, 0.675, 0.18, 0.675),
            .line(0.26, 0.14, 0.26, 0.88),
            .solidRect(0.36, 0.62, 0.26, 0.07),
            .solidRect(0.36, 0.32, 0.07, 0.3),
            .line(0.4, 0.22, 0.7, 0.3),
            .line(0.7, 0.3, 0.7, 0.44),
            .dot(0.7, 0.47, 0.06),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .deltMachine: return [
            .solidRect(0.42, 0.2, 0.16, 0.34),
            .solidRect(0.32, 0.56, 0.36, 0.08),
            .line(0.5, 0.64, 0.5, 0.88),
            .bar(0.4, 0.36, 0.14, 0.26),
            .bar(0.6, 0.36, 0.86, 0.26),
            .dot(0.12, 0.25, 0.06),
            .dot(0.88, 0.25, 0.06),
            .line(0.08, 0.88, 0.92, 0.88)
        ]
        case .floorMat: return [
            .solidRect(0.06, 0.78, 0.88, 0.07),
            .dot(0.2, 0.44, 0.075),
            .line(0.28, 0.48, 0.84, 0.7),
            .line(0.3, 0.52, 0.3, 0.76)
        ]
        case .plate: return [
            .ring(0.5, 0.5, 0.36),
            .ring(0.5, 0.5, 0.2),
            .dot(0.5, 0.5, 0.07)
        ]
        }
    }
}
