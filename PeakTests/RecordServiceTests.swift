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

    func testChartRangeAvailability() {
        XCTAssertEqual(ExerciseDetailView.ChartRange.available(forSpanDays: 10), [.all])
        XCTAssertEqual(ExerciseDetailView.ChartRange.available(forSpanDays: 45), [.oneMonth, .all])
        XCTAssertEqual(ExerciseDetailView.ChartRange.available(forSpanDays: 120), [.oneMonth, .threeMonths, .all])
        XCTAssertEqual(ExerciseDetailView.ChartRange.available(forSpanDays: 400), [.threeMonths, .year, .all])
    }

    func testEditingSetReferenceRecordCalculation() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Panca Inclinata", muscleGroup: .petto)
        context.insert(exercise)
        let workout = addWorkout(context)

        let base = Date()
        // Prima serie: 80 kg × 5 (diventa record)
        RecordService.addSet(to: workout, exercise: exercise, weight: 80, reps: 5, isWarmup: false, context: context)
        workout.orderedSets[0].createdAt = base
        RecordService.recompute(exercise, context: context)
        try context.save()
        XCTAssertEqual(exercise.recordList.count, 1)
        let set1 = workout.orderedSets[0]

        // Seconda serie: 75 kg × 5 (non è un record)
        RecordService.addSet(to: workout, exercise: exercise, weight: 75, reps: 5, isWarmup: false, context: context)
        workout.orderedSets[1].createdAt = base.addingTimeInterval(60)
        RecordService.recompute(exercise, context: context)
        try context.save()
        let set2 = workout.orderedSets[1]
        XCTAssertFalse(set2.isPersonalRecord)

        // Quando modifichiamo set2 per portarlo a 85 kg × 5:
        // Il record di riferimento esclude set2 (rimane set1 da 80kg)
        let referenceWithoutSet2 = exercise.recordList.filter { $0.uuid != set2.uuid }.max { a, b in
            if abs(a.weight - b.weight) > MaxRanking.epsilon { return a.weight < b.weight }
            return a.reps < b.reps
        }
        XCTAssertEqual(referenceWithoutSet2?.weight, 80)
        let best2 = referenceWithoutSet2.map { (weight: $0.weight, reps: $0.reps) }
        XCTAssertTrue(MaxRanking.beats(weight: 85, reps: 5, than: best2))

        // Se invece modifichiamo set1 escludendolo:
        let referenceWithoutSet1 = exercise.recordList.filter { $0.uuid != set1.uuid }.max { a, b in
            if abs(a.weight - b.weight) > MaxRanking.epsilon { return a.weight < b.weight }
            return a.reps < b.reps
        }
        XCTAssertNil(referenceWithoutSet1)
    }

    func testPreviousWorkingSetsIgnoresWarmupAndCurrentWorkout() throws {
        let context = try makeContext()
        let exercise = Exercise(name: "Squat", muscleGroup: .gambe)
        context.insert(exercise)

        // Allenamento precedente (ieri)
        let yesterday = Date().addingTimeInterval(-86400)
        let pastWorkout = Workout(date: yesterday)
        context.insert(pastWorkout)

        // Serie di riscaldamento + 2 serie allenanti
        RecordService.addSet(to: pastWorkout, exercise: exercise, weight: 60, reps: 10, isWarmup: true, context: context)
        RecordService.addSet(to: pastWorkout, exercise: exercise, weight: 100, reps: 8, isWarmup: false, context: context)
        RecordService.addSet(to: pastWorkout, exercise: exercise, weight: 105, reps: 6, isWarmup: false, context: context)

        // Allenamento di oggi
        let todayWorkout = addWorkout(context)
        RecordService.addSet(to: todayWorkout, exercise: exercise, weight: 110, reps: 5, isWarmup: false, context: context)

        let allWorkouts = [pastWorkout, todayWorkout]
        let otherWorkouts = allWorkouts
            .filter { $0.uuid != todayWorkout.uuid && $0.date < todayWorkout.date }
            .sorted { $0.date > $1.date }

        var previousWorking: [WorkoutSet] = []
        for w in otherWorkouts {
            let working = w.orderedSets.filter { $0.exercise?.uuid == exercise.uuid && !$0.isWarmup }
            if !working.isEmpty {
                previousWorking = working
                break
            }
        }

        XCTAssertEqual(previousWorking.count, 2)
        XCTAssertEqual(previousWorking.map(\.weight), [100, 105])
        XCTAssertEqual(previousWorking.map(\.reps), [8, 6])
    }
}
