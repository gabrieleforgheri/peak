import XCTest
import SwiftData
@testable import Massimali

/// I record "da allenamento" si ricalcolano dalle serie: la regola sta in `RecordRules` (provata
/// anche dai vettori condivisi con la versione web, `scripts/check_records.sh`); qui si prova che
/// il servizio la applichi ai modelli SwiftData.
@MainActor
final class RecordServiceTests: XCTestCase {

    private func makeContext() throws -> ModelContext {
        let container = try AppStore.makeContainer(inMemory: true)
        return ModelContext(container)
    }

    private func addWorkout(_ context: ModelContext) -> Workout {
        let workout = Workout(date: Date())
        context.insert(workout)
        return workout
    }

    func testFirstWorkingSetCreatesRecord() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Leg Press", muscleGroup: .gambe, incrementStep: 10)
        context.insert(exercise)
        let workout = addWorkout(context)

        let isRecord = RecordService.addSet(to: workout, exercise: exercise, weight: 120, reps: 5, isWarmup: false, context: context)

        XCTAssertTrue(isRecord)
        XCTAssertEqual(exercise.recordList.count, 1)
        XCTAssertEqual(exercise.currentMax ?? 0, 120, accuracy: 0.01, "il massimale è il carico vero, non la stima")
        XCTAssertTrue(exercise.recordList[0].isEstimated)
        XCTAssertEqual(exercise.recordList[0].uuid, workout.setList[0].uuid, "il record automatico porta l'uuid della sua serie")
    }

    func testWeakerSetDoesNotCreateRecord() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Lat Machine", muscleGroup: .schiena)
        context.insert(exercise)
        context.insert(MaxRecord(weight: 90, reps: 1, exercise: exercise))
        let workout = addWorkout(context)

        let isRecord = RecordService.addSet(to: workout, exercise: exercise, weight: 60, reps: 8, isWarmup: false, context: context)

        XCTAssertFalse(isRecord)
        XCTAssertEqual(exercise.recordList.count, 1)
    }

    func testWarmupSetIsIgnored() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Panca Piana Bilanciere", muscleGroup: .petto)
        context.insert(exercise)
        let workout = addWorkout(context)

        let isRecord = RecordService.addSet(to: workout, exercise: exercise, weight: 200, reps: 5, isWarmup: true, context: context)

        XCTAssertFalse(isRecord)
        XCTAssertTrue(exercise.recordList.isEmpty)
    }

    func testDeletingASetRemovesItsAutomaticRecord() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Shoulder Press", muscleGroup: .spalle)
        context.insert(exercise)
        let workout = addWorkout(context)
        RecordService.addSet(to: workout, exercise: exercise, weight: 50, reps: 6, isWarmup: false, context: context)
        XCTAssertEqual(exercise.recordList.count, 1)

        RecordService.delete(workout.setList[0], context: context)

        XCTAssertTrue(exercise.recordList.isEmpty)
    }

    func testManualRecordIsNeverRemoved() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Curl Bilanciere", muscleGroup: .braccia)
        context.insert(exercise)
        context.insert(MaxRecord(weight: 40, reps: 3, isEstimated: false, exercise: exercise))
        let workout = addWorkout(context)
        RecordService.addSet(to: workout, exercise: exercise, weight: 40, reps: 3, isWarmup: false, context: context)

        RecordService.delete(workout.setList[0], context: context)

        XCTAssertEqual(exercise.recordList.count, 1, "un record inserito a mano non va toccato")
        XCTAssertFalse(exercise.recordList[0].isEstimated)
    }

    /// Prima l'eliminazione di un allenamento lasciava i suoi record: ora spariscono con lui.
    func testDeletingAWorkoutRemovesItsAutomaticRecords() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Hack Squat", muscleGroup: .gambe)
        context.insert(exercise)
        let workout = addWorkout(context)
        RecordService.addSet(to: workout, exercise: exercise, weight: 100, reps: 8, isWarmup: false, context: context)
        XCTAssertEqual(exercise.recordList.count, 1)

        RecordService.delete(workout, context: context)

        XCTAssertTrue(exercise.recordList.isEmpty)
        XCTAssertTrue(exercise.setList.isEmpty)
    }

    /// Abbassando la prima serie, quelle dopo diventano record: si ricontrollano tutte.
    func testEditingASetRechecksTheLaterOnes() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Rematore Machine", muscleGroup: .schiena)
        context.insert(exercise)
        let workout = addWorkout(context)
        let base = Date()
        for (i, weight) in [100.0, 90, 95].enumerated() {
            RecordService.addSet(to: workout, exercise: exercise, weight: weight, reps: 5, isWarmup: false, context: context)
            workout.orderedSets[i].createdAt = base.addingTimeInterval(Double(i) * 600)
        }
        RecordService.recomputeAll(context: context)
        XCTAssertEqual(workout.orderedSets.map(\.isPersonalRecord), [true, false, false])

        workout.orderedSets[0].weight = 80
        RecordService.recompute(exercise, context: context)
        try context.save() // il record cancellato esce da recordList solo dopo il salvataggio, come nell'app

        XCTAssertEqual(workout.orderedSets.map(\.isPersonalRecord), [true, true, true])
        XCTAssertEqual(exercise.recordList.count, 3)
    }

    /// Un record da allenamento eliminato dallo storico: la serie resta, ma non conta più.
    func testDeletingAnAutomaticRecordExcludesItsSet() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Pulley Basso", muscleGroup: .schiena)
        context.insert(exercise)
        let workout = addWorkout(context)
        RecordService.addSet(to: workout, exercise: exercise, weight: 70, reps: 8, isWarmup: false, context: context)
        let automatic = exercise.recordList[0]

        RecordService.delete(automatic, context: context)

        XCTAssertTrue(exercise.recordList.isEmpty)
        XCTAssertEqual(workout.setList.count, 1, "la serie resta nell'allenamento")
        XCTAssertTrue(workout.setList[0].excludedFromRecords)
        XCTAssertFalse(workout.setList[0].isPersonalRecord)
    }
}
