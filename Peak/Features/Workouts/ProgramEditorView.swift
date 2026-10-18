import SwiftUI
import SwiftData

/// Crea o modifica una scheda: nome e, se vuoi, un giorno fisso della settimana per ogni giorno della scheda.
struct ProgramEditorView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \WorkoutProgram.sortIndex) private var programs: [WorkoutProgram]
    @Query(sort: \WorkoutTemplate.sortIndex) private var templates: [WorkoutTemplate]

    let program: WorkoutProgram?

    @State private var name: String
    @State private var useDays: Bool
    /// Il giorno fisso scelto per ogni giorno della scheda (uuid → 0…6, −1 = nessuno); si applica con Salva.
    @State private var weekdays: [UUID: Int] = [:]
    @State private var confirmingDelete = false

    init(program: WorkoutProgram? = nil) {
        self.program = program
        _name = State(initialValue: program?.name ?? "")
        _useDays = State(initialValue: program?.useDays ?? false)
    }

    private var days: [WorkoutTemplate] { program.map { Programs.days(of: $0, in: templates) } ?? [] }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 12) {
                            SectionHeader(title: "Nome")
                            TextField("Es. Push Pull Legs", text: $name)
                                .font(Theme.rounded(17, .semibold))
                                .textInputAutocapitalization(.words)
                                .submitLabel(.done)
                        }
                    }

                    GlassCard {
                        Toggle("Giorni fissi della settimana", isOn: $useDays)
                            .font(Theme.rounded(15, .semibold))
                            .tint(settings.accentColor)
                    }

                    if useDays {
                        ForEach(days) { day in
                            GlassCard {
                                VStack(alignment: .leading, spacing: 10) {
                                    Text(day.name).font(Theme.rounded(15, .bold)).lineLimit(1)
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 8) {
                                            weekdayChip("Nessuno", on: weekdays[day.uuid] == -1) { weekdays[day.uuid] = -1 }
                                            ForEach(0..<7, id: \.self) { i in
                                                weekdayChip(Programs.weekdays[i], on: weekdays[day.uuid] == i) { weekdays[day.uuid] = i }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if program != nil, programs.count > 1 {
                        Button(role: .destructive) { confirmingDelete = true } label: { Label("Elimina scheda", systemImage: "trash") }
                            .buttonStyle(SecondaryButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 30)
            }
            .scrollDismissesKeyboard(.interactively)
            .screenBackground(settings.accentColor)
            .navigationTitle(program == nil ? "Nuova scheda" : "Scheda")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Annulla") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Salva") { save() }.fontWeight(.bold).disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .confirmationDialog("Eliminare questa scheda e i suoi giorni? Gli allenamenti già fatti restano.", isPresented: $confirmingDelete, titleVisibility: .visible) {
                Button("Elimina scheda", role: .destructive) { delete() }
            }
            .onAppear { weekdays = Dictionary(days.map { ($0.uuid, $0.weekday) }, uniquingKeysWith: { a, _ in a }) }
        }
        .presentationDetents([.large])
    }

    private func weekdayChip(_ title: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: { action(); Haptics.tap() }) {
            Text(title).font(Theme.rounded(13, .semibold))
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(on ? settings.accentColor.opacity(0.22) : Theme.card, in: Capsule())
                .overlay(Capsule().strokeBorder(on ? settings.accentColor.opacity(0.7) : Theme.stroke, lineWidth: 1))
                .foregroundStyle(on ? settings.accentColor : Theme.textSecondary)
        }
        .buttonStyle(.plain)
    }

    private func save() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        let target: WorkoutProgram
        if let program { target = program } else {
            target = WorkoutProgram(name: trimmed, sortIndex: (programs.map(\.sortIndex).max() ?? -1) + 1)
            context.insert(target)
        }
        target.name = trimmed
        target.useDays = useDays
        for day in days { day.weekday = useDays ? (weekdays[day.uuid] ?? day.weekday) : -1 }
        if program == nil { settings.activeProgram = target.uuid.uuidString }
        try? context.save()
        Haptics.commit()
        dismiss()
    }

    private func delete() {
        guard let program else { return }
        for day in days { context.delete(day) }
        context.delete(program)
        if settings.activeProgram.uppercased() == program.uuid.uuidString { settings.activeProgram = "" }
        try? context.save()
        dismiss()
    }
}
