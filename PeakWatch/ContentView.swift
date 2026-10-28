import SwiftUI

struct ContentView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        if let exercise = model.exercise {
            ExerciseView(exercise: exercise)
        } else {
            VStack(spacing: 8) {
                Image(systemName: "iphone")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                Text("Apri un esercizio nella sessione sull'iPhone.")
                    .font(.footnote)
                    .multilineTextAlignment(.center)
            }
            .padding()
        }
    }
}

private struct ExerciseView: View {
    @Environment(WatchModel.self) private var model
    let exercise: WatchExercise

    var body: some View {
        @Bindable var model = model
        // una sola schermata, senza scorrere: peso, ripetizioni, riscaldamento e salva
        VStack(spacing: 6) {
            stepper(model.displayWeight(model.weight), size: 22,
                    minus: { model.weightSteps = max(model.weightSteps - 1, model.weightStepRange.lowerBound) },
                    plus: { model.weightSteps = min(model.weightSteps + 1, model.weightStepRange.upperBound) })
            stepper("\(model.reps)", size: 34,
                    minus: { if model.reps > 0 { model.reps -= 1 } },
                    plus: { model.reps += 1 })

            Toggle("Riscaldamento", isOn: $model.isWarmup)
                .font(.footnote)

            Button("Salva") { model.send() }
                .tint(.green)
                .disabled(model.reps == 0)
        }
        .padding(.horizontal, 4)
    }

    /// Valore al centro, − e + ai lati.
    private func stepper(_ value: String, size: CGFloat, minus: @escaping () -> Void, plus: @escaping () -> Void) -> some View {
        HStack {
            Button(action: minus) { Image(systemName: "minus") }
            Text(value)
                .font(.system(size: size, weight: .bold, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity)
            Button(action: plus) { Image(systemName: "plus") }
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
    }
}
