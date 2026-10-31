package com.gabrieleforghieri.peak.domain

/**
 * Uno o due arti. Gemello di «uno o due arti» in common/domain.ts e di `Limbs.swift`: stesse regole, stesso catalogo.
 * Il peso scritto è sempre quello sull'attrezzo (un manubrio, il perno della pila, il bilanciere intero). Il macchinario dice
 * come si fa: sempre insieme, sempre un arto alla volta, oppure tutti e due con il selettore sulla serie. Solo nel terzo caso
 * il flag `singleArm` della serie conta: apre la linea di massimali a parte.
 */
enum class Limbs {
    both, one, either;

    companion object {
        /** Voci del catalogo che si fanno spesso in tutti e due i modi: lo stesso macchinario, a due o a uno. */
        private val EITHER_IDS = setOf(
            "leg-extension", "seated-leg-curl", "lying-leg-curl", "standing-leg-curl", "leg-curl-cable",
            "leg-press-45", "leg-press-horizontal", "leg-press-vertical", "leg-press-plate-loaded",
            "dumbbell-curl", "alternating-dumbbell-curl", "hammer-curl", "incline-dumbbell-curl", "preacher-curl-dumbbell",
            "preacher-curl-machine", "curl-machine", "cable-curl", "dumbbell-row",
            "lateral-raise", "cable-lateral-raise", "front-raise", "front-raise-cable", "rear-delt-fly-cable",
            "triceps-kickback-dumbbell", "cable-kickback", "glute-kickback-cable", "overhead-triceps-extension-dumbbell",
        )
        private val ONE_NAME = Regex("single|one[- ]?(arm|leg)|unilateral|singol|un braccio|una gamba", RegexOption.IGNORE_CASE)
        private val HANDLE_GLYPHS = setOf("dumbbell", "dumbbellBench", "inclineDumbbell", "cableStation", "cableCross", "cableRope", "cablePushdown", "kettlebell")
        private val HANDLE_NAME = Regex("manubr|cav[io]\\b|dumbbell|cable|kettlebell", RegexOption.IGNORE_CASE)

        /** Come si fa di base: dal catalogo, per i personalizzati dal nome. Core sempre insieme. */
        fun defaultLimbs(catalog: String, name: String, muscleGroup: String): Limbs {
            if (muscleGroup == MuscleGroup.core.raw) return both
            if (catalog.isNotEmpty()) {
                if (catalog.contains("single") || catalog == "concentration-curl" || catalog == "meadows-row") return one
                return if (catalog in EITHER_IDS) either else both
            }
            return if (ONE_NAME.containsMatchIn(name)) one else both
        }

        /** La scelta salvata, altrimenti il vecchio segno «un braccio» (`isUnilateral`), altrimenti quella di base. */
        fun limbsOf(saved: String?, isUnilateral: Boolean, catalog: String, name: String, muscleGroup: String): Limbs {
            entries.firstOrNull { it.name == saved }?.let { return it }
            return if (isUnilateral) one else defaultLimbs(catalog, name, muscleGroup)
        }

        /** La serie va sulla linea «a un braccio/una gamba»: solo se il macchinario si fa in tutti e due i modi. */
        fun lineArm(limbs: Limbs, singleArm: Boolean): Boolean = limbs == either && singleArm

        /** Manubri, cavi, kettlebell: il peso è già quello di un manico, passando a un arto non si dimezza (la pila unica sì). */
        fun twoHandles(catalog: String, name: String, glyph: String): Boolean {
            if (catalog.contains("dumbbell") || catalog.contains("cable") || catalog.contains("kettlebell") || glyph in HANDLE_GLYPHS) return true
            return catalog.isEmpty() && HANDLE_NAME.containsMatchIn(name)
        }
    }
}
