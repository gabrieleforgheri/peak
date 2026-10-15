import ActivityKit
import Foundation
import SwiftData

/// La sessione in corso sulla schermata di blocco e nella Dynamic Island.
/// Una sola alla volta: se ne esiste già una, si aggiorna quella.
enum WorkoutActivity {

    private static var current: Activity<WorkoutActivityAttributes>? {
        Activity<WorkoutActivityAttributes>.activities.first
    }

    private static func state(for workout: Workout, unit: WeightUnit) -> WorkoutActivityAttributes.ContentState {
        .init(
            exercise: workout.orderedSets.last?.exercise?.name ?? "",
            sets: workout.workingSetCount,
            volume: Fmt.volume(workout.totalVolume, unit: unit),
            restEnd: RestTimer.shared.end,
            next: next(for: workout)
        )
    }

    /// Il primo esercizio della lista di oggi ancora senza serie.
    private static func next(for workout: Workout) -> String? {
        guard let context = workout.modelContext else { return nil }
        let done = Set(workout.setList.compactMap { $0.exercise?.uuid })
        for item in workout.plan where !done.contains(item.exercise) {
            let id = item.exercise
            var descriptor = FetchDescriptor<Exercise>(predicate: #Predicate { $0.uuid == id })
            descriptor.fetchLimit = 1
            if let exercise = try? context.fetch(descriptor).first { return exercise.name }
        }
        return nil
    }

    /// Avvia l'attività se manca, altrimenti la aggiorna. Va bene anche alla
    /// riapertura di una sessione, dopo che iOS l'ha chiusa.
    static func start(for workout: Workout, unit: WeightUnit) {
        guard workout.isActive, ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        guard current == nil else { return update(for: workout, unit: unit) }
        _ = try? Activity.request(
            attributes: WorkoutActivityAttributes(startDate: workout.date),
            content: .init(state: state(for: workout, unit: unit), staleDate: nil)
        )
    }

    static func update(for workout: Workout, unit: WeightUnit) {
        guard let activity = current else { return }
        let content = ActivityContent(state: state(for: workout, unit: unit), staleDate: nil)
        Task { await activity.update(content) }
    }

    static func end() {
        for activity in Activity<WorkoutActivityAttributes>.activities {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
    }
}
