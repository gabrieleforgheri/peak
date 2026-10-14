import Foundation
import SwiftData

/// Massimali e record a partire dagli allenamenti.
///
/// I record "da allenamento" (`MaxRecord.isEstimated`) non sono dati ma una conseguenza delle
/// serie: dopo ogni modifica si ricalcolano da zero per quel macchinario (`recompute`), con
/// la regola di `RecordRules`. Un massimale inserito a mano (`isEstimated == false`) non si
/// tocca mai. Per questo cancellare una serie, correggerla o eliminare un intero allenamento
/// lascia sempre i record giusti, anche per le serie successive.
enum RecordService {

    /// Registra una serie in coda all'allenamento e ricalcola i record del macchinario.
    /// Unico percorso sia per la sessione sull'iPhone sia per le serie dal Watch.
    /// Restituisce `true` se la serie è un nuovo massimale.
    @discardableResult
    static func addSet(
        to workout: Workout,
        exercise: Exercise,
        weight: Double,
        reps: Int,
        isWarmup: Bool,
        context: ModelContext
    ) -> Bool {
        let order = (workout.setList.map(\.order).max() ?? -1) + 1
        let set = WorkoutSet(weight: weight, reps: reps, isWarmup: isWarmup, order: order)
        context.insert(set)
        set.exercise = exercise
        set.workout = workout
        recompute(exercise, context: context)
        try? context.save()
        return set.isPersonalRecord
    }

    /// Ricalcola i record di un macchinario: via i record automatici, poi uno per ogni serie
    /// che batte il migliore fino a quel momento (il suo uuid è quello della serie).
    static func recompute(_ exercise: Exercise, context: ModelContext) {
        let records = exercise.recordList.filter { !$0.isDeleted }
        let manual = records.filter { !$0.isEstimated }
        for record in records where record.isEstimated { context.delete(record) }

        // Solo le serie di un allenamento che esiste ancora.
        let sets = exercise.setList.filter { !$0.isDeleted && $0.workout != nil }
        let winners = Set(RecordRules.personalRecordSets(
            manual: manual.map {
                RecordRules.ManualRecord(uuid: $0.uuid.uuidString, date: $0.date, weight: $0.weight, reps: $0.reps)
            },
            sets: sets.map {
                RecordRules.SetEntry(
                    uuid: $0.uuid.uuidString, createdAt: $0.createdAt, order: $0.order, weight: $0.weight,
                    reps: $0.reps, isWarmup: $0.isWarmup, excludedFromRecords: $0.excludedFromRecords
                )
            }
        ))

        for set in sets {
            let isRecord = winners.contains(set.uuid.uuidString)
            if set.isPersonalRecord != isRecord { set.isPersonalRecord = isRecord }
            guard isRecord else { continue }
            let record = MaxRecord(
                weight: set.weight, reps: set.reps, date: set.createdAt, isEstimated: true, note: "Da allenamento"
            )
            record.uuid = set.uuid
            context.insert(record)
            record.exercise = exercise
        }
    }

    /// Tutti i macchinari: dopo un ripristino, una sincronizzazione o l'aggiornamento a Peak 2.0.
    static func recomputeAll(context: ModelContext) {
        for exercise in (try? context.fetch(FetchDescriptor<Exercise>())) ?? [] {
            recompute(exercise, context: context)
        }
        try? context.save()
    }

    /// Elimina una serie e ricalcola: se era un record, torna quello di prima (o il successivo).
    static func delete(_ set: WorkoutSet, context: ModelContext) {
        let exercise = set.exercise
        context.delete(set)
        try? context.save()
        if let exercise { recompute(exercise, context: context) }
        try? context.save()
    }

    /// Elimina un allenamento con le sue serie: i record che aveva fatto nascere spariscono con lui.
    static func delete(_ workout: Workout, context: ModelContext) {
        let exercises = Set(workout.setList.compactMap(\.exercise))
        context.delete(workout)
        try? context.save()
        for exercise in exercises { recompute(exercise, context: context) }
        try? context.save()
    }

    /// Elimina un massimale dallo storico. Uno inserito a mano sparisce. Uno "da allenamento"
    /// non si cancella da solo (si ricalcolerebbe): la serie che l'ha fatto nascere smette di
    /// contare per i record, e resta nell'allenamento.
    static func delete(_ record: MaxRecord, context: ModelContext) {
        guard let exercise = record.exercise else { context.delete(record); try? context.save(); return }
        if record.isEstimated {
            if let set = exercise.setList.first(where: { $0.uuid == record.uuid }) { set.excludedFromRecords = true }
        } else {
            context.delete(record)
            try? context.save()
        }
        recompute(exercise, context: context)
        try? context.save()
    }

    /// Massimali toccati durante una sessione, per il riepilogo di fine allenamento.
    static func personalRecords(in workout: Workout) -> [WorkoutSet] {
        workout.orderedSets.filter(\.isPersonalRecord)
    }
}
