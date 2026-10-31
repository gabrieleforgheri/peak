import Foundation
import Observation
import SwiftData

/// La fine di un allenamento, in un posto solo: la usano il tasto «Termina allenamento» della sessione
/// e il comando «Termina» che arriva dal Watch (`WatchBridge`). Chi la chiama pensa a cosa mostrare dopo.
@MainActor
@Observable
final class WorkoutFinisher {

    static let shared = WorkoutFinisher()

    private static let healthSentKey = "peak.healthSentWorkouts"

    /// Allenamenti già scritti in Salute (uuid): un secondo invio duplicherebbe l'allenamento.
    private(set) var sentToHealth: Set<String> = Set(UserDefaults.standard.stringArray(forKey: healthSentKey) ?? [])
    /// Invii in corso, per non lanciarne due insieme.
    private(set) var sendingToHealth: Set<String> = []

    func isSent(_ workout: Workout) -> Bool { sentToHealth.contains(workout.uuid.uuidString) }
    func isSending(_ workout: Workout) -> Bool { sendingToHealth.contains(workout.uuid.uuidString) }

    /// Chiude l'allenamento in corso: ora di fine, recupero, Live Activity, Watch, backup, Salute.
    /// Non fa niente se è già concluso (un doppio comando non chiude due volte).
    func finish(_ workout: Workout, context: ModelContext, settings: AppSettings) {
        guard workout.isActive else { return }
        workout.endedAt = Date()
        try? context.save()
        RestTimer.shared.stop()
        WorkoutActivity.end()
        WatchBridge.shared.clear()
        if !SyncService.shared.isConnected, let date = try? BackupService.writeAutoBackup(context: context) {
            settings.lastBackupDate = date
        }
        if settings.healthSyncEnabled {
            // un invio automatico che fallisce non dice niente: coprirebbe il riepilogo, e il tasto «Invia a Salute» resta
            Task { _ = await sendToHealth(workout) }
        }
        Haptics.success()
    }

    /// Manda la sessione a Salute e ne ricorda l'uuid. `nil` = fatto (o già fatto); altrimenti l'errore.
    /// Le date si leggono **qui**, sul main actor, e solo quelle attraversano il confine con HealthKit.
    func sendToHealth(_ workout: Workout) async -> Error? {
        guard !workout.setList.isEmpty else { return HealthService.HealthError.emptyWorkout }
        let id = workout.uuid.uuidString
        guard !sentToHealth.contains(id), !sendingToHealth.contains(id) else { return nil }
        let start = workout.date
        let end = workout.endedAt ?? Date()
        sendingToHealth.insert(id)
        defer { sendingToHealth.remove(id) }
        do {
            try await HealthService.saveWorkout(start: start, end: end)
            sentToHealth.insert(id)
            UserDefaults.standard.set(Array(sentToHealth), forKey: Self.healthSentKey)
            return nil
        } catch {
            return error
        }
    }
}
