import SwiftUI
import SwiftData

/// Impostazioni → Dati: macchinari archiviati e cancellazione di tutto.
struct DataSettingsView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Query(sort: \Exercise.sortIndex) private var exercises: [Exercise]
    @State private var showingEraseConfirm = false
    @State private var errorMessage: String?
    private let sync = SyncService.shared

    private var archivedCount: Int { exercises.filter(\.isArchived).count }

    var body: some View {
        SettingsPage {
            GlassCard {
                VStack(alignment: .leading, spacing: 12) {
                    SectionHeader(title: "Dati", trailing: "\(exercises.count) macchinari")

                    NavigationLink {
                        ArchivedExercisesView()
                    } label: {
                        HStack {
                            Label("Macchinari archiviati", systemImage: "archivebox")
                                .font(Theme.rounded(15, .semibold))
                                .foregroundStyle(Theme.textPrimary)
                            Spacer()
                            Text("\(archivedCount)")
                                .font(Theme.rounded(13, .medium))
                                .foregroundStyle(Theme.textTertiary)
                            Image(systemName: "chevron.right")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(Theme.textTertiary)
                        }
                        .padding(.vertical, 4)
                    }

                    Divider().overlay(Theme.stroke)

                    Button(role: .destructive) {
                        showingEraseConfirm = true
                    } label: {
                        Label("Cancella tutti i dati", systemImage: "trash")
                            .font(Theme.rounded(14, .semibold))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Theme.negative)
                }
            }
        }
        .confirmationDialog(
            "Cancellare tutti i dati?",
            isPresented: $showingEraseConfirm,
            titleVisibility: .visible
        ) {
            Button("Cancella tutto", role: .destructive) { eraseAll() }
            Button("Annulla", role: .cancel) {}
        } message: {
            Text(sync.isConnected
                ? "Macchinari, massimali, allenamenti e pesate verranno eliminati da questo iPhone, da Peak sul server e da ogni dispositivo collegato. Esporta un backup prima di procedere."
                : "Macchinari, massimali, allenamenti e pesate verranno eliminati. Esporta un backup prima di procedere.")
        }
        .alert("Errore", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Ok", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func eraseAll() {
        do {
            try context.delete(model: WorkoutSet.self)
            try context.delete(model: Workout.self)
            try context.delete(model: MaxRecord.self)
            try context.delete(model: Exercise.self)
            try context.delete(model: BodyWeightEntry.self)
            try context.delete(model: WorkoutTemplate.self)
            try context.delete(model: WorkoutProgram.self)
            // collegato, la cancellazione deve arrivare al server: la guardia contro le cancellazioni di massa non la ferma
            sync.permitMassDeletion()
            try context.save()
            Haptics.warning()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

// MARK: - Archiviati

struct ArchivedExercisesView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings

    @Query(sort: \Exercise.sortIndex) private var exercises: [Exercise]

    private var archived: [Exercise] { exercises.filter(\.isArchived) }

    var body: some View {
        Group {
            if archived.isEmpty {
                ContentUnavailableView(
                    "Nessun macchinario archiviato",
                    systemImage: "archivebox",
                    description: Text("Gli archiviati spariscono dalle liste ma conservano tutto lo storico.")
                )
            } else {
                List {
                    ForEach(archived) { exercise in
                        HStack(spacing: 12) {
                            Image(systemName: exercise.muscleGroup.symbol)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(exercise.muscleGroup.tint)
                                .frame(width: 30, height: 30)
                                .background(exercise.muscleGroup.tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 9, style: .continuous))

                            Text(exercise.name)
                                .font(Theme.rounded(15, .semibold))
                                .foregroundStyle(Theme.textPrimary)

                            Spacer()

                            Button("Ripristina") {
                                exercise.isArchived = false
                                try? context.save()
                                Haptics.commit()
                            }
                            .font(Theme.rounded(13, .semibold))
                            .foregroundStyle(settings.accentColor)
                            .buttonStyle(.plain)
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 14)
                        .glassSurface(radius: Theme.radiusSmall)
                        .plainListRow()
                    }
                }
                .listStyle(.plain)
            }
        }
        .screenBackground(settings.accentColor)
        .navigationTitle("Archiviati")
        .navigationBarTitleDisplayMode(.inline)
    }
}
