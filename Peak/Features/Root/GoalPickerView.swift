import SwiftUI

/// La scelta dell'obiettivo, a tutto schermo: quanti allenamenti a settimana. Si apre la prima volta e dall'Allenamento.
struct GoalPickerView: View {
    @Environment(AppSettings.self) private var settings

    /// L'obiettivo attuale (0 = prima volta: non si può annullare).
    let current: Int
    let suggested: Int
    var onClose: () -> Void

    @State private var pick = 0

    private let options: [(n: Int, name: String, text: String)] = [
        (2, "Leggero", "Per cominciare, senza fretta."),
        (3, "Regolare", "Il passo giusto per crescere."),
        (4, "Impegnato", "Una routine seria."),
        (5, "Intenso", "Quasi tutti i giorni."),
        (6, "Da campione", "Per chi vive in palestra.")
    ]

    var body: some View {
        ZStack(alignment: .topLeading) {
            Theme.background(accent: settings.accentColor)
            ScrollView {
                VStack(spacing: 8) {
                    Image(systemName: "mountain.2.fill")
                        .font(.system(size: 60)).foregroundStyle(settings.accentColor)
                        .shadow(color: settings.accentColor.opacity(0.5), radius: 18, y: 6).padding(.top, 36)
                    Text("Quanti allenamenti a settimana?")
                        .font(Theme.rounded(28, .heavy)).tracking(-0.8).multilineTextAlignment(.center)
                    Text("Scegli un obiettivo che puoi mantenere. Lo cambi quando vuoi.")
                        .font(Theme.rounded(15, .medium)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)

                    VStack(spacing: 10) {
                        ForEach(options, id: \.n) { option in row(option) }
                    }
                    .padding(.top, 20)

                    Button("Continua") {
                        settings.weeklyGoal = pick
                        Haptics.success()
                        onClose()
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                    .padding(.top, 14)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
            if current > 0 {
                Button("Annulla") { onClose() }
                    .font(Theme.rounded(15, .semibold)).foregroundStyle(settings.accentColor).padding(20)
            }
        }
        .onAppear { pick = current > 0 ? current : max(2, min(6, suggested)) }
    }

    private func row(_ o: (n: Int, name: String, text: String)) -> some View {
        let on = pick == o.n
        return Button {
            pick = o.n
            Haptics.tap()
        } label: {
            HStack(spacing: 14) {
                HStack(spacing: 3) {
                    ForEach(0..<6, id: \.self) { k in
                        RoundedRectangle(cornerRadius: 3).fill(k < o.n ? settings.accentColor : Color.white.opacity(0.14)).frame(width: 7, height: 22)
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(o.n) a settimana").font(Theme.rounded(17, .bold)).foregroundStyle(Theme.textPrimary)
                    Text("\(o.name) · \(o.text)").font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.leading)
                }
                Spacer()
                Circle().strokeBorder(on ? settings.accentColor : Theme.stroke, lineWidth: 2)
                    .background(Circle().fill(on ? settings.accentColor : .clear).padding(5))
                    .frame(width: 22, height: 22)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(on ? settings.accentColor.opacity(0.12) : Theme.card))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(on ? settings.accentColor : Theme.stroke, lineWidth: 2))
            .shadow(color: on ? settings.accentColor.opacity(0.18) : .clear, radius: 12)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? [.isSelected] : [])
    }
}
