import Foundation
import HealthKit
import Observation
import WatchConnectivity
import WatchKit

/// Stato dell'app del Watch: l'esercizio aperto sull'iPhone e la serie in corso.
@Observable
final class WatchModel: NSObject, WCSessionDelegate {

    private(set) var context: WatchContext?
    /// Passi del macchinario rispetto al peso proposto: la Digital Crown muove questo,
    /// così il carico resta sempre un peso inseribile (anche su pacchi sfalsati).
    var weightSteps: Double = 0
    /// Carico proposto dall'iPhone, in kg: l'origine della griglia.
    private(set) var anchorWeight: Double = 0

    var step: Double { max(exercise?.step ?? 2.5, 0.25) }

    /// Carico della serie, in kg: sempre sulla griglia del macchinario.
    var weight: Double {
        max(0, anchorWeight + weightSteps.rounded() * step)
    }

    /// Escursione della ghiera: da zero fino a 300 kg sopra il proposto.
    var weightStepRange: ClosedRange<Double> {
        -(anchorWeight / step).rounded(.down)...(300 / step).rounded()
    }
    var reps = 0
    /// Serie di riscaldamento: non conta per i record.
    var isWarmup = false
    /// Ultima serie inviata, per conferma a schermo.
    private(set) var lastSent: WatchSet?

    var exercise: WatchExercise? { context?.exercise }

    @ObservationIgnored private let health = HKHealthStore()
    @ObservationIgnored private var workout: HKWorkoutSession?

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func displayWeight(_ kilograms: Double) -> String {
        let factor = context?.unitFactor ?? 1
        let value = (kilograms * factor).formatted(.number.precision(.fractionLength(0...2)))
        return "\(value) \(context?.unitSymbol ?? "kg")"
    }

    // MARK: - Serie

    /// Manda la serie all'iPhone: subito se raggiungibile, altrimenti in coda garantita.
    func send() {
        guard let exercise, reps > 0 else { return }
        let set = WatchSet(exerciseUUID: exercise.uuid, weight: weight, reps: reps, isWarmup: isWarmup)
        let payload = set.watchPayload(key: WatchSet.key)
        let session = WCSession.default
        if session.isReachable {
            session.sendMessage(payload, replyHandler: nil) { _ in
                session.transferUserInfo(payload)
            }
        } else {
            session.transferUserInfo(payload)
        }
        lastSent = set
        reps = 0
        isWarmup = false
        WKInterfaceDevice.current().play(.success)
    }

    // MARK: - Sessione di allenamento

    /// Una sessione di allenamento tiene l'app viva a polso abbassato.
    /// Non viene mai salvata: in Salute l'allenamento lo scrive l'iPhone.
    func startWorkout() {
        guard workout == nil, HKHealthStore.isHealthDataAvailable() else { return }
        let configuration = HKWorkoutConfiguration()
        configuration.activityType = .traditionalStrengthTraining
        configuration.locationType = .indoor
        health.requestAuthorization(toShare: [HKObjectType.workoutType()], read: []) { [weak self] _, _ in
            DispatchQueue.main.async {
                guard let self, self.workout == nil,
                      let session = try? HKWorkoutSession(healthStore: self.health, configuration: configuration)
                else { return }
                self.workout = session
                session.startActivity(with: Date())
            }
        }
    }

    private func endWorkout() {
        workout?.end()
        workout = nil
    }

    // MARK: - Dall'iPhone

    private func apply(_ newContext: WatchContext) {
        let changedExercise = newContext.exercise?.uuid != context?.exercise?.uuid
        context = newContext

        guard newContext.exercise != nil else {
            // Allenamento finito sull'iPhone.
            endWorkout()
            return
        }

        if changedExercise || newContext.startRequestedAt != nil {
            anchorWeight = newContext.exercise?.weight ?? 0
            weightSteps = 0
            reps = 0
            lastSent = nil
        }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        // Il contesto più recente, arrivato mentre l'app era chiusa.
        let received = session.receivedApplicationContext
        guard let context = WatchContext(watchPayload: received, key: WatchContext.key) else { return }
        Task { @MainActor in self.apply(context) }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        guard let context = WatchContext(watchPayload: applicationContext, key: WatchContext.key) else { return }
        Task { @MainActor in self.apply(context) }
    }
}
