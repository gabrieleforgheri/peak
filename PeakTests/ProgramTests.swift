import XCTest
@testable import Massimali

final class ProgramTests: XCTestCase {
    /// Stessi esempi di common/plan.test.ts: l'icona dedotta dal nome è la stessa sul web e qui.
    func testIconSuggestedFromName() {
        XCTAssertEqual(TemplateIcon.suggest("Push 1"), "muscle:petto")
        XCTAssertEqual(TemplateIcon.suggest("Pull"), "muscle:schiena")
        XCTAssertEqual(TemplateIcon.suggest("Gambe"), "muscle:gambe")
        XCTAssertEqual(TemplateIcon.suggest("Cardio"), "heart")
        XCTAssertEqual(TemplateIcon.suggest("Giorno A"), "")
        XCTAssertEqual(TemplateIcon.resolved("star", name: "Push"), "star")
    }

    func testTodayOnlyWithFixedDays() {
        let program = WorkoutProgram(name: "PPL")
        let days = ["Push", "Pull"].enumerated().map { i, n -> WorkoutTemplate in
            let t = WorkoutTemplate(name: n, sortIndex: i)
            t.programRaw = program.uuid.uuidString
            t.weekday = i   // lunedì, martedì
            return t
        }
        let monday = Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 26, hour: 12))!
        let sunday = Calendar.current.date(from: DateComponents(year: 2026, month: 11, day: 1, hour: 12))!
        XCTAssertNil(Programs.today(program: program, templates: days, now: monday))   // giorni non in uso
        program.useDays = true
        XCTAssertEqual(Programs.today(program: program, templates: days, now: monday)?.name, "Push")
        XCTAssertNil(Programs.today(program: program, templates: days, now: sunday))   // riposo
    }
}
