import Foundation
import Observation
import WatchConnectivity

/// Stato dell'app del Watch: l'esercizio aperto sull'iPhone, la serie in corso, il recupero.
/// La sessione di allenamento (tempo, battito, calorie) è in `WorkoutController`.
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

    /// Sessione di allenamento locale: tempo, battito, calorie, pausa, riepilogo.
    let workout = WorkoutController()

    /// Pagine dell'allenamento, da sinistra a destra come nell'app Allenamento.
    enum Page: Hashable { case controls, metrics, log, music }
    var page: Page = .metrics

    /// Serie mandate all'iPhone in questo allenamento.
    private(set) var setsLogged = 0

    /// Recupero in corso dopo una serie.
    struct Rest: Equatable {
        var end: Date
        var total: TimeInterval
    }
    private(set) var rest: Rest?
    @ObservationIgnored private var restTask: Task<Void, Never>?

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
        setsLogged += 1
        startRest(exercise.restDuration(warmup: isWarmup))
        reps = 0
        isWarmup = false
        Haptic.success()
    }

    // MARK: - Recupero

    /// Anello di recupero dopo la serie; `nil` se l'iPhone ha il timer spento.
    private func startRest(_ seconds: Int?) {
        restTask?.cancel()
        guard let seconds else { rest = nil; return }
        rest = Rest(end: Date().addingTimeInterval(Double(seconds)), total: Double(seconds))
        page = .log
        restTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            rest = nil
            Haptic.alert()
        }
    }

    func skipRest() {
        restTask?.cancel()
        rest = nil
        Haptic.click()
    }

    // MARK: - Sessione di allenamento

    /// Avvio dall'iPhone («Avvia sul Watch») o dalla scheda di partenza: conto alla rovescia e poi la sessione.
    func startWorkout() {
        guard workout.phase == .idle else { return }
        setsLogged = 0
        page = exercise == nil ? .metrics : .log
        workout.start()
    }

    /// Termina la sessione del Watch (l'allenamento dell'iPhone resta com'è) e mostra il riepilogo.
    func endWorkout() {
        restTask?.cancel()
        rest = nil
        workout.end()
    }

    /// «Fine» nel riepilogo.
    func finishSummary() {
        workout.finishSummary()
        setsLogged = 0
        lastSent = nil
    }

    // MARK: - Dall'iPhone

    private func apply(_ newContext: WatchContext) {
        let previous = context?.exercise
        let changedExercise = newContext.exercise?.uuid != previous?.uuid
        context = newContext

        guard newContext.exercise != nil else {
            // Allenamento finito sull'iPhone. Solo se prima c'era un esercizio: un contesto vuoto
            // vecchio, arrivato all'apertura, non deve chiudere la sessione appena partita.
            if previous != nil { endWorkout() }
            return
        }

        if changedExercise || newContext.startRequestedAt != nil {
            anchorWeight = newContext.exercise?.weight ?? 0
            weightSteps = 0
            reps = 0
            lastSent = nil
            if workout.phase == .active { page = .log }
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
