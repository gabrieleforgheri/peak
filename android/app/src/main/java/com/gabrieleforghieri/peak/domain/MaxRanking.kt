package com.gabrieleforghieri.peak.domain

/**
 * Come si confrontano due prestazioni sullo stesso macchinario.
 *
 * Conta quello che hai davvero caricato, non una stima: prima il peso, poi le ripetizioni. Quindi 85 × 3 batte 80 × 8
 * (più carico), e a parità di carico 80 × 8 batte 80 × 5. Il 1RM stimato non partecipa mai a questa decisione.
 */
object MaxRanking {
    /** Tolleranza per evitare che un arrotondamento faccia scattare un falso record. */
    const val EPSILON = 0.01

    data class Performance(val weight: Double, val reps: Int)

    /** `true` se `weight × reps` è una prestazione migliore di `other`. Senza un termine di paragone vince qualsiasi serie valida. */
    fun beats(weight: Double, reps: Int, other: Performance?): Boolean {
        if (!(weight > 0) || reps < 1) return false
        if (other == null) return true
        if (weight > other.weight + EPSILON) return true
        if (weight < other.weight - EPSILON) return false
        return reps > other.reps
    }

    /**
     * Il massimo vero: più carico, poi più ripetizioni, a parità esatta il più recente (`Exercise.bestRecord`, `bestOf` in domain.ts).
     */
    fun <T> best(records: List<T>, weight: (T) -> Double, reps: (T) -> Int, date: (T) -> Long): T? {
        var best: T? = null
        for (r in records) {
            val b = best
            if (b == null) { best = r; continue }
            if (beats(weight(r), reps(r), Performance(weight(b), reps(b)))) { best = r; continue }
            val same = Math.abs(weight(r) - weight(b)) < EPSILON && reps(r) == reps(b)
            if (same && date(r) > date(b)) best = r
        }
        return best
    }
}
