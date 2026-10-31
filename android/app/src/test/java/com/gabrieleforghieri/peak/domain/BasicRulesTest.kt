package com.gabrieleforghieri.peak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** OneRepMaxTests.swift, MaxRankingTests.swift e i casi di `oneRepMax`/`beats` in common/domain.test.ts. */
class OneRepMaxTest {
    @Test fun singleRepReturnsRawWeight() = assertEquals(100.0, OneRepMax.estimate(100.0, 1), 0.0)

    @Test fun epley() {
        // 100 × (1 + 5/30) = 116,66…
        assertNear(116.666, OneRepMax.estimate(100.0, 5))
        assertNear(106.666, OneRepMax.estimate(80.0, 10))
    }

    @Test fun invalidInputIsZero() {
        assertEquals(0.0, OneRepMax.estimate(0.0, 5), 0.0)
        assertEquals(0.0, OneRepMax.estimate(100.0, 0), 0.0)
        assertEquals(0.0, OneRepMax.estimate(100.0, -3), 0.0)
    }

    @Test fun staysIncreasingAtHighReps() {
        var previous = 0.0
        for (reps in 1..50) {
            val value = OneRepMax.estimate(60.0, reps)
            assertTrue(value > previous)
            previous = value
        }
    }
}

class MaxRankingTest {
    private fun beats(w: Double, r: Int, than: Pair<Double, Int>?) =
        MaxRanking.beats(w, r, than?.let { MaxRanking.Performance(it.first, it.second) })

    @Test fun firstPerformanceAlwaysWins() {
        assertTrue(beats(40.0, 1, null))
        assertFalse(beats(0.0, 5, null))
        assertFalse(beats(40.0, 0, null))
    }

    @Test fun heavierWins() {
        assertTrue(beats(85.0, 3, 80.0 to 8))
        assertFalse(beats(75.0, 12, 80.0 to 8))
    }

    @Test fun sameWeightMoreRepsWins() {
        assertTrue(beats(80.0, 8, 80.0 to 5))
        assertFalse(beats(80.0, 5, 80.0 to 8))
        assertFalse(beats(80.0, 8, 80.0 to 8))
    }

    /** Mezzo grammo di differenza non è un record: sarebbe solo un arrotondamento. */
    @Test fun roundingIsNotARecord() {
        assertFalse(beats(80.005, 5, 80.0 to 5))
        assertTrue(beats(80.5, 5, 80.0 to 5))
    }

    private data class R(val w: Double, val r: Int, val d: Long)

    /** `bestOf`: più carico, poi più ripetizioni, a parità esatta il più recente. */
    @Test fun bestRecordIsTheRealLoadAndTheLatestOnTies() {
        val best = MaxRanking.best(listOf(R(100.0, 5, 1), R(100.0, 5, 3), R(95.0, 12, 2)), R::w, R::r, R::d)
        assertEquals(R(100.0, 5, 3), best)
        assertNull(MaxRanking.best(emptyList<R>(), R::w, R::r, R::d))
    }
}

/** WarmupTests.swift e i casi di riscaldamento di common/domain.test.ts. */
class WarmupTest {
    @Test fun rampPercentagesAndReps() {
        val plan = Warmup.plan(100.0, 5.0)
        assertEquals(3, plan.size)
        assertEquals(listOf(10, 6, 3), plan.map { it.reps })
        assertEquals(listOf(40.0, 60.0, 80.0), plan.map { it.weight })
        assertEquals(listOf("40%", "60%", "80%"), plan.map { it.percentLabel })
    }

    /** Si scende dal massimale di N passi fino al peso più vicino al calcolato. */
    @Test fun weightsAreNearestOnMachineGrid() {
        assertEquals(listOf(49.0, 69.0, 99.0), Warmup.plan(119.0, 10.0).map { it.weight })
        assertEquals(listOf(47.5, 72.5, 95.0), Warmup.plan(120.0, 2.5).map { it.weight })
    }

    /** Pacco sfalsato (passo 3,75 che non parte da zero): ogni peso suggerito dev'essere raggiungibile dal massimale per passi interi. */
    @Test fun offsetStackStaysOnGrid() {
        val base = 50.0
        val step = 3.75
        for (warmup in Warmup.plan(base, step)) {
            val steps = (base - warmup.weight) / step
            assertEquals("${warmup.weight} fuori griglia", Math.rint(steps), steps, 0.0001)
            assertTrue(Math.abs(warmup.weight - base * warmup.percent) <= step / 2 + 0.0001)
        }
    }

    /** Con massimali piccoli non deve mai produrre zero: resta il peso più leggero della griglia. */
    @Test fun neverSuggestsZero() {
        val plan = Warmup.plan(8.0, 5.0)
        assertEquals(3.0, plan.first().weight, 0.0)
        assertTrue(plan.all { it.weight > 0 })
        assertEquals(3.75, Warmup.nearest(0.5, 60.0, 3.75), 0.0)
    }

    @Test fun noMaxMeansNoPlan() {
        assertTrue(Warmup.plan(null, 5.0).isEmpty())
        assertTrue(Warmup.plan(0.0, 5.0).isEmpty())
    }

    @Test fun rampIsNonDecreasing() {
        var base = 10.0
        while (base <= 300.0) {
            for (step in listOf(1.0, 2.5, 5.0, 10.0)) {
                val weights = Warmup.plan(base, step).map { it.weight }
                assertEquals("rampa non crescente con massimale $base e passo $step", weights.sorted(), weights)
                assertEquals(weights.size, weights.toSet().size)
                assertTrue(weights.all { it > 0 && it < base })
            }
            base += 7.5
        }
    }

    /** Base piccola e passo grande: niente ripetizioni né pesi uguali al carico vero. */
    @Test fun smallBaseDropsDuplicatesAndTheWorkingWeight() {
        assertEquals(listOf(5.0), Warmup.plan(10.0, 5.0).map { it.weight })
    }

    @Test fun poundsPlanIsComputedOnThePoundGrid() {
        // 100 kg ≈ 220,46 lb, passo 2,5 kg → 5 lb: i pesi sono multipli di 5 lb sotto il massimale
        val plan = Warmup.plan(100.0, 2.5, WeightUnit.lb)
        assertTrue(plan.isNotEmpty())
        for (s in plan) {
            val lb = WeightUnit.lb.fromKilograms(s.weight)
            assertNear(0.0, Math.IEEEremainder(WeightUnit.lb.fromKilograms(100.0) - lb, 5.0), 0.05)
        }
    }

    /** «da due bracci a uno»: casi di `switchArm` e `snapToGrid` in common/domain.test.ts. */
    private fun half(w: Double, bar: Double = 0.0, step: Double = 5.0) = ArmSwitch.weight(true, w, null, bar, step).weight

    @Test fun twoToOneHalvesOnTheGridRoundingDown() {
        assertEquals(25.0, half(55.0), 0.0)   // 27,5: pari tra 25 e 30
        assertEquals(25.0, half(50.0), 0.0)
        assertEquals(30.0, half(60.0), 0.0)
        assertEquals(30.0, half(65.0), 0.0)   // 32,5: pari tra 30 e 35
        assertEquals(35.0, half(70.0), 0.0)
        assertEquals(5.0, half(5.0), 0.0)     // mai sotto un passo
        assertEquals(5.0, half(10.0, 0.0, 2.5), 0.0)
        assertEquals(0.0, half(0.0), 0.0)
        // con la sbarra: 20 + k·2,5
        assertEquals(25.0, half(50.0, 20.0, 2.5), 0.0)
        assertEquals(22.5, half(45.0, 20.0, 2.5), 0.0)
        assertEquals(20.0, half(30.0, 20.0, 2.5), 0.0)  // 15 sotto la sbarra: la sbarra
        assertEquals(20.0, half(23.5, 20.0, 5.0), 0.0)
        assertEquals(25.0, ArmSwitch.snapToGrid(26.0, 20.0, 5.0), 0.0)
        assertEquals(25.0, ArmSwitch.snapToGrid(27.5, 20.0, 5.0), 0.0)
    }

    @Test fun oneToTwoRestoresIfUntouchedElseDoubles() {
        val down = ArmSwitch.weight(true, 55.0, null, 0.0, 5.0)
        assertEquals(ArmSwitch.Result(25.0, ArmSwitch.Memo(55.0, 25.0)), down)
        assertEquals(55.0, ArmSwitch.weight(false, 25.0, down.memo, 0.0, 5.0).weight, 0.0)     // non toccato: 55, non 50
        assertEquals(ArmSwitch.Result(60.0, null), ArmSwitch.weight(false, 30.0, down.memo, 0.0, 5.0)) // cambiato: il doppio
        assertEquals(55.0, ArmSwitch.weight(false, 27.0, null, 0.0, 5.0).weight, 0.0)          // 54 -> 55
        assertEquals(65.0, ArmSwitch.weight(false, 32.5, null, 0.0, 5.0).weight, 0.0)
        assertEquals(25.0, ArmSwitch.weight(false, 12.5, null, 0.0, 5.0).weight, 0.0)
    }

    @Test fun oneArmInPoundsUsesTheNativeStep() {
        fun lb(kg: Double) = WeightUnit.lb.fromKilograms(kg)
        val step = WeightUnit.lb.displayStep(2.5)                                          // 5 lb
        val d = ArmSwitch.weight(true, lb(55.0), null, 0.0, step)
        assertEquals(60.0, d.weight, 0.0)
        assertEquals(lb(55.0), ArmSwitch.weight(false, d.weight, d.memo, 0.0, step).weight, 0.0)
        val bar = lb(20.0)
        assertNear(bar + 13 * step, ArmSwitch.weight(true, lb(100.0), null, bar, step).weight)
    }
}

class ProgressionTest {
    private val range = 8..12
    private fun w(vararg p: Pair<Double, Int>) = p.map { Progression.Work(it.first, it.second) }

    @Test fun allSetsAtTopOfRangeAddsOneStep() =
        assertEquals(Progression.Suggestion(62.5, 8), Progression.next(w(60.0 to 12, 60.0 to 12, 60.0 to 13), 2.5, range))

    @Test fun oneSetShortKeepsWeightAndAddsARep() =
        assertEquals(Progression.Suggestion(60.0, 12), Progression.next(w(60.0 to 12, 60.0 to 10), 2.5, range))

    /** Le serie più leggere (back-off) non frenano la progressione. */
    @Test fun onlyHeaviestWeightCounts() =
        assertEquals(Progression.Suggestion(62.5, 8), Progression.next(w(60.0 to 12, 50.0 to 6), 2.5, range))

    @Test fun belowRangeClimbsToTheBottom() =
        assertEquals(Progression.Suggestion(80.0, 8), Progression.next(w(80.0 to 5), 5.0, range))

    @Test fun noSessionNoSuggestion() = assertNull(Progression.next(emptyList(), 2.5, range))

    private fun s(w: Double, r: Int, wo: String, date: Long, ended: Boolean = true, warm: Boolean = false, one: Boolean = false) =
        Progression.SessionSet(w, r, warm, wo, date, ended, one)

    /** `progressionFor`: l'ultima sessione conclusa, senza riscaldamento. */
    @Test fun usesTheLastEndedSessionAndIgnoresWarmup() {
        val sets = listOf(
            s(50.0, 12, "A", 1), s(50.0, 12, "A", 1),
            s(60.0, 12, "B", 2), s(60.0, 12, "B", 2), s(20.0, 10, "B", 2, warm = true),
            s(100.0, 5, "C", 3, ended = false),
        )
        assertEquals(Progression.Suggestion(62.5, 8), Progression.forExercise(sets, 2.5, range))
        assertNull(Progression.forExercise(listOf(s(60.0, 8, "A", 1, ended = false)), 2.5, range))
    }

    /** Una sessione con serie a due e a un arto: si ragiona sulla linea dell'ultima serie fatta, i pesi non si mescolano. */
    @Test fun followsTheLineOfTheLastSetOfTheSession() {
        val sets = listOf(s(120.0, 12, "A", 1), s(60.0, 9, "A", 1, one = true))
        assertEquals(Progression.Suggestion(60.0, 10), Progression.forExercise(sets, 5.0, range))
    }
}
