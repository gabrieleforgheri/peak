import SwiftUI

/// Conferma in un foglio basso, nello stile dell'app: titolo, tasto principale, «annulla» sotto.
/// Sostituisce `confirmationDialog`, che su iOS recente nasce ancorato a metà schermo.
/// L'azione parte a foglio chiuso (come `onDismiss`), così quello che apre subito dopo non si scontra con la chiusura.
private struct ConfirmSheet: ViewModifier {
    @Binding var isPresented: Bool
    let title: String
    let confirm: String
    let cancel: String
    let tint: Color
    let action: () -> Void

    @State private var confirmed = false

    func body(content: Content) -> some View {
        content.sheet(isPresented: $isPresented, onDismiss: {
            if confirmed {
                confirmed = false
                action()
            }
        }) {
            VStack(spacing: 18) {
                Text(verbatim: title.tr)
                    .font(Theme.rounded(20, .bold))
                    .foregroundStyle(Theme.textPrimary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 8)

                VStack(spacing: 10) {
                    Button {
                        confirmed = true
                        isPresented = false
                    } label: {
                        Text(verbatim: confirm.tr)
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: tint))

                    Button {
                        isPresented = false
                    } label: {
                        Text(verbatim: cancel.tr)
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Theme.bgRaised)
            .presentationDetents([.height(210)])
            .presentationDragIndicator(.visible)
            .presentationBackground(Theme.bgRaised)
        }
    }
}

extension View {
    /// Foglio di conferma: `title` e i due tasti sono chiavi di traduzione. `tint` colora il tasto principale
    /// (colore d'accento, o rosso per le azioni che cancellano).
    func confirmSheet(isPresented: Binding<Bool>, title: String, confirm: String, cancel: String,
                      tint: Color, action: @escaping () -> Void) -> some View {
        modifier(ConfirmSheet(isPresented: isPresented, title: title, confirm: confirm, cancel: cancel, tint: tint, action: action))
    }
}
