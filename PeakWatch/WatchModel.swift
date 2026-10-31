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
    enum Page: Hashable { case controls, metrics, exercises, log, music }
    var page: Page = .metrics

    /// Serie mandate all'iPhone in questo allenamento.
    private(set) var setsLogged = 0

    /// La lista di oggi, per la pagina «Esercizi».
    var plan: [WatchPlanItem] { context?.plan ?? [] }
    /// Esercizio toccato sull'orologio e non ancora confermato dall'iPhone: la riga resta evidenziata.
    private(set) var pendingSelection: UUID?
    /// Ultimi totali dell'allenamento mandati dall'iPhone (serie di lavoro, volume in kg), per il riepilogo:
    /// il contesto che chiude l'allenamento non li porta più.
    private(set) var totals: (sets: Int, volume: Double)?

    /// Serie del riepilogo: quelle dell'iPhone (contano anche le sue), almeno quelle mandate da qui.
    var summarySets: Int { max(totals?.sets ?? 0, setsLogged) }
    /// Volume del riepilogo, già nell'unità dell'utente; `nil` se l'iPhone non l'ha mai mandato.
    var summaryVolume: String? {
        guard let kilograms = totals?.volume else { return nil }
        let factor = context?.unitFactor ?? 1
        let value = (kilograms * factor).formatted(.number.precision(.fractionLength(0)))
        return "\(value) \(context?.unitSymbol ?? "kg")"
    }

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
        let set = WatchSet(exerciseUUID: exercise.uuid, weight: weight, reps: reps, isWarmup: isWarmup,
                           id: UUID(), sentAt: Date())
        transmit(set.watchPayload(key: WatchSet.key))
        lastSent = set
        setsLogged += 1
        startRest(exercise.restDuration(warmup: isWarmup))
        reps = 0
        isWarmup = false
        Haptic.success()
    }

    /// Subito se l'iPhone è raggiungibile, altrimenti (o se il messaggio fallisce) in coda garantita.
    private func transmit(_ payload: [String: Any]) {
        let session = WCSession.default
        if session.isReachable {
            session.sendMessage(payload, replyHandler: nil) { _ in
                session.transferUserInfo(payload)
            }
        } else {
            session.transferUserInfo(payload)
        }
    }

    private func send(_ action: String, exercise: UUID? = nil) {
        let command = WatchCommand(action: action, exerciseUUID: exercise, id: UUID(), sentAt: Date())
        transmit(command.watchPayload(key: WatchCommand.key))
    }

    /// Riga toccata in «Esercizi»: se è già quella in corso si va alla serie; altrimenti si chiede all'iPhone,
    /// e si salta alla serie quando arriva l'esercizio nuovo (`apply`).
    func select(_ item: WatchPlanItem) {
        Haptic.click()
        if item.uuid == exercise?.uuid {
            page = .log
        } else {
            pendingSelection = item.uuid
            send(WatchCommand.select, exercise: item.uuid)
            // se l'iPhone non risponde la riga non resta accesa per sempre
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(6))
                if pendingSelection == item.uuid { pendingSelection = nil }
            }
        }
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
        // già in corso (o al conto alla rovescia): un secondo avvio, per esempio «Avvia sul Watch» dopo che l'iPhone
        // ha già fatto partire il Watch, non rifà il 3-2-1 né riavvia la sessione di HealthKit
        if workout.phase == .summary { workout.finishSummary() }
        guard workout.phase == .idle else { return }
        setsLogged = 0
        page = exercise != nil ? .log : (plan.isEmpty ? .metrics : .exercises)
        workout.start()
    }

    /// «Termina» confermato sul Watch: riepilogo qui e comando all'iPhone, che chiude l'allenamento come se
    /// avessi toccato «Termina allenamento». `notifyPhone == false` quando è stato l'iPhone a chiudere.
    func endWorkout(notifyPhone: Bool = true) {
        restTask?.cancel()
        rest = nil
        pendingSelection = nil
        let wasRunning = workout.phase == .active
        workout.end()
        if notifyPhone, wasRunning { send(WatchCommand.end) }
    }

    /// «Fine» nel riepilogo.
    func finishSummary() {
        workout.finishSummary()
        setsLogged = 0
        totals = nil
        lastSent = nil
    }

    // MARK: - Dall'iPhone

    private func apply(_ newContext: WatchContext) {
        let wasActive = context?.isWorkoutActive == true
        let changedExercise = newContext.exercise?.uuid != context?.exercise?.uuid
        context = newContext
        if let sets = newContext.workoutSets { totals = (sets, newContext.workoutVolume ?? 0) }
        if pendingSelection == newContext.exercise?.uuid { pendingSelection = nil }

        guard newContext.isWorkoutActive else {
            // Allenamento finito sull'iPhone (anche con «Termina» dal Watch, che ha già il suo riepilogo).
            // Solo se prima c'era un allenamento: un contesto vuoto vecchio, arrivato all'apertura,
            // non deve chiudere la sessione appena partita.
            if wasActive { endWorkout(notifyPhone: false) }
            return
        }

        if changedExercise || newContext.startRequestedAt != nil {
            anchorWeight = newContext.exercise?.weight ?? 0
            weightSteps = 0
            reps = 0
            lastSent = nil
            if workout.phase == .active { page = newContext.exercise == nil ? (plan.isEmpty ? .metrics : .exercises) : .log }
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
