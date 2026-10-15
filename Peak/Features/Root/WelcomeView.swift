import SwiftUI

/// La prima pagina della 2.0, una volta sola (anche per chi aggiorna). Collegarsi è facoltativo:
/// «Salta» lascia l'app com'era, tutta in locale.
///
/// Sopra ai pulsanti girano sei scene in ciclo, le stesse del benvenuto web (web/public/benvenuto.*):
/// Peak, Massimali, Schede, Serie, Progressi, Web e iPhone.
struct WelcomeView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var connecting = false
    @State private var scene = 0

    /// Quanto resta ogni scena, in secondi (come `durations` in benvenuto.js).
    private static let durations: [Double] = [3.2, 5.5, 5, 4.5, 4.5, 4.5]

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
        VStack(spacing: 14) {
            stage
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityHidden(true)

            HStack(spacing: 6) {
                ForEach(0..<Self.durations.count, id: \.self) { index in
                    Capsule()
                        .fill(index == scene ? settings.accentColor : Theme.textTertiary)
                        .frame(width: index == scene ? 18 : 6, height: 6)
                }
            }
            .animation(.easeInOut(duration: 0.4), value: scene)
            .accessibilityHidden(true)

            Button("Collega a yG") { connecting = true }
                .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                .controlSize(.large)

            Button("Salta") { settings.didSeeWelcome = true }
                .font(Theme.rounded(13, .semibold))
                .foregroundStyle(Theme.textTertiary)
                .padding(.vertical, 2)

            Text("Puoi collegarti anche dopo, da Impostazioni.")
                .font(Theme.rounded(11, .medium))
                .foregroundStyle(Theme.textTertiary)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 20)
        .task {
            // senza movimento: l'ultima scena, ferma
            guard !reduceMotion else { scene = Self.durations.count - 1; return }
            var next = 0
            while !Task.isCancelled {
                withAnimation(.easeInOut(duration: 0.6)) { scene = next }
                try? await Task.sleep(for: .seconds(Self.durations[next]))
                next = (next + 1) % Self.durations.count
            }
        }
    }

    // MARK: - Scene

    private var stage: some View {
        ZStack {
            peakScene.opacity(scene == 0 ? 1 : 0)
            maxesScene.opacity(scene == 1 ? 1 : 0)
            templateScene.opacity(scene == 2 ? 1 : 0)
            setScene.opacity(scene == 3 ? 1 : 0)
            progressScene.opacity(scene == 4 ? 1 : 0)
            syncScene.opacity(scene == 5 ? 1 : 0)
        }
    }

    private var peakScene: some View {
        let on = scene == 0
        return VStack(spacing: 14) {
            // l'icona dell'app (come nel benvenuto web), non un simbolo
            Image("WelcomeIcon")
                .resizable()
                .frame(width: 96, height: 96)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .shadow(color: settings.accentColor.opacity(0.3), radius: 20)
                .reveal(on, delay: 0.1)
            Text("Peak")
                .font(Theme.display(52))
                .foregroundStyle(Theme.textPrimary)
                .reveal(on, delay: 0.35)
            Text("Il tuo massimale vero.")
                .font(Theme.rounded(18, .semibold))
                .foregroundStyle(Theme.textSecondary)
                .reveal(on, delay: 0.9)
        }
    }

    private var maxesScene: some View {
        let on = scene == 1
        return VStack(alignment: .leading, spacing: 12) {
            sceneLabel("Massimali")
            maxCard("Leg press", "× 6", "180", on: on, delay: 0.3)
            maxCard("Panca piana", "× 5", "80", on: on, delay: 0.85)
            maxCard("Lat machine", "× 8", "70", on: on, delay: 1.4)
            caption("Il peso che hai alzato davvero, su ogni macchina.", on: on, delay: 2.4)
        }
    }

    /// La scheda: nome, poi una riga alla volta con il suo obiettivo.
    private var templateScene: some View {
        let on = scene == 2
        return VStack(alignment: .leading, spacing: 12) {
            sceneLabel("Schede")
            GlassCard(padding: 18) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .firstTextBaseline) {
                        Text("Push 1")
                            .font(Theme.rounded(22, .bold))
                            .foregroundStyle(Theme.textPrimary)
                        Spacer()
                        Text("3 esercizi")
                            .font(Theme.rounded(13, .semibold))
                            .foregroundStyle(Theme.textTertiary)
                    }
                    templateLine("Panca piana", "4 × 8–10 · 80 kg", on: on, delay: 0.8)
                    templateLine("Leg press", "3 × 8–10 · 180 kg", on: on, delay: 1.3)
                    templateLine("Lat machine", "3 × 10–12 · 70 kg", on: on, delay: 1.8)
                }
            }
            .reveal(on, delay: 0.2)
            caption("La tua scheda, pronta a ogni allenamento.", on: on, delay: 2.9)
        }
    }

    private var setScene: some View {
        SetScene(on: scene == 3, tint: settings.accentColor)
    }

    private var progressScene: some View {
        let on = scene == 4
        let heights: [CGFloat] = [0.30, 0.44, 0.38, 0.58, 0.52, 0.74, 0.92]
        return VStack(alignment: .leading, spacing: 12) {
            sceneLabel("Progressi")
            GlassCard(padding: 18) {
                HStack(alignment: .bottom, spacing: 10) {
                    ForEach(heights.indices, id: \.self) { index in
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(index == heights.count - 1 ? settings.accentColor : settings.accentColor.opacity(0.35))
                            .frame(maxWidth: .infinity)
                            .frame(height: 150 * (on ? heights[index] : 0.02), alignment: .bottom)
                            .animation(on ? .spring(duration: 0.8, bounce: 0.1).delay(0.5 + Double(index) * 0.12) : .easeIn(duration: 0.3), value: on)
                    }
                }
                .frame(height: 150, alignment: .bottom)
            }
            .reveal(on, delay: 0.2)
            GlassCard(padding: 16) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cresciuti di più")
                            .font(Theme.rounded(12, .semibold))
                            .foregroundStyle(Theme.textTertiary)
                        Text("Panca piana")
                            .font(Theme.rounded(17, .bold))
                            .foregroundStyle(Theme.textPrimary)
                    }
                    Spacer()
                    Text("+8%")
                        .font(Theme.rounded(26, .heavy))
                        .foregroundStyle(Theme.positive)
                }
            }
            .reveal(on, delay: 2)
            caption("Settimana dopo settimana.", on: on, delay: 2.9)
        }
    }

    private var syncScene: some View {
        let on = scene == 5
        return VStack(spacing: 12) {
            Text("Sul web e sull'iPhone.\nSempre in sincrono.")
                .font(Theme.rounded(24, .bold))
                .foregroundStyle(Theme.textPrimary)
                .multilineTextAlignment(.center)
                .reveal(on, delay: 0.5)
            Text("Collega il tuo account yG: allenamenti e massimali si sincronizzano con peak.yrb4g.com e restano al sicuro sul server.")
                .font(Theme.rounded(13, .medium))
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .reveal(on, delay: 1.2)
        }
    }

    // MARK: - Pezzi

    private func sceneLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(Theme.rounded(13, .bold))
            .tracking(1)
            .foregroundStyle(Theme.textTertiary)
    }

    private func caption(_ text: String, on: Bool, delay: Double) -> some View {
        Text(text)
            .font(Theme.rounded(16, .semibold))
            .foregroundStyle(Theme.textSecondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.top, 8)
            .reveal(on, delay: delay)
    }

    private func maxCard(_ name: String, _ reps: String, _ weight: String, on: Bool, delay: Double) -> some View {
        GlassCard(padding: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text(name)
                        .font(Theme.rounded(17, .bold))
                        .foregroundStyle(Theme.textPrimary)
                    Text(reps)
                        .font(Theme.rounded(13, .semibold))
                        .foregroundStyle(Theme.textTertiary)
                }
                Spacer()
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(weight).font(Theme.rounded(38, .heavy)).foregroundStyle(settings.accentColor)
                    Text("kg").font(Theme.rounded(15, .bold)).foregroundStyle(Theme.textTertiary)
                }
            }
        }
        .reveal(on, delay: delay)
    }

    private func templateLine(_ name: String, _ goal: String, on: Bool, delay: Double) -> some View {
        VStack(spacing: 12) {
            Divider().overlay(Theme.stroke)
            HStack(alignment: .firstTextBaseline) {
                Text(name)
                    .font(Theme.rounded(15, .semibold))
                    .foregroundStyle(Theme.textPrimary)
                Spacer()
                Text(goal)
                    .font(Theme.rounded(14, .bold))
                    .monospacedDigit()
                    .foregroundStyle(settings.accentColor)
            }
        }
        .reveal(on, delay: delay)
    }
}

/// La serie che batte il record: il peso sale da 180 a 190 e compare il badge.
private struct SetScene: View {
    let on: Bool
    let tint: Color

    @State private var weight = 180

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("SERIE")
                .font(Theme.rounded(13, .bold))
                .tracking(1)
                .foregroundStyle(Theme.textTertiary)
            GlassCard(padding: 16, tint: tint) {
                HStack {
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Leg press")
                            .font(Theme.rounded(17, .bold))
                            .foregroundStyle(Theme.textPrimary)
                        Text("× 6")
                            .font(Theme.rounded(13, .semibold))
                            .foregroundStyle(Theme.textTertiary)
                    }
                    Spacer()
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text("\(weight)").font(Theme.rounded(38, .heavy)).foregroundStyle(tint).monospacedDigit()
                        Text("kg").font(Theme.rounded(15, .bold)).foregroundStyle(Theme.textTertiary)
                    }
                }
            }
            .reveal(on, delay: 0.2)
            Text("NUOVO RECORD")
                .font(Theme.rounded(12, .heavy))
                .tracking(1)
                .foregroundStyle(Color(red: 0.04, green: 0.05, blue: 0.08))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Theme.positive, in: Capsule())
                .scaleEffect(on ? 1 : 0.7)
                .opacity(on ? 1 : 0)
                .animation(on ? .spring(duration: 0.6, bounce: 0.35).delay(2) : .easeIn(duration: 0.3), value: on)
            Text("Un record? Lo vedi subito.")
                .font(Theme.rounded(16, .semibold))
                .foregroundStyle(Theme.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
                .reveal(on, delay: 3)
        }
        .task(id: on) {
            weight = 180
            guard on else { return }
            try? await Task.sleep(for: .seconds(1.1))
            for step in 1...10 {
                guard !Task.isCancelled else { return }
                withAnimation(.snappy) { weight = 180 + step }
                try? await Task.sleep(for: .seconds(0.1))
            }
        }
    }
}

private extension View {
    /// Entra dal basso dopo `delay` quando la scena è attiva; esce senza attese.
    func reveal(_ on: Bool, delay: Double) -> some View {
        self
            .opacity(on ? 1 : 0)
            .offset(y: on ? 0 : 14)
            .animation(on ? .easeOut(duration: 0.6).delay(delay) : .easeIn(duration: 0.3), value: on)
    }
}
