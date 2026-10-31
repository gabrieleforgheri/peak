package com.gabrieleforghieri.peak.sync

import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity

/** Tutto il database in un colpo: la base su cui lavorano la sincronizzazione e i backup (logica pura, senza Room). */
data class Snapshot(
    val exercises: List<ExerciseEntity> = emptyList(),
    val records: List<MaxRecordEntity> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val sets: List<WorkoutSetEntity> = emptyList(),
    val bodyWeights: List<BodyWeightEntity> = emptyList(),
    val templates: List<WorkoutTemplateEntity> = emptyList(),
    val programs: List<WorkoutProgramEntity> = emptyList(),
    /** Chiave → valore JSON, come nella tabella `setting`. */
    val settings: Map<String, String> = emptyMap(),
) {
    /** C'è un allenamento non finito. */
    val hasActiveWorkout: Boolean get() = workouts.any { it.endedAt == null }

    /** Qualcosa di suo oltre al catalogo di serie: allenamenti, massimali inseriti a mano o pesate (`localHasUserData`). */
    val hasUserData: Boolean get() = workouts.isNotEmpty() || records.isNotEmpty() || bodyWeights.isNotEmpty()

    /**
     * Il risultato di scrivere [c] nel database, con le stesse conseguenze delle chiavi esterne di Room: via un macchinario spariscono
     * i suoi massimali e le serie restano senza; via un allenamento spariscono le sue serie.
     */
    fun applying(c: Changes): Snapshot {
        var s = if (c.wipe) copy(exercises = emptyList(), records = emptyList(), workouts = emptyList(), sets = emptyList(), bodyWeights = emptyList(), templates = emptyList(), programs = emptyList()) else this
        fun <T> merge(old: List<T>, new: List<T>, gone: Set<String>, id: (T) -> String): List<T> {
            val replaced = new.associateBy(id)
            return old.filter { id(it) !in gone && id(it) !in replaced }.plus(replaced.values.filter { id(it) !in gone })
        }
        val gone = c.gone
        s = s.copy(
            exercises = merge(s.exercises, c.exercises, gone.exercises) { it.uuid },
            workouts = merge(s.workouts, c.workouts, gone.workouts) { it.uuid },
            templates = merge(s.templates, c.templates, gone.templates) { it.uuid },
            programs = merge(s.programs, c.programs, gone.programs) { it.uuid },
            bodyWeights = merge(s.bodyWeights, c.bodyWeights, gone.bodyWeights) { it.uuid },
            records = merge(s.records, c.records, gone.records) { it.uuid },
            sets = merge(s.sets, c.sets, gone.sets) { it.uuid },
            settings = s.settings + c.settings,
        )
        val liveExercises = s.exercises.mapTo(HashSet()) { it.uuid }
        val liveWorkouts = s.workouts.mapTo(HashSet()) { it.uuid }
        return s.copy(
            records = s.records.filter { it.exerciseUuid == null || it.exerciseUuid in liveExercises },
            sets = s.sets.filter { it.workoutUuid != null && it.workoutUuid in liveWorkouts }
                .map { if (it.exerciseUuid != null && it.exerciseUuid !in liveExercises) it.copy(exerciseUuid = null) else it },
        )
    }
}

/** Gli uuid cancellati, per tipo. */
data class Gone(
    val exercises: Set<String> = emptySet(),
    val records: Set<String> = emptySet(),
    val workouts: Set<String> = emptySet(),
    val sets: Set<String> = emptySet(),
    val bodyWeights: Set<String> = emptySet(),
    val templates: Set<String> = emptySet(),
    val programs: Set<String> = emptySet(),
)

/** Cosa scrivere nel database in una sola transazione: righe nuove o cambiate, uuid da togliere, impostazioni. `wipe` = prima si svuota tutto (impostazioni escluse). */
data class Changes(
    val wipe: Boolean = false,
    val exercises: List<ExerciseEntity> = emptyList(),
    val records: List<MaxRecordEntity> = emptyList(),
    val workouts: List<WorkoutEntity> = emptyList(),
    val sets: List<WorkoutSetEntity> = emptyList(),
    val bodyWeights: List<BodyWeightEntity> = emptyList(),
    val templates: List<WorkoutTemplateEntity> = emptyList(),
    val programs: List<WorkoutProgramEntity> = emptyList(),
    val gone: Gone = Gone(),
    val settings: Map<String, String> = emptyMap(),
)

/** Il database, visto dalla sincronizzazione e dai backup: Room nell'app, una copia in memoria nelle prove. */
interface SyncStorage {
    /** Il database di ripiego (non si è aperto quello vero): non si sincronizza mai, per non spingere al server un «tutto cancellato». */
    val isFallback: Boolean

    suspend fun snapshot(): Snapshot

    /** Scrive tutto in una transazione sola. */
    suspend fun commit(changes: Changes)

    /** I massimali «da allenamento» si ricalcolano dalle serie dopo ogni scrittura da fuori (`RecordService.recomputeAll`). */
    suspend fun recomputeRecords()
}

/** Le foto dei macchinari: i byte su disco (JPEG), nel database resta solo lo sha256. */
interface PhotoStore {
    fun has(sha: String): Boolean
    fun read(sha: String): ByteArray?

    /** Salva i byte e restituisce il loro sha256. */
    fun write(bytes: ByteArray): String

    /** Salva i byte sotto lo sha dato, se coincide con quello dei byte; altrimenti `false`. */
    fun writeAs(sha: String, bytes: ByteArray): Boolean = if (sha256Hex(bytes) == sha) { write(bytes); true } else false
}
