import Foundation
import HealthKit
import Observation

/// Quello che si legge nella schermata finale.
struct WorkoutSummary: Equatable {
    var duration: TimeInterval
    var activeKcal: Double
    var totalKcal: Double
    var averageHeartRate: Double?
}

/// Sessione di allenamento del Watch: tiene viva l'app a polso abbassato e misura
/// battito e calorie in diretta (HKLiveWorkoutBuilder).
///
/// Scelta: alla fine il builder SCARTA l'allenamento (`discardWorkout()`). L'allenamento
/// in Salute lo scrive già l'iPhone; salvarlo anche da qui lo duplicherebbe. Il prezzo è che
/// battito e calorie raccolti qui non restano in Salute (restano solo a schermo e nel riepilogo).
/// Il tempo lo calcoliamo noi (`elapsed`), così le schermate funzionano anche se HealthKit
/// non è disponibile o negato.
@Observable
final class WorkoutController: NSObject {

    enum Phase { case idle, countdown, active, summary }

    private(set) var phase: Phase = .idle
    /// 3, 2, 1 prima di partire (come fa Apple).
    private(set) var countdown = 3
    private(set) var isPaused = false
    private(set) var heartRate: Double?
    private(set) var averageHeartRate: Double?
    private(set) var activeKcal = 0.0
    private(set) var basalKcal = 0.0
    private(set) var startDate = Date()
    private(set) var summary: WorkoutSummary?

    var totalKcal: Double { activeKcal + basalKcal }

    @ObservationIgnored private let health = HKHealthStore()
    @ObservationIgnored private var session: HKWorkoutSession?
    @ObservationIgnored private var builder: HKLiveWorkoutBuilder?
    @ObservationIgnored private var pausedTotal: TimeInterval = 0
    @ObservationIgnored private var pauseStart: Date?
    @ObservationIgnored private var countdownTask: Task<Void, Never>?
    @ObservationIgnored private var authorization: Task<Void, Never>?

    @ObservationIgnored private let heartType = HKQuantityType(.heartRate)
    @ObservationIgnored private let activeType = HKQuantityType(.activeEnergyBurned)
    @ObservationIgnored private let basalType = HKQuantityType(.basalEnergyBurned)

    /// Tempo di allenamento a `date`, escluse le pause.
    func elapsed(at date: Date) -> TimeInterval {
        max(0, (pauseStart ?? date).timeIntervalSince(startDate) - pausedTotal)
    }

    // MARK: - Inizio e fine

    func start(countdown withCountdown: Bool = true) {
        guard phase == .idle else { return }
        heartRate = nil
        averageHeartRate = nil
        activeKcal = 0
        basalKcal = 0
        isPaused = false
        pausedTotal = 0
        pauseStart = nil
        summary = nil
        // il permesso si chiede mentre si conta, così il foglio di sistema non copre l'inizio
        authorization = Task { @MainActor in await requestAuthorization() }
        guard withCountdown else { begin(); return }
        phase = .countdown
        countdown = 3
        countdownTask = Task { @MainActor in
            for n in [3, 2, 1] {
                countdown = n
                Haptic.click()
                try? await Task.sleep(for: .seconds(1))
                if Task.isCancelled { return }
            }
            begin()
        }
    }

    /// Chiude la sessione e prepara il riepilogo. Sull'iPhone l'allenamento resta com'è.
    func end() {
        switch phase {
        case .countdown:
            countdownTask?.cancel()
            phase = .idle
        case .active:
            summary = WorkoutSummary(duration: elapsed(at: Date()), activeKcal: activeKcal,
                                     totalKcal: totalKcal, averageHeartRate: averageHeartRate)
            phase = .summary
            isPaused = false
            session?.end()
        default:
            break
        }
    }

    /// «Fine» nel riepilogo: si torna alla schermata di partenza.
    func finishSummary() {
        guard phase == .summary else { return }
        phase = .idle
        summary = nil
    }

    func togglePause() {
        guard phase == .active else { return }
        let now = Date()
        if isPaused {
            session?.resume()
            pausedTotal += now.timeIntervalSince(pauseStart ?? now)
            pauseStart = nil
        } else {
            session?.pause()
            pauseStart = now
        }
        isPaused.toggle()
        Haptic.click()
    }

    private func begin() {
        phase = .active
        startDate = Date()
        guard HKHealthStore.isHealthDataAvailable() else { return }
        let date = startDate
        Task { @MainActor in
            await authorization?.value
            guard phase == .active else { return }
            let configuration = HKWorkoutConfiguration()
            configuration.activityType = .traditionalStrengthTraining
            configuration.locationType = .indoor
            guard let session = try? HKWorkoutSession(healthStore: health, configuration: configuration) else { return }
            let builder = session.associatedWorkoutBuilder()
            let source = HKLiveWorkoutDataSource(healthStore: health, workoutConfiguration: configuration)
            // di base il Watch raccoglie battito e calorie attive; qui si aggiunge anche il metabolismo basale
            source.enableCollection(for: basalType, predicate: nil)
            builder.dataSource = source
            session.delegate = self
            builder.delegate = self
            self.session = session
            self.builder = builder
            session.startActivity(with: date)
            try? await builder.beginCollection(at: date)
        }
    }

    private func requestAuthorization() async {
        guard HKHealthStore.isHealthDataAvailable() else { return }
        try? await health.requestAuthorization(toShare: [HKObjectType.workoutType()],
                                               read: [heartType, activeType, basalType])
    }

    /// A sessione chiusa: fine della raccolta e allenamento scartato (vedi sopra).
    @MainActor
    fileprivate func finalize(at date: Date) async {
        guard let builder else { return }
        try? await builder.endCollection(at: date)
        builder.discardWorkout()
        self.builder = nil
        session = nil
    }
}

extension WorkoutController: HKWorkoutSessionDelegate {
    func workoutSession(_ workoutSession: HKWorkoutSession, didChangeTo toState: HKWorkoutSessionState,
                        from fromState: HKWorkoutSessionState, date: Date) {
        guard toState == .ended else { return }
        Task { @MainActor in await self.finalize(at: date) }
    }

    func workoutSession(_ workoutSession: HKWorkoutSession, didFailWithError error: Error) {}
}

extension WorkoutController: HKLiveWorkoutBuilderDelegate {
    func workoutBuilderDidCollectEvent(_ workoutBuilder: HKLiveWorkoutBuilder) {}

    func workoutBuilder(_ workoutBuilder: HKLiveWorkoutBuilder, didCollectDataOf collectedTypes: Set<HKSampleType>) {
        let bpm = HKUnit.count().unitDivided(by: .minute())
        let heart = workoutBuilder.statistics(for: heartType)
        let active = workoutBuilder.statistics(for: activeType)?.sumQuantity()?.doubleValue(for: .kilocalorie())
        let basal = workoutBuilder.statistics(for: basalType)?.sumQuantity()?.doubleValue(for: .kilocalorie())
        let latest = heart?.mostRecentQuantity()?.doubleValue(for: bpm)
        let average = heart?.averageQuantity()?.doubleValue(for: bpm)
        Task { @MainActor in
            if let latest { self.heartRate = latest }
            if let average { self.averageHeartRate = average }
            if let active { self.activeKcal = active }
            if let basal { self.basalKcal = basal }
        }
    }
}
