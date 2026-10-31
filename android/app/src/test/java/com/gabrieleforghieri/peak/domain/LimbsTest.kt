package com.gabrieleforghieri.peak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** LimbsTests.swift: gemello di «uno o due arti» e «due manici» in common/domain.test.ts. */
class LimbsTest {
    private fun limbs(catalog: String, name: String = "", group: String = "petto") = Limbs.defaultLimbs(catalog, name, group)

    @Test fun defaultFromCatalogAsInRealData() {
        // Cable Fly basso: si fa sempre insieme, il segno «1 braccio» di ieri non apre una linea a parte
        assertEquals(Limbs.both, limbs("cable-fly"))
        assertFalse(Limbs.lineArm(limbs("cable-fly"), true))
        // alzate laterali a un braccio al cavo: sempre un arto
        assertEquals(Limbs.one, limbs("lateral-raise-single-arm-cable", "", "spalle"))
        assertFalse(Limbs.lineArm(Limbs.one, true))
        // leg extension: lo stesso macchinario a due o a una gamba
        assertEquals(Limbs.either, limbs("leg-extension", "", "gambe"))
        assertTrue(Limbs.lineArm(Limbs.either, true))
        assertFalse(Limbs.lineArm(Limbs.either, false))
        assertEquals(Limbs.both, limbs("bench-press-barbell"))
        assertEquals(Limbs.both, limbs("crunch", "", "core"))
        assertEquals(Limbs.one, limbs("", "Curl a un braccio", "braccia"))
        assertEquals(Limbs.both, limbs("", "Panca mia"))
    }

    @Test fun savedChoiceWinsAndOldUnilateralMeansOne() {
        assertEquals(Limbs.either, Limbs.limbsOf("either", false, "cable-fly", "", "petto"))
        assertEquals(Limbs.one, Limbs.limbsOf(null, true, "bench-press-barbell", "", "petto"))
        assertEquals(Limbs.both, Limbs.limbsOf("boh", false, "bench-press-barbell", "", "petto"))
    }

    @Test fun twoHandlesDumbbellsAndCablesDoNotHalve() {
        assertTrue(Limbs.twoHandles("dumbbell-curl", "", ""))
        assertTrue(Limbs.twoHandles("cable-curl", "", ""))
        assertFalse(Limbs.twoHandles("leg-extension", "", "legLever"))
        assertTrue(Limbs.twoHandles("", "Curl ai cavi", ""))
        assertFalse(Limbs.twoHandles("", "Leg extension", ""))
    }

    /** Volume = peso × ripetizioni, un arto o due non cambia il conto. */
    @Test fun volumeIsWeightTimesRepsWhateverTheLimbs() {
        val one = StatSet(null, 20.0, 10, false)
        assertEquals(200.0, StatWorkout(0, listOf(one)).totalVolume, 0.0)
        assertEquals(0.0, StatWorkout(0, listOf(one.copy(isWarmup = true))).totalVolume, 0.0)
    }

    /** Il catalogo vero: ogni voce ha una scelta di base valida e le «insieme» non aprono la linea a un braccio. */
    @Test fun everyCatalogEntryHasADefault() {
        TestCatalog.load()
        for (e in Catalog.entries) {
            val l = Limbs.defaultLimbs(e.id, e.nameIt, e.group)
            if (e.group == "core") assertEquals(e.id, Limbs.both, l)
            assertFalse(e.id, Limbs.lineArm(Limbs.both, true))
        }
    }
}
