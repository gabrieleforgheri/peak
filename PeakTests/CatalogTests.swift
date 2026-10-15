import XCTest
@testable import Massimali

/// Gli stessi casi di common/catalog.test.ts: la ricerca deve comportarsi come sul web e sul server.
final class CatalogTests: XCTestCase {

    func testIDsAreUniqueAndWellFormed() {
        var seen = Set<String>()
        for entry in Catalog.entries {
            XCTAssertTrue(entry.id.range(of: "^[a-z0-9]+(-[a-z0-9]+)*$", options: .regularExpression) != nil, entry.id)
            XCTAssertTrue(seen.insert(entry.id).inserted, "id doppio \(entry.id)")
            XCTAssertNotNil(MuscleGroup(rawValue: entry.group), entry.id)
            XCTAssertGreaterThan(entry.step, 0)
        }
    }

    func testNormalization() {
        XCTAssertEqual(Catalog.norm("  Leg Press 45° "), "leg press 45")
        XCTAssertEqual(Catalog.norm("Farmer's Walk"), "farmer s walk")
        XCTAssertEqual(Catalog.norm("Perché"), "perche")
    }

    func testMatchByNameFindsItalianEnglishAndSynonyms() {
        XCTAssertEqual(Catalog.match(name: "panca piana bilanciere")?.id, "bench-press-barbell")
        XCTAssertEqual(Catalog.match(name: "Barbell Bench Press")?.id, "bench-press-barbell")
        XCTAssertEqual(Catalog.match(name: "bench press")?.id, "bench-press-barbell")
        XCTAssertNil(Catalog.match(name: "Panca"))
        XCTAssertNil(Catalog.match(name: "Il mio macchinario"))
        XCTAssertNil(Catalog.match(name: "   "))
    }

    func testSearchOrdersByPrefixAndAcceptsAnyWordOrder() {
        XCTAssertEqual(Catalog.search("bilanciere panca").first?.id, "bench-press-barbell")
        XCTAssertEqual(Catalog.search("butterfly").first?.id, "pec-deck")
        XCTAssertTrue(Catalog.search("xyzzy").isEmpty)
        XCTAssertEqual(Catalog.search("", limit: 5).count, 5)
    }

    func testEveryOldCatalogNameStillLinks() {
        for name in ["Chest Press", "Leg Press 45°", "Stacco da Terra", "Plank", "Curl ai Polsi", "Pectoral Machine"] {
            XCTAssertNotNil(Catalog.match(name: name), name)
        }
    }
}
