import SwiftUI

/// Riepilogo a fine allenamento, come quello di Apple: tempo, calorie, battito medio, serie e volume, poi «Fine».
/// Compatto per il 41 mm: calorie e serie/volume stanno ciascuno su una riga.
struct SummaryView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        let summary = model.workout.summary
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text(w("Riepilogo", "Summary"))
                    .font(.headline)
                    .foregroundStyle(Color.workoutEnergy)

                row(w("Tempo totale", "Total time"), elapsedText(summary?.duration ?? 0, centiseconds: false), .workoutTime)
                row(w("Calorie attive · totali", "Active · total energy"),
                    "\(Int((summary?.activeKcal ?? 0).rounded())) · \(Int((summary?.totalKcal ?? 0).rounded())) kcal", .workoutEnergy)
                row(w("Battito medio", "Avg. heart rate"),
                    summary?.averageHeartRate.map { "\(Int($0.rounded())) bpm" } ?? "--", .workoutHeart)
                row(w("Serie · volume", "Sets · volume"),
                    model.summaryVolume.map { "\(model.summarySets) · \($0)" } ?? "\(model.summarySets)", .white)

                Button(w("Fine", "Done")) { model.finishSummary() }
                    .tint(.workoutTime)
            }
            .padding(.horizontal, 6)
        }
        // su 41 mm le righe non stanno tutte: la barra di scorrimento dice che c'è altro e «Fine» si raggiunge con la ghiera
        .scrollIndicators(.visible)
    }

    private func row(_ title: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.caption2.weight(.semibold)).foregroundStyle(color)
            Text(value)
                .font(.system(size: 20, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
    }
}
