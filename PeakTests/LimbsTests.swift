import XCTest
@testable import Massimali

/// Gemello di «uno o due arti» e «due manici» in common/domain.test.ts.
final class LimbsTests: XCTestCase {

    private func limbs(_ catalog: String, _ name: String = "", _ group: String = "petto") -> Limbs {
        Limbs.defaultLimbs(catalog: catalog, name: name, muscleGroup: group)
    }

    func testDefaultFromCatalogAsInRealData() {
        // Cable Fly basso: si fa sempre insieme, il segno «1 braccio» di ieri non apre una linea a parte
        XCTAssertEqual(limbs("cable-fly"), .both)
        XCTAssertFalse(Limbs.lineArm(limbs("cable-fly"), true))
        // alzate laterali a un braccio al cavo: sempre un arto
        XCTAssertEqual(limbs("lateral-raise-single-arm-cable", "", "spalle"), .one)
        XCTAssertFalse(Limbs.lineArm(.one, true))
        // leg extension: lo stesso macchinario a due o a una gamba
        XCTAssertEqual(limbs("leg-extension", "", "gambe"), .either)
        XCTAssertTrue(Limbs.lineArm(.either, true))
        XCTAssertFalse(Limbs.lineArm(.either, false))
        XCTAssertEqual(limbs("bench-press-barbell"), .both)
        XCTAssertEqual(limbs("crunch", "", "core"), .both)
        XCTAssertEqual(limbs("", "Curl a un braccio", "braccia"), .one)
        XCTAssertEqual(limbs("", "Panca mia"), .both)
    }

    func testSavedChoiceWinsAndOldUnilateralMeansOne() {
        XCTAssertEqual(Limbs.limbsOf(saved: "either", isUnilateral: false, catalog: "cable-fly", name: "", muscleGroup: "petto"), .either)
        XCTAssertEqual(Limbs.limbsOf(saved: nil, isUnilateral: true, catalog: "bench-press-barbell", name: "", muscleGroup: "petto"), .one)
        XCTAssertEqual(Limbs.limbsOf(saved: "boh", isUnilateral: false, catalog: "bench-press-barbell", name: "", muscleGroup: "petto"), .both)
    }

    func testTwoHandlesDumbbellsAndCablesDoNotHalve() {
        XCTAssertTrue(Limbs.twoHandles(catalog: "dumbbell-curl", name: "", glyph: ""))
        XCTAssertTrue(Limbs.twoHandles(catalog: "cable-curl", name: "", glyph: ""))
        XCTAssertFalse(Limbs.twoHandles(catalog: "leg-extension", name: "", glyph: "legLever"))
        XCTAssertTrue(Limbs.twoHandles(catalog: "", name: "Curl ai cavi", glyph: ""))
        XCTAssertFalse(Limbs.twoHandles(catalog: "", name: "Leg extension", glyph: ""))
    }

    func testSetAndRecordLinesFollowTheMachine() {
        let e = Exercise(name: "Leg extension", muscleGroup: .gambe)
        e.catalogID = "leg-extension"
        XCTAssertEqual(e.limbs, .either)
        XCTAssertTrue(e.isOneLimbLine(true))
        e.setLimbs(.both)
        XCTAssertFalse(e.isOneLimbLine(true))
        XCTAssertFalse(e.isUnilateral)
        e.setLimbs(.one)
        XCTAssertTrue(e.isUnilateral)
        XCTAssertFalse(e.isOneLimbLine(true))
    }

    func testVolumeIsWeightTimesRepsWhateverTheLimbs() {
        XCTAssertEqual(WorkoutSet(weight: 20, reps: 10, singleArm: true).volume, 200)
        XCTAssertEqual(WorkoutSet(weight: 20, reps: 10).volume, 200)
    }
}
