import SwiftUI

/// La prima pagina della 2.0, una volta sola (anche per chi aggiorna). Collegarsi è facoltativo:
/// «Salta» lascia l'app com'era, tutta in locale.
struct WelcomeView: View {
    @Environment(AppSettings.self) private var settings

    @State private var connecting = false

    var body: some View {
        Group {
            if connecting {
                ConnectFlowView(
                    onFinish: { settings.didSeeWelcome = true },
                    onCancel: { connecting = false }
                )
            } else {
                intro
            }
        }
        .screenBackground(settings.accentColor)
    }

    private var intro: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "mountain.2.fill")
                .font(.system(size: 56, weight: .semibold))
                .foregroundStyle(settings.accentColor)
            Text("Peak")
                .font(Theme.display(40))
                .foregroundStyle(Theme.textPrimary)
            Text("Collega il tuo account yG: allenamenti e massimali si sincronizzano con il web (peak.yrb4g.com) e restano al sicuro sul server.")
                .font(Theme.rounded(15, .medium))
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()

            Button("Collega a yG") { connecting = true }
                .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                .controlSize(.large)

            Button("Salta") { settings.didSeeWelcome = true }
                .font(Theme.rounded(13, .semibold))
                .foregroundStyle(Theme.textTertiary)
                .padding(.vertical, 6)

            Text("Puoi collegarti anche dopo, da Impostazioni.")
                .font(Theme.rounded(11, .medium))
                .foregroundStyle(Theme.textTertiary)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 20)
    }
}
