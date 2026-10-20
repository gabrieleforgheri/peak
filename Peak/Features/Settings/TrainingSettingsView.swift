import SwiftUI
import SwiftData

/// Impostazioni → Allenamento: timer di recupero, progressione e calcolatore dischi.
struct TrainingSettingsView: View {
    @Environment(AppSettings.self) private var settings

    var body: some View {
        SettingsPage { trainingCard }
    }

    var trainingCard: some View {
        @Bindable var settings = settings
        return GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Allenamento")

                Stepper(value: Binding(get: { max(1, settings.weeklyGoal) }, set: { settings.weeklyGoal = $0 }), in: 1...7) {
                    Text("Obiettivo della settimana: \(max(1, settings.weeklyGoal)) allenamenti")
                        .font(Theme.rounded(14, .medium))
                }

                Divider().overlay(Theme.stroke)

                Toggle(isOn: $settings.restTimerEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Timer di recupero")
                            .font(Theme.rounded(15, .semibold))
                        Text("Parte dopo ogni serie e avvisa quando è ora di ricominciare.")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                .tint(settings.accentColor)

                if settings.restTimerEnabled {
                    Stepper(value: $settings.restSeconds, in: 15...600, step: 15) {
                        Text("Recupero predefinito: \(Fmt.duration(seconds: settings.restSeconds))")
                            .font(Theme.rounded(14, .medium))
                    }
                }

                Divider().overlay(Theme.stroke)

                Toggle(isOn: $settings.progressionEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Suggerisci il prossimo carico")
                            .font(Theme.rounded(15, .semibold))
                        Text("Doppia progressione: quando tutte le serie arrivano in cima al range, +1 passo e si riparte dal fondo.")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                .tint(settings.accentColor)

                if settings.progressionEnabled {
                    Stepper(value: $settings.repRangeMin, in: 1...30) {
                        Text("Ripetizioni minime: \(settings.repRangeMin)")
                            .font(Theme.rounded(14, .medium))
                    }
                    Stepper(value: $settings.repRangeMax, in: settings.repRangeMin...30) {
                        Text("Ripetizioni massime: \(settings.repRangeMax)")
                            .font(Theme.rounded(14, .medium))
                    }
                }

                Divider().overlay(Theme.stroke)

                Toggle(isOn: $settings.plateCalculatorEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Calcolatore dischi")
                            .font(Theme.rounded(15, .semibold))
                        Text("Sui bilancieri e sulle plate loaded mostra quali dischi mettere per lato.")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                .tint(settings.accentColor)

                if settings.plateCalculatorEnabled {
                    Text("Dischi disponibili")
                        .font(Theme.rounded(12, .semibold))
                        .foregroundStyle(Theme.textTertiary)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 58), spacing: 8)], spacing: 8) {
                        ForEach(Plates.catalog(for: settings.unit), id: \.self) { plate in
                            let isOn = settings.availablePlates.contains { abs($0 - plate) < 0.001 }
                            Button {
                                if isOn {
                                    settings.availablePlates.removeAll { abs($0 - plate) < 0.001 }
                                } else {
                                    settings.availablePlates.append(plate)
                                }
                                Haptics.tap()
                            } label: {
                                Text(Fmt.weightValue(plate, unit: settings.unit))
                                    .font(Theme.rounded(13, .bold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(
                                        isOn ? PlateBarView.color(for: plate).opacity(0.3) : Theme.card,
                                        in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    )
                                    .foregroundStyle(isOn ? Theme.textPrimary : Theme.textTertiary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
}
