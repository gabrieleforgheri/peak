package com.gabrieleforghieri.peak.ui.workouts

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.WeightUnit

/** Una card della sessione: il macchinario con la sua riga della lista di oggi e quello che serve a disegnarla (`ExerciseSessionCard`). */
data class SessionCard(
    val exercise: ExerciseEntity,
    /** Obiettivo della scheda («2/4 serie · 8–10 rip · 60 kg»), vuoto se non c'è. */
    val goal: String,
    /** Le serie dell'ultimo altro allenamento con questo macchinario (le prime tre si mostrano). */
    val previousSets: List<WorkoutSetEntity>,
    /** Gli ultimi 3 allenamenti, per la seconda pagina di «Scorso». */
    val previousDays: List<PreviousDay>,
    /** Le serie di oggi. */
    val sets: List<WorkoutSetEntity>,
    /** È l'esercizio che stai facendo adesso. */
    val isCurrent: Boolean,
    /** Menu dell'esercizio: sposta solo a sessione in corso; sostituisci e togli solo senza serie. */
    val canMove: Boolean,
    val canReplace: Boolean,
    val canRemove: Boolean,
)

/** Tutto quello che la sessione disegna (lo costruisce [sessionUi] dai dati letti). */
data class SessionUi(
    val workout: WorkoutEntity,
    val cards: List<SessionCard>,
    /** Serie il cui macchinario è stato eliminato: contano nei totali, quindi si vedono (e si possono togliere). */
    val orphans: List<WorkoutSetEntity>,
    val workingSets: Int,
    val volume: Double,
    val recordCount: Int,
    val hasItems: Boolean,
)

/**
 * Lo stato della sessione dai dati letti: la lista di oggi in ordine (il piano e, in coda, gli esercizi con serie fuori piano), per ognuno
 * le sue serie, il «Scorso» e il menu. `allSets` sono le serie di tutti gli allenamenti, `workoutDates` le date di quelli che esistono.
 */
fun sessionUi(
    workout: WorkoutEntity,
    sets: List<WorkoutSetEntity>,
    exercises: List<ExerciseEntity>,
    allSets: List<WorkoutSetEntity>,
    workoutDates: Map<String, Long>,
    unit: WeightUnit,
    currentExercise: String?,
): SessionUi {
    val known = exercises.associateBy { it.uuid }
    val items = sessionItems(workout, sets, { it in known })
    val setsByExercise = allSets.filter { it.exerciseUuid != null }.groupBy { it.exerciseUuid!! }
    val active = workout.isActive
    val cards = items.mapNotNull { item ->
        val exercise = known[item.exercise] ?: return@mapNotNull null
        val mine = setsFor(sets, exercise.uuid)
        val history = setsByExercise[exercise.uuid].orEmpty()
        SessionCard(
            exercise = exercise,
            goal = Fmt.targetLine(item, mine.count { !it.isWarmup }, null, unit),
            previousSets = previousWorkingSets(history, workout.uuid, workoutDates),
            previousDays = previousDays(history, workout.uuid, workoutDates),
            sets = mine,
            isCurrent = exercise.uuid == currentExercise,
            canMove = active,
            canReplace = active && mine.isEmpty(),
            canRemove = active && mine.isEmpty(),
        )
    }
    return SessionUi(
        workout = workout, cards = cards, orphans = sets.filter { it.exerciseUuid == null }.sortedBy { it.order },
        workingSets = sets.workingSetCount, volume = sets.totalVolume, recordCount = sets.count { it.isPersonalRecord },
        hasItems = items.isNotEmpty(),
    )
}
