import XCTest
@testable import Massimali

/// Gli stessi casi di common/medals.test.ts.
final class MedalsTests: XCTestCase {

    private func medal(_ input: MedalInput, _ id: MedalID) -> Medal {
        Medals.list(input).first { $0.kind == id }!
    }

    func testTiersAtThresholds() {
        XCTAssertEqual(medal(MedalInput(), .sessions).tier, 0)
        XCTAssertEqual(medal(MedalInput(sessions: 4), .sessions).tier, 0)
        XCTAssertEqual(medal(MedalInput(sessions: 5), .sessions).tier, 1)
        XCTAssertEqual(medal(MedalInput(sessions: 25), .sessions).tier, 2)
        XCTAssertEqual(medal(MedalInput(sessions: 100), .sessions).tier, 3)
        XCTAssertEqual(medal(MedalInput(friends: 1), .friends).tier, 1)
    }

    func testProgressTowardsNextTier() {
        let half = medal(MedalInput(sessions: 15), .sessions)
        XCTAssertEqual(half.next, 25)
        XCTAssertEqual(half.progress, 0.5, accuracy: 1e-9)
        let gold = medal(MedalInput(sessions: 300), .sessions)
        XCTAssertNil(gold.next)
        XCTAssertEqual(gold.progress, 1)
    }

    func testTotals() {
        XCTAssertEqual(Medals.totals(Medals.list(MedalInput())).total, 21)
        let some = Medals.totals(Medals.list(MedalInput(sessions: 100, records: 10, streak: 2)))
        XCTAssertEqual(some.earned, 6)
    }

    private func at(_ s: Double) -> Date { Date(timeIntervalSince1970: s) }

    func testTierDatesAreTheFirstInstantTheTierWasThere() {
        // sessioni = quanti secondi sono passati (1 per istante): bronzo a 5, argento a 25
        let times = (1...30).map { at(Double($0) * 1000) }
        let medalsAt: (Date) -> [Medal] = { Medals.list(MedalInput(sessions: Int($0.timeIntervalSince1970 / 1000))) }
        let current = medalsAt(at(30_000))
        let d = Medals.tierDates(times: times.reversed(), at: medalsAt, current: current) // l'ordine in ingresso non conta
        XCTAssertEqual(d[.sessions]!, [at(5000), at(25_000), nil]) // oro non preso: niente data
        XCTAssertEqual(d[.records]!, [nil, nil, nil])
        XCTAssertEqual(Medals.tierDates(times: [], at: medalsAt, current: current)[.sessions]!, [nil, nil, nil])
    }

    func testTierDatesLostAndRegainedCountFromTheFirstTime() {
        let vals: [Double: Int] = [1: 2, 2: 0, 3: 2] // settimane di fila nei tre istanti
        let medalsAt: (Date) -> [Medal] = { Medals.list(MedalInput(streak: vals[$0.timeIntervalSince1970]!)) }
        let d = Medals.tierDates(times: [at(1), at(2), at(3)], at: medalsAt, current: medalsAt(at(3)))
        XCTAssertEqual(d[.streak]!, [at(1), nil, nil])
    }

    func testOrderedPutsClosestFirstAndGoldLast() {
        // costanza 24/25, record 5/10, serie d'oro; il resto è a zero e resta nell'ordine di sempre
        let list = Medals.list(MedalInput(sessions: 24, records: 5, streak: 12))
        XCTAssertEqual(Medals.ordered(list).map(\.kind), [.sessions, .records, .volume, .strength, .variety, .friends, .streak])
    }

    func testMotivationStartIndexIsInRange() {
        XCTAssertTrue((0..<Motivation.phrases.count).contains(Motivation.startIndex()))
    }
}

/// L'obiettivo della settimana, come `weeklyGoal` in common/stats.ts.
final class WeeklyGoalTests: XCTestCase {
    private func workouts(weeksAgo: Int, count: Int, now: Date) -> [Workout] {
        var cal = Calendar.current
        cal.firstWeekday = 2
        let monday = cal.dateInterval(of: .weekOfYear, for: now)!.start
        let base = cal.date(byAdding: .weekOfYear, value: -weeksAgo, to: monday)!
        return (0..<count).map { Workout(date: base.addingTimeInterval(Double($0 + 1) * 3600), note: "") }
    }

    func testDefaultsToThreeWithoutHistory() {
        XCTAssertEqual(Stats.weeklyGoal(workouts: []), 3)
        XCTAssertEqual(Stats.weeklyGoal(workouts: workouts(weeksAgo: 1, count: 4, now: Date())), 3)
    }

    func testAverageIsRoundedAndClamped() {
        let now = Date()
        XCTAssertEqual(Stats.weeklyGoal(workouts: (1...8).flatMap { workouts(weeksAgo: $0, count: 4, now: now) }, now: now), 4)
        XCTAssertEqual(Stats.weeklyGoal(workouts: (1...8).flatMap { workouts(weeksAgo: $0, count: 6, now: now) }, now: now), 5)
        XCTAssertEqual(Stats.weeklyGoal(workouts: (1...8).flatMap { workouts(weeksAgo: $0, count: 1, now: now) }, now: now), 2)
    }
}
