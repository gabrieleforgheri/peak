import Foundation
import Observation
import UserNotifications

/// Il recupero in corso, unico per tutta l'app: lo avvia la sessione sull'iPhone
/// e anche una serie arrivata dal Watch. La notifica serve con il telefono in tasca;
/// in app ci pensa la vista della sessione con una vibrazione.
@Observable
final class RestTimer {

    static let shared = RestTimer()

    /// Fine del recupero, `nil` se non ce n'è uno.
    private(set) var end: Date?

    private static let notificationID = "rest-timer"
    private var center: UNUserNotificationCenter { .current() }

    func start(seconds: Int) {
        set(Date.now.addingTimeInterval(TimeInterval(seconds)))
    }

    func shift(by seconds: Int) {
        guard let end else { return }
        let shifted = end.addingTimeInterval(TimeInterval(seconds))
        set(shifted <= .now ? nil : shifted)
    }

    func stop() { set(nil) }

    /// La notifica in programma: un nuovo `set` o uno `stop` la annulla prima che arrivi al centro notifiche.
    private var scheduling: Task<Void, Never>?

    private func set(_ newEnd: Date?) {
        end = newEnd
        scheduling?.cancel()
        scheduling = nil
        center.removePendingNotificationRequests(withIdentifiers: [Self.notificationID])
        guard let newEnd, newEnd.timeIntervalSinceNow > 1 else { return }
        let center = center
        scheduling = Task { @MainActor in
            // Al primo uso chiede il permesso; le volte dopo risponde subito.
            guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { return }
            // nel frattempo il recupero può essere stato fermato o spostato (anche con la richiesta del permesso a schermo)
            let seconds = newEnd.timeIntervalSinceNow
            guard !Task.isCancelled, self.end == newEnd, seconds > 1 else { return }
            let content = UNMutableNotificationContent()
            content.title = "Recupero finito".tr
            content.body = "Tocca alla prossima serie.".tr
            // Suoni spenti nelle Impostazioni → Generale: l'avviso arriva lo stesso, ma muto
            content.sound = (UserDefaults.standard.object(forKey: SoundFX.enabledKey) as? Bool ?? true) ? .default : nil
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: Self.notificationID, content: content, trigger: trigger))
        }
    }
}
