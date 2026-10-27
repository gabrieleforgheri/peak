import SwiftUI
import SwiftData

/// Cerca nel catalogo degli esercizi e sceglie quello giusto (o il più vicino). `onPick(nil, group)` = personalizzato.
struct CatalogPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var settings
    @Query private var exercises: [Exercise]

    var title = "Scegli il macchinario"
    var allowsCustom = true
    /// La voce scelta (o `nil` = personalizzato) e il gruppo da cui si veniva, se ce n'era uno.
    let onPick: (CatalogEntry?, MuscleGroup?) -> Void
    /// Chiamata se si chiude senza scegliere nulla.
    var onCancel: () -> Void = {}
    /// Passi 1 e 2 della creazione di un macchinario: sta dentro la NavigationStack di chi lo usa, non si chiude da solo
    /// scegliendo, e mostra «Passo N di 3» e i suggerimenti della prima volta.
    var wizard = false

    @State private var query = ""
    @State private var picked = false
    /// Prima il gruppo muscolare, poi la ricerca dentro il gruppo.
    @State private var group: MuscleGroup?
    @State private var tipsDone: Set<Int> = []

    private var owned: Set<String> { Set(exercises.filter { !$0.isArchived }.map(\.catalogID)) }
    private var results: [CatalogEntry] { Catalog.search(query, group: group) }

    var body: some View {
        if wizard { content } else { NavigationStack { content } }
    }

    private var content: some View {
        List {
            if group == nil, query.isEmpty {
                Section {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 10)], spacing: 10) {
                        ForEach(MuscleGroup.allCases) { g in
                            Button { withAnimation(.snappy) { group = g } } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: g.symbol).foregroundStyle(g.tint)
                                    Text(g.title).font(Theme.rounded(15, .semibold))
                                    Spacer(minLength: 0)
                                }
                                .padding(14)
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Theme.stroke, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(Theme.textPrimary)
                        }
                    }
                    .tourTarget("tip-group")
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                } header: {
                    Text(wizard ? "Passo 1 di 3 · Scegli il gruppo muscolare" : "Scegli il gruppo muscolare, poi cerca").font(Theme.rounded(12, .medium))
                }
            } else {
            if wizard {
                Text("Passo 2 di 3 · Scegli il macchinario")
                    .font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textTertiary)
                    .listRowBackground(Color.clear)
            }
            if let group {
                Button { withAnimation(.snappy) { self.group = nil } } label: {
                    Label("Tutti i gruppi · \(group.title)", systemImage: "chevron.left")
                        .font(Theme.rounded(13, .semibold)).foregroundStyle(group.tint)
                }
                .listRowBackground(Color.clear)
            }
            ForEach(results) { entry in
                Button {
                    picked = true
                    onPick(entry, group)
                    if !wizard { dismiss() }
                } label: {
                    HStack(spacing: 12) {
                        Circle()
                            .fill(entry.muscleGroup.tint)
                            .frame(width: 10, height: 10)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(verbatim: entry.name)
                                .font(Theme.rounded(16, .semibold))
                                .foregroundStyle(Theme.textPrimary)
                            Text("\(entry.otherName) · \(entry.muscleGroup.title.tr)")
                                .font(Theme.rounded(12, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                        Spacer()
                        if owned.contains(entry.id) {
                            Text("Ce l'hai già")
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                    }
                    .padding(.vertical, 4)
                }
                .background { if entry.id == results.first?.id { Color.clear.tourTarget("tip-list") } }
                .listRowBackground(Color.clear)
            }

            if results.isEmpty {
                Text("Nessun risultato.")
                    .font(Theme.rounded(14, .medium))
                    .foregroundStyle(Theme.textTertiary)
                    .listRowBackground(Color.clear)
            }
            }

            if allowsCustom {
                Section {
                    Button {
                        picked = true
                        onPick(nil, group)
                        if !wizard { dismiss() }
                    } label: {
                        Label("Non c'è: crea un macchinario personalizzato", systemImage: "plus.circle")
                            .font(Theme.rounded(15, .semibold))
                            .foregroundStyle(settings.accentColor)
                    }
                    .listRowBackground(Color.clear)
                } footer: {
                    Text("Scegli il più vicino se puoi: solo i macchinari del catalogo entrano nelle classifiche con gli amici.")
                        .font(Theme.rounded(11, .medium))
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .screenBackground(settings.accentColor)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Cerca nel catalogo")
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Annulla") { dismiss() }
            }
        }
        .onDisappear { if !picked, !wizard { onCancel() } }
        .machineTip(tipStep == 1 ? "tip-group" : "tip-list", show: tipsOn, title: tipStep == 1 ? "Il gruppo" : "Scegli dalla lista",
                    text: tipStep == 1 ? "Parti dal gruppo muscolare: tocca quello che alleni."
                        : "Tocca il tuo macchinario. Solo quelli del catalogo entrano nelle classifiche con gli amici; se non c'è, in fondo lo crei tu.") {
            tipsDone.insert(tipStep)
        }
    }

    /// I suggerimenti della prima volta: uno per passo, finché non hai ancora nessun macchinario.
    private var tipStep: Int { group == nil && query.isEmpty ? 1 : 2 }
    private var tipsOn: Bool { wizard && !settings.machineTipsSeen && exercises.isEmpty && !tipsDone.contains(tipStep) }
}
