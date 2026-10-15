import XCTest
@testable import Massimali

final class OneRepMaxTests: XCTestCase {

    func testSingleRepReturnsRawWeight() {
        XCTAssertEqual(OneRepMax.estimate(weight: 100, reps: 1), 100)
    }

    func testEpley() {
        // 100 × (1 + 5/30) = 116,66…
        XCTAssertEqual(OneRepMax.estimate(weight: 100, reps: 5), 116.666, accuracy: 0.01)
        XCTAssertEqual(OneRepMax.estimate(weight: 80, reps: 10), 106.666, accuracy: 0.01)
    }

    func testInvalidInputIsZero() {
        XCTAssertEqual(OneRepMax.estimate(weight: 0, reps: 5), 0)
        XCTAssertEqual(OneRepMax.estimate(weight: 100, reps: 0), 0)
        XCTAssertEqual(OneRepMax.estimate(weight: 100, reps: -3), 0)
    }

    func testStaysIncreasingAtHighReps() {
        var previous = 0.0
        for reps in 1...50 {
            let value = OneRepMax.estimate(weight: 60, reps: reps)
            XCTAssertGreaterThan(value, previous)
            previous = value
        }
    }
}
