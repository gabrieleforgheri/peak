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

    @State private var query = ""
    @State private var picked = false
    /// Prima il gruppo muscolare, poi la ricerca dentro il gruppo.
    @State private var group: MuscleGroup?

    private var owned: Set<String> { Set(exercises.filter { !$0.isArchived }.map(\.catalogID)) }
    private var results: [CatalogEntry] { Catalog.search(query).filter { group == nil || $0.muscleGroup == group } }

    var body: some View {
        NavigationStack {
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
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    } header: {
                        Text("Scegli il gruppo muscolare, poi cerca").font(Theme.rounded(12, .medium))
                    }
                } else {
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
                        dismiss()
                    } label: {
                        HStack(spacing: 12) {
                            Circle()
                                .fill(entry.muscleGroup.tint)
                                .frame(width: 10, height: 10)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(entry.it)
                                    .font(Theme.rounded(16, .semibold))
                                    .foregroundStyle(Theme.textPrimary)
                                Text("\(entry.en) · \(entry.muscleGroup.title)")
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
                            dismiss()
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
            .onDisappear { if !picked { onCancel() } }
        }
    }
}
