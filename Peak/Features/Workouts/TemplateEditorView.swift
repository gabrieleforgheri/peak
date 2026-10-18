import SwiftUI
import SwiftData

/// Crea o modifica un giorno della scheda: nome, icona, scheda, giorno fisso e righe (esercizio, serie, ripetizioni min–max, peso).
/// Con `template == nil` la scheda è nuova; `initialItems` la riempie (salva come scheda).
struct TemplateEditorView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \Exercise.sortIndex) private var exercises: [Exercise]
    @Query(sort: \WorkoutTemplate.sortIndex) private var templates: [WorkoutTemplate]
    @Query(sort: \WorkoutProgram.sortIndex) private var programs: [WorkoutProgram]

    let template: WorkoutTemplate?
    var onSaved: (() -> Void)?

    /// Una riga in modifica: i campi sono testo, così restano vuoti finché non si scrive.
    private struct Row: Identifiable {
        var exercise: UUID
        var sets = ""
        var repsMin = ""
        var repsMax = ""
        var weight = ""
        var id: UUID { exercise }
    }

    @State private var name: String
    @State private var rows: [Row]
    @State private var showingPicker = false
    @State private var confirmingDelete = false
    @State private var isHidden = false
    @State private var icon: String
    @State private var programRaw: String
    @State private var weekday: Int

    init(template: WorkoutTemplate? = nil, initialName: String = "", initialItems: [TemplateItem] = [], onSaved: (() -> Void)? = nil) {
        self.template = template
        self.onSaved = onSaved
        _name = State(initialValue: template?.name ?? initialName)
        _isHidden = State(initialValue: template?.isHidden ?? false)
        _icon = State(initialValue: template?.iconRaw ?? "")
        _programRaw = State(initialValue: template?.programRaw ?? "")
        _weekday = State(initialValue: template?.weekday ?? -1)
        // i pesi si scrivono nell'unità scelta: la conversione in `rowsFor` serve ai kg salvati
        _rows = State(initialValue: (template?.items ?? initialItems).map { Row(exercise: $0.exercise) })
        pendingItems = template?.items ?? initialItems
    }

    /// Le righe di partenza in kg: si trasformano in testo appena c'è `settings` (onAppear).
    private let pendingItems: [TemplateItem]

    private var byUUID: [UUID: Exercise] {
        Dictionary(exercises.map { ($0.uuid, $0) }, uniquingKeysWith: { first, _ in first })
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 12) {
                            SectionHeader(title: "Nome")
                            TextField("Es. Push, Pull 1, Gambe", text: $name)
                                .font(Theme.rounded(17, .semibold))
                                .textInputAutocapitalization(.words)
                                .submitLabel(.done)
                        }
                    }

                    iconCard
                    placeCard

                    ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                        if let exercise = byUUID[row.exercise] {
                            rowCard(index: index, exercise: exercise)
                        }
                    }

                    Button {
                        showingPicker = true
                    } label: {
                        Label("Aggiungi esercizio", systemImage: "plus")
                    }
                    .buttonStyle(SecondaryButtonStyle())

                    if !rows.isEmpty {
                        Text("Senza peso scritto si vede l'ultimo usato.")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }

                    GlassCard {
                        Toggle("Nascondi agli amici", isOn: $isHidden)
                            .font(Theme.rounded(15, .semibold))
                            .tint(settings.accentColor)
                    }

                    if template != nil {
                        Button(role: .destructive) {
                            confirmingDelete = true
                        } label: {
                            Label("Elimina giorno", systemImage: "trash")
                        }
                        .buttonStyle(SecondaryButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 30)
            }
            .scrollDismissesKeyboard(.interactively)
            .screenBackground(settings.accentColor)
            .navigationTitle(template == nil ? "Nuovo giorno" : "Giorno")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Annulla") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Salva") { save() }
                        .fontWeight(.bold)
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .sheet(isPresented: $showingPicker) {
                ExercisePickerSheet { exercise in
                    if !rows.contains(where: { $0.exercise == exercise.uuid }) {
                        rows.append(Row(exercise: exercise.uuid))
                    }
                }
            }
            .confirmationDialog("Eliminare questo giorno? Gli allenamenti già fatti restano.", isPresented: $confirmingDelete, titleVisibility: .visible) {
                Button("Elimina giorno", role: .destructive) {
                    if let template { context.delete(template) }
                    try? context.save()
                    dismiss()
                }
            }
            .onAppear(perform: fillRows)
        }
        .presentationDetents([.large])
    }

    // MARK: - Icona, scheda e giorno

    private var iconCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                SectionHeader(title: "Icona")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        chip(selected: icon.isEmpty, action: { icon = "" }) { Text("Automatica").font(Theme.rounded(13, .semibold)) }
                        ForEach(MuscleGroup.allCases) { g in
                            chip(selected: icon == "muscle:\(g.rawValue)", action: { icon = "muscle:\(g.rawValue)" }) {
                                Image(systemName: g.symbol).foregroundStyle(g.tint)
                            }
                        }
                        ForEach(TemplateIcon.named, id: \.name) { item in
                            chip(selected: icon == item.name, action: { icon = item.name }) { Image(systemName: item.symbol) }
                        }
                    }
                }
            }
        }
    }

    private var chosenProgram: WorkoutProgram? {
        programs.first { $0.uuid.uuidString == programRaw } ?? Programs.active(programs, settings: settings)
    }

    @ViewBuilder private var placeCard: some View {
        if programs.count > 1 || chosenProgram?.useDays == true {
            GlassCard {
                VStack(alignment: .leading, spacing: 10) {
                    if programs.count > 1 {
                        SectionHeader(title: "Scheda")
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(programs) { p in
                                    chip(selected: chosenProgram?.uuid == p.uuid, action: { programRaw = p.uuid.uuidString }) {
                                        Text(p.name).font(Theme.rounded(13, .semibold))
                                    }
                                }
                            }
                        }
                    }
                    if chosenProgram?.useDays == true {
                        SectionHeader(title: "Giorno fisso")
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                chip(selected: weekday < 0, action: { weekday = -1 }) { Text("Nessuno").font(Theme.rounded(13, .semibold)) }
                                ForEach(0..<7, id: \.self) { i in
                                    chip(selected: weekday == i, action: { weekday = i }) { Text(Programs.weekdays[i]).font(Theme.rounded(13, .semibold)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func chip<Content: View>(selected: Bool, action: @escaping () -> Void, @ViewBuilder _ label: () -> Content) -> some View {
        Button(action: { action(); Haptics.tap() }) {
            label()
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(selected ? settings.accentColor.opacity(0.22) : Theme.card, in: Capsule())
                .overlay(Capsule().strokeBorder(selected ? settings.accentColor.opacity(0.7) : Theme.stroke, lineWidth: 1))
                .foregroundStyle(selected ? settings.accentColor : Theme.textSecondary)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Riga

    private func rowCard(index: Int, exercise: Exercise) -> some View {
        GlassCard(padding: 14) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    ExerciseThumbnail(exercise: exercise, size: 34)
                    ExerciseFigure(exercise: exercise, size: 34)
                    Text(exercise.name)
                        .font(Theme.rounded(16, .semibold))
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    roundButton("chevron.up", label: "Su", disabled: index == 0) { rows.swapAt(index, index - 1) }
                    roundButton("chevron.down", label: "Giù", disabled: index == rows.count - 1) { rows.swapAt(index, index + 1) }
                    roundButton("xmark", label: "Togli", disabled: false) { rows.remove(at: index) }
                }

                HStack(spacing: 8) {
                    field("Serie", text: binding(index, \.sets), keyboard: .numberPad)
                    field("Rip min", text: binding(index, \.repsMin), keyboard: .numberPad)
                    field("Rip max", text: binding(index, \.repsMax), keyboard: .numberPad)
                    field(settings.unit.symbol, text: binding(index, \.weight), keyboard: .decimalPad, prompt: "–")
                }
            }
        }
    }

    private func binding(_ index: Int, _ keyPath: WritableKeyPath<Row, String>) -> Binding<String> {
        Binding(
            get: { rows.indices.contains(index) ? rows[index][keyPath: keyPath] : "" },
            set: { if rows.indices.contains(index) { rows[index][keyPath: keyPath] = $0 } }
        )
    }

    private func field(_ title: String, text: Binding<String>, keyboard: UIKeyboardType, prompt: String = "") -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title.uppercased())
                .font(Theme.rounded(10, .bold))
                .tracking(0.6)
                .foregroundStyle(Theme.textTertiary)
            TextField(prompt, text: text)
                .keyboardType(keyboard)
                .font(Theme.rounded(16, .semibold))
                .padding(.horizontal, 10)
                .padding(.vertical, 9)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .frame(maxWidth: .infinity)
    }

    private func roundButton(_ symbol: String, label: String, disabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .bold))
                .frame(width: 32, height: 32)
                .background(Theme.card, in: Circle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(Theme.textSecondary)
        .opacity(disabled ? 0.35 : 1)
        .disabled(disabled)
        .accessibilityLabel(label)
    }

    // MARK: - Dati

    private func fillRows() {
        guard rows.allSatisfy({ $0.sets.isEmpty && $0.repsMin.isEmpty && $0.repsMax.isEmpty && $0.weight.isEmpty }) else { return }
        rows = pendingItems.map { item in
            Row(
                exercise: item.exercise,
                sets: item.sets > 0 ? String(item.sets) : "",
                repsMin: item.repsMin > 0 ? String(item.repsMin) : "",
                repsMax: item.repsMax > 0 ? String(item.repsMax) : "",
                weight: item.weight.map { Self.number(settings.unit.fromKilograms($0)) } ?? ""
            )
        }
    }

    private static func number(_ value: Double) -> String {
        let rounded = (value * 100).rounded() / 100
        return rounded == rounded.rounded() ? String(Int(rounded)) : String(rounded)
    }

    private func items() -> [TemplateItem] {
        rows.filter { byUUID[$0.exercise] != nil }.map { row in
            func int(_ text: String, _ max: Int) -> Int { min(max, Swift.max(0, Int(text.trimmingCharacters(in: .whitespaces)) ?? 0)) }
            var low = int(row.repsMin, 100)
            var high = int(row.repsMax, 100)
            if low == 0 { low = high }          // basta una cifra sola
            if high < low { high = low }
            let typed = Double(row.weight.replacingOccurrences(of: ",", with: "."))
            let weight = typed.flatMap { $0 >= 0 ? min(2000, settings.unit.toKilograms($0)) : nil }
            return TemplateItem(exercise: row.exercise, sets: int(row.sets, 20), repsMin: low, repsMax: high, weight: weight)
        }
    }

    private func save() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        let program = programs.first { $0.uuid.uuidString == programRaw } ?? Programs.current(context: context, settings: settings)
        let day = program.useDays ? weekday : -1
        if let template {
            template.name = trimmed
            template.items = items()
            template.isHidden = isHidden
            template.iconRaw = icon
            template.programRaw = program.uuid.uuidString
            template.weekday = day
        } else {
            let top = templates.map(\.sortIndex).max() ?? -1
            let created = WorkoutTemplate(name: trimmed, items: items(), sortIndex: top + 1)
            created.isHidden = isHidden
            created.iconRaw = icon
            created.programRaw = program.uuid.uuidString
            created.weekday = day
            context.insert(created)
        }
        try? context.save()
        Haptics.commit()
        onSaved?()
        dismiss()
    }
}
