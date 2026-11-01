package com.gabrieleforghieri.peak.ui.me

import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.domain.ChartRange
import com.gabrieleforghieri.peak.domain.Medal
import com.gabrieleforghieri.peak.domain.MedalID
import com.gabrieleforghieri.peak.domain.Medals
import com.gabrieleforghieri.peak.domain.OneRepMax
import com.gabrieleforghieri.peak.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** La logica di Me che non è disegno: i numeri di `HomeStats`, la progressione, il peso corporeo, i grafici. */
class MeStatsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val dayMs = 86_400_000L
    // mercoledì 28 ottobre 2026, mezzogiorno
    private val now = LocalDate.of(2026, 10, 28).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun ago(days: Int) = now - days * dayMs

    private val bench = ExerciseEntity("BENCH", name = "Panca piana", muscleGroup = "petto")
    private val squat = ExerciseEntity("SQUAT", name = "Squat", muscleGroup = "gambe")
    private val legExt = ExerciseEntity("LEG", name = "Leg extension", muscleGroup = "gambe", catalogID = "leg-extension")

    private fun workout(id: String, daysAgo: Int, ended: Boolean = true) =
        WorkoutEntity(id, date = ago(daysAgo), endedAt = if (ended) ago(daysAgo) + 3_600_000 else null)

    private fun set(id: String, w: String, e: ExerciseEntity?, kg: Double, reps: Int, warmup: Boolean = false, arm: Boolean = false) =
        WorkoutSetEntity(id, w, e?.uuid, kg, reps, isWarmup = warmup, singleArm = arm, createdAt = 1)

    @Test fun emptyDatabaseHasNothingToShow() {
        val data = MeData.compute(emptyList(), emptyList(), emptyList(), emptyList(), 0, now, zone)
        assertFalse(data.hasTrainingData)
        assertEquals(0, data.stats.finished)
        assertEquals(12, data.stats.weeks.size)
        assertTrue(data.stats.weeks.all { it.volume == 0.0 })
        assertTrue(data.progression.isEmpty() && data.movers.isEmpty() && data.recentByExercise.isEmpty())
        assertEquals(0, Medals.totals(data.medals).earned)
    }

    @Test fun anUnfinishedWorkoutDoesNotCount() {
        val data = MeData.compute(listOf(workout("A", 1, ended = false)), listOf(set("S", "A", bench, 100.0, 5)), listOf(bench), emptyList(), 0, now, zone)
        assertEquals(0, data.stats.finished)
        assertEquals(0.0, data.stats.totalVolume, 0.0)
        // ma il macchinario ha una serie, quindi è «usato» e il suo 1RM conta (come su iPhone)
        assertEquals(1, data.stats.machinesUsed)
        assertEquals(OneRepMax.estimate(100.0, 5), data.stats.bestOneRm, 1e-9)
        assertFalse(data.hasTrainingData)
    }

    @Test fun homeStatsCountsWorkingSetsOnly() {
        val workouts = listOf(workout("A", 0), workout("B", 7), workout("C", 40))
        val sets = listOf(
            set("1", "A", bench, 100.0, 5), set("2", "A", bench, 60.0, 10, warmup = true),
            set("3", "B", bench, 80.0, 10), set("4", "C", squat, 120.0, 3),
        )
        val records = listOf(MaxRecordEntity("R1", "BENCH", ago(7), 100.0, 5), MaxRecordEntity("R2", "SQUAT", ago(40), 120.0, 3))
        val s = HomeStats.compute(workouts, sets, listOf(bench, squat, legExt), records, now, zone)
        assertEquals(3, s.finished)
        assertEquals(500.0 + 800.0 + 360.0, s.totalVolume, 1e-9)
        assertEquals(2, s.recordCount)
        assertEquals(2, s.machinesUsed)
        assertEquals(OneRepMax.estimate(120.0, 3), s.bestOneRm, 1e-9)
        assertEquals(2, s.streak)
        assertEquals(3, s.last98Days)
        assertEquals(1, s.perDay[LocalDate.of(2026, 10, 28)])
        assertEquals(1, s.perDay[LocalDate.of(2026, 10, 21)])
    }

    @Test fun weeksAreTwelveFromMondayEvenWhenEmpty() {
        val s = HomeStats.compute(listOf(workout("A", 0), workout("B", 14)), listOf(set("1", "A", bench, 100.0, 5), set("2", "B", bench, 50.0, 4)), listOf(bench), emptyList(), now, zone)
        assertEquals(12, s.weeks.size)
        assertEquals(DayOfWeek.MONDAY, s.weeks.last().start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 26), s.weeks.last().start)
        assertEquals(500.0, s.weeks.last().volume, 0.0)
        assertEquals(0.0, s.weeks[10].volume, 0.0)
        assertEquals(200.0, s.weeks[9].volume, 0.0)
    }

    @Test fun volumeByGroupFeedsTheDonut() {
        val w = listOf(workout("A", 2))
        val sets = listOf(set("1", "A", bench, 100.0, 10), set("2", "A", squat, 100.0, 20), set("3", "A", squat, 40.0, 10, warmup = true))
        val s = HomeStats.compute(w, sets, listOf(bench, squat), emptyList(), now, zone)
        assertEquals(listOf("gambe", "petto"), s.groups.map { it.group.raw })
        assertEquals(2000.0, s.groups[0].volume, 0.0)
    }

    @Test fun oneArmLineIsKeptOutOfTheTwoArmNumbers() {
        // leg extension si fa in tutti e due i modi: i massimali a un arto sono una linea a parte
        val records = listOf(MaxRecordEntity("A", "LEG", ago(3), 80.0, 5), MaxRecordEntity("B", "LEG", ago(2), 100.0, 5, singleArm = true))
        val data = MeData.compute(emptyList(), emptyList(), listOf(legExt), records, 0, now, zone)
        assertEquals(1, data.stats.recordCount)
        assertEquals(OneRepMax.estimate(80.0, 5), data.stats.bestOneRm, 1e-9)
        assertEquals(80.0, data.recentByExercise.single().second.weight, 0.0)
        assertTrue(data.hasTrainingData)
    }

    @Test fun onlyOneArmRecordsMeansNoTrainingData() {
        val records = listOf(MaxRecordEntity("B", "LEG", ago(2), 100.0, 5, singleArm = true))
        assertFalse(MeData.compute(emptyList(), emptyList(), listOf(legExt), records, 0, now, zone).hasTrainingData)
    }

    @Test fun recentRecordsAreOnePerMachineLatestFirstCappedAtFour() {
        val ex = (1..6).map { ExerciseEntity("E$it", name = "M$it") }
        val records = ex.mapIndexed { i, e -> MaxRecordEntity("R$i", e.uuid, ago(10 - i), 50.0 + i, 5) } + MaxRecordEntity("OLD", "E6", ago(30), 99.0, 5)
        val data = MeData.compute(emptyList(), emptyList(), ex, records, 0, now, zone)
        assertEquals(listOf("E6", "E5", "E4", "E3"), data.recentByExercise.map { it.first.uuid })
        assertEquals(55.0, data.recentByExercise.first().second.weight, 0.0)
    }

    @Test fun progressionNeedsTwoDaysAndKeepsTheBestOfEachDay() {
        val workouts = listOf(workout("A", 20), workout("B", 10), workout("ACT", 1, ended = false))
        val sets = listOf(
            set("1", "A", bench, 80.0, 5), set("2", "A", bench, 85.0, 3), set("3", "A", bench, 40.0, 10, warmup = true),
            set("4", "B", bench, 90.0, 5), set("5", "ACT", bench, 200.0, 5),
        )
        val records = listOf(MaxRecordEntity("R", "BENCH", ago(20), 82.0, 5))
        val data = MeData.compute(workouts, sets, listOf(bench, squat), records, 0, now, zone)
        val series = data.progression.single()
        assertEquals("BENCH", series.id)
        assertEquals(2, series.points.size)
        assertEquals(maxOf(OneRepMax.estimate(80.0, 5), OneRepMax.estimate(85.0, 3), OneRepMax.estimate(82.0, 5)), series.points[0].oneRm, 1e-9)
        assertEquals(OneRepMax.estimate(90.0, 5), series.points[1].oneRm, 1e-9)
        assertTrue(series.points[0].date < series.points[1].date)
    }

    @Test fun archivedMachinesAreNotInProgressionAndOrderIsByBest1RM() {
        val a = bench.copy(isArchived = true)
        val workouts = listOf(workout("A", 20), workout("B", 10))
        val sets = listOf(set("1", "A", a, 100.0, 5), set("2", "B", a, 110.0, 5), set("3", "A", squat, 100.0, 5), set("4", "B", squat, 120.0, 5))
        val records = listOf(MaxRecordEntity("R", "SQUAT", ago(10), 120.0, 5))
        val data = MeData.compute(workouts, sets, listOf(a, squat), records, 0, now, zone)
        assertEquals(listOf("SQUAT"), data.progression.map { it.id })
    }

    @Test fun moversAreTheMachinesThatGrewInTheLast90Days() {
        val records = listOf(
            MaxRecordEntity("1", "BENCH", ago(120), 80.0, 5), MaxRecordEntity("2", "BENCH", ago(10), 90.0, 5),
            MaxRecordEntity("3", "SQUAT", ago(10), 100.0, 5),
        )
        val m = MeData.compute(emptyList(), emptyList(), listOf(bench, squat), records, 0, now, zone).movers
        assertEquals(listOf("SQUAT", "BENCH"), m.map { it.exercise.uuid })
        assertEquals(0.0, m[0].previous, 0.0)
        assertEquals(80.0, m[1].previous, 0.0)
        assertEquals(12.5, m[1].deltaPercent, 1e-9)
    }

    @Test fun medalsFollowTheNumbersAndTheFriendCount() {
        val data = MeData.compute(emptyList(), emptyList(), emptyList(), emptyList(), 3, now, zone)
        val friends = data.medals.single { it.kind == MedalID.friends }
        assertEquals(2, friends.tier)
        assertEquals(10.0, friends.next!!, 0.0)
    }

    @Test fun medalDatesComeFromTheHistory() {
        val workouts = (0 until 6).map { workout("W$it", 60 - it * 7) }
        val sets = workouts.map { set("S${it.uuid}", it.uuid, bench, 50.0, 10) }
        val data = MeData.compute(workouts, sets, listOf(bench), emptyList(), 0, now, zone)
        val sessions = data.medals.single { it.kind == MedalID.sessions }
        assertEquals(1, sessions.tier)
        val dates = medalDates(workouts, sets, listOf(bench), emptyList(), 0, data.medals, zone)
        // il bronzo (5 allenamenti) è scattato al quinto allenamento, il più vicino a oggi dopo quattro precedenti
        assertEquals(workouts[4].date, dates.getValue(MedalID.sessions)[0])
        assertNull(dates.getValue(MedalID.sessions)[1])
        assertEquals(listOf<Long?>(null, null, null), dates.getValue(MedalID.friends))
    }

    // ---- peso corporeo ----

    private fun weights(vararg daysAndKg: Pair<Int, Double>) = daysAndKg.map { (d, kg) -> BodyWeightEntity("B$d", ago(d), kg) }.sortedByDescending { it.date }

    @Test fun monthDeltaComparesWithTheWeighingAMonthBefore() {
        assertNull(monthDelta(emptyList()))
        assertNull(monthDelta(weights(0 to 80.0, 10 to 81.0)))
        assertEquals(-2.0, monthDelta(weights(0 to 80.0, 10 to 81.0, 31 to 82.0))!!, 1e-9)
        // la più recente fra quelle di almeno 30 giorni prima, non la più vecchia
        assertEquals(-1.0, monthDelta(weights(0 to 80.0, 30 to 81.0, 90 to 90.0))!!, 1e-9)
    }

    @Test fun rangesDependOnHowOldTheDataIs() {
        assertEquals(listOf(ChartRange.all), availableRanges(null, now))
        assertEquals(listOf(ChartRange.all), availableRanges(ago(10), now))
        assertEquals(listOf(ChartRange.oneMonth, ChartRange.all), availableRanges(ago(40), now))
        assertEquals(listOf(ChartRange.oneMonth, ChartRange.threeMonths, ChartRange.all), availableRanges(ago(200), now))
        assertEquals(listOf(ChartRange.threeMonths, ChartRange.year, ChartRange.all), availableRanges(ago(400), now))
        // un intervallo che non c'è più cede al primo disponibile
        assertEquals(ChartRange.oneMonth, activeRange(ChartRange.year, listOf(ChartRange.oneMonth, ChartRange.all)))
    }

    @Test fun bodyWeightPointsAreOldestFirstAndCutByRange() {
        val e = weights(0 to 80.0, 20 to 81.0, 100 to 85.0)
        assertEquals(listOf(85.0, 81.0, 80.0), bodyWeightPoints(e, ChartRange.all, now).map { it.weight })
        assertEquals(listOf(81.0, 80.0), bodyWeightPoints(e, ChartRange.threeMonths, now).map { it.weight })
    }

    @Test fun pickDayKeepsTheTimeOfDay() {
        val ms = LocalDate.of(2026, 10, 28).atTime(17, 45).atZone(zone).toInstant().toEpochMilli()
        val picked = pickDay(ms, LocalDate.of(2026, 10, 3), zone)
        val back = java.time.Instant.ofEpochMilli(picked).atZone(zone)
        assertEquals(LocalDate.of(2026, 10, 3), back.toLocalDate())
        assertEquals(17, back.hour)
        assertEquals(45, back.minute)
    }

    @Test fun bodyWeightLimitsAreTheServers() {
        assertTrue(20.0 in BODY_WEIGHT_KG && 400.0 in BODY_WEIGHT_KG)
        assertFalse(19.9 in BODY_WEIGHT_KG || 400.1 in BODY_WEIGHT_KG)
    }

    // ---- grafici e testi ----

    @Test fun niceTicksCoverTheDataWithRoundSteps() {
        assertEquals(listOf(70.0, 75.0, 80.0, 85.0), niceTicks(72.0, 83.0, 4))
        assertEquals(listOf(0.0, 50.0, 100.0), niceTicks(3.0, 97.0, 3))
        assertEquals(listOf(5.0), niceTicks(5.0, 5.0))
        // mai tacche sotto il passo minimo (le etichette sono interi)
        val t = niceTicks(74.8, 75.6, 4, minStep = 1.0)
        assertTrue(t.zipWithNext().all { (a, b) -> b - a >= 1.0 })
        assertTrue(t.first() <= 74.8 && t.last() >= 75.6)
    }

    @Test fun heatmapIsFourteenWeeksFromMondayWithFutureDaysMarked() {
        val today = LocalDate.of(2026, 10, 28) // mercoledì
        val cols = heatmapColumns(mapOf(today to 9, LocalDate.of(2026, 10, 26) to 3), today)
        assertEquals(14, cols.size)
        assertTrue(cols.all { it.size == 7 })
        assertEquals(DayOfWeek.MONDAY, cols[0][0].first.dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 27), cols[0][0].first)
        assertEquals(3, cols.last()[0].second)
        assertEquals(9, cols.last()[2].second)
        assertEquals(-1, cols.last()[3].second) // giovedì: non è ancora arrivato
        assertEquals(0, cols[0][0].second)
    }

    @Test fun medalProgressText() {
        val m = Medals.list(com.gabrieleforghieri.peak.domain.MedalInput(sessions = 12, volumeKg = 12_400.0, bestOneRm = 150.0))
        fun of(k: MedalID) = m.single { it.kind == k }
        assertEquals("12 / 25", com.gabrieleforghieri.peak.ui.me.progressLine(of(MedalID.sessions), WeightUnit.kg))
        assertTrue(com.gabrieleforghieri.peak.ui.me.progressLine(of(MedalID.volume), WeightUnit.kg).contains("t"))
        // a oro preso solo il valore
        assertEquals("150 kg", com.gabrieleforghieri.peak.ui.me.progressLine(of(MedalID.strength), WeightUnit.kg))
    }

    @Test fun pathFillRunsFromTheTierBelow() {
        val m = Medal(MedalID.sessions, 15.0, 2, 0.0, 100.0)
        assertEquals(1.0, pathFill(m, 0), 0.0)
        assertEquals(0.5, pathFill(m, 1), 1e-9)  // da 5 a 25: 15 è a metà
        assertEquals(0.0, pathFill(m, 2), 0.0)
    }

    @Test fun relativeDaysAndInitial() {
        assertEquals("Oggi", relativeDayKey(now - 3_600_000, now, zone))
        assertEquals("Ieri", relativeDayKey(ago(1), now, zone))
        assertNull(relativeDayKey(ago(2), now, zone))
        assertEquals("G", initialOf("gabriele"))
        assertEquals("P", initialOf(""))
        assertNotNull(metalTint(3))
    }
}
