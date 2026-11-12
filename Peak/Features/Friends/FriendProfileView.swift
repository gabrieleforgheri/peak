import SwiftUI
import SwiftData

/// Il profilo di un amico: i suoi massimali (confrontati ai tuoi) e le sue schede, che si copiano fra le tue.
struct FriendProfileView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var settings
    @Query private var exercises: [Exercise]

    let person: FriendPerson

    @State private var profile: FriendProfileData?
    @State private var failed = false
    @State private var copiedMessage: String?
    @State private var confirmRemove = false

    /// Il tuo miglior 1RM per voce del catalogo, per il confronto.
    private var mine: [String: Double] {
        var out: [String: Double] = [:]
        for e in exercises where !e.catalogID.isEmpty && !e.isArchived {
            var best = e.twoArmRecords.map(\.estimatedOneRepMax).max() ?? 0
            best = max(best, e.setList.filter { !$0.isWarmup && !$0.isOneLimbLine }.map(\.estimatedOneRepMax).max() ?? 0)
            if best > 0 { out[e.catalogID] = max(out[e.catalogID] ?? 0, best) }
        }
        return out
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                if let profile {
                    SectionHeader(title: "Massimali")
                    if profile.maxes.isEmpty {
                        note("Ancora nessun massimale.")
                    } else {
                        GlassCard(padding: Theme.Space.xs) {
                            VStack(spacing: 0) {
                                ForEach(Array(profile.maxes.enumerated()), id: \.offset) { i, m in
                                    maxRow(m)
                                    if i < profile.maxes.count - 1 { Divider().overlay(Theme.stroke) }
                                }
                            }
                        }
                        .appear(0)
                    }

                    SectionHeader(title: "Schede").padding(.top, Theme.Space.s)
                    if profile.templates.isEmpty {
                        note("Nessuna scheda condivisa.")
                    } else {
                        ForEach(Array(profile.templates.enumerated()), id: \.element.id) { i, t in templateCard(t).appear(i + 1) }
                    }

                    Button(role: .destructive) { confirmRemove = true } label: { Label("Togli dagli amici", systemImage: "person.badge.minus") }
                        .buttonStyle(SecondaryButtonStyle()).padding(.top, Theme.Space.m)
                } else if failed {
                    note("Non riesco a caricare questo profilo.")
                } else {
                    ProgressView().tint(settings.accentColor).frame(maxWidth: .infinity).padding(.top, 60)
                }
            }
            .padding(.horizontal, Theme.Space.l)
            .padding(.bottom, Theme.Space.xl)
        }
        .screenBackground(settings.accentColor)
        .navigationTitle("@\(person.username)")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard let api = SyncService.shared.peakAPI else { failed = true; return }
            do { profile = try await api.profile(person.id) } catch { failed = true }
        }
        .alert(copiedMessage ?? "", isPresented: Binding(get: { copiedMessage != nil }, set: { if !$0 { copiedMessage = nil } })) {
            Button("Ok", role: .cancel) {}
        }
        .confirmationDialog("Togliere @\(person.username) dagli amici?", isPresented: $confirmRemove, titleVisibility: .visible) {
            Button("Togli", role: .destructive) {
                Task {
                    try? await SyncService.shared.peakAPI?.removeFriend(person.id)
                    await SocialStore.shared.reload()
                    dismiss()
                }
            }
            Button("Annulla", role: .cancel) {}
        }
    }

    private func note(_ text: String) -> some View {
        Text(text).font(Theme.Text.footnote.weight(.medium)).foregroundStyle(Theme.textTertiary).frame(maxWidth: .infinity).padding(.vertical, Theme.Space.m)
    }

    private func maxRow(_ m: SharedMax) -> some View {
        let entry = Catalog.entry(id: m.catalog)
        let me = m.catalog.isEmpty ? nil : mine[m.catalog]
        let diff = me.map { Int(((m.oneRm - $0) / $0 * 100).rounded()) }
        return HStack(spacing: Theme.Space.m) {
            Circle().fill((entry?.muscleGroup ?? .core).tint).frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: entry?.name ?? m.name).font(Theme.Text.subhead.weight(.bold)).lineLimit(1)
                Text((entry != nil && entry?.name != m.name ? "\(m.name) · " : "") + Fmt.relativeDay(m.date))
                    .font(Theme.Text.micro.weight(.medium)).foregroundStyle(Theme.textTertiary).lineLimit(1)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                Text(Fmt.setLine(weight: m.weight, reps: m.reps, unit: settings.unit)).font(Theme.Text.footnote.weight(.bold))
                if let diff, diff != 0 {
                    Text(diff > 0 ? "\(diff)% sopra di te" : "\(-diff)% sotto di te").font(Theme.Text.micro.weight(.medium)).foregroundStyle(Theme.textTertiary)
                }
            }
        }
        .padding(.horizontal, Theme.Space.s).padding(.vertical, Theme.Space.s)
    }

    private func templateCard(_ t: SharedTemplate) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: Theme.Space.s) {
                HStack {
                    Text(t.name).font(Theme.Text.subhead.weight(.bold))
                    Spacer()
                    Text(t.rows.count == 1 ? "1 esercizio" : "\(t.rows.count) esercizi").font(Theme.Text.micro.weight(.medium)).foregroundStyle(Theme.textTertiary)
                }
                ForEach(Array(t.rows.enumerated()), id: \.offset) { _, r in
                    HStack {
                        Text(verbatim: Catalog.entry(id: r.catalog)?.name ?? r.name).font(Theme.Text.footnote.weight(.medium)).lineLimit(1)
                        Spacer()
                        Text(target(r)).font(Theme.Text.caption).foregroundStyle(Theme.textSecondary)
                    }
                }
                Button("Copia nelle mie schede") { copy(t) }.buttonStyle(SecondaryButtonStyle())
            }
        }
    }

    private func target(_ r: SharedRow) -> String {
        let reps = r.repsMax == 0 ? "" : (r.repsMin == r.repsMax ? "\(r.repsMax)" : "\(r.repsMin)–\(r.repsMax)")
        return (r.sets > 0 ? "\(r.sets)×" : "") + reps
    }

    /// Ogni riga si abbina a un tuo esercizio con la stessa voce del catalogo (o, se personalizzato, lo stesso nome);
    /// quelli che mancano si creano. Senza pesi.
    private func copy(_ shared: SharedTemplate) {
        var mineExercises = exercises.filter { !$0.isArchived }
        var nextIndex = (exercises.map(\.sortIndex).max() ?? 0) + 1
        var items: [TemplateItem] = []
        for row in shared.rows {
            var exercise = row.catalog.isEmpty
                ? mineExercises.first { $0.catalogID.isEmpty && Catalog.norm($0.name) == Catalog.norm(row.name) }
                : mineExercises.first { $0.catalogID == row.catalog }
            if exercise == nil {
                let entry = Catalog.entry(id: row.catalog)
                let created = Exercise(name: entry?.name ?? row.name, muscleGroup: entry?.muscleGroup ?? .petto, incrementStep: entry?.step ?? 2.5, notes: entry?.notes ?? "", sortIndex: nextIndex)
                created.catalogID = entry?.id ?? ""
                created.setLimbs(created.limbs)
                created.isCustom = entry == nil
                context.insert(created)
                mineExercises.append(created)
                nextIndex += 1
                exercise = created
            }
            if let exercise, !items.contains(where: { $0.exercise == exercise.uuid }) {
                items.append(TemplateItem(exercise: exercise.uuid, sets: row.sets, repsMin: row.repsMin, repsMax: row.repsMax, weight: nil))
            }
        }
        let top = ((try? context.fetch(FetchDescriptor<WorkoutTemplate>()))?.map(\.sortIndex).max() ?? -1) + 1
        let template = WorkoutTemplate(name: shared.name, items: items, sortIndex: top)
        template.programRaw = Programs.current(context: context, settings: settings).uuid.uuidString
        context.insert(template)
        try? context.save()
        Haptics.success()
        copiedMessage = "Giorno copiato fra i tuoi."
    }
}
