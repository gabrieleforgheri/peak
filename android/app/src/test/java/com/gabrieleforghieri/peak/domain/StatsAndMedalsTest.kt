package com.gabrieleforghieri.peak.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = 86_400_000L

private val ROME = ZoneId.of("Europe/Rome")

private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, ROME).toInstant().toEpochMilli()

/** StatsTests.swift e gli aggregati di common/domain.test.ts. Giovedì 29 ottobre 2026, mezzogiorno. */
class StatsTest {
    private val now = at(2026, 10, 29)

    @Test fun weeklyVolumeSkipsEmptyWeeks() {
        val w = StatWorkout(now, listOf(StatSet("E", 100.0, 5, false)))
        val weekly = Stats.weeklyVolume(listOf(w), 8, now, ROME)
        assertEquals("le settimane senza allenamenti non devono occupare il grafico", 1, weekly.size)
        assertNear(500.0, weekly.last().volume, 0.001)
        // lunedì 26 ottobre, mezzanotte locale
        assertEquals(at(2026, 10, 26, 0), weekly.last().weekStart)
    }

    @Test fun weeklyVolumeKeepsOnlyTheLastWeeks() {
        val old = StatWorkout(now - 20 * 7 * DAY, listOf(StatSet("E", 100.0, 5, false)))
        assertTrue(Stats.weeklyVolume(listOf(old), 12, now, ROME).isEmpty())
    }

    @Test fun volumeByGroupSkipsWarmup() {
        val w = StatWorkout(now, listOf(StatSet("C", 60.0, 10, false), StatSet("C", 20.0, 20, true)))
        val groups = Stats.volumeByGroup(listOf(w), { MuscleGroup.petto }, 30, now)
        assertEquals(1, groups.size)
        assertEquals(MuscleGroup.petto, groups[0].group)
        assertNear(600.0, groups[0].volume, 0.001)
    }

    @Test fun volumeByGroupIsSortedAndAnsweredByTheGroupOfTheExercise() {
        val groupOf = { id: String -> if (id == "A") MuscleGroup.gambe else if (id == "B") MuscleGroup.schiena else null }
        val w = StatWorkout(now, listOf(StatSet("A", 100.0, 10, false), StatSet("B", 50.0, 10, false), StatSet("X", 90.0, 10, false), StatSet(null, 90.0, 10, false)))
        assertEquals(listOf(MuscleGroup.gambe to 1000.0, MuscleGroup.schiena to 500.0), Stats.volumeByGroup(listOf(w), groupOf, 30, now).map { it.group to it.volume })
    }

    @Test fun topMoversRanksByPercentGain() {
        val old = now - 120 * DAY
        val recent = now - 3 * DAY
        val movers = Stats.topMovers(
            listOf("Leg Extension" to listOf(40.0 to old, 60.0 to recent), "Pulley Basso" to listOf(50.0 to old, 55.0 to recent)),
            90, now = now,
        )
        assertEquals(2, movers.size)
        assertEquals("Leg Extension", movers[0].exercise)
        assertNear(50.0, movers[0].deltaPercent)
        assertNear(10.0, movers[1].deltaPercent)
        // nuovo (nessun record prima) vale +100; nessun miglioramento: fuori
        val fresh = Stats.topMovers(listOf("N" to listOf(30.0 to recent), "Piatto" to listOf(50.0 to old, 50.0 to recent)), 90, now = now)
        assertEquals(listOf("N"), fresh.map { it.exercise })
        assertEquals(100.0, fresh[0].deltaPercent, 0.0)
    }

    @Test fun tickIndices() {
        assertEquals(listOf(0, 1, 2), Stats.tickIndices(3))
        assertEquals(listOf(0, 3, 6, 9), Stats.tickIndices(10))
        assertEquals(emptyList<Int>(), Stats.tickIndices(0))
    }

    @Test fun weeklyStreakCountsConsecutiveWeeks() {
        val dates = listOf(now, now - 7 * DAY, now - 14 * DAY, now - 28 * DAY) // salto la quarta settimana
        assertEquals(3, Stats.weeklyStreak(dates, now, ROME))
    }

    /** La serie resta viva finché la settimana in corso non finisce. */
    @Test fun weeklyStreakStaysAliveBeforeTheFirstWorkoutOfTheWeek() {
        assertEquals(2, Stats.weeklyStreak(listOf(now - 7 * DAY, now - 14 * DAY), now, ROME))
        assertEquals(0, Stats.weeklyStreak(listOf(now - 14 * DAY), now, ROME))
        assertEquals(0, Stats.weeklyStreak(emptyList(), now, ROME))
    }

    @Test fun sessionCount() = assertEquals(2, Stats.sessionCount(listOf(now, now - 5 * DAY, now - 40 * DAY), 30, now))

    @Test fun chartRangeAvailability() {
        assertEquals(listOf(ChartRange.all), ChartRange.available(10.0))
        assertEquals(listOf(ChartRange.oneMonth, ChartRange.all), ChartRange.available(45.0))
        assertEquals(listOf(ChartRange.oneMonth, ChartRange.threeMonths, ChartRange.all), ChartRange.available(120.0))
        assertEquals(listOf(ChartRange.threeMonths, ChartRange.year, ChartRange.all), ChartRange.available(400.0))
    }
}

/** L'obiettivo della settimana (WeeklyGoalTests in MedalsTests.swift), come `weeklyGoal` in common/stats.ts. */
class WeeklyGoalTest {
    private val now = at(2026, 10, 29)
    private val thisMonday = at(2026, 10, 26, 0)

    private fun workouts(weeksAgo: Int, count: Int): List<Long> {
        val base = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(thisMonday), ROME).minusWeeks(weeksAgo.toLong()).toInstant().toEpochMilli()
        return (0 until count).map { base + (it + 1) * 3_600_000L }
    }

    private fun goal(dates: List<Long>) = Stats.weeklyGoal(dates, now, ROME)

    @Test fun defaultsToThreeWithoutHistory() {
        assertEquals(3, goal(emptyList()))
        assertEquals(3, goal(workouts(1, 4)))
    }

    @Test fun averageIsRoundedAndClamped() {
        assertEquals(4, goal((1..8).flatMap { workouts(it, 4) }))
        assertEquals(5, goal((1..8).flatMap { workouts(it, 6) }))
        assertEquals(2, goal((1..8).flatMap { workouts(it, 1) }))
    }

    /** Con meno di otto settimane di storia si divide per quelle che ci sono. */
    @Test fun shortHistoryDividesByItsOwnLength() = assertEquals(4, goal((1..3).flatMap { workouts(it, 4) }))
}

/** MedalsTests.swift: gli stessi casi di common/medals.test.ts. */
class MedalsTest {
    private fun medal(input: MedalInput, id: MedalID) = Medals.list(input).first { it.kind == id }

    @Test fun tiersAtThresholds() {
        assertEquals(0, medal(MedalInput(), MedalID.sessions).tier)
        assertEquals(0, medal(MedalInput(sessions = 4), MedalID.sessions).tier)
        assertEquals(1, medal(MedalInput(sessions = 5), MedalID.sessions).tier)
        assertEquals(2, medal(MedalInput(sessions = 25), MedalID.sessions).tier)
        assertEquals(3, medal(MedalInput(sessions = 100), MedalID.sessions).tier)
        assertEquals(1, medal(MedalInput(friends = 1), MedalID.friends).tier)
    }

    @Test fun progressTowardsNextTier() {
        val half = medal(MedalInput(sessions = 15), MedalID.sessions)
        assertEquals(25.0, half.next!!, 0.0)
        assertEquals(0.5, half.progress, 1e-9)
        val gold = medal(MedalInput(sessions = 300), MedalID.sessions)
        assertNull(gold.next)
        assertEquals(1.0, gold.progress, 0.0)
    }

    @Test fun totals() {
        assertEquals(21, Medals.totals(Medals.list(MedalInput())).total)
        assertEquals(6, Medals.totals(Medals.list(MedalInput(sessions = 100, records = 10, streak = 2))).earned)
    }

    private fun medalsAt(ms: Long) = Medals.list(MedalInput(sessions = (ms / 1000).toInt()))

    @Test fun tierDatesAreTheFirstInstantTheTierWasThere() {
        // sessioni = quanti secondi sono passati (1 per istante): bronzo a 5, argento a 25
        val times = (1..30).map { it * 1000L }
        val current = medalsAt(30_000)
        val d = Medals.tierDates(times.reversed(), ::medalsAt, current) // l'ordine in ingresso non conta
        assertEquals(listOf(5000L, 25_000L, null), d.getValue(MedalID.sessions)) // oro non preso: niente data
        assertEquals(listOf<Long?>(null, null, null), d.getValue(MedalID.records))
        assertEquals(listOf<Long?>(null, null, null), Medals.tierDates(emptyList(), ::medalsAt, current).getValue(MedalID.sessions))
    }

    @Test fun tierDatesLostAndRegainedCountFromTheFirstTime() {
        val vals = mapOf(1L to 2, 2L to 0, 3L to 2) // settimane di fila nei tre istanti
        val at = { t: Long -> Medals.list(MedalInput(streak = vals.getValue(t))) }
        val d = Medals.tierDates(listOf(1, 2, 3), at, at(3))
        assertEquals(listOf<Long?>(1L, null, null), d.getValue(MedalID.streak))
    }

    @Test fun orderedPutsClosestFirstAndGoldLast() {
        // costanza 24/25, record 5/10, serie d'oro; il resto è a zero e resta nell'ordine di sempre
        val list = Medals.list(MedalInput(sessions = 24, records = 5, streak = 12))
        assertEquals(
            listOf(MedalID.sessions, MedalID.records, MedalID.volume, MedalID.strength, MedalID.variety, MedalID.friends, MedalID.streak),
            Medals.ordered(list).map { it.kind },
        )
    }

    @Test fun motivationStartIndexIsInRange() {
        assertTrue(Motivation.startIndex() in Motivation.italian.indices)
        assertEquals(32, Motivation.italian.size)
        assertEquals(Motivation.startIndex(at(2026, 10, 29)) + 1, Motivation.startIndex(at(2026, 10, 30)).let { if (it == 0) Motivation.italian.size else it })
    }

    /** `Medals.at`: le medaglie com'erano a una data (MedalsAt.swift). */
    @Test fun medalsAtCountsOnlyWhatWasThere() {
        val w1 = Medals.AtWorkout("W1", at(2026, 10, 1), false, 1000.0)
        val w2 = Medals.AtWorkout("W2", at(2026, 10, 8), false, 500.0)
        val live = Medals.AtWorkout("W3", at(2026, 10, 9), true, 99.0)
        val ex = Medals.AtExercise(
            Limbs.either,
            records = listOf(Medals.AtRecord(at(2026, 10, 1), 100.0, 5, false), Medals.AtRecord(at(2026, 10, 8), 120.0, 3, false), Medals.AtRecord(at(2026, 10, 8), 200.0, 5, true)),
            sets = listOf(Medals.AtSet("W1", false, 100.0, 5, false), Medals.AtSet("W2", false, 120.0, 3, false), Medals.AtSet("W2", true, 300.0, 5, false), Medals.AtSet("W3", false, 400.0, 5, false)),
        )
        val before = Medals.at(listOf(w1, w2, live), listOf(ex), 2, at(2026, 10, 5), true, ROME)
        assertEquals(1.0, before.first { it.kind == MedalID.sessions }.value, 0.0)
        assertEquals(1.0, before.first { it.kind == MedalID.records }.value, 0.0)
        assertEquals(1.0, before.first { it.kind == MedalID.variety }.value, 0.0)
        assertEquals(2.0, before.first { it.kind == MedalID.friends }.value, 0.0)
        val after = Medals.at(listOf(w1, w2, live), listOf(ex), 0, at(2026, 10, 20), true, ROME)
        assertEquals(2.0, after.first { it.kind == MedalID.sessions }.value, 0.0)
        // il record a un braccio (linea separata) e la serie dell'allenamento in corso non contano
        assertEquals(2.0, after.first { it.kind == MedalID.records }.value, 0.0)
        assertEquals(OneRepMax.estimate(120.0, 3), after.first { it.kind == MedalID.strength }.value, 1e-9)
        assertEquals(1500.0, after.first { it.kind == MedalID.volume }.value, 0.0)
        // l'inizio dell'intervallo conta o no secondo `inclusive`
        assertEquals(0.0, Medals.at(listOf(w1), listOf(ex), 0, at(2026, 10, 1), false, ROME).first { it.kind == MedalID.sessions }.value, 0.0)
        assertEquals(1.0, Medals.at(listOf(w1), listOf(ex), 0, at(2026, 10, 1), true, ROME).first { it.kind == MedalID.sessions }.value, 0.0)
    }

    @Test fun medalDatesHaveNoFriendsDate() {
        val w = (0 until 6).map { Medals.AtWorkout("W$it", at(2026, 9, 1) + it * DAY, false, 100.0) }
        val current = Medals.at(w, emptyList(), 1, at(2026, 10, 1), true, ROME)
        val dates = Medals.dates(w, emptyList(), 1, current, ROME)
        assertEquals(w[4].date, dates.getValue(MedalID.sessions)[0])
        assertEquals(listOf<Long?>(null, null, null), dates.getValue(MedalID.friends))
    }
}
