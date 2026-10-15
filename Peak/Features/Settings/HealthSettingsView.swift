import SwiftUI
import SwiftData

/// Impostazioni → App Salute.
struct HealthSettingsView: View {
    @Environment(AppSettings.self) private var settings
    @State private var healthMessage: String?

    var body: some View {
        SettingsPage { healthCard }
    }

    var healthCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "App Salute")

                if HealthService.isAvailable {
                    Toggle(isOn: Binding(
                        get: { settings.healthSyncEnabled },
                        set: { newValue in
                            settings.healthSyncEnabled = newValue
                            if newValue { Task { await connectHealth() } }
                        }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Sincronizza con Salute")
                                .font(Theme.rounded(15, .semibold))
                            Text("Legge il peso corporeo e registra lì i tuoi allenamenti.")
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                    }
                    .tint(settings.accentColor)

                    if let healthMessage {
                        Text(healthMessage)
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                } else {
                    Text("Salute non è disponibile su questo dispositivo.")
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
        }
    }

    func connectHealth() async {
        do {
            try await HealthService.requestAuthorization()
            healthMessage = "Collegato. Concedi i permessi nell'app Salute se non l'hai già fatto."
            Haptics.success()
        } catch {
            settings.healthSyncEnabled = false
            healthMessage = error.localizedDescription
            Haptics.warning()
        }
    }
}
