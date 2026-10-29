import Foundation
import SwiftData

/// Il primo avvio: i macchinari che si propongono per ogni gruppo e la scheda che ne nasce. Come `common/onboarding.ts`
/// e `createStarterPlan`/`undoStarterPlan` del web: stessi id del catalogo, stesso ordine.
enum Onboarding {
    /// Cinque comuni in palestra, prima macchine e cavi che pesi liberi.
    static let suggested: [MuscleGroup: [String]] = [
        .petto: ["chest-press-machine", "pec-deck", "incline-chest-press-machine", "cable-fly", "bench-press-barbell"],
        .schiena: ["lat-pulldown", "seated-cable-row", "row-machine", "assisted-pull-up", "back-extension-machine"],
        .gambe: ["leg-press-45", "leg-extension", "seated-leg-curl", "standing-calf-machine", "hack-squat"],
        .spalle: ["shoulder-press-machine", "lateral-raise-machine", "reverse-pec-deck", "cable-lateral-raise", "face-pull-rope"],
        .braccia: ["curl-machine", "triceps-pushdown", "cable-curl", "triceps-machine", "preacher-curl-machine"],
        .core: ["crunch-machine", "cable-crunch", "rotary-torso", "roman-chair", "hanging-leg-raise"]
    ]

    /// Quanti, dall'inizio della lista, partono già scelti.
    static let preselected = 3

    /// Un giorno della scheda: il gruppo, il suo nome e i macchinari del catalogo, nell'ordine.
    struct Day {
        let group: MuscleGroup
        let name: String
        let machines: [CatalogEntry]
    }

    /// L'app ha già qualcosa dell'utente (macchinari, allenamenti, massimali, pesate)? Allora il primo avvio non serve. Come `hasData` del web.
    @MainActor
    static func hasData(_ context: ModelContext) -> Bool {
        let n = [
            (try? context.fetchCount(FetchDescriptor<Exercise>())) ?? 0,
            (try? context.fetchCount(FetchDescriptor<Workout>())) ?? 0,
            (try? context.fetchCount(FetchDescriptor<MaxRecord>())) ?? 0,
            (try? context.fetchCount(FetchDescriptor<BodyWeightEntry>())) ?? 0
        ].reduce(0, +)
        return n > 0
    }

    /// Cosa ha creato `createPlan`, per toglierlo se si torna indietro.
    struct Made {
        let program: UUID
        let exercises: [UUID]
    }

    /// Una scheda in uso con un giorno per gruppo, nell'ordine dato, righe senza serie né ripetizioni né peso.
    /// Il macchinario si crea dal catalogo solo se non ce l'hai già (non archiviato).
    @MainActor
    static func createPlan(name: String, days: [Day], context: ModelContext, settings: AppSettings) -> Made {
        let owned = (try? context.fetch(FetchDescriptor<Exercise>())) ?? []
        var made: [String: UUID] = [:]
        var created: [UUID] = []
        var top = owned.map(\.sortIndex).max() ?? -1
        let dayTop = ((try? context.fetch(FetchDescriptor<WorkoutTemplate>())) ?? []).map(\.sortIndex).max() ?? -1
        let programTop = ((try? context.fetch(FetchDescriptor<WorkoutProgram>())) ?? []).map(\.sortIndex).max() ?? -1

        let program = WorkoutProgram(name: name, sortIndex: programTop + 1)
        context.insert(program)
        for (i, day) in days.enumerated() {
            var items: [TemplateItem] = []
            for entry in day.machines {
                let id: UUID
                if let known = made[entry.id] ?? owned.first(where: { $0.catalogID == entry.id && !$0.isArchived })?.uuid {
                    id = known
                } else {
                    top += 1
                    let ex = Exercise(name: entry.name, muscleGroup: entry.muscleGroup, incrementStep: entry.step, notes: entry.notes, sortIndex: top)
                    context.insert(ex)
                    ex.catalogID = entry.id
                    ex.barWeight = Plates.defaultBar(for: entry.machineGlyph, name: entry.name)
                    ex.plateLoaded = Plates.guessPlateLoaded(glyph: entry.machineGlyph, name: entry.name)
                    made[entry.id] = ex.uuid
                    created.append(ex.uuid)
                    id = ex.uuid
                }
                if !items.contains(where: { $0.exercise == id }) { items.append(TemplateItem(exercise: id)) }
            }
            let template = WorkoutTemplate(name: day.name, items: items, sortIndex: dayTop + 1 + i)
            template.iconRaw = "muscle:\(day.group.rawValue)"
            template.programRaw = program.uuid.uuidString
            context.insert(template)
        }
        try? context.save()
        settings.activeProgram = program.uuid.uuidString
        return Made(program: program.uuid, exercises: created)
    }

    /// Toglie quello che ha fatto `createPlan` (si torna indietro dalla schermata finale).
    @MainActor
    static func undoPlan(_ made: Made, context: ModelContext, settings: AppSettings) {
        for t in (try? context.fetch(FetchDescriptor<WorkoutTemplate>())) ?? [] where t.programRaw == made.program.uuidString { context.delete(t) }
        for p in (try? context.fetch(FetchDescriptor<WorkoutProgram>())) ?? [] where p.uuid == made.program { context.delete(p) }
        for e in (try? context.fetch(FetchDescriptor<Exercise>())) ?? [] where made.exercises.contains(e.uuid) { context.delete(e) }
        try? context.save()
        if settings.activeProgram.uppercased() == made.program.uuidString { settings.activeProgram = "" }
    }
}
