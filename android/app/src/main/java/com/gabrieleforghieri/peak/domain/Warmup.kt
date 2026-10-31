package com.gabrieleforghieri.peak.domain

import kotlin.math.abs
import kotlin.math.floor

/**
 * Serie di riscaldamento suggerite a partire dal carico vero dell'esercizio.
 *
 * La rampa è quella classica in palestra: si sale in tre serie scaricando le ripetizioni. I pesi stanno sulla griglia del
 * macchinario: si parte dal massimale, che è un peso che hai davvero inserito, e si scende di N passi fino al più vicino a
 * quello calcolato. Così anche un pacco sfalsato (5 → 8,75 → 12,5…) dà pesi inseribili.
 */
object Warmup {

    data class Step(
        /** Frazione del massimale, es. 0.4 per il 40%. */
        val percent: Double,
        val reps: Int,
        /** Peso in kg, già portato sulla griglia del macchinario. */
        val weight: Double,
    ) {
        val percentLabel: String get() = "${Swift.round(percent * 100).toInt()}%"
    }

    /** 40% × 10 → 60% × 6 → 80% × 3. */
    val ramp: List<Pair<Double, Int>> = listOf(0.40 to 10, 0.60 to 6, 0.80 to 3)

    /** Il peso della griglia `anchor − n·step` più vicino a `weight`, mai sotto zero: se il più vicino fosse ≤ 0 si prende il più piccolo positivo. */
    fun nearest(weight: Double, anchor: Double, step: Double): Double {
        if (!(step > 0)) return maxOf(0.0, Swift.round(weight * 2) / 2)
        val n = Swift.round((anchor - weight) / step)
        var candidate = anchor - n * step
        if (candidate <= 0.0001) {
            candidate = anchor - floor((anchor - 0.0001) / step) * step
        }
        // Via il rumore della virgola mobile: 47,4999… resta 47,5.
        return Swift.round(candidate * 100) / 100
    }

    /** La rampa completa, in percentuale del carico di riferimento. Vuota se non c'è ancora niente su cui calcolarla. */
    fun plan(base: Double?, step: Double): List<Step> {
        if (base == null || !(base > 0)) return emptyList()
        // Con basi piccole e passi grandi i pesi si ripetono o arrivano al carico vero: quelli non servono come riscaldamento.
        val steps = mutableListOf<Step>()
        for ((percent, reps) in ramp) {
            val weight = nearest(base * percent, base, step)
            if (weight < base && steps.none { it.weight == weight }) steps += Step(percent, reps, weight)
        }
        return steps
    }

    /** La rampa sulla griglia dell'unità scelta: in lb si calcola in lb (passo di 5 lb, non 2,5 kg) e si riporta in kg (`Exercise.warmupPlan(unit:)`). */
    fun plan(baseKg: Double?, incrementStep: Double, unit: WeightUnit): List<Step> {
        if (unit != WeightUnit.lb || baseKg == null) return plan(baseKg, incrementStep)
        val rounded = Swift.round(unit.fromKilograms(baseKg) * 100) / 100
        return plan(rounded, unit.displayStep(incrementStep)).map { Step(it.percent, it.reps, unit.toKilograms(it.weight)) }
    }
}

/**
 * Il peso quando si passa da due bracci a uno e viceversa (gemello di `snapToGrid` / `switchArm` in common/domain.ts).
 * I pesi sono nell'unità mostrata: in lb il passo è quello nativo (5 lb), non i 2,5 kg convertiti.
 */
object ArmSwitch {

    /** Il peso prima e dopo il dimezzamento: se non lo si tocca, tornando a due bracci si rimette identico. */
    data class Memo(val before: Double, val after: Double)

    data class Result(val weight: Double, val memo: Memo?)

    /** Il peso della griglia `bar + k·step` più vicino a `weight`; a pari distanza il più basso. Mai sotto `bar` (né sotto un passo se `bar` è 0 e `weight` > 0). */
    fun snapToGrid(weight: Double, bar: Double, step: Double): Double {
        if (!(weight > 0)) return weight
        if (!(step > 0)) return Swift.round(maxOf(weight, bar) * 100) / 100
        val k = (weight - bar) / step
        var n = floor(k + 1e-6)
        if (k - n > 0.5 + 1e-6) n += 1
        return Swift.round((bar + maxOf(n, if (bar > 0) 0.0 else 1.0) * step) * 100) / 100
    }

    /** Il peso dopo un tocco sul selettore «2 | 1»: a uno la metà sulla griglia; di nuovo a due il precedente se il peso è ancora quello dimezzato, altrimenti il doppio sulla griglia. */
    fun weight(toSingle: Boolean, from: Double, memo: Memo?, bar: Double, step: Double): Result {
        if (toSingle) {
            val after = snapToGrid(from / 2, bar, step)
            return Result(after, if (from > 0) Memo(from, after) else null)
        }
        if (memo != null && abs(from - memo.after) < 0.005) return Result(memo.before, null)
        return Result(snapToGrid(from * 2, bar, step), null)
    }
}
