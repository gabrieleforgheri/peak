import SwiftUI

// MARK: - Comandi (pagina a sinistra)

/// Tasti rotondi come nell'app Allenamento: Termina in rosso, Pausa in giallo, Nuova serie in verde.
struct ControlsPage: View {
    @Environment(WatchModel.self) private var model
    @State private var confirmingEnd = false

    var body: some View {
        let paused = model.workout.isPaused
        VStack(spacing: 8) {
            HStack(spacing: 14) {
                RoundButton(title: w("Termina", "End"), symbol: "xmark", color: .red) {
                    confirmingEnd = true
                }
                RoundButton(title: paused ? w("Riprendi", "Resume") : w("Pausa", "Pause"),
                            symbol: paused ? "play.fill" : "pause.fill", color: .yellow) {
                    model.workout.togglePause()
                }
            }
            RoundButton(title: w("Nuova serie", "New set"), symbol: "plus", color: .green) {
                model.page = .log
            }
        }
        .padding(.bottom, 10)
        // come Apple: Termina chiede conferma, per non chiudere con un tocco sbagliato
        .confirmationDialog(w("Terminare l'allenamento?", "End workout?"), isPresented: $confirmingEnd) {
            Button(w("Termina", "End"), role: .destructive) { model.endWorkout() }
            Button(w("Annulla", "Cancel"), role: .cancel) {}
        }
    }
}

private struct RoundButton: View {
    let title: String
    let symbol: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Image(systemName: symbol)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(color)
                    .frame(width: 46, height: 46)
                    .background(color.opacity(0.25), in: Circle())
                Text(title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

// MARK: - Numeri (pagina centrale)

/// Tempo in giallo, calorie, battito col cuore che pulsa, esercizio e serie fatte.
struct MetricsPage: View {
    @Environment(WatchModel.self) private var model
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    var body: some View {
        let workout = model.workout
        VStack(alignment: .leading, spacing: 4) {
            TimelineView(MetricsSchedule(from: workout.startDate, paused: workout.isPaused)) { timeline in
                Text(elapsedText(workout.elapsed(at: timeline.date), centiseconds: !isLuminanceReduced && !workout.isPaused))
                    .font(.system(size: 36, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Color.workoutTime)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
            }
            if workout.isPaused {
                Label(w("In pausa", "Paused"), systemImage: "pause.fill")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.orange)
            }

            metric(value: workout.activeKcal.formatted(.number.precision(.fractionLength(0))), unit: "kcal",
                   color: .workoutEnergy,
                   detail: w("tot \(Int(workout.totalKcal.rounded()))", "total \(Int(workout.totalKcal.rounded()))"))

            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(workout.heartRate.map { String(Int($0.rounded())) } ?? "--")
                    .font(.system(size: 28, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                Text("bpm").font(.caption.weight(.semibold)).foregroundStyle(Color.workoutHeart)
                Image(systemName: "heart.fill")
                    .font(.caption)
                    .foregroundStyle(Color.workoutHeart)
                    .symbolEffect(.pulse, isActive: workout.heartRate != nil && !workout.isPaused && !isLuminanceReduced)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }

            if let exercise = model.exercise {
                VStack(alignment: .leading, spacing: 0) {
                    Text(exercise.name).font(.footnote.weight(.semibold)).lineLimit(1)
                    if let target = exercise.target {
                        Text(target).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
            }
            Label(w("\(model.setsLogged) serie", "\(model.setsLogged) sets"), systemImage: "checkmark.circle.fill")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 6)
        .padding(.bottom, 10)
    }

    private func metric(value: String, unit: String, color: Color, detail: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(value)
                .font(.system(size: 28, weight: .semibold, design: .rounded))
                .monospacedDigit()
            Text(unit).font(.caption.weight(.semibold)).foregroundStyle(color)
            Text(detail).font(.caption2).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
    }
}

/// Aggiornamento del cronometro: 30 volte al secondo a schermo acceso (centesimi), una volta a schermo
/// attenuato o in pausa. È lo schema dell'esempio di Apple.
private struct MetricsSchedule: TimelineSchedule {
    var start: Date
    var paused: Bool

    init(from start: Date, paused: Bool) {
        self.start = start
        self.paused = paused
    }

    func entries(from startDate: Date, mode: TimelineScheduleMode) -> PeriodicTimelineSchedule.Entries {
        PeriodicTimelineSchedule(from: start, by: mode == .lowFrequency || paused ? 1 : 1.0 / 30.0)
            .entries(from: startDate, mode: mode)
    }
}
