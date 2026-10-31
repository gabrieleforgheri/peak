package com.gabrieleforghieri.peak.domain

/**
 * Massimali e record a partire dagli allenamenti (`Peak/Store/RecordService.swift`).
 *
 * I record «da allenamento» (`isEstimated`) non sono dati ma una conseguenza delle serie: dopo ogni modifica si ricalcolano da
 * zero per quel macchinario ([recompute]), con la regola di [RecordRules]. Un massimale inserito a mano non si tocca mai. Per
 * questo cancellare una serie, correggerla o eliminare un intero allenamento lascia sempre i record giusti.
 *
 * È l'unico percorso di scrittura delle serie: sia la sessione sia le serie che arrivano da fuori passano di qui. Il database
 * sta dietro [RecordStorage] (Room nell'app, una copia in memoria nei test).
 */
class RecordService(private val storage: RecordStorage) {

    /** Un record di un macchinario, com'è nel database. */
    data class RecordRow(
        val uuid: String, val date: Long, val weight: Double, val reps: Int,
        val isEstimated: Boolean, val note: String, val singleArm: Boolean,
    )

    /** Una serie di un allenamento che esiste ancora. */
    data class SetRow(
        val uuid: String, val createdAt: Long, val order: Int, val weight: Double, val reps: Int,
        val isWarmup: Boolean, val excludedFromRecords: Boolean, val isPersonalRecord: Boolean, val singleArm: Boolean,
    )

    /** Tutto quello che serve a ricalcolare un macchinario. */
    data class ExerciseState(val uuid: String, val limbs: Limbs, val records: List<RecordRow>, val sets: List<SetRow>)

    /** Cosa cambiare nel database dopo un ricalcolo. */
    data class Diff(
        val deleteRecords: Set<String>,
        /** Record da creare o aggiornare (l'uuid è quello della serie). */
        val upsertRecords: List<RecordRow>,
        /** uuid della serie → nuovo valore di `isPersonalRecord`, solo per quelle che cambiano. */
        val setFlags: Map<String, Boolean>,
    ) {
        val isEmpty: Boolean get() = deleteRecords.isEmpty() && upsertRecords.isEmpty() && setFlags.isEmpty()
    }

    /** Il database, visto da qui. Tutte le operazioni di una chiamata di [RecordService] stanno in una transazione. */
    interface RecordStorage {
        suspend fun <T> transaction(block: suspend () -> T): T
        suspend fun exerciseState(exercise: String): ExerciseState?
        suspend fun allExercises(): List<String>
        suspend fun nextOrder(workout: String): Int
        suspend fun insertSet(set: SetRow, workout: String, exercise: String?)
        suspend fun apply(exercise: String, diff: Diff)
        suspend fun exerciseOfSet(set: String): String?
        suspend fun deleteSet(set: String)
        suspend fun exercisesOfWorkout(workout: String): Set<String>
        suspend fun deleteWorkout(workout: String)
        /** `null` se il record non esiste; `exercise` è `null` se non ha macchinario. */
        suspend fun record(uuid: String): RecordHead?
        suspend fun deleteRecord(uuid: String)
        suspend fun excludeSet(uuid: String)
        suspend fun personalRecordSets(workout: String): List<String>
    }

    data class RecordHead(val exercise: String?, val isEstimated: Boolean)

    /**
     * Registra una serie in coda all'allenamento e ricalcola i record del macchinario. Restituisce `true` se la serie è un nuovo
     * massimale. Senza macchinario (eliminato mentre il Watch era in giro) non c'è nulla da ricalcolare.
     */
    suspend fun addSet(
        workout: String, exercise: String?, weight: Double, reps: Int, isWarmup: Boolean,
        singleArm: Boolean = false, now: Long = System.currentTimeMillis(), uuid: String = Uuids.new(),
    ): Boolean = storage.transaction {
        val order = storage.nextOrder(workout)
        val row = SetRow(uuid, now, order, weight, reps, isWarmup, excludedFromRecords = false, isPersonalRecord = false, singleArm = singleArm)
        storage.insertSet(row, workout, exercise)
        if (exercise != null) recompute(exercise)
        storage.exerciseState(exercise ?: return@transaction false)?.sets?.firstOrNull { it.uuid == uuid }?.isPersonalRecord == true
    }

    /**
     * Ricalcola i record di un macchinario: uno per ogni serie che batte il migliore fino a quel momento (il suo uuid è quello della
     * serie). Si confronta con quelli che ci sono già: restano (aggiornati se la serie è cambiata), si cancellano quelli non più
     * vincenti, si creano i mancanti.
     */
    suspend fun recompute(exercise: String) = storage.transaction {
        val state = storage.exerciseState(exercise) ?: return@transaction
        val diff = diff(state)
        if (!diff.isEmpty) storage.apply(exercise, diff)
    }

    /** Tutti i macchinari: dopo un ripristino, una sincronizzazione o un aggiornamento. */
    suspend fun recomputeAll() = storage.transaction {
        for (e in storage.allExercises()) recompute(e)
    }

    /** Elimina una serie e ricalcola: se era un record, torna quello di prima (o il successivo). */
    suspend fun deleteSet(set: String) = storage.transaction {
        val exercise = storage.exerciseOfSet(set)
        storage.deleteSet(set)
        if (exercise != null) recompute(exercise)
    }

    /** Elimina un allenamento con le sue serie: i record che aveva fatto nascere spariscono con lui. */
    suspend fun deleteWorkout(workout: String) = storage.transaction {
        val exercises = storage.exercisesOfWorkout(workout)
        storage.deleteWorkout(workout)
        for (e in exercises) recompute(e)
    }

    /**
     * Elimina un massimale dallo storico. Uno inserito a mano sparisce. Uno «da allenamento» non si cancella da solo (si ricalcolerebbe):
     * la serie che l'ha fatto nascere smette di contare per i record, e resta nell'allenamento.
     */
    suspend fun deleteRecord(record: String) = storage.transaction {
        val head = storage.record(record) ?: return@transaction
        val exercise = head.exercise
        if (exercise == null) { storage.deleteRecord(record); return@transaction }
        if (head.isEstimated) storage.excludeSet(record) else storage.deleteRecord(record)
        recompute(exercise)
    }

    /** Massimali toccati durante una sessione, per il riepilogo di fine allenamento. */
    suspend fun personalRecords(workout: String): List<String> = storage.personalRecordSets(workout)

    companion object {
        private const val NOTE = "Da allenamento"

        /** Il calcolo puro: cosa va cambiato perché i record del macchinario tornino giusti. */
        fun diff(state: ExerciseState): Diff {
            val limbs = state.limbs
            val manual = state.records.filter { !it.isEstimated }
            val winners = RecordRules.personalRecordSets(
                manual.map { RecordRules.ManualRecord(it.uuid, it.date, it.weight, it.reps, Limbs.lineArm(limbs, it.singleArm)) },
                state.sets.map {
                    RecordRules.SetEntry(it.uuid, it.createdAt, it.order, it.weight, it.reps, it.isWarmup, it.excludedFromRecords, Limbs.lineArm(limbs, it.singleArm))
                },
            ).toHashSet()

            // Un solo record automatico per serie vincente; il resto (non più vincenti, doppioni) via.
            val kept = HashMap<String, RecordRow>()
            val delete = HashSet<String>()
            for (r in state.records) {
                if (!r.isEstimated) continue
                if (r.uuid in winners && r.uuid !in kept) kept[r.uuid] = r else delete += r.uuid
            }

            val upsert = mutableListOf<RecordRow>()
            val flags = HashMap<String, Boolean>()
            for (s in state.sets) {
                val isRecord = s.uuid in winners
                if (s.isPersonalRecord != isRecord) flags[s.uuid] = isRecord
                if (!isRecord) continue
                val line = Limbs.lineArm(limbs, s.singleArm)
                val existing = kept[s.uuid]
                val wanted = RecordRow(s.uuid, s.createdAt, s.weight, s.reps, isEstimated = true, note = NOTE, singleArm = line)
                if (existing == null || existing != wanted) upsert += wanted
            }
            return Diff(delete, upsert, flags)
        }
    }
}
