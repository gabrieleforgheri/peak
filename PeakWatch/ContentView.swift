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
        ScrollView {
            VStack(spacing: 8) {
                Text(exercise.name)
                    .font(.headline)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)

                if let target = exercise.target {
                    Text(target)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }

                stepper(model.displayWeight(model.weight), size: 22,
                        minus: { model.weightSteps = max(model.weightSteps - 1, model.weightStepRange.lowerBound) },
                        plus: { model.weightSteps = min(model.weightSteps + 1, model.weightStepRange.upperBound) })
                stepper("\(model.reps)", size: 40,
                        minus: { if model.reps > 0 { model.reps -= 1 } },
                        plus: { model.reps += 1 })

                Toggle("Riscaldamento", isOn: $model.isWarmup)
                    .font(.footnote)

                Button("Registra") { model.send() }
                    .tint(.green)
                    .disabled(model.reps == 0)

                if let last = model.lastSent {
                    Text("Registrata: \(model.displayWeight(last.weight)) × \(last.reps)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Peak")
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
    }
}
