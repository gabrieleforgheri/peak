package com.gabrieleforghieri.peak.data.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Le query dell'area Allenamenti (`Features/Workouts/`): allenamenti, giorni, schede, serie. Proprietario: l'area workouts.
 *
 * Solo scritture di pochi campi: la sessione cambia la nota, il piano o l'ora di fine di un allenamento senza riscrivere la riga
 * intera (una sincronizzazione che arriva nel frattempo non si perde). Le letture sono in `PeakRepository`; le serie e i massimali si
 * scrivono solo dal `RecordService`. Si prende da `PeakDatabase.areaWorkouts()`.
 */
@Dao
interface WorkoutsDao {
    /** Chiude un allenamento in corso; uno già chiuso resta com'è (un doppio comando non chiude due volte). */
    @Query("UPDATE workout SET endedAt = :now WHERE uuid = :uuid AND endedAt IS NULL")
    suspend fun finish(uuid: String, now: Long): Int

    @Query("UPDATE workout SET note = :note WHERE uuid = :uuid")
    suspend fun setNote(uuid: String, note: String)

    @Query("UPDATE workout SET name = :name, focus = :focus WHERE uuid = :uuid")
    suspend fun setNameAndFocus(uuid: String, name: String, focus: String)

    @Query("UPDATE workout SET plan = :plan WHERE uuid = :uuid")
    suspend fun setPlan(uuid: String, plan: String)

    /** La correzione di una serie: il resto (ordine, data) resta; i record si ricalcolano dopo, col `RecordService`. */
    @Query("UPDATE workout_set SET weight = :weight, reps = :reps, isWarmup = :isWarmup, singleArm = :singleArm WHERE uuid = :uuid")
    suspend fun updateSet(uuid: String, weight: Double, reps: Int, isWarmup: Boolean, singleArm: Boolean)

    /** «Aggiorna il giorno con le modifiche di oggi». */
    @Query("UPDATE workout_template SET items = :items WHERE uuid = :uuid")
    suspend fun setTemplateItems(uuid: String, items: String)

    @Query("UPDATE workout_template SET weekday = :weekday WHERE uuid = :uuid")
    suspend fun setTemplateWeekday(uuid: String, weekday: Int)

    @Query("DELETE FROM workout_template WHERE program = :program")
    suspend fun deleteTemplatesOfProgram(program: String)
}
