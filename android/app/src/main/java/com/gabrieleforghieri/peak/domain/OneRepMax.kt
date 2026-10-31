package com.gabrieleforghieri.peak.domain

/**
 * Stima del 1RM (Epley) partendo da peso × ripetizioni.
 *
 * È solo una nota a margine: il massimale dell'app è il carico vero sollevato sul macchinario (vedi [MaxRanking]);
 * questa stima serve a confrontare serie con ripetizioni diverse e a ordinare le classifiche, e non decide mai un record.
 */
object OneRepMax {
    /** Massimale stimato in kg. Con 1 ripetizione restituisce il peso grezzo. */
    fun estimate(weight: Double, reps: Int): Double {
        if (!(weight > 0) || reps < 1) return 0.0
        if (reps == 1) return weight
        return weight * (1 + reps.toDouble() / 30)
    }
}
