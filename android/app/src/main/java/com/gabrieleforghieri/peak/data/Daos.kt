package com.gabrieleforghieri.peak.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE uuid = :uuid")
    suspend fun get(uuid: String): ExerciseEntity?

    @Query("SELECT uuid FROM exercise")
    suspend fun allUuids(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exercise: ExerciseEntity)

    @Query("DELETE FROM exercise WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface MaxRecordDao {
    @Query("SELECT * FROM max_record WHERE exerciseUuid = :exercise")
    suspend fun ofExercise(exercise: String): List<MaxRecordEntity>

    @Query("SELECT * FROM max_record")
    fun observeAll(): Flow<List<MaxRecordEntity>>

    @Query("SELECT * FROM max_record WHERE uuid = :uuid")
    suspend fun get(uuid: String): MaxRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MaxRecordEntity)

    @Query("DELETE FROM max_record WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout ORDER BY date DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE uuid = :uuid")
    suspend fun get(uuid: String): WorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workout: WorkoutEntity)

    @Query("DELETE FROM workout WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface WorkoutSetDao {
    /** Le serie di un macchinario che stanno in un allenamento che esiste ancora. */
    @Query("SELECT * FROM workout_set WHERE exerciseUuid = :exercise AND workoutUuid IS NOT NULL")
    suspend fun liveOfExercise(exercise: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE workoutUuid = :workout ORDER BY setOrder")
    suspend fun ofWorkout(workout: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE workoutUuid = :workout ORDER BY setOrder")
    fun observeOfWorkout(workout: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE uuid = :uuid")
    suspend fun get(uuid: String): WorkoutSetEntity?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(set: WorkoutSetEntity)

    @Update
    suspend fun update(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_set WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyWeightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BodyWeightEntity)

    @Query("DELETE FROM body_weight WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_template ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_template WHERE uuid = :uuid")
    suspend fun get(uuid: String): WorkoutTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_template WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM workout_program ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<WorkoutProgramEntity>>

    @Query("SELECT * FROM workout_program WHERE uuid = :uuid")
    suspend fun get(uuid: String): WorkoutProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(program: WorkoutProgramEntity)

    @Query("DELETE FROM workout_program WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM setting WHERE `key` = :key")
    suspend fun get(key: String): SettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}
