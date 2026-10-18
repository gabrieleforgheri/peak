import SwiftUI

// I pezzi condivisi delle feste (record, fine allenamento, obiettivo, serie di settimane): coriandoli, ingressi con rimbalzo,
// numeri che scorrono, il toast. Il linguaggio del movimento è uno solo:
// - «pop» (entrate di festa): da scala 0.6–0.9 e trasparente a pieno, `.spring(duration: 0.5, bounce: 0.35)`, mai da zero;
// - feedback piccolo: `.spring(duration: 0.3, bounce: 0.15)` o `.snappy`; dissolvenze e ingressi `easeOut` 0.2–0.3 s;
// - numeri: `numericText` dentro `withAnimation(.smooth(duration: 0.8))`; fratelli a cascata ogni 50–80 ms.
// Con «Riduci movimento» restano solo dissolvenze brevi: niente coriandoli, scale, spostamenti, rotazioni.

// MARK: - Coriandoli

/// Un'esplosione di coriandoli, una volta sola. Ogni cambio di `trigger` la fa ripartire.
/// Disegna solo mentre dura (≈1,4 s): finita, la `TimelineView` esce dalla gerarchia e non ridisegna più nulla.
/// I pezzi si preparano alla partenza con un seme; il disegno è una funzione del tempo trascorso (niente stato per frame).
struct ConfettiBurst: View {
    let trigger: Int
    /// Il punto di partenza, nello spazio di questa vista.
    var origin: CGPoint

    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var started: Date?
    @State private var pieces: [Piece] = []
    @State private var shots: UInt64 = 0

    private static let duration: Double = 1.4
    private static let count = 96
    private static let drag = 1.3      // attrito dell'aria: i pezzi frenano in salita
    private static let gravity = 1100.0 // pt/s²

    private struct Piece {
        let vx: Double
        let vy: Double
        let spin: Double
        let phase: Double
        let flip: Double
        let w: CGFloat
        let h: CGFloat
        let round: Bool
        let color: Color
    }

    var body: some View {
        Color.clear
            .overlay {
                if let started {
                    TimelineView(.animation) { timeline in
                        Canvas { context, _ in
                            draw(&context, t: timeline.date.timeIntervalSince(started))
                        }
                    }
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onChange(of: trigger) { fire() }
            .task(id: started) {
                guard started != nil else { return }
                try? await Task.sleep(for: .seconds(Self.duration + 0.1))
                if !Task.isCancelled { started = nil } // fuori dalla gerarchia: la timeline si ferma
            }
    }

    private func fire() {
        guard !reduceMotion else { return }
        shots += 1
        var random = SeededRandom(seed: shots &* 0x9E37_79B9_7F4A_7C15 &+ 1)
        let colors = [settings.accentColor] + MuscleGroup.allCases.map(\.tint)
        pieces = (0..<Self.count).map { _ in
            // a ventaglio verso l'alto, un po' più largo dei lati
            let angle = -Double.pi / 2 + (random.next() - 0.5) * 2.3
            let speed = 430 + random.next() * 640
            let size = 6 + random.next() * 5
            let round = random.next() < 0.3
            return Piece(
                vx: cos(angle) * speed, vy: sin(angle) * speed,
                spin: (random.next() - 0.5) * 16, phase: random.next() * .pi * 2, flip: 6 + random.next() * 8,
                w: CGFloat(size), h: CGFloat(round ? size : size * 1.7), round: round,
                color: colors[Int(random.next() * Double(colors.count)) % colors.count]
            )
        }
        started = Date()
    }

    private func draw(_ context: inout GraphicsContext, t: Double) {
        guard t >= 0, t < Self.duration else { return }
        // negli ultimi 30% sfuma
        let fadeStart = Self.duration * 0.7
        let alpha = t > fadeStart ? max(0, 1 - (t - fadeStart) / (Self.duration - fadeStart)) : 1
        let glide = (1 - exp(-Self.drag * t)) / Self.drag
        for p in pieces {
            var c = context
            c.opacity = alpha
            c.translateBy(x: origin.x + p.vx * glide, y: origin.y + p.vy * glide + 0.5 * Self.gravity * t * t)
            c.rotate(by: .radians(p.phase + p.spin * t))
            if p.round {
                c.fill(Path(ellipseIn: CGRect(x: -p.w / 2, y: -p.w / 2, width: p.w, height: p.w)), with: .color(p.color))
            } else {
                // il foglietto che gira su sé stesso: si schiaccia e si riapre
                c.scaleBy(x: 1, y: 0.25 + 0.75 * abs(cos(p.flip * t)))
                c.fill(Path(CGRect(x: -p.w / 2, y: -p.h / 2, width: p.w, height: p.h)), with: .color(p.color))
            }
        }
    }
}

/// SplitMix64: gli stessi semi danno gli stessi pezzi.
private struct SeededRandom {
    var state: UInt64
    init(seed: UInt64) { state = seed }

    /// Un numero in 0..<1.
    mutating func next() -> Double {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        z ^= z >> 31
        return Double(z >> 11) / Double(1 << 53)
    }
}

private struct ConfettiSpotKey: PreferenceKey {
    static let defaultValue: [String: Anchor<CGPoint>] = [:]
    static func reduce(value: inout [String: Anchor<CGPoint>], nextValue: () -> [String: Anchor<CGPoint>]) {
        value.merge(nextValue()) { _, new in new }
    }
}

extension View {
    /// Segna il punto da cui partono i coriandoli di nome `name`: il centro di questa vista.
    func confettiSpot(_ name: String) -> some View {
        anchorPreference(key: ConfettiSpotKey.self, value: .center) { [name: $0] }
    }

    /// Lo strato dei coriandoli, sopra questa vista. `bursts`: per ogni nome, quante volte è partito (ogni aumento è un'esplosione).
    /// Parte dal punto marcato con `confettiSpot(name)`; se non c'è, da `fallback` (di base in alto al centro).
    func confettiLayer(_ bursts: [String: Int], fallback: UnitPoint = UnitPoint(x: 0.5, y: 0.2)) -> some View {
        overlayPreferenceValue(ConfettiSpotKey.self) { spots in
            GeometryReader { geo in
                ZStack {
                    ForEach(bursts.keys.sorted(), id: \.self) { name in
                        ConfettiBurst(
                            trigger: bursts[name] ?? 0,
                            origin: spots[name].map { geo[$0] } ?? CGPoint(x: geo.size.width * fallback.x, y: geo.size.height * fallback.y)
                        )
                    }
                }
            }
            .allowsHitTesting(false)
        }
    }
}

// MARK: - Ingressi e rimbalzi

private struct PopModifier: ViewModifier {
    let shown: Bool
    let from: CGFloat
    let rotation: Double
    let delay: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .scaleEffect(shown || reduceMotion ? 1 : from)
            .rotationEffect(.degrees(shown || reduceMotion ? 0 : rotation))
            .opacity(shown ? 1 : 0)
            .animation(
                reduceMotion ? .easeOut(duration: 0.2).delay(delay) : .spring(duration: 0.5, bounce: 0.35).delay(delay),
                value: shown
            )
    }
}

private struct EnterModifier: ViewModifier {
    let shown: Bool
    let offset: CGFloat
    let delay: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown || reduceMotion ? 0 : offset)
            .animation(.easeOut(duration: reduceMotion ? 0.2 : 0.3).delay(delay), value: shown)
    }
}

private struct BounceModifier: ViewModifier {
    let trigger: Int
    let peak: CGFloat
    let wobble: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private struct Frame {
        var scale: CGFloat = 1
        var angle: Double = 0
    }

    func body(content: Content) -> some View {
        let still = reduceMotion
        return content.keyframeAnimator(initialValue: Frame(), trigger: trigger) { view, frame in
            view
                .scaleEffect(still ? 1 : frame.scale)
                .rotationEffect(.degrees(still ? 0 : frame.angle))
        } keyframes: { _ in
            KeyframeTrack(\.scale) {
                CubicKeyframe(peak, duration: 0.16)
                SpringKeyframe(1, duration: 0.5, spring: .bouncy)
            }
            KeyframeTrack(\.angle) {
                CubicKeyframe(-wobble, duration: 0.09)
                CubicKeyframe(wobble, duration: 0.13)
                CubicKeyframe(-wobble * 0.55, duration: 0.13)
                CubicKeyframe(wobble * 0.25, duration: 0.13)
                CubicKeyframe(0, duration: 0.14)
            }
        }
    }
}

private struct RollModifier<V: Equatable>: ViewModifier {
    let value: V
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .contentTransition(.numericText())
            .animation(reduceMotion ? nil : .smooth(duration: 0.5), value: value)
    }
}

extension View {
    /// Ingresso di festa: da scala `from` (mai da zero) e trasparente a pieno, con rimbalzo e, se serve, un giro che si assesta.
    /// Si accende quando `shown` diventa `true`; con «Riduci movimento» è solo una dissolvenza.
    func pop(_ shown: Bool, from: CGFloat = 0.7, rotation: Double = 0, delay: Double = 0) -> some View {
        modifier(PopModifier(shown: shown, from: from, rotation: rotation, delay: delay))
    }

    /// Ingresso morbido per le schede e i testi: dissolve e sale da `offset` pt. `delay` per la cascata (50–80 ms a fratello).
    func enter(_ shown: Bool, offset: CGFloat = 12, delay: Double = 0) -> some View {
        modifier(EnterModifier(shown: shown, offset: offset, delay: delay))
    }

    /// Un colpo di scala (e, con `wobble` in gradi, un'oscillazione che si assesta) ogni volta che `trigger` cambia.
    /// Per la fiamma della serie, l'anello di oggi, il trofeo. Non parte alla comparsa.
    func bounce(trigger: Int, peak: CGFloat = 1.3, wobble: Double = 0) -> some View {
        modifier(BounceModifier(trigger: trigger, peak: peak, wobble: wobble))
    }

    /// Il numero che cambia scorre cifra per cifra invece di saltare (le statistiche che si aggiornano da sole).
    /// `value` è il dato che cambia: solo allora anima.
    func rolls<V: Equatable>(on value: V) -> some View {
        modifier(RollModifier(value: value))
    }
}

// MARK: - Toast

/// La capsula «Nuovo massimale!»: sale dall'alto, resta ~1,8 s, esce da dove è entrata.
struct CelebrationToast: View {
    let text: String
    var symbol: String = "trophy.fill"
    var tint: Color = Theme.positive

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: symbol).font(.system(size: 15, weight: .bold))
            Text(text.tr).font(Theme.rounded(15, .heavy))
        }
        .foregroundStyle(Theme.bg)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(tint, in: Capsule())
        .shadow(color: tint.opacity(0.4), radius: 14, y: 6)
        .accessibilityElement(children: .combine)
    }
}

private struct ToastHost: ViewModifier {
    let text: String
    let symbol: String
    let tint: Color
    let trigger: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false
    @State private var last = 0

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .top) {
                if shown {
                    CelebrationToast(text: text, symbol: symbol, tint: tint)
                        .padding(.top, 8)
                        .transition(reduceMotion ? .opacity : .move(edge: .top).combined(with: .opacity))
                        .allowsHitTesting(false)
                }
            }
            .task(id: trigger) {
                // `last`: se la vista torna a comparire con un trigger vecchio, il toast non riparte
                guard trigger > 0, trigger != last else { return }
                last = trigger
                withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(duration: 0.4, bounce: 0.15)) { shown = true }
                try? await Task.sleep(for: .seconds(1.8))
                guard !Task.isCancelled else { return }
                withAnimation(.easeOut(duration: 0.25)) { shown = false }
            }
            .onDisappear { shown = false }
    }
}

extension View {
    /// Il toast di festa in alto: appare a ogni aumento di `trigger`, sparisce da solo dopo ~1,8 s.
    func celebrationToast(_ text: String, symbol: String = "trophy.fill", tint: Color = Theme.positive, trigger: Int) -> some View {
        modifier(ToastHost(text: text, symbol: symbol, tint: tint, trigger: trigger))
    }
}

// MARK: - Cosa è già stato festeggiato

/// Quanto è già stato festeggiato, in `UserDefaults` (di questo iPhone): il riepilogo lo scrive quando fa la festa e la Home
/// festeggia solo quello che è più alto, ad esempio un allenamento registrato dal web e arrivato con la sincronizzazione.
enum Celebrated {
    private static let streakKey = "celebrated.streak"
    private static let weekKey = "celebrated.week"

    /// Le settimane di fila già festeggiate; `nil` = mai salvato (prima volta in assoluto: si salva senza festa).
    static var streak: Int? {
        get { UserDefaults.standard.object(forKey: streakKey) as? Int }
        set { UserDefaults.standard.set(newValue, forKey: streakKey) }
    }

    /// Il lunedì della settimana di `date`, come lo contano le statistiche.
    static func weekStart(of date: Date = Date()) -> Date {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        return calendar.dateInterval(of: .weekOfYear, for: date)?.start ?? date
    }

    /// Gli allenamenti già festeggiati nella settimana che inizia a `start`: 0 se l'ultimo valore salvato è di una settimana
    /// passata, `nil` se non c'è mai stato niente.
    static func week(start: Date) -> Int? {
        guard let raw = UserDefaults.standard.string(forKey: weekKey) else { return nil }
        let parts = raw.split(separator: ":")
        guard parts.count == 2, let stamp = Int(parts[0]), let count = Int(parts[1]) else { return nil }
        return stamp == Int(start.timeIntervalSince1970) ? count : 0
    }

    static func store(week count: Int, start: Date) {
        UserDefaults.standard.set("\(Int(start.timeIntervalSince1970)):\(count)", forKey: weekKey)
    }
}
