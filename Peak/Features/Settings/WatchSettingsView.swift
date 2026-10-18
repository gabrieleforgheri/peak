import SwiftUI

/// Impostazioni → Apple Watch: accendere o spegnere l'uso dell'orologio per registrare le serie.
struct WatchSettingsView: View {
    @Environment(AppSettings.self) private var settings

    var body: some View {
        SettingsPage { watchCard }
    }

    private var status: String {
        if !settings.watchEnabled { return "Spento: l'orologio non riceve né invia nulla." }
        return WatchBridge.shared.isWatchAppInstalled
            ? "Collegato: durante un allenamento puoi far partire la serie dal Watch."
            : "L'app non è sull'orologio abbinato. Si installa dal Mac."
    }

    private var watchCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Apple Watch")
                Toggle(isOn: Binding(
                    get: { settings.watchEnabled },
                    set: { settings.watchEnabled = $0; WatchBridge.shared.enabledChanged() }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Usa l'Apple Watch")
                            .font(Theme.rounded(15, .semibold))
                        Text("Conta le ripetizioni sul polso e registra la serie sull'iPhone.")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                .tint(settings.accentColor)
                Text(status)
                    .font(Theme.rounded(11, .medium))
                    .foregroundStyle(Theme.textTertiary)
            }
        }
    }
}
