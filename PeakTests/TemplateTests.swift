import XCTest
import SwiftData
@testable import Massimali

@MainActor
final class TemplateTests: XCTestCase {

    private let a = UUID(), b = UUID(), c = UUID()
    private func item(_ id: UUID, sets: Int = 4) -> TemplateItem {
        TemplateItem(exercise: id, sets: sets, repsMin: 8, repsMax: 10, weight: 60)
    }

    func testItemsRoundTripThroughJSONAndTheServerDocument() {
        let items = [item(a), TemplateItem(exercise: b)]
        XCTAssertEqual(TemplateItems.decode(TemplateItems.encode(items)), items)
        XCTAssertEqual(TemplateItems.encode([]), "")
        XCTAssertEqual(TemplateItems.parse(TemplateItems.json(items)), items)
        // una riga rotta si salta, le altre restano
        XCTAssertEqual(TemplateItems.parse([["exercise": "non-un-uuid"], TemplateItems.json([item(a)])[0]]), [item(a)])
    }

    func testSessionOrderAndModes() {
        let sets = [PlanSet(exercise: c, weight: 30, reps: 10, isWarmup: false), PlanSet(exercise: a, weight: 60, reps: 8, isWarmup: false)]
        let gone = UUID()
        let plan = [item(a), item(gone), item(b)]
        let exists: (UUID) -> Bool = { $0 != gone }

        XCTAssertEqual(Plan.session(plan: plan, sets: sets, exists: exists).map(\.exercise), [a, b, c])
        XCTAssertEqual(Plan.session(plan: plan, sets: sets, exists: exists)[2], Plan.blank(c))
        XCTAssertEqual(Plan.session(plan: plan, sets: sets, exists: exists, mode: .targets)[0], item(a), "gli obiettivi scritti restano")
        XCTAssertEqual(Plan.session(plan: plan, sets: sets, exists: exists, mode: .targets)[2], Plan.item(from: sets, exercise: c))
        XCTAssertEqual(Plan.session(plan: plan, sets: sets, exists: exists, mode: .done)[0], Plan.item(from: sets, exercise: a))
        XCTAssertEqual(Plan.session(plan: plan, sets: sets, exists: exists, mode: .done)[1], item(b), "senza serie resta l'obiettivo")
    }

    func testEditingTheList() {
        let list = [item(a), item(b), item(c)]
        XCTAssertEqual(Plan.adding(list, UUID()).count, 4)
        XCTAssertEqual(Plan.adding(list, a), list)
        XCTAssertEqual(Plan.removing(list, b).map(\.exercise), [a, c])
        let x = UUID()
        XCTAssertEqual(Plan.replacing(list, from: b, to: x)[1], TemplateItem(exercise: x, sets: 4, repsMin: 8, repsMax: 10, weight: nil))
        XCTAssertEqual(Plan.replacing(list, from: b, to: c), list)
        XCTAssertEqual(Plan.moving(list, c, by: -1).map(\.exercise), [a, c, b])
        XCTAssertEqual(Plan.moving(list, a, by: -1), list)
    }

    func testDiffersIgnoresTargets() {
        let template = [item(a), item(b)]
        XCTAssertFalse(Plan.differs(today: [item(a, sets: 1), item(b, sets: 9)], template: template, exists: { _ in true }))
        XCTAssertTrue(Plan.differs(today: [item(b), item(a)], template: template, exists: { _ in true }))
        XCTAssertFalse(Plan.differs(today: [item(a)], template: template, exists: { $0 == a }))
    }

    func testTemplateAndPlanSurviveBackupAndOldBackupsHaveNone() throws {
        let source = ModelContext(try AppStore.makeContainer(inMemory: true))
        let exercise = Exercise(name: "Chest Press", muscleGroup: .petto)
        source.insert(exercise)
        let template = WorkoutTemplate(name: "Push 1", items: [item(exercise.uuid), item(UUID())], sortIndex: 1)
        source.insert(template)
        let workout = Workout()
        workout.templateRaw = template.uuid.uuidString
        workout.plan = [item(exercise.uuid)]
        source.insert(workout)
        try source.save()

        let destination = ModelContext(try AppStore.makeContainer(inMemory: true))
        try BackupService.restore(from: try BackupService.data(context: source), context: destination, mode: .replace)
        let restored = try destination.fetch(FetchDescriptor<WorkoutTemplate>()).first
        XCTAssertEqual(restored?.name, "Push 1")
        XCTAssertEqual(restored?.items, [item(exercise.uuid)], "la riga di un macchinario che non c'è sparisce")
        let restoredWorkout = try destination.fetch(FetchDescriptor<Workout>()).first
        XCTAssertEqual(restoredWorkout?.templateUUID, template.uuid)
        XCTAssertEqual(restoredWorkout?.plan, [item(exercise.uuid)])

        let old = """
        {"version":1,"exportedAt":"2026-01-15T10:00:00Z","exercises":[],
         "workouts":[{"uuid":"\(UUID().uuidString)","date":"2026-01-15T10:00:00Z","note":"","sets":[]}]}
        """
        let context = ModelContext(try AppStore.makeContainer(inMemory: true))
        try BackupService.restore(from: Data(old.utf8), context: context, mode: .replace)
        XCTAssertEqual(try context.fetch(FetchDescriptor<WorkoutTemplate>()).count, 0)
        XCTAssertEqual(try context.fetch(FetchDescriptor<Workout>()).first?.plan, [])
    }
}
