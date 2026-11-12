import SwiftUI

// MARK: - Pillola selezionabile

/// Pillola di un selettore a scorrimento (metrica, esercizio): piena nel colore `tint` se scelta, vetro altrimenti.
/// Sostituisce il `Button` con `Text` + `Capsule` ripetuto nei selettori di Friends. La didascalia è una vista (di solito un `Text`):
/// chi la passa decide se tradurla (`Text(String)`) o no (`Text(verbatim:)`).
struct SelectablePill<Label: View>: View {
    let isOn: Bool
    var tint: Color
    let action: () -> Void
    @ViewBuilder var label: Label

    var body: some View {
        Button(action: action) {
            label
                .font(Theme.Text.footnote)
                .foregroundStyle(isOn ? Color.black.opacity(0.85) : Theme.textSecondary)
                .padding(.horizontal, Theme.Space.m).padding(.vertical, Theme.Space.s)
                .background(Capsule().fill(isOn ? tint : Theme.card))
                .animation(Motion.out(Motion.fade), value: isOn) // i colori sfumano
        }
        .buttonStyle(PressStyle())
    }
}

#if DEBUG
#Preview("SelectablePill") {
    HStack(spacing: Theme.Space.s) {
        SelectablePill(isOn: true, tint: Theme.positive, action: {}) { Text(verbatim: "Volume 30 giorni") }
        SelectablePill(isOn: false, tint: Theme.positive, action: {}) { Text(verbatim: "Settimane di fila") }
    }
    .padding(Theme.Space.l)
    .background(Theme.background(accent: Theme.positive))
}
#endif
