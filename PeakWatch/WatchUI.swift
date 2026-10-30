import SwiftUI
#if os(watchOS)
import WatchKit
#endif

/// Il Watch segue la lingua del sistema (italiano, altrimenti inglese).
func w(_ it: String, _ en: String) -> String {
    Locale.preferredLanguages.first?.hasPrefix("it") == true ? it : en
}

/// Vibrazioni del polso.
enum Haptic {
    static func success() { play(.success) }
    static func click() { play(.click) }
    /// Fine del recupero.
    static func alert() { play(.notification) }

    private static func play(_ type: WKHapticType) {
        WKInterfaceDevice.current().play(type)
    }
}

/// Colori come nell'app Allenamento: sfondo nero, tempo giallo, accenti verde/rosso/arancio.
extension Color {
    static let workoutTime = Color.yellow
    static let workoutEnergy = Color.green
    static let workoutHeart = Color.red
    static let workoutRest = Color.orange
}

/// Tempo come lo scrive Apple: `mm:ss.cc`, con le ore solo quando servono; a schermo attenuato niente centesimi.
func elapsedText(_ time: TimeInterval, centiseconds: Bool) -> String {
    let total = Int(max(0, time))
    let (h, m, s) = (total / 3600, total / 60 % 60, total % 60)
    var text = h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
    if centiseconds { text += String(format: ".%02d", Int(max(0, time) * 100) % 100) }
    return text
}
