package com.gabrieleforghieri.peak.domain

/**
 * Doppia progressione: si resta sullo stesso carico aggiungendo ripetizioni finché tutte le serie arrivano in cima al range,
 * poi si sale di un passo e si riparte dal fondo.
 */
object Progression {

    data class Suggestion(val weight: Double, val reps: Int)

    data class Work(val weight: Double, val reps: Int)

    /** `lastSession`: serie allenanti (niente riscaldamento) dell'ultima sessione. Conta solo il carico più pesante di quella sessione. */
    fun next(lastSession: List<Work>, step: Double, range: IntRange): Suggestion? {
        val top = lastSession.maxOfOrNull { it.weight } ?: return null
        if (!(top > 0)) return null
        val topReps = lastSession.filter { Math.abs(it.weight - top) < MaxRanking.EPSILON }.map { it.reps }
        if (topReps.all { it >= range.last }) return Suggestion(top + step, range.first)
        val reps = minOf(maxOf((topReps.maxOrNull() ?: 0) + 1, range.first), range.last)
        return Suggestion(top, reps)
    }

    /** Una serie con il suo allenamento, per ricavare l'ultima sessione conclusa. */
    data class SessionSet(
        val weight: Double,
        val reps: Int,
        val isWarmup: Boolean,
        val workout: String,
        val workoutDate: Long,
        val workoutEnded: Boolean,
        /** Sulla linea a un arto di questo macchinario (`WorkoutSet.isOneLimbLine`). */
        val oneLimbLine: Boolean = false,
    )

    /**
     * Sull'ultima sessione conclusa con serie allenanti su questo macchinario (`Exercise.progression` / `progressionFor`).
     * Una sessione con serie a due e a un arto: si ragiona sulla linea dell'ultima serie fatta (i pesi non si mescolano).
     * `sets` va nell'ordine di creazione. I pesi sono nell'unità mostrata, il passo pure.
     */
    fun forExercise(sets: List<SessionSet>, step: Double, range: IntRange): Suggestion? {
        val working = sets.filter { !it.isWarmup && it.workoutEnded }
        if (working.isEmpty()) return null
        var last = working[0]
        for (s in working) if (s.workoutDate > last.workoutDate) last = s
        val day = working.filter { it.workout == last.workout }
        val oneLimb = day.last().oneLimbLine
        return next(day.filter { it.oneLimbLine == oneLimb }.map { Work(it.weight, it.reps) }, step, range)
    }
}
