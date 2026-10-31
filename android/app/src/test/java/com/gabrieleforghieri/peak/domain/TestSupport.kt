package com.gabrieleforghieri.peak.domain

import com.gabrieleforghieri.peak.domain.RecordService.Diff
import com.gabrieleforghieri.peak.domain.RecordService.ExerciseState
import com.gabrieleforghieri.peak.domain.RecordService.RecordHead
import com.gabrieleforghieri.peak.domain.RecordService.RecordRow
import com.gabrieleforghieri.peak.domain.RecordService.RecordStorage
import com.gabrieleforghieri.peak.domain.RecordService.SetRow
import org.junit.Assert.assertEquals

/** Un uuid finto ma ben formato. */
fun uuid(n: Int) = "00000000-0000-4000-8000-%012X".format(n)

fun assertNear(expected: Double, actual: Double, eps: Double = 0.01) {
    assertEquals(expected, actual, eps)
}

/** Il catalogo vero (`common/catalog.json`), caricato una volta per tutte le prove. */
object TestCatalog {
    init {
        val text = checkNotNull(TestCatalog::class.java.classLoader.getResource("catalog.json")) { "catalog.json non trovato" }.readText()
        check(Catalog.install(text))
    }

    fun load() = Unit
}

/** Il database in memoria al posto di Room: stessi comportamenti (cascate comprese) per provare [RecordService] sul JVM. */
class InMemoryStorage : RecordStorage {
    class Exercise(val uuid: String, var limbs: Limbs = Limbs.both)
    class Rec(var row: RecordRow, var exercise: String?)
    class Set(var row: SetRow, var workout: String?, var exercise: String?)

    val exercises = linkedMapOf<String, Exercise>()
    val records = linkedMapOf<String, Rec>()
    val sets = linkedMapOf<String, Set>()
    val workouts = linkedSetOf<String>()

    fun addExercise(uuid: String, limbs: Limbs = Limbs.both) = Exercise(uuid, limbs).also { exercises[uuid] = it }
    fun addWorkout(uuid: String) = workouts.add(uuid)
    fun addManual(uuid: String, exercise: String, weight: Double, reps: Int, date: Long = 0, singleArm: Boolean = false) {
        records[uuid] = Rec(RecordRow(uuid, date, weight, reps, isEstimated = false, note = "", singleArm = singleArm), exercise)
    }

    fun recordsOf(exercise: String) = records.values.filter { it.exercise == exercise }.map { it.row }
    fun setsOf(workout: String) = sets.values.filter { it.workout == workout }.sortedBy { it.row.order }

    override suspend fun <T> transaction(block: suspend () -> T): T = block()

    override suspend fun exerciseState(exercise: String): ExerciseState? {
        val e = exercises[exercise] ?: return null
        return ExerciseState(
            exercise, e.limbs, recordsOf(exercise),
            sets.values.filter { it.exercise == exercise && it.workout != null }.map { it.row },
        )
    }

    override suspend fun allExercises() = exercises.keys.toList()

    override suspend fun nextOrder(workout: String) = (sets.values.filter { it.workout == workout }.maxOfOrNull { it.row.order } ?: -1) + 1

    override suspend fun insertSet(set: SetRow, workout: String, exercise: String?) {
        sets[set.uuid] = Set(set, workout, exercise)
    }

    override suspend fun apply(exercise: String, diff: Diff) {
        diff.deleteRecords.forEach { records.remove(it) }
        diff.upsertRecords.forEach { records[it.uuid] = Rec(it, exercise) }
        diff.setFlags.forEach { (uuid, flag) -> sets[uuid]?.let { it.row = it.row.copy(isPersonalRecord = flag) } }
    }

    override suspend fun exerciseOfSet(set: String) = sets[set]?.exercise
    override suspend fun deleteSet(set: String) { sets.remove(set) }
    override suspend fun exercisesOfWorkout(workout: String) = sets.values.filter { it.workout == workout }.mapNotNull { it.exercise }.toSet()
    override suspend fun deleteWorkout(workout: String) { workouts.remove(workout); sets.values.removeAll { it.workout == workout } }
    override suspend fun record(uuid: String) = records[uuid]?.let { RecordHead(it.exercise, it.row.isEstimated) }
    override suspend fun deleteRecord(uuid: String) { records.remove(uuid) }
    override suspend fun excludeSet(uuid: String) { sets[uuid]?.let { it.row = it.row.copy(excludedFromRecords = true, isPersonalRecord = false) } }
    override suspend fun personalRecordSets(workout: String) = setsOf(workout).filter { it.row.isPersonalRecord }.map { it.row.uuid }
}
