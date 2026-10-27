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
        XCTAssertNil(Programs.today(program: nil, templates: days, now: monday))      // nessuna scheda
    }

    /// Domenica è 6, un giorno di un'altra scheda non conta, con due giorni sullo stesso giorno vince il primo.
    func testTodayEdgeCases() {
        let program = WorkoutProgram(name: "A")
        program.useDays = true
        let other = WorkoutProgram(name: "B")
        func day(_ name: String, _ weekday: Int, _ p: WorkoutProgram, _ i: Int) -> WorkoutTemplate {
            let t = WorkoutTemplate(name: name, sortIndex: i)
            t.programRaw = p.uuid.uuidString
            t.weekday = weekday
            return t
        }
        let sunday = Calendar.current.date(from: DateComponents(year: 2026, month: 11, day: 1, hour: 23, minute: 59))!
        let templates = [day("Altra", 6, other, 0), day("Lunga", 6, program, 1), day("Doppia", 6, program, 2), day("Senza", -1, program, 3)]
        XCTAssertEqual(Programs.today(program: program, templates: templates, now: sunday)?.name, "Lunga")
        XCTAssertEqual(Programs.today(program: other, templates: templates, now: sunday)?.name, nil)   // B non usa i giorni
    }
}
