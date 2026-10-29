import SwiftUI

/// Il movimento di tutti i giorni, specchio dei token del web (app.css --dur-1..4, --ease-out; fx.ts).
/// Sobrio: breve, ease-out, pochi punti, mai rimbalzi. Con «Riduci movimento» restano solo le dissolvenze e i numeri sono subito quelli finali.
/// Le entrate partono una volta, la prima volta che il blocco entra nello schermo (non a ogni ridisegno dello scroll).
enum Motion {
    static let press = 0.12   // --dur-1: tocco
    static let fade = 0.2     // --dur-2: colori, dissolvenze
    static let enter = 0.3    // --dur-3: entrata di un blocco
    static let chart = 0.6    // --dur-4: grafici che si disegnano

    /// cubic-bezier(0.23, 1, 0.32, 1), come --ease-out.
    static func out(_ duration: Double) -> Animation { .timingCurve(0.23, 1, 0.32, 1, duration: duration) }

    /// Ritardo della cascata: 30 ms l'uno, dall'ottavo in poi tutti insieme.
    static func stagger(_ index: Int) -> Double { Double(min(max(index, 0), 8)) * 0.03 }

    /// Chi ha già fatto la sua entrata (chiave stabile): righe di lista che il List ricrea scorrendo non la ripetono.
    @MainActor static var seen = Set<String>()
}

// MARK: - Prima volta in vista

/// Esegue `perform` una sola volta, quando il blocco compare davvero nello schermo (anche in uno ScrollView non pigro, dove `onAppear` scatta subito per tutti).
private struct FirstVisible: ViewModifier {
    let perform: () -> Void
    @State private var done = false

    func body(content: Content) -> some View {
        content.background {
            if !done {
                GeometryReader { geo in
                    let visible = geo.frame(in: .global).minY < UIScreen.main.bounds.height - 24
                    Color.clear.onChange(of: visible, initial: true) { _, now in
                        guard now, !done else { return }
                        done = true
                        perform()
                    }
                }
            }
        }
    }
}

// MARK: - Entrata a cascata

private struct Appear: ViewModifier {
    let index: Int
    let key: String?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown: Bool

    init(index: Int, key: String?) {
        self.index = index
        self.key = key
        _shown = State(initialValue: key.map { Motion.seen.contains($0) } ?? false)
    }

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown || reduceMotion ? 0 : 8)
            .modifier(FirstVisible {
                guard !shown else { return }
                if let key { Motion.seen.insert(key) }
                withAnimation(Motion.out(reduceMotion ? Motion.fade : Motion.enter).delay(reduceMotion ? 0 : Motion.stagger(index))) { shown = true }
            })
    }
}

// MARK: - Grafici

/// Disegna da sinistra a destra (linee, barre, mappa): una maschera che si apre. Con `anchor: .bottom` le barre crescono dal basso.
private struct DrawIn: ViewModifier {
    let anchor: UnitPoint
    let delay: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drawn = false

    func body(content: Content) -> some View {
        content
            .mask {
                Rectangle()
                    .scaleEffect(x: anchor == .leading && !drawn && !reduceMotion ? 0.001 : 1, y: anchor == .bottom && !drawn && !reduceMotion ? 0.001 : 1, anchor: anchor)
                    // la maschera copre anche ombre e punti che escono dal riquadro
                    .padding(-20)
            }
            .opacity(drawn || !reduceMotion ? 1 : 0)
            .modifier(FirstVisible {
                withAnimation(Motion.out(reduceMotion ? Motion.fade : Motion.chart).delay(reduceMotion ? 0 : delay)) { drawn = true }
            })
    }
}

/// Un numero che conta fino al valore la prima volta in vista e scorre quando il dato cambia. `format` riceve il valore intermedio.
struct CountText: View {
    let value: Double
    var format: (Double) -> String = { String(Int($0.rounded())) }
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown: Double = 0

    var body: some View {
        Text(format(shown))
            .contentTransition(.numericText(value: shown))
            .monospacedDigit()
            .modifier(FirstVisible {
                if reduceMotion { shown = value } else { withAnimation(.smooth(duration: Motion.chart)) { shown = value } }
            })
            .onChange(of: value) { _, new in
                withAnimation(reduceMotion ? nil : .smooth(duration: 0.4)) { shown = new }
            }
    }
}

/// L'anello a fette: un progresso 0...1 che apre le fette in senso orario la prima volta in vista.
struct DrawProgress: ViewModifier {
    @Binding var progress: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content.modifier(FirstVisible {
            if reduceMotion { progress = 1 } else { withAnimation(Motion.out(Motion.chart)) { progress = 1 } }
        })
    }
}

// MARK: - Tocco

/// Stato premuto per righe e card: scala appena, 120 ms ease-out. Sostituisce `.plain` dove il bottone è una card.
struct PressStyle: ButtonStyle {
    var scale: CGFloat = 0.98
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? scale : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(Motion.out(Motion.press), value: configuration.isPressed)
    }
}

extension View {
    /// Il blocco entra (sale di 8 pt e appare) la prima volta che si vede, in cascata con gli altri per `index`.
    /// `key`: per le righe di una lista che si ricrea scorrendo; chi l'ha già fatta non la ripete.
    func appear(_ index: Int = 0, key: String? = nil) -> some View {
        modifier(Appear(index: index, key: key))
    }

    /// Un grafico che si disegna da sinistra la prima volta in vista.
    func drawIn(delay: Double = 0) -> some View {
        modifier(DrawIn(anchor: .leading, delay: delay))
    }

    /// Una barra che cresce dal basso la prima volta in vista; `index` per la cascata.
    func growUp(_ index: Int = 0) -> some View {
        modifier(DrawIn(anchor: .bottom, delay: Motion.stagger(index)))
    }

    /// Apre `progress` fino a 1 la prima volta in vista (anelli e fette).
    func drawProgress(_ progress: Binding<CGFloat>) -> some View {
        modifier(DrawProgress(progress: progress))
    }
}
