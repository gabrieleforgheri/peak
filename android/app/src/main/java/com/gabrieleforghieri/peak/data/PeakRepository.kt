package com.gabrieleforghieri.peak.data

import kotlinx.coroutines.flow.Flow

/**
 * Lettura comune per le schermate: i `@Query` delle viste dell'iPhone (cartella `Features`), come `Flow` che si riemettono a ogni modifica.
 * Proprietario: la base della fase 3; le query di una sola area vanno nel suo DAO (`data/dao/`), non qui.
 *
 * Le scritture non passano da qui: le serie e i massimali dal `RecordService` (`PeakApplication.records`), il resto dai DAO.
 * Ogni elenco ha l'ordine della `@Query` corrispondente di SwiftUI. Gli archiviati ci sono tutti: li filtra chi mostra.
 */
class PeakRepository(private val db: PeakDatabase) {
    /** `@Query(sort: \Exercise.sortIndex)` (e `name` a parità). */
    val exercises: Flow<List<ExerciseEntity>> get() = db.exercises().observeAll()
    fun exercise(uuid: String): Flow<ExerciseEntity?> = db.exercises().observe(uuid)

    /** `@Query(sort: \Workout.date, order: .reverse)`. */
    val workouts: Flow<List<WorkoutEntity>> get() = db.workouts().observeAll()
    fun workout(uuid: String): Flow<WorkoutEntity?> = db.workouts().observe(uuid)

    /** L'allenamento in corso, se c'è (`Workout.isActive`). */
    val activeWorkout: Flow<WorkoutEntity?> get() = db.workouts().observeActive()

    /** Le serie di un allenamento, in ordine (`workout.setList`). */
    fun setsOfWorkout(workout: String): Flow<List<WorkoutSetEntity>> = db.sets().observeOfWorkout(workout)

    /** Tutte le serie di allenamenti che esistono ancora (`@Query private var sets`). */
    val sets: Flow<List<WorkoutSetEntity>> get() = db.sets().observeLive()

    /** Le serie di un macchinario, dalla più recente (`exercise.sets`). */
    fun setsOfExercise(exercise: String): Flow<List<WorkoutSetEntity>> = db.sets().observeLiveOfExercise(exercise)

    /** Le ultime serie svolte, per proporre in cima gli esercizi che usi (`ExercisePickerSheet`: `fetchLimit = 80`). */
    fun recentSets(limit: Int = 80): Flow<List<WorkoutSetEntity>> = db.sets().observeRecent(limit)

    /** Tutti i massimali (`@Query` di `MeSections`, `Stats`). */
    val records: Flow<List<MaxRecordEntity>> get() = db.records().observeAll()

    /** I massimali di un macchinario, dal più recente (`exercise.records`). */
    fun recordsOfExercise(exercise: String): Flow<List<MaxRecordEntity>> = db.records().observeOfExercise(exercise)

    /** `@Query(sort: \WorkoutTemplate.sortIndex)`: i giorni, anche quelli nascosti. */
    val templates: Flow<List<WorkoutTemplateEntity>> get() = db.templates().observeAll()
    fun template(uuid: String): Flow<WorkoutTemplateEntity?> = db.templates().observe(uuid)

    /** `@Query(sort: \WorkoutProgram.sortIndex)`. */
    val programs: Flow<List<WorkoutProgramEntity>> get() = db.programs().observeAll()
    fun program(uuid: String): Flow<WorkoutProgramEntity?> = db.programs().observe(uuid)

    /** `@Query(sort: \BodyWeightEntry.date, order: .reverse)`. */
    val bodyWeights: Flow<List<BodyWeightEntity>> get() = db.bodyWeights().observeAll()
}
