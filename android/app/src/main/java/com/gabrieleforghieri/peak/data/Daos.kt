package com.gabrieleforghieri.peak.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Upsert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise")
    suspend fun all(): List<ExerciseEntity>

    @Query("DELETE FROM exercise")
    suspend fun deleteAll()

    @Query("SELECT * FROM exercise ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE uuid = :uuid")
    suspend fun get(uuid: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE uuid = :uuid")
    fun observe(uuid: String): Flow<ExerciseEntity?>

    @Query("SELECT uuid FROM exercise")
    suspend fun allUuids(): List<String>

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Query("DELETE FROM exercise WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface MaxRecordDao {
    @Query("SELECT * FROM max_record")
    suspend fun all(): List<MaxRecordEntity>

    @Query("DELETE FROM max_record")
    suspend fun deleteAll()

    @Query("SELECT * FROM max_record WHERE exerciseUuid = :exercise")
    suspend fun ofExercise(exercise: String): List<MaxRecordEntity>

    @Query("SELECT * FROM max_record")
    fun observeAll(): Flow<List<MaxRecordEntity>>

    @Query("SELECT * FROM max_record WHERE uuid = :uuid")
    suspend fun get(uuid: String): MaxRecordEntity?

    @Query("SELECT * FROM max_record WHERE exerciseUuid = :exercise ORDER BY date DESC")
    fun observeOfExercise(exercise: String): Flow<List<MaxRecordEntity>>

    @Upsert
    suspend fun upsert(record: MaxRecordEntity)

    @Query("DELETE FROM max_record WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout")
    suspend fun all(): List<WorkoutEntity>

    @Query("DELETE FROM workout")
    suspend fun deleteAll()

    @Query("SELECT * FROM workout ORDER BY date DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE uuid = :uuid")
    suspend fun get(uuid: String): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE uuid = :uuid")
    fun observe(uuid: String): Flow<WorkoutEntity?>

    /** L'allenamento in corso (`endedAt` vuoto), il più recente se per un errore ce n'è più d'uno. */
    @Query("SELECT * FROM workout WHERE endedAt IS NULL ORDER BY date DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutEntity?>

    @Upsert
    suspend fun upsert(workout: WorkoutEntity)

    @Query("DELETE FROM workout WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_set")
    suspend fun all(): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_set")
    suspend fun deleteAll()

    /** Le serie di un macchinario che stanno in un allenamento che esiste ancora. */
    @Query("SELECT * FROM workout_set WHERE exerciseUuid = :exercise AND workoutUuid IS NOT NULL")
    suspend fun liveOfExercise(exercise: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE workoutUuid = :workout ORDER BY setOrder")
    suspend fun ofWorkout(workout: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE workoutUuid = :workout ORDER BY setOrder")
    fun observeOfWorkout(workout: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE uuid = :uuid")
    suspend fun get(uuid: String): WorkoutSetEntity?

    /** Le serie di tutti gli allenamenti che esistono ancora. */
    @Query("SELECT * FROM workout_set WHERE workoutUuid IS NOT NULL")
    fun observeLive(): Flow<List<WorkoutSetEntity>>

    /** Le serie di un macchinario in allenamenti che esistono ancora, dalla più recente. */
    @Query("SELECT * FROM workout_set WHERE exerciseUuid = :exercise AND workoutUuid IS NOT NULL ORDER BY createdAt DESC")
    fun observeLiveOfExercise(exercise: String): Flow<List<WorkoutSetEntity>>

    /** Le ultime serie svolte (`@Query` con `fetchLimit` del selettore di esercizi). */
    @Query("SELECT * FROM workout_set WHERE exerciseUuid IS NOT NULL ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WorkoutSetEntity>>

    @Query("SELECT COALESCE(MAX(setOrder), -1) FROM workout_set WHERE workoutUuid = :workout")
    suspend fun maxOrder(workout: String): Int

    @Query("SELECT DISTINCT exerciseUuid FROM workout_set WHERE workoutUuid = :workout AND exerciseUuid IS NOT NULL")
    suspend fun exercisesOfWorkout(workout: String): List<String>

    @Query("UPDATE workout_set SET isPersonalRecord = :flag WHERE uuid = :uuid")
    suspend fun setPersonalRecord(uuid: String, flag: Boolean)

    @Query("UPDATE workout_set SET excludedFromRecords = 1, isPersonalRecord = 0 WHERE uuid = :uuid")
    suspend fun exclude(uuid: String)

    @Query("SELECT uuid FROM workout_set WHERE workoutUuid = :workout AND isPersonalRecord = 1 ORDER BY setOrder")
    suspend fun personalRecordsOf(workout: String): List<String>

    @Upsert
    suspend fun upsert(set: WorkoutSetEntity)

    @Update
    suspend fun update(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_set WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight")
    suspend fun all(): List<BodyWeightEntity>

    @Query("DELETE FROM body_weight")
    suspend fun deleteAll()

    @Query("SELECT * FROM body_weight ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyWeightEntity>>

    @Upsert
    suspend fun upsert(entry: BodyWeightEntity)

    @Query("DELETE FROM body_weight WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_template")
    suspend fun all(): List<WorkoutTemplateEntity>

    @Query("DELETE FROM workout_template")
    suspend fun deleteAll()

    @Query("SELECT * FROM workout_template ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_template WHERE uuid = :uuid")
    suspend fun get(uuid: String): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_template WHERE uuid = :uuid")
    fun observe(uuid: String): Flow<WorkoutTemplateEntity?>

    @Upsert
    suspend fun upsert(template: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_template WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM workout_program")
    suspend fun all(): List<WorkoutProgramEntity>

    @Query("DELETE FROM workout_program")
    suspend fun deleteAll()

    @Query("SELECT * FROM workout_program ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<WorkoutProgramEntity>>

    @Query("SELECT * FROM workout_program WHERE uuid = :uuid")
    suspend fun get(uuid: String): WorkoutProgramEntity?

    @Query("SELECT * FROM workout_program WHERE uuid = :uuid")
    fun observe(uuid: String): Flow<WorkoutProgramEntity?>

    @Upsert
    suspend fun upsert(program: WorkoutProgramEntity)

    @Query("DELETE FROM workout_program WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    suspend fun all(): List<SettingEntity>

    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM setting WHERE `key` = :key")
    suspend fun get(key: String): SettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}
