import SwiftUI

/// La figura dei muscoli, come nelle foto dei macchinari: solo la zona che lavora, i muscoli colorati per gruppo.
/// Stessi disegni e stessa regola dello zoom del web (MuscleFigure.svelte, common/muscles.ts).
struct MuscleFigureView: View {
    let muscles: [MuscleId]
    /// Vista forzata; nil = quella che mostra più muscoli.
    var view: AnatomyView? = nil
    /// Solo la zona che lavora (come nelle foto), o tutto il corpo.
    var zoom: Bool = true
    /// Con un gesto, si toccano i muscoli (per il macchinario personalizzato).
    var onTap: ((MuscleId) -> Void)? = nil

    var body: some View {
        let side = view ?? anatomyView(for: muscles)
        let box = zoom ? AnatomyZoom.rect(for: muscles, view: side) : CGRect(origin: .zero, size: AnatomyShapes.size)
        let hot = Set(muscles)
        ZStack {
            ForEach(Array(AnatomyCache.pieces(side).enumerated()), id: \.offset) { _, piece in
                if let muscle = piece.muscle {
                    let on = hot.contains(muscle)
                    FigurePath(path: piece.path, box: box)
                        .fill(on ? muscle.group.tint : Color(red: 0.29, green: 0.31, blue: 0.35).opacity(0.55))
                        .overlay(FigurePath(path: piece.path, box: box).fill(sheen).allowsHitTesting(false))
                        .shadow(color: on ? muscle.group.tint.opacity(0.7) : .clear, radius: 9)
                        .overlay(FigurePath(path: piece.path, box: box).stroke(Color(red: 0.11, green: 0.12, blue: 0.14), lineWidth: 1))
                        .contentShape(FigurePath(path: piece.path, box: box))
                        .onTapGesture { onTap?(muscle) }
                } else {
                    FigurePath(path: piece.path, box: box)
                        .fill(skin)
                        .overlay(FigurePath(path: piece.path, box: box).fill(sheen).allowsHitTesting(false))
                        .allowsHitTesting(false)
                }
            }
        }
        .aspectRatio(box.width / box.height, contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(muscles.map(\.title).joined(separator: ", "))
    }

    private var skin: LinearGradient {
        LinearGradient(colors: [Color(red: 0.18, green: 0.2, blue: 0.23), Color(red: 0.49, green: 0.51, blue: 0.56),
                                Color(red: 0.7, green: 0.73, blue: 0.76), Color(red: 0.49, green: 0.51, blue: 0.56),
                                Color(red: 0.18, green: 0.2, blue: 0.23)], startPoint: .leading, endPoint: .trailing)
    }

    private var sheen: LinearGradient {
        LinearGradient(colors: [.white.opacity(0.3), .white.opacity(0.0), .black.opacity(0.25)], startPoint: .top, endPoint: .bottom)
    }
}

/// Un percorso già letto, scalato nel riquadro come fa `meet` nel web: il corpo, o la zona che lavora.
struct FigurePath: Shape {
    let path: Path
    let box: CGRect

    func path(in rect: CGRect) -> Path {
        let s = min(rect.width / box.width, rect.height / box.height)
        let t = CGAffineTransform(a: s, b: 0, c: 0, d: s, tx: rect.midX - box.midX * s, ty: rect.midY - box.midY * s)
        return path.applying(t)
    }
}

/// I percorsi del corpo letti una volta sola: il parsing dei numeri costa, e la figura compare in molte liste.
enum AnatomyCache {
    static let front: [(muscle: MuscleId?, path: Path)] = parse(AnatomyShapes.front)
    static let back: [(muscle: MuscleId?, path: Path)] = parse(AnatomyShapes.back)

    static func pieces(_ view: AnatomyView) -> [(muscle: MuscleId?, path: Path)] {
        view == .front ? front : back
    }

    private static func parse(_ pieces: [AnatomyPiece]) -> [(muscle: MuscleId?, path: Path)] {
        pieces.flatMap { piece in piece.paths.map { (muscle: piece.muscle, path: AnatomyShapes.path($0)) } }
    }
}

/// La vista che mostra più dei muscoli dati; a parità, quella di fronte. Come `viewFor` in common/muscles.ts.
func anatomyView(for muscles: [MuscleId]) -> AnatomyView {
    func count(_ view: AnatomyView) -> Int {
        let here = Set(AnatomyCache.pieces(view).compactMap(\.muscle))
        return muscles.filter { here.contains($0) }.count
    }
    return count(.front) >= count(.back) ? .front : .back
}

/// La figura di un macchinario in un riquadro, col colore del suo gruppo; niente se non ha muscoli.
struct ExerciseFigure: View {
    let exercise: Exercise
    var size: CGFloat = 44

    var body: some View {
        let muscles = exercise.figureMuscles
        if !muscles.isEmpty {
            MuscleFigureView(muscles: muscles, view: AnatomyView(rawValue: exercise.figureView))
                .padding(size * 0.08)
                .frame(width: size, height: size)
                .background(exercise.muscleGroup.tint.opacity(0.2), in: RoundedRectangle(cornerRadius: size * 0.3, style: .continuous))
        }
    }
}
