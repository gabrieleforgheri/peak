import SwiftUI

/// Sfondo della card esercizio (foto macchinario sfocata o glifo sfocato + scrim gradiente scuro).
/// Usato nella sessione di allenamento, nella card del massimale/dettaglio e nelle righe della scheda.
struct ExerciseCardBackground: View {
    let exercise: Exercise

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottomTrailing) {
                if let data = exercise.photo, let uiImage = UIImage(data: data) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geo.size.width + 30, height: geo.size.height + 30)
                        .clipped()
                        .blur(radius: 10)
                        .brightness(-0.15)
                        .opacity(0.65)
                        .scaleEffect(1.08)
                        .position(x: geo.size.width / 2, y: geo.size.height / 2)
                } else if let glyph = exercise.glyph {
                    MachineGlyphView(glyph: glyph, tint: exercise.muscleGroup.tint, size: 210)
                        .opacity(0.28)
                        .blur(radius: 1.5)
                        .offset(x: 20, y: 15)
                }

                LinearGradient(
                    stops: [
                        .init(color: Color(red: 0.06, green: 0.07, blue: 0.09).opacity(0.90), location: 0.0),
                        .init(color: Color(red: 0.06, green: 0.07, blue: 0.09).opacity(0.65), location: 0.55),
                        .init(color: Color(red: 0.05, green: 0.06, blue: 0.08).opacity(0.40), location: 1.0)
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
        }
        .allowsHitTesting(false)
    }
}
