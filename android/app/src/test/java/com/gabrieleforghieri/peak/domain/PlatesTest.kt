package com.gabrieleforghieri.peak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PlatesTests.swift e i casi di dischi, sbarra e unità di common/domain.test.ts. */
class PlatesTest {
    private val standard = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    @Test fun barbellUsesFewestPlates() {
        val load = Plates.load(102.5, 20.0, standard)
        assertEquals(listOf(25.0, 15.0, 1.25), load.perSide)
        assertEquals(0.0, load.leftover, 0.0)
    }

    @Test fun plateLoadedHasNoBar() = assertEquals(listOf(25.0, 20.0), Plates.load(90.0, 0.0, standard).perSide)

    @Test fun missingPlatesLeaveALeftover() {
        val load = Plates.load(65.0, 20.0, listOf(20.0, 10.0, 5.0))
        assertEquals(listOf(20.0), load.perSide)
        assertEquals(2.5, load.leftover, 0.0)
        assertEquals(Plates.Load(emptyList(), 10.0), Plates.load(40.0, 20.0, emptyList()))
    }

    @Test fun onlyTheBar() {
        assertEquals(emptyList<Double>(), Plates.load(20.0, 20.0, standard).perSide)
        assertEquals(emptyList<Double>(), Plates.load(15.0, 20.0, standard).perSide)
    }

    /** Un set su misura: il greedy da solo lascerebbe 15 per lato. */
    @Test fun customPlateSetFindsAnExactLoad() {
        val load = Plates.load(100.0, 20.0, listOf(25.0, 20.0))
        assertEquals(listOf(20.0, 20.0), load.perSide)
        assertEquals(0.0, load.leftover, 0.0)
        assertEquals(listOf(30.0, 30.0), Plates.load(120.0, 0.0, listOf(50.0, 20.0, 30.0)).perSide)
    }

    /** Come `barForUnit` nel web: 20 kg sono 45 lb, la sbarra di serie dell'unità. */
    @Test fun barFollowsTheUnit() {
        val kg = WeightUnit.KILOGRAMS_PER_POUND
        assertEquals(20.0, Plates.defaultBar(MachineGlyph.barbell, "Curl Bilanciere", WeightUnit.kg), 0.0)
        assertEquals(45 * kg, Plates.defaultBar(MachineGlyph.barbell, "Curl Bilanciere", WeightUnit.lb), 1e-9)
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.barbell, "Rematore macchina", WeightUnit.lb), 0.0)
        assertEquals(20.0, Plates.bar(20.0, WeightUnit.kg), 0.0)
        assertEquals(0.0, Plates.bar(0.0, WeightUnit.lb), 0.0)
        assertEquals(20.0, Plates.bar(10.0, WeightUnit.lb) / kg, 1e-9)
        // 45 lb di sbarra con dischi in lb: nessun resto
        assertEquals(0.0, Plates.load(135 * kg, Plates.bar(20.0, WeightUnit.lb), Plates.defaults(WeightUnit.lb)).leftover, 0.0)
    }

    /** Come `defaultBarWeightFor` nel web: una macchina non ha il bilanciere, il Multipower sì. */
    @Test fun machinesHaveNoDefaultBar() {
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.barbell, "Rematore macchina"), 0.0)
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.benchFlat, "Curl Machine"), 0.0)
        assertEquals(20.0, Plates.defaultBar(MachineGlyph.barbell, "Curl Bilanciere"), 0.0)
        assertEquals(15.0, Plates.defaultBar(MachineGlyph.smithMachine, "Smith Machine Squat"), 0.0)
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.preacherBench, "Curl Machine"), 0.0)
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.preacherBench, "Biceps Curl Machine"), 0.0)
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.benchFlat, "MACHINE press"), 0.0)
        assertEquals(10.0, Plates.defaultBar(MachineGlyph.ezBar, "Curl Bilanciere EZ"), 0.0)
        assertEquals(25.0, Plates.defaultBar(MachineGlyph.trapBar, "Stacco con Trap Bar"), 0.0)
        assertEquals(15.0, Plates.defaultBar(MachineGlyph.smithMachine, "Panca Piana al Multipower"), 0.0)
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.dumbbell, "Curl Manubri"), 0.0)
    }

    /**
     * DISACCORDO Swift/TypeScript, qui fissato com'è in Swift (la fase 1 traduce Swift): `defaultBarWeightFor` nel web dà
     * 20 kg ai pittogrammi `preacherBench` e `landmine` e 15 a `smithMachine` anche senza «smith» nel nome (common/domain.test.ts:140
     * si aspetta `defaultBarWeightFor('preacherBench', 'Panca Scott') == 20`). Swift (`Plates.defaultBar(for:name:)`) dà 0 e 0.
     * Se si decide di allineare, questo test e `Plates.defaultBar` cambiano insieme.
     */
    @Test fun defaultBarDisagreementWithTheWebIsKeptAsInSwift() {
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.preacherBench, "Panca Scott"), 0.0) // web: 20
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.landmine, "Landmine Press"), 0.0)   // web: 20
        assertEquals(0.0, Plates.defaultBar(MachineGlyph.smithMachine, "Squat"), 0.0)        // web: 15
    }

    /** Il catalogo, in entrambe le lingue: la lingua non cambia la sbarra e le voci «machine» non ce l'hanno (salvo Smith). */
    @Test fun catalogBarsDoNotDependOnTheLanguage() {
        TestCatalog.load()
        for (e in Catalog.entries) {
            val it = Plates.defaultBar(e.machineGlyph, e.nameIt)
            val en = Plates.defaultBar(e.machineGlyph, e.nameEn)
            assertEquals(e.id, it, en, 0.0)
            if (e.nameEn.contains("machine", ignoreCase = true) && !e.nameEn.contains("smith", ignoreCase = true)) assertEquals(e.id, 0.0, en, 0.0)
        }
    }

    @Test fun calculatorModeFollowsBarAndGlyph() {
        fun mode(g: MachineGlyph, n: String, bar: Double = 0.0, loaded: Boolean = false) = Plates.barBase(g, n, bar, loaded)
        assertEquals(0.0, mode(MachineGlyph.plateLoaded, "Chest Press Plate Loaded")!!, 0.0)
        assertNull(mode(MachineGlyph.benchFlat, "Panca Piana Manubri"))
        assertNull(mode(MachineGlyph.smithMachine, "Squat al Multipower"))
        assertNull(mode(MachineGlyph.legPress, "Leg Press 45°"))
        assertEquals(0.0, mode(MachineGlyph.rowMachine, "Rematore Plate Loaded")!!, 0.0)
        assertEquals(0.0, mode(MachineGlyph.rowMachine, "Plated row")!!, 0.0)
        assertEquals(0.0, mode(MachineGlyph.rowMachine, "Rematore macchina", 0.0, true)!!, 0.0)
        assertEquals(20.0, mode(MachineGlyph.rowMachine, "Rematore macchina", 20.0)!!, 0.0)
        assertEquals(0.0, mode(MachineGlyph.ezBar, "Curl Bilanciere EZ")!!, 0.0)
        assertNull(mode(MachineGlyph.legCurlLying, "Leg Curl Sdraiato"))
        assertTrue(Plates.guessPlateLoaded(MachineGlyph.plateLoaded, "x"))
        assertTrue(Plates.guessPlateLoaded(null, "Plated row"))
        assertFalse(Plates.guessPlateLoaded(null, "Template"))
        TestCatalog.load()
        for (e in Catalog.entries) {
            if (Regex("manubri|kettlebell|machine", RegexOption.IGNORE_CASE).containsMatchIn(e.nameIt) && !e.nameIt.contains("plate", ignoreCase = true)) {
                assertNull(e.id, Plates.barBase(e.machineGlyph, e.nameIt, 0.0))
            }
        }
    }

    @Test fun unitsAndSteps() {
        assertNear(181.88, WeightUnit.lb.fromKilograms(82.5))
        assertEquals(82.5, WeightUnit.lb.toKilograms(WeightUnit.lb.fromKilograms(82.5)), 0.0001)
        assertEquals(listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5), Plates.defaults(WeightUnit.lb).map { Math.round(WeightUnit.lb.fromKilograms(it) * 100) / 100.0 })
        assertEquals(2.5, WeightUnit.kg.displayStep(2.5), 0.0)
        assertEquals(5.0, WeightUnit.lb.displayStep(2.5), 0.0)
        assertEquals(2.5, WeightUnit.lb.displayStep(1.25), 0.0)
        assertEquals(20.0, WeightUnit.lb.displayStep(10.0), 0.0)
        assertEquals(2.5, WeightUnit.lb.displayStep(0.5), 0.0)
        assertEquals(17.5, WeightUnit.lb.displayStep(7.5), 0.0)
    }

    /** Cambio unità: dischi e bilanciere di serie seguono, quelli personalizzati restano. */
    @Test fun unitChangeKeepsCustomPlates() {
        val (a, bar) = AppSettings().withUnit(WeightUnit.lb, 20.0)
        assertEquals(Plates.defaults(WeightUnit.lb), a.availablePlates)
        assertEquals(Plates.defaultBar(WeightUnit.lb), bar!!, 1e-9)
        val (b, bar2) = AppSettings(availablePlates = listOf(20.0, 10.0)).withUnit(WeightUnit.lb, 15.0)
        assertEquals(listOf(20.0, 10.0), b.availablePlates)
        assertEquals(15.0, bar2!!, 0.0)
    }
}
