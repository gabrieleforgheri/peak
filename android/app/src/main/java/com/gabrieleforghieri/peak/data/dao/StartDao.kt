package com.gabrieleforghieri.peak.data.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Le query dell'area Avvio (`Features/Root/`): avvio, tutorial, obiettivo, collegamento. Proprietario: l'area start.
 *
 * Servono al primo avvio (`Onboarding.hasData`, `createPlan`, `undoPlan`) e a `Programs.adoptLoose`; le scritture vere passano da
 * `ui/start/StartActions.kt`, dentro una transazione. Si prende da `PeakDatabase.areaStart()`.
 */
@Dao
interface StartDao {
    /** Quanti macchinari, allenamenti, massimali e pesate ci sono (`Onboarding.hasData`: se non è zero il primo avvio non serve). */
    @Query("SELECT (SELECT COUNT(*) FROM exercise) + (SELECT COUNT(*) FROM workout) + (SELECT COUNT(*) FROM max_record) + (SELECT COUNT(*) FROM body_weight)")
    suspend fun userDataCount(): Int

    /** Il sortIndex più alto che c'è già (`null` = nessuno), per mettere i nuovi in fondo. */
    @Query("SELECT MAX(sortIndex) FROM exercise")
    suspend fun topExercise(): Int?

    @Query("SELECT MAX(sortIndex) FROM workout_template")
    suspend fun topTemplate(): Int?

    @Query("SELECT MAX(sortIndex) FROM workout_program")
    suspend fun topProgram(): Int?

    /** I macchinari che hai già, per non crearli due volte (`Onboarding.Owned`). */
    @Query("SELECT uuid, catalogID, isArchived, sortIndex FROM exercise")
    suspend fun owned(): List<OwnedRow>

    /** I giorni fuori da ogni scheda (`Programs.adoptLoose`). */
    @Query("SELECT COUNT(*) FROM workout_template WHERE program = ''")
    suspend fun looseTemplates(): Int

    @Query("UPDATE workout_template SET program = :program WHERE program = ''")
    suspend fun adoptTemplates(program: String)

    @Query("DELETE FROM workout_template WHERE program = :program")
    suspend fun deleteTemplatesOf(program: String)

    @Query("DELETE FROM workout_program WHERE uuid = :uuid")
    suspend fun deleteProgram(uuid: String)

    @Query("DELETE FROM exercise WHERE uuid IN (:uuids)")
    suspend fun deleteExercises(uuids: List<String>)
}

/** Una riga di `exercise` ridotta a quello che serve per riconoscere un macchinario già presente. */
data class OwnedRow(val uuid: String, val catalogID: String, val isArchived: Boolean, val sortIndex: Int)
