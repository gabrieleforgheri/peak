package com.gabrieleforghieri.peak.data.dao

import androidx.room.Dao
import androidx.room.Transaction
import androidx.room.Upsert
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity

/**
 * Le query dell'area Amici (`Features/Friends/`): amici e classifiche vengono dal server (niente tabelle); qui sta solo la scrittura di
 * «Copia nelle mie schede». Proprietario: l'area friends. Si prende da `PeakDatabase.areaFriends()`.
 *
 * Una classe astratta e non un'interfaccia perché ha un metodo con corpo (`@Transaction`).
 */
@Dao
abstract class FriendsDao {
    @Upsert protected abstract suspend fun upsertExercise(exercise: ExerciseEntity)

    @Upsert protected abstract suspend fun upsertTemplate(template: WorkoutTemplateEntity)

    @Upsert protected abstract suspend fun upsertProgram(program: WorkoutProgramEntity)

    /** I macchinari nuovi, la scheda se non c'era e il giorno copiato: tutto o niente. */
    @Transaction
    open suspend fun addSharedDay(exercises: List<ExerciseEntity>, program: WorkoutProgramEntity?, template: WorkoutTemplateEntity) {
        exercises.forEach { upsertExercise(it) }
        if (program != null) upsertProgram(program)
        upsertTemplate(template)
    }
}
