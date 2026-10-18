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
        save(context)
        return set.isPersonalRecord
    }

    /// Salva solo se c'è davvero qualcosa: ogni salvataggio fa rileggere tutte le query e parte la sincronizzazione.
    private static func save(_ context: ModelContext) {
        if context.hasChanges { try? context.save() }
    }

    /// Ricalcola i record di un macchinario: uno per ogni serie che batte il migliore fino a quel
    /// momento (il suo uuid è quello della serie). Si confronta con quelli che ci sono già: restano
    /// (aggiornati se la serie è cambiata), si cancellano quelli non più vincenti, si creano i mancanti.
    static func recompute(_ exercise: Exercise, context: ModelContext) {
        let records = exercise.recordList.filter { !$0.isDeleted }
        let manual = records.filter { !$0.isEstimated }

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

        // Un solo record automatico per serie vincente; il resto (non più vincenti, doppioni) via.
        var kept: [UUID: MaxRecord] = [:]
        for record in records where record.isEstimated {
            if winners.contains(record.uuid.uuidString), kept[record.uuid] == nil { kept[record.uuid] = record } else { context.delete(record) }
        }

        let note = "Da allenamento"
        for set in sets {
            let isRecord = winners.contains(set.uuid.uuidString)
            if set.isPersonalRecord != isRecord { set.isPersonalRecord = isRecord }
            guard isRecord else { continue }
            if let record = kept[set.uuid] {
                if record.weight != set.weight { record.weight = set.weight }
                if record.reps != set.reps { record.reps = set.reps }
                if record.date != set.createdAt { record.date = set.createdAt }
                if record.note != note { record.note = note }
                continue
            }
            let record = MaxRecord(weight: set.weight, reps: set.reps, date: set.createdAt, isEstimated: true, note: note)
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
        save(context)
    }

    /// Elimina una serie e ricalcola: se era un record, torna quello di prima (o il successivo).
    static func delete(_ set: WorkoutSet, context: ModelContext) {
        let exercise = set.exercise
        context.delete(set)
        save(context)
        if let exercise { recompute(exercise, context: context) }
        save(context)
    }

    /// Elimina un allenamento con le sue serie: i record che aveva fatto nascere spariscono con lui.
    static func delete(_ workout: Workout, context: ModelContext) {
        let exercises = Set(workout.setList.compactMap(\.exercise))
        context.delete(workout)
        save(context)
        for exercise in exercises { recompute(exercise, context: context) }
        save(context)
    }

    /// Elimina un massimale dallo storico. Uno inserito a mano sparisce. Uno "da allenamento"
    /// non si cancella da solo (si ricalcolerebbe): la serie che l'ha fatto nascere smette di
    /// contare per i record, e resta nell'allenamento.
    static func delete(_ record: MaxRecord, context: ModelContext) {
        guard let exercise = record.exercise else { context.delete(record); save(context); return }
        if record.isEstimated {
            if let set = exercise.setList.first(where: { $0.uuid == record.uuid }) { set.excludedFromRecords = true }
        } else {
            context.delete(record)
            save(context)
        }
        recompute(exercise, context: context)
        save(context)
    }

    /// Massimali toccati durante una sessione, per il riepilogo di fine allenamento.
    static func personalRecords(in workout: Workout) -> [WorkoutSet] {
        workout.orderedSets.filter(\.isPersonalRecord)
    }
}
