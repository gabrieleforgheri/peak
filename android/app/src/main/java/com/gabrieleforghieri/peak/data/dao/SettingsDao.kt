package com.gabrieleforghieri.peak.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Le query dell'area Impostazioni (`Features/Settings/`): archivio dei macchinari e cancellazione dei dati. Proprietario: l'area settings.
 * Non è [com.gabrieleforghieri.peak.data.SettingDao] (al singolare), che legge e scrive la tabella `setting` e resta in `Daos.kt`.
 */
@Dao
interface SettingsDao {
    /** «Ripristina» di `ArchivedExercisesView`: il macchinario torna nelle liste. */
    @Query("UPDATE exercise SET isArchived = 0 WHERE uuid = :uuid")
    suspend fun unarchive(uuid: String)

    @Query("DELETE FROM workout_set") suspend fun deleteSets()
    @Query("DELETE FROM workout") suspend fun deleteWorkouts()
    @Query("DELETE FROM max_record") suspend fun deleteRecords()
    @Query("DELETE FROM exercise") suspend fun deleteExercises()
    @Query("DELETE FROM body_weight") suspend fun deleteBodyWeights()
    @Query("DELETE FROM workout_template") suspend fun deleteTemplates()
    @Query("DELETE FROM workout_program") suspend fun deletePrograms()

    /** «Cancella tutti i dati» di `DataSettingsView`: tutto tranne le impostazioni, in una transazione sola. */
    @Transaction
    suspend fun eraseAll() {
        deleteSets(); deleteWorkouts(); deleteRecords(); deleteExercises(); deleteBodyWeights(); deleteTemplates(); deletePrograms()
    }
}
