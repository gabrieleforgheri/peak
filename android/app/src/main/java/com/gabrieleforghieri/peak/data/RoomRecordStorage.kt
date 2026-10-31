package com.gabrieleforghieri.peak.data

import androidx.room.withTransaction
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.RecordService.Diff
import com.gabrieleforghieri.peak.domain.RecordService.ExerciseState
import com.gabrieleforghieri.peak.domain.RecordService.RecordHead
import com.gabrieleforghieri.peak.domain.RecordService.RecordRow
import com.gabrieleforghieri.peak.domain.RecordService.RecordStorage
import com.gabrieleforghieri.peak.domain.RecordService.SetRow

/** Il database vero dietro [com.gabrieleforghieri.peak.domain.RecordService]. */
class RoomRecordStorage(private val db: PeakDatabase) : RecordStorage {

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun exerciseState(exercise: String): ExerciseState? {
        val e = db.exercises().get(exercise) ?: return null
        val limbs = Limbs.limbsOf(e.limbs, e.isUnilateral, e.catalogID, e.name, e.muscleGroup)
        return ExerciseState(
            exercise, limbs,
            db.records().ofExercise(exercise).map { RecordRow(it.uuid, it.date, it.weight, it.reps, it.isEstimated, it.note, it.singleArm) },
            db.sets().liveOfExercise(exercise).map {
                SetRow(it.uuid, it.createdAt, it.order, it.weight, it.reps, it.isWarmup, it.excludedFromRecords, it.isPersonalRecord, it.singleArm)
            },
        )
    }

    override suspend fun allExercises(): List<String> = db.exercises().allUuids()

    override suspend fun nextOrder(workout: String): Int = db.sets().maxOrder(workout) + 1

    override suspend fun insertSet(set: SetRow, workout: String, exercise: String?) {
        db.sets().upsert(
            WorkoutSetEntity(
                uuid = set.uuid, workoutUuid = workout, exerciseUuid = exercise, weight = set.weight, reps = set.reps,
                isWarmup = set.isWarmup, order = set.order, createdAt = set.createdAt,
                excludedFromRecords = set.excludedFromRecords, singleArm = set.singleArm,
            ),
        )
    }

    override suspend fun apply(exercise: String, diff: Diff) {
        for (uuid in diff.deleteRecords) db.records().delete(uuid)
        for (r in diff.upsertRecords) {
            db.records().upsert(MaxRecordEntity(r.uuid, exercise, r.date, r.weight, r.reps, r.isEstimated, r.note, r.singleArm))
        }
        for ((uuid, flag) in diff.setFlags) db.sets().setPersonalRecord(uuid, flag)
    }

    override suspend fun exerciseOfSet(set: String): String? = db.sets().get(set)?.exerciseUuid

    override suspend fun deleteSet(set: String) = db.sets().delete(set)

    override suspend fun exercisesOfWorkout(workout: String): Set<String> = db.sets().exercisesOfWorkout(workout).toSet()

    override suspend fun deleteWorkout(workout: String) = db.workouts().delete(workout)

    override suspend fun record(uuid: String): RecordHead? = db.records().get(uuid)?.let { RecordHead(it.exerciseUuid, it.isEstimated) }

    override suspend fun deleteRecord(uuid: String) = db.records().delete(uuid)

    override suspend fun excludeSet(uuid: String) = db.sets().exclude(uuid)

    override suspend fun personalRecordSets(workout: String): List<String> = db.sets().personalRecordsOf(workout)
}
