import SwiftUI
import SwiftData

/// L'icona di un giorno della scheda. Stessi nomi del web (`TEMPLATE_ICONS` in common/docs.ts):
/// `muscle:<gruppo>`, `glyph:<pittogramma>` e `body:<x>` sono quelli del web; qui `body:` ripiega sul manubrio.
enum TemplateIcon {
    /// Quelle che si possono scegliere (oltre ai gruppi muscolari), con il simbolo di sistema che le disegna.
    static let named: [(name: String, symbol: String)] = [
        ("dumbbell", "dumbbell.fill"), ("flame", "flame.fill"), ("bolt", "bolt.fill"), ("mountain", "mountain.2.fill"),
        ("trophy", "trophy.fill"), ("star", "star.fill"), ("timer", "timer"), ("repeat", "repeat"),
        ("compass", "safari.fill"), ("scale", "scalemass.fill"), ("target", "target"), ("heart", "heart.fill"),
        ("weight", "scalemass"), ("medal", "medal.fill"), ("bike", "bicycle"), ("fist", "hand.raised.fill"),
        ("pulse", "waveform.path.ecg"), ("rocket", "paperplane.fill"), ("crown", "crown.fill"), ("person", "figure.arms.open")
    ]

    /// Come `suggestTemplateIcon` in common/template-icon.ts: stesse parole e stesso ordine.
    private static let rules: [(String, String)] = [
        (#"\b(leg|legs|gamb\w*|squat\w*|quad\w*|glut\w*|polpacc\w*|calf|calves|lower)\b"#, "muscle:gambe"),
        (#"\b(pull|schien\w*|back|row|dors\w*|lat)\b"#, "muscle:schiena"),
        (#"\b(push|petto|chest|pett\w*|bench|panca)\b"#, "muscle:petto"),
        (#"\b(spall\w*|shoulder\w*|delt\w*)\b"#, "muscle:spalle"),
        (#"\b(bracc\w*|arm|arms|bicip\w*|tricip\w*|curl)\b"#, "muscle:braccia"),
        (#"\b(core|abs|addom\w*)\b"#, "muscle:core"),
        (#"\b(cardio|run|corsa|hiit)\b"#, "heart"),
        (#"\b(forza|strength|power)\b"#, "bolt")
    ]

    /// "" se il nome non dice niente: allora si mostra il manubrio.
    static func suggest(_ name: String) -> String {
        let n = name.lowercased().folding(options: .diacriticInsensitive, locale: nil)
        return rules.first { n.range(of: $0.0, options: .regularExpression) != nil }?.1 ?? ""
    }

    /// L'icona da mostrare: quella scelta, altrimenti quella dedotta dal nome.
    static func resolved(_ icon: String, name: String) -> String { icon.isEmpty ? suggest(name) : icon }

    static func group(_ icon: String) -> MuscleGroup? {
        icon.hasPrefix("muscle:") ? MuscleGroup(rawValue: String(icon.dropFirst(7))) : nil
    }

    static func symbol(_ icon: String) -> String {
        if let g = group(icon) { return g.symbol }
        return named.first { $0.name == icon }?.symbol ?? "dumbbell.fill"
    }
}

/// L'icona di un giorno, nel suo colore.
struct TemplateIconView: View {
    let icon: String
    let name: String
    var size: CGFloat = 20

    var body: some View {
        let r = TemplateIcon.resolved(icon, name: name)
        Image(systemName: TemplateIcon.symbol(r))
            .font(.system(size: size, weight: .semibold))
            .foregroundStyle(TemplateIcon.group(r)?.tint ?? .primary)
    }
}

/// La scheda in uso, i suoi giorni e quello che tocca. Stessi conti di `activeProgram`/`daysOf`/`nextDay` del web (derive.ts).
enum Programs {
    static func active(_ programs: [WorkoutProgram], settings: AppSettings) -> WorkoutProgram? {
        programs.first { $0.uuid.uuidString == settings.activeProgram.uppercased() } ?? programs.first
    }

    /// I giorni di una scheda, nell'ordine in cui li hai messi. Senza scheda: quelli che non stanno in nessuna.
    static func days(of program: WorkoutProgram?, in templates: [WorkoutTemplate]) -> [WorkoutTemplate] {
        templates.filter { $0.programRaw == (program?.uuid.uuidString ?? "") }
    }

    /// Con giorni fissi: quello di oggi o il prossimo in calendario; senza, quello dopo l'ultimo fatto in questa scheda, a rotazione.
    static func next(program: WorkoutProgram?, templates: [WorkoutTemplate], workouts: [Workout], now: Date = Date()) -> WorkoutTemplate? {
        let days = days(of: program, in: templates)
        guard !days.isEmpty else { return nil }
        if program?.useDays == true {
            let today = (Calendar.current.component(.weekday, from: now) + 5) % 7   // 0 = lunedì
            let fixed = days.filter { $0.weekday >= 0 }
            if let d = fixed.min(by: { ($0.weekday - today + 7) % 7 < ($1.weekday - today + 7) % 7 }) { return d }
        }
        let last = workouts.first { w in days.contains { $0.uuid.uuidString == w.templateRaw } }
        let i = last.flatMap { l in days.firstIndex { $0.uuid.uuidString == l.templateRaw } } ?? -1
        return days[(i + 1) % days.count]
    }

    /// Le schede fuori da ogni scheda finiscono in «La mia scheda» (stesso uuid del web, una sola volta su ogni dispositivo).
    @MainActor
    static func adoptLoose(context: ModelContext, name: String) {
        guard let all = try? context.fetch(FetchDescriptor<WorkoutTemplate>()) else { return }
        let loose = all.filter { $0.programRaw.isEmpty }
        guard !loose.isEmpty else { return }
        let existing = (try? context.fetch(FetchDescriptor<WorkoutProgram>())) ?? []
        if !existing.contains(where: { $0.uuid == WorkoutProgram.defaultUUID }) {
            let p = WorkoutProgram(name: name)
            p.uuid = WorkoutProgram.defaultUUID
            context.insert(p)
        }
        for t in loose { t.programRaw = WorkoutProgram.defaultUUID.uuidString }
        try? context.save()
    }

    /// La scheda a cui aggiungere un giorno nuovo: quella in uso, o «La mia scheda» se non ce n'è nessuna.
    @MainActor
    static func current(context: ModelContext, settings: AppSettings) -> WorkoutProgram {
        let all = (try? context.fetch(FetchDescriptor<WorkoutProgram>(sortBy: [SortDescriptor(\.sortIndex)]))) ?? []
        if let p = active(all, settings: settings) { return p }
        let p = WorkoutProgram(name: "La mia scheda")
        p.uuid = WorkoutProgram.defaultUUID
        context.insert(p)
        return p
    }

    /// Nomi dei giorni della settimana, 0 = lunedì.
    static let weekdays = ["lun", "mar", "mer", "gio", "ven", "sab", "dom"]
}
