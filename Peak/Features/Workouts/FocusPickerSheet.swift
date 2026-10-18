import SwiftUI
import SwiftData

/// "Cosa alleni oggi?": le schede (tocco = si parte con la loro lista), oppure nome della sessione e gruppi.
/// I gruppi filtrano il selettore degli esercizi; nessun gruppo scelto = nessun filtro.
struct FocusPickerSheet: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \WorkoutTemplate.sortIndex) private var allTemplates: [WorkoutTemplate]
    @Query(sort: \WorkoutProgram.sortIndex) private var programs: [WorkoutProgram]

    private var templates: [WorkoutTemplate] { Programs.days(of: Programs.active(programs, settings: settings), in: allTemplates) }

    let confirmTitle: String
    let onConfirm: (String, Set<MuscleGroup>) -> Void
    /// Solo all'avvio: toccare una scheda parte subito con la sua lista. In modifica di una sessione è `nil`.
    let onStartTemplate: ((WorkoutTemplate) -> Void)?

    @State private var name: String
    @State private var selection: Set<MuscleGroup>

    init(
        initialName: String = "",
        initial: Set<MuscleGroup> = [],
        confirmTitle: String,
        onStartTemplate: ((WorkoutTemplate) -> Void)? = nil,
        onConfirm: @escaping (String, Set<MuscleGroup>) -> Void
    ) {
        self.confirmTitle = confirmTitle
        self.onConfirm = onConfirm
        self.onStartTemplate = onStartTemplate
        _name = State(initialValue: initialName)
        _selection = State(initialValue: initial)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    if let onStartTemplate, !templates.isEmpty {
                        GlassCard {
                            VStack(alignment: .leading, spacing: 4) {
                                SectionHeader(title: "I tuoi giorni")
                                ForEach(templates) { template in
                                    Button {
                                        onStartTemplate(template)
                                        dismiss()
                                    } label: {
                                        HStack(spacing: 12) {
                                            TemplateIconView(icon: template.iconRaw, name: template.name, size: 18).frame(width: 26)
                                            VStack(alignment: .leading, spacing: 1) {
                                                Text(template.name)
                                                    .font(Theme.rounded(16, .semibold))
                                                    .foregroundStyle(Theme.textPrimary)
                                                Text(template.items.count == 1 ? "1 esercizio" : "\(template.items.count) esercizi")
                                                    .font(Theme.rounded(11, .medium))
                                                    .foregroundStyle(Theme.textTertiary)
                                            }
                                            Spacer()
                                            Image(systemName: "chevron.right")
                                                .font(.system(size: 12, weight: .semibold))
                                                .foregroundStyle(Theme.textTertiary)
                                        }
                                        .padding(.vertical, 8)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        Text("Oppure scegli i gruppi")
                            .font(Theme.rounded(12, .semibold))
                            .foregroundStyle(Theme.textTertiary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 4)
                    }

                    GlassCard {
                        VStack(alignment: .leading, spacing: 12) {
                            SectionHeader(title: "Nome")
                            TextField("Es. Push 1, Pull 2, Gambe", text: $name)
                                .font(Theme.rounded(17, .semibold))
                                .textInputAutocapitalization(.words)
                                .submitLabel(.done)
                        }
                    }

                    GlassCard {
                        VStack(alignment: .leading, spacing: 12) {
                            SectionHeader(title: "Gruppi muscolari")
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 8)], spacing: 8) {
                                ForEach(MuscleGroup.allCases) { group in
                                    let isOn = selection.contains(group)
                                    Button {
                                        if isOn { selection.remove(group) } else { selection.insert(group) }
                                        Haptics.tap()
                                    } label: {
                                        HStack(spacing: 6) {
                                            Image(systemName: group.symbol)
                                                .font(.system(size: 11, weight: .semibold))
                                            Text(group.title)
                                                .font(Theme.rounded(13, .semibold))
                                        }
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .background(
                                            isOn ? group.tint.opacity(0.22) : Theme.card,
                                            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        )
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .strokeBorder(isOn ? group.tint.opacity(0.6) : Theme.stroke, lineWidth: 1)
                                        )
                                        .foregroundStyle(isOn ? group.tint : Theme.textSecondary)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            Text(selection.isEmpty
                                 ? "Nessun gruppo scelto: vedrai tutti i macchinari."
                                 : "Quando aggiungi un esercizio vedrai solo questi gruppi.")
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                    }

                    Button {
                        onConfirm(name.trimmingCharacters(in: .whitespaces), selection)
                        dismiss()
                    } label: {
                        Label(confirmTitle, systemImage: "bolt.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }
            .scrollDismissesKeyboard(.interactively)
            .screenBackground(settings.accentColor)
            .navigationTitle("Cosa alleni oggi?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Annulla") { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
    }
}
