import Foundation
import SwiftData

/// Una riga di scheda. Serie e ripetizioni 0 = non indicato; peso in kg, `nil` = si mostra l'ultimo usato.
/// Stesso formato del documento `template` del server (`TemplateItem` in common/docs.ts).
struct TemplateItem: Codable, Hashable, Identifiable {
    var exercise: UUID
    var sets: Int = 0
    var repsMin: Int = 0
    var repsMax: Int = 0
    var weight: Double?

    var id: UUID { exercise }
}

/// Una scheda: nome e righe in ordine. Le righe stanno in un JSON (come `Workout.focusRaw`):
/// niente seconda tabella e il documento sincronizzato è lo stesso testo.
@Model
final class WorkoutTemplate {
    var uuid: UUID = UUID()
    var name: String = ""
    var itemsRaw: String = ""
    var sortIndex: Int = 0
    var createdAt: Date = Date()
    /// Nascosta agli amici.
    var isHidden: Bool = false
    /// Icona del giorno (nome di `TemplateIcon`); vuoto = dedotta dal nome.
    var iconRaw: String = ""
    /// uuid della scheda che lo contiene; vuoto = fuori da ogni scheda (si adotta in «La mia scheda»).
    var programRaw: String = ""
    /// Giorno fisso della settimana, 0 = lunedì … 6 = domenica; −1 = nessuno.
    var weekday: Int = -1

    init(name: String, items: [TemplateItem] = [], sortIndex: Int = 0) {
        self.uuid = UUID()
        self.name = name
        self.sortIndex = sortIndex
        self.itemsRaw = TemplateItems.encode(items)
    }

    var items: [TemplateItem] {
        get { TemplateItems.decode(itemsRaw) }
        set { itemsRaw = TemplateItems.encode(newValue) }
    }
}

/// La scheda generale: un contenitore di giorni (`WorkoutTemplate`). Con `useDays` ogni giorno può avere un giorno fisso della settimana.
@Model
final class WorkoutProgram {
    var uuid: UUID = UUID()
    var name: String = ""
    var sortIndex: Int = 0
    var createdAt: Date = Date()
    var useDays: Bool = false

    init(name: String, sortIndex: Int = 0) {
        self.uuid = UUID()
        self.name = name
        self.sortIndex = sortIndex
    }

    /// Stesso uuid del web (`DEFAULT_PROGRAM` in actions.ts): «La mia scheda» è la stessa su ogni dispositivo.
    static let defaultUUID = UUID(uuidString: "00000000-0000-4000-8000-5045414B0001")!
}

/// Conversioni delle righe: JSON per il disco e per il backup, dizionari per i documenti del server.
enum TemplateItems {
    static func encode(_ items: [TemplateItem]) -> String {
        guard !items.isEmpty, let data = try? JSONEncoder().encode(items) else { return "" }
        return String(decoding: data, as: UTF8.self)
    }

    static func decode(_ raw: String) -> [TemplateItem] {
        guard !raw.isEmpty, let items = try? JSONDecoder().decode([TemplateItem].self, from: Data(raw.utf8)) else { return [] }
        return items
    }

    /// Il documento del server: chiavi sempre presenti, `weight` nullo se non scritto.
    static func json(_ items: [TemplateItem]) -> [[String: Any]] {
        items.map {
            ["exercise": $0.exercise.uuidString, "sets": $0.sets, "repsMin": $0.repsMin, "repsMax": $0.repsMax,
             "weight": $0.weight.map { $0 as Any } ?? NSNull()]
        }
    }

    /// Dal documento del server. Una riga rotta si salta, le altre restano.
    static func parse(_ value: Any?) -> [TemplateItem] {
        (value as? [[String: Any]] ?? []).compactMap { d in
            guard let id = (d["exercise"] as? String).flatMap(UUID.init(uuidString:)) else { return nil }
            func int(_ k: String) -> Int { (d[k] as? NSNumber)?.intValue ?? 0 }
            return TemplateItem(exercise: id, sets: int("sets"), repsMin: int("repsMin"), repsMax: int("repsMax"),
                                weight: (d["weight"] as? NSNumber)?.doubleValue)
        }
    }
}

/// Una serie vista dal piano: basta l'esercizio, il carico, le ripetizioni e se è di riscaldamento.
struct PlanSet {
    var exercise: UUID?
    var weight: Double
    var reps: Int
    var isWarmup: Bool
}

/// La lista degli esercizi di un allenamento e il suo rapporto con la scheda di partenza.
/// Stessi conti di `common/plan.ts`.
enum Plan {
    /// `view`: le righe fuori piano sono vuote. `targets`: prendono ciò che si è fatto (per aggiornare la
    /// scheda senza toccare gli obiettivi scritti). `done`: ogni riga con serie prende ciò che si è fatto.
    enum Mode { case view, targets, done }

    static func blank(_ exercise: UUID) -> TemplateItem { TemplateItem(exercise: exercise) }

    /// Serie di lavoro, ripetizioni min–max e peso più alto (kg) di un esercizio.
    static func item(from sets: [PlanSet], exercise: UUID) -> TemplateItem {
        let work = sets.filter { $0.exercise == exercise && !$0.isWarmup && $0.reps > 0 }
        guard !work.isEmpty else { return blank(exercise) }
        let reps = work.map(\.reps)
        return TemplateItem(
            exercise: exercise,
            sets: min(20, work.count),
            repsMin: min(100, reps.min()!),
            repsMax: min(100, reps.max()!),
            weight: min(2000, work.map(\.weight).max()!)
        )
    }

    /// La lista di oggi, in ordine: le righe del piano il cui esercizio esiste ancora, poi gli esercizi con
    /// serie che nel piano non ci sono.
    static func session(plan: [TemplateItem], sets: [PlanSet], exists: (UUID) -> Bool, mode: Mode = .view) -> [TemplateItem] {
        var out: [TemplateItem] = []
        var seen = Set<UUID>()
        for p in plan where !seen.contains(p.exercise) && exists(p.exercise) {
            seen.insert(p.exercise)
            if mode == .done {
                let done = item(from: sets, exercise: p.exercise)
                out.append(done.sets > 0 ? done : p)
            } else {
                out.append(p)
            }
        }
        for s in sets {
            guard let id = s.exercise, !seen.contains(id), exists(id) else { continue }
            seen.insert(id)
            out.append(mode == .view ? blank(id) : item(from: sets, exercise: id))
        }
        return out
    }

    static func adding(_ list: [TemplateItem], _ exercise: UUID) -> [TemplateItem] {
        list.contains { $0.exercise == exercise } ? list : list + [blank(exercise)]
    }

    static func removing(_ list: [TemplateItem], _ exercise: UUID) -> [TemplateItem] {
        list.filter { $0.exercise != exercise }
    }

    /// Al posto di `from` mette `to`: stesse serie e ripetizioni, il peso torna «ultimo usato».
    static func replacing(_ list: [TemplateItem], from: UUID, to: UUID) -> [TemplateItem] {
        guard !list.contains(where: { $0.exercise == to }) else { return list }
        return list.map { item in
            guard item.exercise == from else { return item }
            var copy = item
            copy.exercise = to
            copy.weight = nil
            return copy
        }
    }

    static func moving(_ list: [TemplateItem], _ exercise: UUID, by step: Int) -> [TemplateItem] {
        guard let i = list.firstIndex(where: { $0.exercise == exercise }) else { return list }
        let j = i + step
        guard list.indices.contains(j) else { return list }
        var out = list
        out.swapAt(i, j)
        return out
    }

    /// La lista di oggi è diversa da quella della scheda (esercizi o ordine)? Gli obiettivi non contano.
    static func differs(today: [TemplateItem], template: [TemplateItem], exists: (UUID) -> Bool) -> Bool {
        today.map(\.exercise) != template.filter { exists($0.exercise) }.map(\.exercise)
    }

    /// «2/4 serie · 8–10 rip · 60 kg»: solo i campi compilati; senza peso scritto, l'ultimo usato.
    static func targetLine(_ item: TemplateItem, done: Int, lastKilograms: Double?, settings: AppSettings) -> String {
        var parts: [String] = []
        if item.sets > 0 { parts.append("\(done)/\(item.sets) serie") }
        if item.repsMax > 0 {
            parts.append(item.repsMin == item.repsMax ? "\(item.repsMax) rip" : "\(item.repsMin)–\(item.repsMax) rip")
        }
        if let weight = item.weight { parts.append(Fmt.weight(weight, unit: settings.unit)) }
        else if let last = lastKilograms { parts.append("ultimo \(Fmt.weight(last, unit: settings.unit))") }
        return parts.joined(separator: " · ")
    }
}
