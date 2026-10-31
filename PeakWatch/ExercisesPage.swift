import SwiftUI

/// Gli esercizi di oggi nell'ordine del piano: nome, serie fatte (su quelle previste, se il piano le dice),
/// un segno quando l'obiettivo è raggiunto. Il tocco sceglie l'esercizio su entrambi i dispositivi e porta alla serie.
struct ExercisesPage: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        let current = model.pendingSelection ?? model.exercise?.uuid
        if model.plan.isEmpty {
            VStack(spacing: 8) {
                Image(systemName: "list.bullet")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                Text(w("Nessun esercizio. Aggiungine uno nella sessione sull'iPhone.",
                       "No exercises yet. Add one in the session on your iPhone."))
                    .font(.footnote)
                    .multilineTextAlignment(.center)
            }
            .padding()
        } else {
            List {
                ForEach(model.plan) { item in
                    Button { model.select(item) } label: { row(item, isCurrent: item.uuid == current) }
                        .listRowBackground(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(item.uuid == current ? Color.workoutEnergy.opacity(0.28) : Color.white.opacity(0.12))
                        )
                }
            }
            .listStyle(.carousel)
        }
    }

    private func row(_ item: WatchPlanItem, isCurrent: Bool) -> some View {
        HStack(spacing: 6) {
            VStack(alignment: .leading, spacing: 1) {
                Text(item.name)
                    .font(.footnote.weight(.semibold))
                    .lineLimit(2)
                Text(progress(item))
                    .font(.caption2)
                    .foregroundStyle(isCurrent ? Color.workoutEnergy : .secondary)
            }
            Spacer(minLength: 0)
            if item.reached {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Color.workoutEnergy)
                    .accessibilityLabel(w("Obiettivo raggiunto", "Target reached"))
            }
        }
    }

    /// «2/4 serie»; senza obiettivo nel piano solo il conteggio.
    private func progress(_ item: WatchPlanItem) -> String {
        item.target > 0
            ? w("\(item.done)/\(item.target) serie", "\(item.done)/\(item.target) sets")
            : w("\(item.done) serie", "\(item.done) sets")
    }
}
