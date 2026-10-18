import SwiftUI
import SwiftData

/// Riepilogo di fine sessione, con i record ottenuti.
struct WorkoutSummaryView: View {
    /// Una vista per volta della figura dei muscoli della seduta.
    @State private var figureSide: AnatomyView = .front

    /// I muscoli di tutti gli esercizi della seduta, senza doppioni.
    private var workoutMuscles: [MuscleId] {
        var seen = Set<MuscleId>()
        var out: [MuscleId] = []
        for exercise in workout.exercisesInOrder {
            for muscle in exercise.figureMuscles where !seen.contains(muscle) {
                seen.insert(muscle)
                out.append(muscle)
            }
        }
        return out
    }
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    @Query private var exercises: [Exercise]
    @State private var showingSaveTemplate = false

    let workout: Workout
    let onDone: () -> Void

    private var records: [WorkoutSet] { RecordService.personalRecords(in: workout) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 6) {
                        Image(systemName: records.isEmpty ? "checkmark.circle.fill" : "trophy.fill")
                            .font(.system(size: 44, weight: .semibold))
                            .foregroundStyle(records.isEmpty ? settings.accentColor : Theme.positive)
                            .padding(.top, 12)
                        Text(records.isEmpty ? "Allenamento concluso" : "Nuovi massimali!")
                            .font(Theme.rounded(24, .bold))
                            .foregroundStyle(Theme.textPrimary)
                        Text(Fmt.dateTime(workout.date))
                            .font(Theme.rounded(12, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                    .padding(.bottom, 4)

                    HStack(spacing: 10) {
                        StatTile(
                            symbol: "clock",
                            title: "Durata",
                            value: workout.duration.map { Fmt.duration($0) } ?? "—",
                            tint: settings.accentColor
                        )
                        StatTile(
                            symbol: "scalemass",
                            title: "Volume",
                            value: Fmt.volume(workout.totalVolume, unit: settings.unit),
                            tint: settings.accentColor
                        )
                    }

                    HStack(spacing: 10) {
                        StatTile(
                            symbol: "list.number",
                            title: "Serie",
                            value: "\(workout.workingSetCount)",
                            tint: settings.accentColor
                        )
                        StatTile(
                            symbol: "dumbbell",
                            title: "Esercizi",
                            value: "\(workout.exercisesInOrder.count)",
                            tint: settings.accentColor
                        )
                    }

                    if !workoutMuscles.isEmpty {
                        GlassCard {
                            VStack(spacing: 12) {
                                SectionHeader(title: "Muscoli lavorati")
                                MuscleFigureView(muscles: workoutMuscles, view: figureSide, zoom: false)
                                    .frame(maxWidth: 220)
                                Button(figureSide == .front ? "Vedi di schiena" : "Vedi di fronte") {
                                    figureSide = figureSide == .front ? .back : .front
                                }
                                .buttonStyle(.bordered)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }

                    if !records.isEmpty {
                        GlassCard(tint: Theme.positive) {
                            VStack(alignment: .leading, spacing: 12) {
                                SectionHeader(title: "Record della giornata", trailing: "\(records.count)")
                                ForEach(records) { set in
                                    HStack(spacing: 10) {
                                        Image(systemName: set.exercise?.muscleGroup.symbol ?? "dumbbell")
                                            .font(.system(size: 12, weight: .semibold))
                                            .foregroundStyle(Theme.positive)
                                            .frame(width: 28, height: 28)
                                            .background(Theme.positive.opacity(0.14), in: RoundedRectangle(cornerRadius: 9, style: .continuous))

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(set.exercise?.name ?? "Esercizio rimosso")
                                                .font(Theme.rounded(14, .semibold))
                                                .foregroundStyle(Theme.textPrimary)
                                            Text(Fmt.setLine(weight: set.weight, reps: set.reps, unit: settings.unit))
                                                .font(Theme.rounded(11, .medium))
                                                .foregroundStyle(Theme.textTertiary)
                                        }

                                        Spacer()

                                        Text(Fmt.weightValue(set.weight, unit: settings.unit))
                                            .font(Theme.rounded(15, .bold))
                                            .foregroundStyle(Theme.positive)
                                    }
                                }
                            }
                        }
                    }

                    if !workout.setList.isEmpty {
                        Button {
                            showingSaveTemplate = true
                        } label: {
                            Label("Salva come scheda", systemImage: "list.bullet")
                        }
                        .buttonStyle(SecondaryButtonStyle())
                        .padding(.top, 4)
                    }

                    Button {
                        dismiss()
                        onDone()
                    } label: {
                        Text("Fine")
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                    .padding(.top, 4)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 28)
            }
            .screenBackground(settings.accentColor)
            .navigationBarTitleDisplayMode(.inline)
            .interactiveDismissDisabled()
            .sheet(isPresented: $showingSaveTemplate) {
                let known = Set(exercises.map(\.uuid))
                TemplateEditorView(initialName: workout.name, initialItems: workout.items(.done, exists: { known.contains($0) }))
            }
        }
    }
}
