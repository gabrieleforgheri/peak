import Foundation
import SwiftData

/// Una sessione di allenamento.
@Model
final class Workout {
    var uuid: UUID = UUID()
    var date: Date = Date()
    /// `nil` finché la sessione è in corso.
    var endedAt: Date?
    var note: String = ""
    /// Nome dato a inizio sessione, es. "Push 1". Vuoto = si mostra la data.
    var name: String = ""
    /// Gruppi scelti a inizio sessione ("cosa alleni oggi"), come rawValue separati
    /// da virgola. Vuoto = nessun filtro.
    var focusRaw: String = ""
    /// uuid della scheda da cui è partito l'allenamento, vuoto = nessuna. La scheda può sparire: non è un legame.
    var templateRaw: String = ""
    /// La lista di oggi: copia delle righe della scheda all'avvio, poi modificata in sessione
    /// (sostituisci, togli, sposta, aggiungi). JSON di `[TemplateItem]`, vuoto = nessuna riga.
    var planRaw: String = ""

    @Relationship(deleteRule: .cascade, inverse: \WorkoutSet.workout)
    var sets: [WorkoutSet]? = []

    init(date: Date = Date(), note: String = "") {
        self.uuid = UUID()
        self.date = date
        self.note = note
    }

    var setList: [WorkoutSet] { sets ?? [] }

    var isActive: Bool { endedAt == nil }

    var focus: Set<MuscleGroup> {
        get { Set(focusRaw.split(separator: ",").compactMap { MuscleGroup(rawValue: String($0)) }) }
        set { focusRaw = MuscleGroup.allCases.filter(newValue.contains).map(\.rawValue).joined(separator: ",") }
    }

    var plan: [TemplateItem] {
        get { TemplateItems.decode(planRaw) }
        set { planRaw = TemplateItems.encode(newValue) }
    }

    var templateUUID: UUID? { UUID(uuidString: templateRaw) }

    private var planSets: [PlanSet] {
        orderedSets.map { PlanSet(exercise: $0.exercise?.uuid, weight: $0.weight, reps: $0.reps, isWarmup: $0.isWarmup) }
    }

    /// La lista di oggi in ordine (vedi `Plan.session`). `exists`: gli esercizi che ci sono ancora.
    func items(_ mode: Plan.Mode = .view, exists: (UUID) -> Bool) -> [TemplateItem] {
        Plan.session(plan: plan, sets: planSets, exists: exists, mode: mode)
    }

    var orderedSets: [WorkoutSet] {
        setList.sorted { $0.order < $1.order }
    }

    /// Volume totale (kg × ripetizioni), riscaldamento escluso.
    var totalVolume: Double {
        setList.filter { !$0.isWarmup }.reduce(0) { $0 + $1.volume }
    }

    var workingSetCount: Int {
        setList.filter { !$0.isWarmup }.count
    }

    /// Esercizi toccati nella sessione, nell'ordine in cui sono comparsi.
    var exercisesInOrder: [Exercise] {
        var seen = Set<PersistentIdentifier>()
        var result: [Exercise] = []
        for set in orderedSets {
            guard let exercise = set.exercise else { continue }
            if seen.insert(exercise.persistentModelID).inserted {
                result.append(exercise)
            }
        }
        return result
    }

    func sets(for exercise: Exercise) -> [WorkoutSet] {
        orderedSets.filter { $0.exercise?.persistentModelID == exercise.persistentModelID }
    }

    /// Se una serie nuova di questo esercizio parte «a un braccio»: come l'ultima fatta qui in allenamento
    /// (riscaldamento compreso, così la tocchi solo quando cambi); la prima della sessione, come l'ultima di sempre.
    func defaultSingleArm(for exercise: Exercise) -> Bool {
        sets(for: exercise).last?.singleArm
            ?? exercise.setList.max { $0.createdAt < $1.createdAt }?.singleArm
            ?? false
    }

    var duration: TimeInterval? {
        guard let endedAt else { return nil }
        return endedAt.timeIntervalSince(date)
    }
}
