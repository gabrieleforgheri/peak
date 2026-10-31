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
        // un allenamento già concluso (aperto dalla storia) non tocca il Watch
        guard workout.isActive else { return }
        push(exercise: watchExercise(exercise, in: workout), start: false)
    }

    /// Apre l'app sul Watch (via HealthKit) e fa partire subito la serie.
    @MainActor
    func start(_ exercise: Exercise, in workout: Workout) {
        guard workout.isActive else { return }
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

    /// L'esercizio mostrato sul Watch è stato eliminato o tolto dalla lista: si passa al primo che resta
    /// nell'allenamento in corso, se non ce n'è si svuota il Watch.
    @MainActor
    func exerciseRemoved(_ uuid: UUID) {
        guard lastContext?.exercise?.uuid == uuid, let container else { return }
        let context = container.mainContext
        var active = FetchDescriptor<Workout>(predicate: #Predicate { $0.endedAt == nil }, sortBy: [SortDescriptor(\.date, order: .reverse)])
        active.fetchLimit = 1
        let exercises = (try? context.fetch(FetchDescriptor<Exercise>())) ?? []
        let known = Dictionary(exercises.map { ($0.uuid, $0) }, uniquingKeysWith: { first, _ in first })
        if let workout = try? context.fetch(active).first,
           let next = workout.items(exists: { $0 != uuid && known[$0] != nil }).first.flatMap({ known[$0.exercise] }) {
            show(next, in: workout)
        } else {
            clear()
        }
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
        let unit = settings?.unit ?? .kg
        let weight = workout.sets(for: exercise).last?.weight
            ?? exercise.progression(range: range, unit: unit)?.weight
            ?? exercise.setList.max(by: { $0.createdAt < $1.createdAt })?.weight
            ?? exercise.currentMax
            ?? exercise.incrementStep * 4
        // stesso recupero del timer dell'iPhone (macchinario, altrimenti impostazioni); spento = nessun anello sul Watch
        let rest: Int? = settings?.restTimerEnabled == true ? settings?.restDuration(for: exercise, warmup: false) : nil
        let warmupRest: Int? = settings?.restTimerEnabled == true ? settings?.restDuration(for: exercise, warmup: true) : nil
        return WatchExercise(
            uuid: exercise.uuid,
            name: exercise.displayName,
            weight: weight,
            // passo nell'unità mostrata (5 lb, non 2,5 kg = 5,51 lb), riportato in kg per la ghiera
            step: unit.toKilograms(unit.displayStep(forKilograms: exercise.incrementStep)),
            target: target(for: exercise, in: workout),
            restSeconds: rest,
            warmupRestSeconds: warmupRest
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

    /// Ultime serie già registrate (id), per scartare le consegne doppie del Watch.
    private static let seenKey = "peak.watchSetIDs"

    /// `true` se l'id è nuovo (e lo ricorda); i messaggi senza id (Watch vecchio) passano sempre.
    private func isNew(_ id: UUID?) -> Bool {
        guard let id else { return true }
        var seen = UserDefaults.standard.stringArray(forKey: Self.seenKey) ?? []
        guard !seen.contains(id.uuidString) else { return false }
        seen.append(id.uuidString)
        UserDefaults.standard.set(Array(seen.suffix(100)), forKey: Self.seenKey)
        return true
    }

    /// Una serie rimasta in coda da più di tanto non apre un allenamento nuovo.
    private static let freshSeconds: TimeInterval = 30 * 60

    /// Registra la serie nella sessione in corso. Se non ce n'è una: una serie recente ne apre una,
    /// una in ritardo va nell'allenamento concluso in cui è stata fatta, altrimenti si scarta.
    @MainActor
    private func record(_ watchSet: WatchSet) {
        guard let container, let settings, watchSet.reps > 0, isNew(watchSet.id) else { return }
        let context = container.mainContext
        let uuid = watchSet.exerciseUUID
        // esercizio eliminato nel frattempo: la serie si salva lo stesso, finisce in «Esercizio rimosso»
        let exercise = try? context.fetch(FetchDescriptor<Exercise>(predicate: #Predicate { $0.uuid == uuid })).first

        let sentAt = watchSet.sentAt ?? Date()
        var active = FetchDescriptor<Workout>(predicate: #Predicate { $0.endedAt == nil }, sortBy: [SortDescriptor(\.date, order: .reverse)])
        active.fetchLimit = 1
        let current = try? context.fetch(active).first
        var workout: Workout?
        var isLive = true
        if let current, sentAt >= current.date.addingTimeInterval(-60) {
            workout = current
        } else {
            // inviata prima dell'allenamento in corso (o senza nessuno): cerca quello concluso che la contiene
            let ended = FetchDescriptor<Workout>(predicate: #Predicate { $0.endedAt != nil }, sortBy: [SortDescriptor(\.date, order: .reverse)])
            workout = (try? context.fetch(ended))?.prefix(20).first { done in
                sentAt >= done.date && sentAt <= (done.endedAt ?? done.date).addingTimeInterval(5 * 60)
            }
            isLive = false
            if workout == nil, current == nil, -sentAt.timeIntervalSinceNow < Self.freshSeconds {
                let created = Workout()
                context.insert(created)
                workout = created
                isLive = true
            }
        }
        guard let workout else {
            print("WatchBridge: serie dal Watch scartata (\(sentAt)), nessun allenamento a cui appartiene")
            return
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
        // una serie in ritardo per un allenamento chiuso non rimette in moto timer, Live Activity e Watch
        guard isLive else { return }
        if let exercise {
            let rest = settings.restDuration(for: exercise, warmup: watchSet.isWarmup)
            if settings.restTimerEnabled, rest > 0 {
                RestTimer.shared.start(seconds: rest)
            }
        }
        WorkoutActivity.start(for: workout, unit: settings.unit)
        if let exercise { show(exercise, in: workout) }
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
