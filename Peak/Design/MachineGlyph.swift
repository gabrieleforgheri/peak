import SwiftUI

/// Un tratto del disegno, in coordinate normalizzate 0…1 (y verso il basso).
/// Tenere le forme come dati invece che come `Path` scritti a mano rende i
/// pittogrammi compatti da definire e uniformi da disegnare.
enum GlyphShape {
    /// Linea sottile: telai, cavi, basamenti.
    case line(CGFloat, CGFloat, CGFloat, CGFloat)
    /// Linea spessa: bilancieri, pedane, schienali inclinati.
    case bar(CGFloat, CGFloat, CGFloat, CGFloat)
    /// Rettangolo pieno con angoli morbidi: sedute, pacchi pesi, imbottiture.
    case solidRect(CGFloat, CGFloat, CGFloat, CGFloat)
    /// Cerchio pieno: impugnature, rulli, teste.
    case dot(CGFloat, CGFloat, CGFloat)
    /// Cerchio vuoto: dischi, pulegge.
    case ring(CGFloat, CGFloat, CGFloat)
}

// `MachineGlyph` (casi, titoli e forme) sta in MachineGlyphShapes.swift, generato dalle forme del web.

// MARK: - Riconoscimento dal nome

extension MachineGlyph {

    /// Coppie (frammento di nome, pittogramma) valutate **in ordine**: le voci
    /// più specifiche stanno prima, così "Rematore Bilanciere" non finisce sul
    /// pittogramma generico del rematore.
    private static let keywords: [(String, MachineGlyph)] = [
        // Gambe
        ("leg press", .legPress), ("pressa", .legPress), ("hack squat", .legPress),
        ("leg extension", .legLever), ("leg curl", .legLever),
        ("calf", .calfPlatform),
        ("adductor", .machine), ("abductor", .machine), ("glute machine", .machine),
        ("multipower", .smithMachine),
        ("squat", .squatRack), ("hip thrust", .barbell), ("step up", .dumbbell),
        ("affondi", .dumbbell), ("bulgarian", .dumbbell),

        // Schiena
        ("lat machine", .latPulldown),
        ("pulley", .seatedRow),
        ("rematore bilanciere", .barbell), ("rematore con manubrio", .dumbbell),
        ("rematore ai cavi", .cableStation), ("rematore t-bar", .barbell),
        ("rematore", .seatedRow),
        ("trazioni", .pullUpBar), ("dip", .pullUpBar),
        ("stacco", .barbell), ("good morning", .barbell),
        ("iperestensioni", .hyperBench),
        ("pullover machine", .machine),

        // Petto e spalle
        ("plate loaded", .plateLoaded),
        ("pectoral", .pecDeck), ("butterfly", .pecDeck),
        ("croci ai cavi", .cableCross),
        ("panca piana", .benchFlat), ("panca stretta", .benchFlat),
        ("panca inclinata", .benchIncline), ("panca declinata", .benchIncline),
        ("panca scott", .benchIncline),
        ("panca addominali", .abBench),
        ("chest press", .seatedPush), ("shoulder press", .seatedPush),
        ("lento avanti", .barbell),

        // Core
        ("sedia romana", .romanChair), ("leg raise", .romanChair),
        ("crunch machine", .machine), ("rotary", .machine),
        ("plank", .bodyweight), ("piegamenti", .bodyweight),
        ("russian", .bodyweight), ("ab wheel", .bodyweight),
        ("crunch a terra", .bodyweight),

        // Generici, per ultimi
        ("cavi", .cableStation), ("cable", .cableStation),
        ("pushdown", .cableStation), ("face pull", .cableStation),
        ("kickback", .cableStation),
        ("machine", .machine),
        ("manubri", .dumbbell), ("manubrio", .dumbbell),
        ("bilanciere", .barbell), ("sbarra", .pullUpBar),
        ("alzate", .dumbbell), ("scrollate", .dumbbell), ("curl", .barbell),
        ("press", .seatedPush)
    ]

    /// Pittogramma dedotto dal nome dell'esercizio; se non riconosce nulla
    /// ripiega su un default sensato per il gruppo muscolare. Non si mostra più:
    /// serve al calcolatore dischi e a fissare, una volta, l'icona dei macchinari
    /// personalizzati di prima del catalogo (per questo le parole chiave restano
    /// quelle di allora). Come `suggestedGlyph` in common/glyph.ts.
    static func suggested(for name: String, group: MuscleGroup) -> MachineGlyph {
        let needle = name.lowercased()
        for (key, glyph) in keywords where needle.contains(key) {
            return glyph
        }
        switch group {
        case .petto, .spalle: return .seatedPush
        case .schiena: return .seatedRow
        case .gambe: return .legLever
        case .braccia: return .dumbbell
        case .core: return .bodyweight
        }
    }
}

// MARK: - Disegno

struct MachineGlyphView: View {
    let glyph: MachineGlyph
    var tint: Color = .white
    /// Lato del quadrato di disegno.
    var size: CGFloat = 26

    var body: some View {
        Canvas { context, canvasSize in
            let s = min(canvasSize.width, canvasSize.height)
            let thin = max(1, s * 0.055)
            let thick = max(1.4, s * 0.10)

            func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: x * s, y: y * s)
            }

            for shape in glyph.shapes {
                switch shape {
                case let .line(x1, y1, x2, y2):
                    var path = Path()
                    path.move(to: point(x1, y1))
                    path.addLine(to: point(x2, y2))
                    context.stroke(path, with: .color(tint), style: StrokeStyle(lineWidth: thin, lineCap: .round))

                case let .bar(x1, y1, x2, y2):
                    var path = Path()
                    path.move(to: point(x1, y1))
                    path.addLine(to: point(x2, y2))
                    context.stroke(path, with: .color(tint), style: StrokeStyle(lineWidth: thick, lineCap: .round))

                case let .solidRect(x, y, w, h):
                    let rect = CGRect(x: x * s, y: y * s, width: w * s, height: h * s)
                    let radius = min(rect.width, rect.height) * 0.35
                    context.fill(Path(roundedRect: rect, cornerRadius: radius), with: .color(tint))

                case let .dot(cx, cy, r):
                    let rect = CGRect(x: (cx - r) * s, y: (cy - r) * s, width: 2 * r * s, height: 2 * r * s)
                    context.fill(Path(ellipseIn: rect), with: .color(tint))

                case let .ring(cx, cy, r):
                    let rect = CGRect(x: (cx - r) * s, y: (cy - r) * s, width: 2 * r * s, height: 2 * r * s)
                    context.stroke(Path(ellipseIn: rect), with: .color(tint), lineWidth: thin)
                }
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
