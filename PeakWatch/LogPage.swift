import SwiftUI

/// Pagina della serie: peso (con la Digital Crown), ripetizioni, riscaldamento, salva.
/// Dopo il salvataggio lascia il posto all'anello di recupero.
struct LogPage: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        if let exercise = model.exercise {
            if let rest = model.rest {
                RestView(rest: rest)
            } else {
                SetEntry(exercise: exercise)
            }
        } else {
            VStack(spacing: 8) {
                Image(systemName: "iphone")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                Text(model.plan.isEmpty
                     ? w("Aggiungi un esercizio nella sessione sull'iPhone.", "Add an exercise in the session on your iPhone.")
                     : w("Scegli un esercizio nella pagina Esercizi.", "Pick an exercise on the Exercises page."))
                    .font(.footnote)
                    .multilineTextAlignment(.center)
            }
            .padding()
        }
    }
}

private struct SetEntry: View {
    @Environment(WatchModel.self) private var model
    let exercise: WatchExercise

    var body: some View {
        @Bindable var model = model
        // una sola schermata, senza scorrere: nome, peso, ripetizioni, riscaldamento e salva
        VStack(spacing: 4) {
            VStack(spacing: 0) {
                Text(exercise.name)
                    .font(.footnote.weight(.semibold))
                    .lineLimit(1)
                if let target = exercise.target {
                    Text(target)
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }

            stepper(model.displayWeight(model.weight), size: 20,
                    minus: { model.weightSteps = max(model.weightSteps - 1, model.weightStepRange.lowerBound) },
                    plus: { model.weightSteps = min(model.weightSteps + 1, model.weightStepRange.upperBound) })
            stepper("\(model.reps)", size: 30,
                    minus: { if model.reps > 0 { model.reps -= 1 } },
                    plus: { model.reps += 1 })

            // riscaldamento e salva sulla stessa riga: sul 41 mm lo spazio è poco
            HStack(spacing: 4) {
                Toggle(isOn: $model.isWarmup) {
                    Image(systemName: "flame.fill")
                }
                .toggleStyle(.button)
                .tint(.orange)
                .frame(width: 44)
                .accessibilityLabel(w("Riscaldamento", "Warm-up"))

                Button(w("Salva", "Save")) { model.send() }
                    .tint(.green)
                    .disabled(model.reps == 0)
            }
        }
        .padding(.horizontal, 4)
        .padding(.bottom, 10)   // sotto i puntini delle pagine
        // la corona muove il peso sulla griglia del macchinario
        .digitalCrownRotation($model.weightSteps,
                              from: model.weightStepRange.lowerBound,
                              through: model.weightStepRange.upperBound,
                              by: 1, sensitivity: .low, isContinuous: false, isHapticFeedbackEnabled: true)
    }

    /// Valore al centro, − e + ai lati.
    private func stepper(_ value: String, size: CGFloat, minus: @escaping () -> Void, plus: @escaping () -> Void) -> some View {
        HStack {
            Button(action: minus) { Image(systemName: "minus") }
            Text(value)
                .font(.system(size: size, weight: .bold, design: .rounded))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .frame(maxWidth: .infinity)
            Button(action: plus) { Image(systemName: "plus") }
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
    }
}

/// Anello che si svuota fino alla fine del recupero; «Salta» lo chiude prima.
private struct RestView: View {
    @Environment(WatchModel.self) private var model
    let rest: WatchModel.Rest

    var body: some View {
        GeometryReader { geometry in
            let side = min(geometry.size.width, geometry.size.height) - 18
            ZStack {
                TimelineView(.periodic(from: .now, by: 0.2)) { timeline in
                    let left = max(0, rest.end.timeIntervalSince(timeline.date))
                    Circle()
                        .trim(from: 0, to: rest.total > 0 ? left / rest.total : 0)
                        .stroke(Color.workoutRest, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .animation(.linear(duration: 0.2), value: left)
                }
                .background(Circle().stroke(Color.workoutRest.opacity(0.25), lineWidth: 10))
                .frame(width: side, height: side)

                VStack(spacing: 2) {
                    Text(w("Recupero", "Rest"))
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Color.workoutRest)
                    Text(timerInterval: Date.now...max(rest.end, Date.now), countsDown: true)
                        .font(.system(size: 38, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                    Button { model.skipRest() } label: {
                        Label(w("Salta", "Skip"), systemImage: "forward.end.fill")
                            .font(.caption2.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.mini)
                    .tint(.workoutRest)
                }
                .frame(width: side - 36)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}
