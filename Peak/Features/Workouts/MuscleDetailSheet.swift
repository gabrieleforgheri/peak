import SwiftUI

/// Mostra i muscoli coinvolti dal macchinario (con vista anatomica fronte/retro e consigli di setup).
struct MuscleDetailSheet: View {
    @Environment(\.dismiss) private var dismiss

    let exercise: Exercise
    /// La figura aperta a tutto schermo, se c'è.
    @State private var zoomSide: AnatomyView?

    private var muscles: [MuscleId] {
        exercise.figureMuscles
    }

    private var catalogEntry: CatalogEntry? {
        exercise.catalogID.isEmpty ? nil : Catalog.entry(id: exercise.catalogID)
    }

    private var notes: String {
        let n = exercise.notes.trimmingCharacters(in: .whitespacesAndNewlines)
        if !n.isEmpty { return n }
        return catalogEntry?.notes.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Header: Nome esercizio e chip del gruppo muscolare
                    HStack(spacing: 10) {
                        Text(exercise.displayName)
                            .font(Theme.rounded(22, .bold))
                            .foregroundStyle(Theme.textPrimary)

                        MuscleGroupChip(group: exercise.muscleGroup)

                        Spacer()
                    }
                    .padding(.horizontal, 4)

                    // Mappa muscolare completa Fronte e Retro
                    GlassCard(padding: 16) {
                        VStack(spacing: 12) {
                            SectionHeader(title: "Mappa muscolare")

                            HStack(alignment: .top, spacing: 14) {
                                VStack(spacing: 6) {
                                    Button { zoomSide = .front } label: {
                                        MuscleFigureView(muscles: muscles, view: .front, zoom: false)
                                            .frame(height: 220)
                                            .overlay(alignment: .bottomTrailing) { ZoomLens() }
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("Ingrandisci".tr)
                                    Text("Fronte".tr)
                                        .font(Theme.rounded(11, .semibold))
                                        .foregroundStyle(Theme.textTertiary)
                                }
                                .frame(maxWidth: .infinity)

                                VStack(spacing: 6) {
                                    Button { zoomSide = .back } label: {
                                        MuscleFigureView(muscles: muscles, view: .back, zoom: false)
                                            .frame(height: 220)
                                            .overlay(alignment: .bottomTrailing) { ZoomLens() }
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("Ingrandisci".tr)
                                    Text("Retro".tr)
                                        .font(Theme.rounded(11, .semibold))
                                        .foregroundStyle(Theme.textTertiary)
                                }
                                .frame(maxWidth: .infinity)
                            }
                            .frame(maxWidth: 340)
                        }
                        .frame(maxWidth: .infinity)
                    }

                    // Muscoli coinvolti
                    if !muscles.isEmpty {
                        GlassCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 12) {
                                SectionHeader(title: "Muscoli coinvolti")

                                MuscleChipGrid(muscles: muscles)
                            }
                        }
                    }

                    // Consigli di setup / note
                    if !notes.isEmpty {
                        GlassCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 8) {
                                SectionHeader(title: "Consigli e setup")
                                Text(notes.tr)
                                    .font(Theme.rounded(13, .regular))
                                    .foregroundStyle(Theme.textSecondary)
                                    .lineSpacing(3)
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Theme.bg)
            .navigationTitle("Dettaglio muscolare".tr)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Chiudi".tr) { dismiss() }
                        .font(Theme.rounded(15, .semibold))
                        .foregroundStyle(exercise.muscleGroup.tint)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .fullScreenCover(item: $zoomSide) { side in
            MuscleZoomView(muscles: muscles, side: side)
        }
    }
}

/// I muscoli come targhette col colore del loro gruppo.
struct MuscleChipGrid: View {
    let muscles: [MuscleId]

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), spacing: 8)], spacing: 8) {
            ForEach(muscles, id: \.self) { muscle in
                HStack(spacing: 6) {
                    Circle()
                        .fill(muscle.group.tint)
                        .frame(width: 8, height: 8)
                    Text(muscle.title.tr)
                        .font(Theme.rounded(13, .semibold))
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(muscle.group.tint.opacity(0.18), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            }
        }
    }
}

/// La lente nell'angolo della figura: dice che si può toccare per ingrandirla.
struct ZoomLens: View {
    var body: some View {
        Image(systemName: "plus.magnifyingglass")
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(Theme.textPrimary)
            .frame(width: 26, height: 26)
            .background(Theme.cardStrong, in: Circle())
    }
}
