import XCTest
@testable import Massimali

final class PlatesTests: XCTestCase {

    private let standard: [Double] = [25, 20, 15, 10, 5, 2.5, 1.25]

    func testBarbellUsesFewestPlates() {
        let load = Plates.load(total: 102.5, bar: 20, plates: standard)
        XCTAssertEqual(load.perSide, [25, 15, 1.25])
        XCTAssertEqual(load.leftover, 0)
    }

    func testPlateLoadedHasNoBar() {
        let load = Plates.load(total: 90, bar: 0, plates: standard)
        XCTAssertEqual(load.perSide, [25, 20])
    }

    func testMissingPlatesLeaveALeftover() {
        let load = Plates.load(total: 65, bar: 20, plates: [20, 10, 5])
        XCTAssertEqual(load.perSide, [20])
        XCTAssertEqual(load.leftover, 2.5)
    }

    /// Come `defaultBarWeightFor` nel web: una macchina non ha il bilanciere, il Multipower sì.
    func testMachinesHaveNoDefaultBar() {
        XCTAssertEqual(Plates.defaultBar(for: .barbell, name: "Rematore macchina"), 0)
        XCTAssertEqual(Plates.defaultBar(for: .benchFlat, name: "Curl Machine"), 0)
        XCTAssertEqual(Plates.defaultBar(for: .barbell, name: "Curl Bilanciere"), 20)
        XCTAssertEqual(Plates.defaultBar(for: .smithMachine, name: "Smith Machine Squat"), 15)
    }

    func testOnlyTheBar() {
        XCTAssertEqual(Plates.load(total: 20, bar: 20, plates: standard).perSide, [])
        XCTAssertEqual(Plates.load(total: 15, bar: 20, plates: standard).perSide, [])
    }

    /// Un set su misura: il greedy da solo lascerebbe 15 per lato.
    func testCustomPlateSetFindsAnExactLoad() {
        let load = Plates.load(total: 100, bar: 20, plates: [25, 20])
        XCTAssertEqual(load.perSide, [20, 20])
        XCTAssertEqual(load.leftover, 0)
    }

    /// Come `barForUnit` nel web: 20 kg sono 45 lb, la sbarra di serie dell'unità.
    func testBarFollowsTheUnit() {
        XCTAssertEqual(Plates.defaultBar(for: .barbell, name: "Curl Bilanciere", unit: .kg), 20)
        XCTAssertEqual(Plates.defaultBar(for: .barbell, name: "Curl Bilanciere", unit: .lb), 45 * 0.45359237, accuracy: 1e-9)
        XCTAssertEqual(Plates.defaultBar(for: .barbell, name: "Rematore macchina", unit: .lb), 0)
    }
}
