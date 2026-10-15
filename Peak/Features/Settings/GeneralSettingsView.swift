import SwiftUI
import SwiftData

/// Impostazioni → Generale: unità e colore.
struct GeneralSettingsView: View {
    @Environment(AppSettings.self) private var settings

    var body: some View {
        SettingsPage {
            unitCard
            accentCard
            GlassCard {
                Button { settings.tutorialDone = false; AppNav.shared.tab = .home } label: {
                    Label("Rivedi il tutorial", systemImage: "questionmark.circle")
                        .font(Theme.rounded(15, .semibold))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .foregroundStyle(settings.accentColor)
            }
        }
    }

    var unitCard: some View {
        @Bindable var settings = settings
        return GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Unità di misura")
                Picker("Unità", selection: $settings.unit) {
                    ForEach(WeightUnit.allCases) { Text($0.symbol.uppercased()).tag($0) }
                }
                .pickerStyle(.segmented)
                Text("I dati restano sempre salvati in chilogrammi: cambia solo come vengono mostrati.")
                    .font(Theme.rounded(11, .medium))
                    .foregroundStyle(Theme.textTertiary)
            }
        }
    }

    var accentCard: some View {
        @Bindable var settings = settings
        return GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Colore accento")
                HStack(spacing: 10) {
                    ForEach(AccentPalette.allCases) { palette in
                        Button {
                            settings.accent = palette
                            Haptics.tap()
                        } label: {
                            Circle()
                                .fill(palette.color)
                                .frame(width: 34, height: 34)
                                .overlay(
                                    Circle()
                                        .strokeBorder(.white.opacity(settings.accent == palette ? 0.9 : 0), lineWidth: 2)
                                        .padding(-4)
                                )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(palette.title)
                    }
                    Spacer()
                }
            }
        }
    }
}
