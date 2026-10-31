import Foundation
import Observation
import SwiftData

/// I numeri pesanti di Allenamento e di Me (statistiche, crescita, progressione, ultimi record), calcolati una volta sola
/// dopo ogni raffica di salvataggi invece che a ogni ridisegno delle schede. Niente di tutto questo si salva.
@MainActor
@Observable
final class Insights {

    static let shared = Insights()

    /// L'andamento del 1RM stimato di un macchinario (almeno due giorni).
    struct Series: Identifiable {
        let exercise: Exercise
        let points: [Progression1RM.Point]
        var id: UUID { exercise.uuid }
    }

    private(set) var stats: HomeStats?
    private(set) var movers: [Stats.Mover] = []
    private(set) var progression: [Series] = []
    /// Gli 8 massimali più recenti, per «Ultimi record».
    private(set) var latestRecords: [MaxRecord] = []
    /// L'ultimo massimale di ogni macchinario, dal più recente: i primi 4, per «Record recenti».
    private(set) var recentByExercise: [(exercise: Exercise, record: MaxRecord)] = []

    @ObservationIgnored private var container: ModelContainer?
    @ObservationIgnored private var settings: AppSettings?
    @ObservationIgnored private var observer: NSObjectProtocol?
    @ObservationIgnored private var debounce: Task<Void, Never>?

    func configure(container: ModelContainer, settings: AppSettings) {
        self.container = container
        self.settings = settings
        if observer == nil {
            observer = NotificationCenter.default.addObserver(forName: ModelContext.didSave, object: nil, queue: .main) { note in
                // dopo una cancellazione subito: le liste qui non devono tenere oggetti che non ci sono più
                let deleted = (note.userInfo?[ModelContext.NotificationKey.deletedIdentifiers.rawValue] as? [PersistentIdentifier])?.isEmpty == false
                Task { @MainActor in deleted ? Insights.shared.refresh() : Insights.shared.schedule() }
            }
        }
        refresh()
    }

    /// Una raffica di salvataggi fa un solo ricalcolo.
    private func schedule() {
        debounce?.cancel()
        debounce = Task {
            try? await Task.sleep(for: .milliseconds(300))
            if !Task.isCancelled { refresh() }
        }
    }

    /// Subito, senza aspettare: per quando cambia qualcosa che non passa dal database (l'obiettivo settimanale).
    func refresh() {
        debounce?.cancel()
        guard let container, let settings else { return }
        let context = container.mainContext
        let workouts = (try? context.fetch(FetchDescriptor<Workout>(sortBy: [SortDescriptor(\.date, order: .reverse)]))) ?? []
        let exercises = (try? context.fetch(FetchDescriptor<Exercise>(sortBy: [SortDescriptor(\.sortIndex)]))) ?? []

        stats = HomeStats(workouts: workouts, exercises: exercises, chosenGoal: settings.weeklyGoal)
        movers = Stats.topMovers(exercises: exercises, days: 90)
        progression = exercises.filter { !$0.isArchived }
            .compactMap { e -> Series? in
                let points = Progression1RM.points(e)
                return points.count >= 2 ? Series(exercise: e, points: points) : nil
            }
            .sorted { ($0.exercise.bestRecord?.estimatedOneRepMax ?? 0) > ($1.exercise.bestRecord?.estimatedOneRepMax ?? 0) }
        // la linea a un arto dipende dal macchinario (`Limbs.lineArm`): non si può filtrare nella query, si prendono un po' di più
        var latest = FetchDescriptor<MaxRecord>(sortBy: [SortDescriptor(\.date, order: .reverse)])
        latest.fetchLimit = 40
        latestRecords = Array(((try? context.fetch(latest)) ?? []).filter { !$0.isOneLimbLine }.prefix(8))
        recentByExercise = exercises
            .compactMap { e in e.twoArmRecords.max { $0.date < $1.date }.map { (exercise: e, record: $0) } }
            .sorted { $0.record.date > $1.record.date }
            .prefix(4).map { $0 }
    }
}
