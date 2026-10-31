package com.gabrieleforghieri.peak.domain

import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le date e gli uuid dei documenti: gli stessi casi di common/docs.test.ts, perché la sincronizzazione (fase 2) ne dipende. */
class DocsTest {
    @Test fun isoDatesAreFlooredToWholeSeconds() {
        val ms = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
        assertEquals("2026-10-14T12:00:00Z", PeakDates.string(ms))
        assertEquals("2026-10-14T12:00:00Z", PeakDates.string(ms + 999))
        assertEquals("2026-10-14T11:59:59Z", PeakDates.string(ms - 1))
        assertEquals(ms, PeakDates.floorSec(ms + 750))
    }

    @Test fun impossibleDatesDoNotRollOver() {
        fun utc(y: Int, m: Int, d: Int, h: Int) = java.time.LocalDateTime.of(y, m, d, h, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        assertEquals(utc(2026, 2, 28, 10), PeakDates.parse("2026-02-28T10:00:00Z"))
        assertEquals(utc(2024, 2, 29, 10), PeakDates.parse("2024-02-29T10:00:00Z"))
        assertNull(PeakDates.parse("2026-02-30T10:00:00Z"))
        assertNull(PeakDates.parse("2026-13-01T10:00:00Z"))
        assertNull(PeakDates.parse("2026-01-01T24:00:00Z"))
        assertEquals(utc(2026, 1, 1, 10), PeakDates.parse("2026-01-01T10:00:00.250Z"))
        assertNull(PeakDates.parse("1999-12-31T23:59:59Z"))
        assertNull(PeakDates.parse("2100-01-02T00:00:00Z"))
        assertNull(PeakDates.parse("2026-01-01 10:00:00"))
        assertNull(PeakDates.parse("2026-01-01T10:00:00+01:00"))
        assertNull(PeakDates.parse(null))
    }

    @Test fun datesRoundTripThroughText() {
        val ms = Instant.parse("2026-03-29T01:30:15Z").toEpochMilli()
        assertEquals(ms, PeakDates.parse(PeakDates.string(ms)))
    }

    @Test fun uuidsAreUppercaseAndValidated() {
        assertEquals("0A1B2C3D-0000-4000-8000-5045414B0001", Uuids.normalize("0a1b2c3d-0000-4000-8000-5045414b0001"))
        assertNull(Uuids.normalize("non-un-uuid"))
        assertNull(Uuids.normalize(null))
        assertNull(Uuids.normalize("0A1B2C3D-0000-4000-8000-5045414B000G"))
        val fresh = Uuids.new()
        assertEquals(fresh, Uuids.normalize(fresh))
        assertNotEquals(fresh, Uuids.new())
        assertEquals(Uuids.DEFAULT_PROGRAM, Uuids.normalize(Uuids.DEFAULT_PROGRAM))
    }

    /** `restAfter` in common/domain.test.ts: il recupero dopo il riscaldamento. */
    @Test fun restAfterWarmup() {
        fun rest(base: Int, warmup: Boolean, percent: Int) = AppSettings(restSeconds = base, warmupRestPercent = percent).restDuration(0, warmup)
        assertEquals(120, rest(120, false, 50))
        assertEquals(60, rest(120, true, 50))
        assertEquals(120, rest(120, true, 100))
        assertEquals(0, rest(120, true, 0))
        assertEquals(5, rest(15, true, 25)) // 3,75 s: minimo 5
        // il recupero proprio del macchinario vale al posto di quello delle impostazioni
        assertEquals(45, AppSettings(restSeconds = 120).restDuration(90, true))
    }

    @Test fun repRangeIsNeverEmpty() {
        assertEquals(8..12, AppSettings().repRange)
        assertEquals(10..10, AppSettings(repRangeMin = 10, repRangeMax = 6).repRange)
    }

    @Test fun defaultsMatchTheSharedOnes() {
        val s = AppSettings()
        assertEquals(WeightUnit.kg, s.unit)
        assertEquals(AccentPalette.lime, s.accent)
        assertEquals(120, s.restSeconds)
        assertEquals(50, s.warmupRestPercent)
        assertEquals(listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25), s.availablePlates)
        assertEquals(1, s.tourSeen)
        assertEquals(0, s.weeklyGoal)
        assertEquals(16, SettingKeys.synced.size)
    }
}

class FmtTest {
    private val it = Locale.ITALY
    private val en = Locale.US

    @Test fun weightUsesTwoDecimalsAtMost() {
        assertEquals("82,5 kg", Fmt.weight(82.5, WeightUnit.kg, it))
        assertEquals("1,25 kg", Fmt.weight(1.25, WeightUnit.kg, it))
        assertEquals("80 kg", Fmt.weight(80.0, WeightUnit.kg, it))
        assertEquals("82.5", Fmt.weight(82.5, WeightUnit.kg, en, includeSymbol = false))
        assertEquals("181.88 lb", Fmt.weight(82.5, WeightUnit.lb, en))
    }

    @Test fun volumeSwitchesToTons() {
        assertEquals("999 kg", Fmt.volume(999.0, WeightUnit.kg, it))
        assertEquals("12,4 t", Fmt.volume(12_400.0, WeightUnit.kg, it))
        assertEquals("12.4 t", Fmt.volume(12_400.0, WeightUnit.kg, en))
        assertTrue(Fmt.volume(12_400.0, WeightUnit.lb, en).endsWith("klb"))
    }

    @Test fun clockAndDuration() {
        assertEquals("1:30", Fmt.clock(90))
        assertEquals("0:00", Fmt.clock(-5))
        assertEquals("45 min", Fmt.duration(45 * 60.0))
        assertEquals("1 h 5 min", Fmt.duration(65 * 60.0))
    }

    @Test fun deltaHasSignAndNoNegativeZero() {
        assertEquals("+4,2%", Fmt.delta(4.2, it))
        assertEquals("-3,5%", Fmt.delta(-3.5, it))
        assertEquals("0%", Fmt.delta(-0.04, it))
        assertEquals("0%", Fmt.delta(0.0, it))
    }

    @Test fun notebookLineGroupsSetsWithTheSameWeight() {
        val sets = listOf(Fmt.NotebookSet(71.5, 8), Fmt.NotebookSet(71.5, 6), Fmt.NotebookSet(54.0, 8))
        assertEquals("71,5 kg 8·6 · 54 kg 8", Fmt.notebookLine(sets, WeightUnit.kg, locale = it))
        val one = listOf(Fmt.NotebookSet(40.0, 8, true), Fmt.NotebookSet(40.0, 7, true), Fmt.NotebookSet(70.0, 10))
        assertEquals("1 br 40 kg 8·7 · 70 kg 10", Fmt.notebookLine(one, WeightUnit.kg, locale = it))
        assertEquals("1 gb 40 kg 8·7 · 70 kg 10", Fmt.notebookLine(one, WeightUnit.kg, MuscleGroup.gambe, locale = it))
        assertEquals("", Fmt.notebookLine(emptyList(), WeightUnit.kg))
    }

    @Test fun setLineAddsTheSideForOneArm() {
        assertEquals("80 kg × 5", Fmt.setLine(80.0, 5, WeightUnit.kg, locale = it))
        assertEquals("80 kg × 5 · 1 br", Fmt.setLine(80.0, 5, WeightUnit.kg, singleArm = true, locale = it))
        assertEquals("80 kg × 5 · 1 gb", Fmt.setLine(80.0, 5, WeightUnit.kg, true, MuscleGroup.gambe, locale = it))
        assertFalse(MuscleGroup.core.hasSides)
    }
}

/** PhotoFramingTests.swift (la parte di inquadratura; il ridimensionamento della foto è della fase 3). */
class PhotoFramingTest {
    @Test fun wideImageOverflowsHorizontally() {
        assertNear(33.33, PhotoFraming.overflow(4.0 / 3.0, 100.0))
        assertFalse(PhotoFraming.isVertical(4.0 / 3.0))
    }

    @Test fun tallImageOverflowsVertically() {
        assertNear(33.33, PhotoFraming.overflow(3.0 / 4.0, 100.0))
        assertTrue(PhotoFraming.isVertical(3.0 / 4.0))
    }

    @Test fun squareImageDoesNotOverflow() = assertEquals(0.0, PhotoFraming.overflow(1.0, 100.0), 0.0001)

    @Test fun offsetEndpointsAndClamp() {
        val aspect = 3.0 / 4.0
        assertEquals(0.0, PhotoFraming.offsetPoints(0.0, aspect, 100.0), 0.001)
        assertNear(16.67, PhotoFraming.offsetPoints(1.0, aspect, 100.0))
        assertNear(-16.67, PhotoFraming.offsetPoints(-1.0, aspect, 100.0))
        assertEquals(PhotoFraming.offsetPoints(1.0, aspect, 100.0), PhotoFraming.offsetPoints(5.0, aspect, 100.0), 0.001)
        assertEquals(-PhotoFraming.offsetPoints(1.0, aspect, 100.0), PhotoFraming.offsetPoints(-9.0, aspect, 100.0), 0.001)
    }

    @Test fun dragAcrossTheFullOverflowReachesTheLimit() {
        val half = PhotoFraming.overflow(3.0 / 4.0, 200.0) / 2
        assertEquals(1.0, PhotoFraming.normalizedDelta(half, 3.0 / 4.0, 200.0), 0.001)
        assertEquals(0.0, PhotoFraming.normalizedDelta(50.0, 1.0, 100.0), 0.0)
    }

    @Test fun degenerateInputsAreSafe() {
        assertEquals(0.0, PhotoFraming.overflow(0.0, 100.0), 0.0)
        assertEquals(0.0, PhotoFraming.overflow(1.5, 0.0), 0.0)
        assertEquals(0.0, PhotoFraming.normalizedDelta(10.0, 0.0, 100.0), 0.0)
    }
}
