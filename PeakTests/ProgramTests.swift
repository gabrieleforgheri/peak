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

    func testNextDayRotatesAndSkipsOtherPrograms() {
        let program = WorkoutProgram(name: "PPL")
        let days = ["Push", "Pull", "Legs"].enumerated().map { i, n -> WorkoutTemplate in
            let t = WorkoutTemplate(name: n, sortIndex: i)
            t.programRaw = program.uuid.uuidString
            return t
        }
        let other = WorkoutTemplate(name: "Altro")
        XCTAssertEqual(Programs.next(program: program, templates: days + [other], workouts: [])?.name, "Push")
        let done = Workout(date: Date())
        done.templateRaw = days[1].uuid.uuidString
        XCTAssertEqual(Programs.next(program: program, templates: days + [other], workouts: [done])?.name, "Legs")
    }
}
