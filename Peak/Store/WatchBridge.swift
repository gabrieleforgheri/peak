import Foundation
import HealthKit
import Observation
import SwiftData
import WatchConnectivity

/// Collegamento con l'app del Watch. L'iPhone dice quale esercizio è aperto,
/// il Watch rimanda le serie concluse, che finiscono nella sessione in corso.
///
/// Funziona solo se l'app iPhone installata contiene l'app Watch (`Watch/`): è il
/// sistema a legare le due app, e lo fa solo così. AltStore non sa firmarla, quindi
/// la versione con il Watch si installa dal Mac con `scripts/install_device.sh`;
/// nell'ipa di AltStore il Watch non c'è e questo ponte resta inerte.
@Observable
final class WatchBridge: NSObject, WCSessionDelegate {

    static let shared = WatchBridge()

    /// `true` se sull'orologio abbinato c'è l'app di Massimali.
    private(set) var isWatchAppInstalled = false

    @ObservationIgnored private var container: ModelContainer?
    @ObservationIgnored private var settings: AppSettings?
    @ObservationIgnored private var lastContext: WatchContext?

    func activate(container: ModelContainer, settings: AppSettings) {
        self.container = container
        self.settings = settings
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    // MARK: - Verso il Watch

    /// Mostra sul Watch l'esercizio, pronto per partire da lì.
    @MainActor
    func show(_ exercise: Exercise, in workout: Workout) {
        push(exercise: watchExercise(exercise, in: workout), start: false)
    }

    /// Apre l'app sul Watch (via HealthKit) e fa partire subito la serie.
    @MainActor
    func start(_ exercise: Exercise, in workout: Workout) {
        let configuration = HKWorkoutConfiguration()
        configuration.activityType = .traditionalStrengthTraining
        configuration.locationType = .indoor
        guard settings?.watchEnabled != false else { return }
        HKHealthStore().startWatchApp(with: configuration) { _, _ in }
        push(exercise: watchExercise(exercise, in: workout), start: true)
    }

    /// Fine allenamento: il Watch torna in attesa.
    @MainActor
    func clear() {
        push(exercise: nil, start: false)
    }

    /// Acceso o spento dalle impostazioni: da spento il Watch torna in attesa e non riceve più nulla.
    @MainActor
    func enabledChanged() {
        if settings?.watchEnabled == true { settingsChanged() } else { push(exercise: nil, start: false, force: true) }
    }

    /// Reinvia le impostazioni cambiate senza toccare l'esercizio mostrato.
    @MainActor
    func settingsChanged() {
        guard let lastContext else { return }
        push(exercise: lastContext.exercise, start: false)
    }

    @MainActor
    private func push(exercise: WatchExercise?, start: Bool, force: Bool = false) {
        // con l'Apple Watch spento non si manda niente (tranne il «torna in attesa» di quando lo si spegne)
        guard let settings, settings.watchEnabled || force,
              WCSession.isSupported(), WCSession.default.activationState == .activated else { return }
        let context = WatchContext(
            exercise: exercise,
            unitSymbol: settings.unit.symbol,
            unitFactor: settings.unit.fromKilograms(1),
            startRequestedAt: start ? Date() : nil
        )
        lastContext = context
        try? WCSession.default.updateApplicationContext(context.watchPayload(key: WatchContext.key))
    }

    /// Carico proposto: l'ultima serie di oggi, altrimenti il consiglio di progressione,
    /// altrimenti l'ultima serie in assoluto, altrimenti il massimale.
    @MainActor
    private func watchExercise(_ exercise: Exercise, in workout: Workout) -> WatchExercise {
        let range = settings?.repRange ?? 8...12
        let weight = workout.sets(for: exercise).last?.weight
            ?? exercise.progression(range: range)?.weight
            ?? exercise.setList.max(by: { $0.createdAt < $1.createdAt })?.weight
            ?? exercise.currentMax
            ?? exercise.incrementStep * 4
        return WatchExercise(
            uuid: exercise.uuid,
            name: exercise.name,
            weight: weight,
            step: exercise.incrementStep,
            target: target(for: exercise, in: workout)
        )
    }

    /// L'obiettivo di questo esercizio nella lista di oggi, se l'allenamento ne ha una e dice qualcosa.
    @MainActor
    private func target(for exercise: Exercise, in workout: Workout) -> String? {
        guard let settings, let item = workout.plan.first(where: { $0.exercise == exercise.uuid }) else { return nil }
        let done = workout.sets(for: exercise).filter { !$0.isWarmup }.count
        // niente «ultimo peso» sull'orologio: solo l'obiettivo scritto nel giorno
        let line = Plan.targetLine(item, done: done, lastKilograms: nil, settings: settings)
        return line.isEmpty ? nil : line
    }

    // MARK: - Dal Watch

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        receive(message)
    }

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        receive(userInfo)
    }

    private func receive(_ payload: [String: Any]) {
        guard settings?.watchEnabled != false else { return }
        guard let set = WatchSet(watchPayload: payload, key: WatchSet.key) else { return }
        Task { @MainActor in self.record(set) }
    }

    /// Registra la serie nella sessione in corso; se non ce n'è una, la apre.
    @MainActor
    private func record(_ watchSet: WatchSet) {
        guard let container, let settings, watchSet.reps > 0 else { return }
        let context = container.mainContext
        let uuid = watchSet.exerciseUUID
        guard let exercise = try? context.fetch(FetchDescriptor<Exercise>(predicate: #Predicate { $0.uuid == uuid })).first
        else { return }

        var active = FetchDescriptor<Workout>(predicate: #Predicate { $0.endedAt == nil }, sortBy: [SortDescriptor(\.date, order: .reverse)])
        active.fetchLimit = 1
        let workout: Workout
        if let existing = try? context.fetch(active).first {
            workout = existing
        } else {
            workout = Workout()
            context.insert(workout)
        }

        RecordService.addSet(
            to: workout,
            exercise: exercise,
            weight: watchSet.weight,
            reps: watchSet.reps,
            isWarmup: watchSet.isWarmup,
            // dal Watch non si sceglie: le serie nuove partono a due
            context: context
        )
        if settings.restTimerEnabled {
            RestTimer.shared.start(seconds: exercise.restSeconds > 0 ? exercise.restSeconds : settings.restSeconds)
        }
        WorkoutActivity.start(for: workout, unit: settings.unit)
        show(exercise, in: workout)
    }

    // MARK: - Stato della sessione

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        let installed = session.isWatchAppInstalled
        Task { @MainActor in self.isWatchAppInstalled = installed }
    }

    func sessionWatchStateDidChange(_ session: WCSession) {
        let installed = session.isWatchAppInstalled
        Task { @MainActor in self.isWatchAppInstalled = installed }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    /// Cambio di orologio: si riattiva per il nuovo.
    func sessionDidDeactivate(_ session: WCSession) {
        WCSession.default.activate()
    }
}
