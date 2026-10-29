import SwiftUI
import SwiftData

// MARK: - I punti del tutorial

struct TourTargetKey: PreferenceKey {
    static var defaultValue: [String: Anchor<CGRect>] = [:]
    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}

extension View {
    /// Segna un controllo che il tutorial può evidenziare.
    func tourTarget(_ id: String) -> some View {
        anchorPreference(key: TourTargetKey.self, value: .bounds) { [id: $0] }
    }

    /// Un foglio (o la sessione) che il tutorial sa riconoscere: finché è aperto, il passo che gli appartiene sta lì dentro.
    func tourSheet(_ kind: TourState.Sheet, when: Bool = true) -> some View {
        onAppear { if when { TourState.shared.sheet = kind } }
            .onDisappear { if when, TourState.shared.sheet == kind { TourState.shared.sheet = nil } }
    }

    /// I passi del tutorial che vivono dentro questa schermata (un foglio o la sessione): il riquadro e la carta stanno sopra al suo contenuto.
    func tourMarks(_ steps: TourState.Step...) -> some View {
        overlayPreferenceValue(TourTargetKey.self) { anchors in
            GeometryReader { geo in
                TourMarks(steps: steps, rects: anchors.mapValues { geo[$0] }, size: geo.size, insets: geo.safeAreaInsets)
            }
        }
    }
}

// MARK: - Lo stato

/// Il tutorial: prima le idee di Peak a tutto schermo, poi un giro sulle schermate vere (le quattro schede, «Avvia allenamento»,
/// il giorno da scegliere, «+ Serie», peso e ripetizioni, il tasto che salva). Si fanno davvero: parte un allenamento vero e la
/// prima serie salvata chiude il giro. Come `Tour.svelte`.
@MainActor
@Observable
final class TourState {
    static let shared = TourState()

    enum Phase { case off, ideas, marks, cheer }
    enum Step {
        case tabs, start, pick, set, load, save
        /// Sopra il foglio che il passo apre: il riquadro non blocca i tocchi.
        var up: Bool { self == .pick || self == .load || self == .save }
        var next: Step? { self == .tabs ? .start : self == .load ? .save : nil }
        /// Il controllo da evidenziare (segnato con `tourTarget`); la scelta del giorno ha due varianti.
        var targets: [String] {
            switch self {
            case .tabs: []
            case .start: ["start"]
            case .pick: ["days", "go"]
            case .set: ["add-set"]
            case .load: ["load"]
            case .save: ["save"]
            }
        }
    }
    enum Sheet { case days, save }

    var phase: Phase = .off
    var step: Step = .tabs
    /// Quale foglio è aperto: la scelta del giorno, la serie da salvare.
    var sheet: Sheet?
    /// La sessione è sullo schermo.
    var inSession = false
    /// Senza giorni nella scheda la scelta è per gruppi: il riquadro punta a «Inizia».
    var hasDays = true

    var running: Bool { phase != .off }

    func start() {
        step = .tabs
        phase = .ideas
        AppNav.shared.tab = .workouts
    }

    func end() { phase = .off }

    /// Alla fine o saltando: il tutorial è fatto.
    func finish(_ settings: AppSettings) {
        settings.tutorialDone = true
        settings.tourSeen = 3
    }

    func skip(_ settings: AppSettings) {
        finish(settings)
        end()
    }

    func title(_ step: Step) -> String {
        switch step {
        case .tabs: "Quattro sezioni"
        case .start: "Inizia da qui"
        case .pick: hasDays ? "Scegli un giorno" : "Scegli i gruppi, poi Inizia"
        case .set: "Aggiungi una serie"
        case .load: "Peso e ripetizioni"
        case .save: "Salva la serie"
        }
    }

    func text(_ step: Step) -> String? {
        switch step {
        case .tabs: "Allenamento, Macchinari, Me, Amici."
        case .start: "Tocca il pulsante per iniziare."
        case .set: "Tocca + Serie."
        case .load: "Usa − e + per scegliere."
        case .pick, .save: nil
        }
    }
}

// MARK: - Chi guida il giro

/// Tiene il passo dove deve stare (i fogli si aprono e si chiudono, la sessione parte) e chiude il giro alla prima serie salvata.
struct TourDriver: View {
    @Environment(AppSettings.self) private var settings
    @Query private var workouts: [Workout]

    private let tour = TourState.shared
    private let nav = AppNav.shared
    /// Le serie dell'allenamento quando il passo è cominciato.
    @State private var base = -1

    private var active: Workout? { workouts.first(where: \.isActive) }

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .onAppear { reconcile() }
            .onChange(of: tour.phase) { reconcile() }
            .onChange(of: tour.step) { reconcile(); watch() }
            .onChange(of: tour.sheet) { reconcile() }
            .onChange(of: tour.inSession) { reconcile() }
            .onChange(of: active?.setList.count) { watch() }
            .onChange(of: active?.uuid) { watch() }
    }

    /// Ogni passo che apre un foglio ha il suo: si apre e si passa a quello, si chiude senza finire e si torna al passo di prima.
    private func reconcile() {
        guard tour.phase == .marks else { return }
        switch tour.step {
        case .tabs:
            break
        case .start, .pick:
            if nav.tab != .workouts { nav.tab = .workouts }
            if tour.inSession { tour.step = .set }
            else if tour.sheet == .days { tour.step = .pick }
            else if tour.step == .pick {
                // il giorno scelto: la sessione sta per aprirsi, il passo la aspetta; se non arriva si torna a «Inizia da qui»
                if active == nil { tour.step = .start }
                else { Task { try? await Task.sleep(for: .milliseconds(1500)); if tour.step == .pick, tour.sheet == nil, !tour.inSession { tour.step = .start } } }
            }
        case .set, .load, .save:
            if tour.sheet == .save { if tour.step == .set { tour.step = .load } }
            else if tour.step != .set { tour.step = .set }
            else if !tour.inSession { tour.step = .start }
        }
    }

    /// L'ultimo passo si chiude da solo quando nell'allenamento compare una serie in più.
    private func watch() {
        guard tour.phase == .marks, [.set, .load, .save].contains(tour.step), let active else { base = -1; return }
        let n = active.setList.count
        if base < 0 || n < base { base = n }
        else if n > base {
            base = -1
            tour.finish(settings)
            Haptics.success()
            tour.phase = .cheer
            Task {
                try? await Task.sleep(for: .milliseconds(3600))
                if tour.phase == .cheer { tour.end() }
            }
        }
    }
}

// MARK: - Il riquadro e la carta

/// Il buco nel velo: tutto lo schermo meno il riquadro, che si sposta (e si anima) da un controllo all'altro.
private struct HoleShape: Shape {
    var hole: CGRect
    var radius: CGFloat = 16

    var animatableData: AnimatablePair<AnimatablePair<CGFloat, CGFloat>, AnimatablePair<CGFloat, CGFloat>> {
        get { .init(.init(hole.minX, hole.minY), .init(hole.width, hole.height)) }
        set { hole = CGRect(x: newValue.first.first, y: newValue.first.second, width: newValue.second.first, height: newValue.second.second) }
    }

    func path(in rect: CGRect) -> Path {
        var p = Path(rect)
        p.addRoundedRect(in: hole, cornerSize: CGSize(width: radius, height: radius), style: .continuous)
        return p
    }
}

/// Il riquadro luminoso con la carta del passo. `target` è il controllo (nelle coordinate di `size`); senza, c'è solo la carta in basso.
/// I passi che vivono dentro un foglio (`up`) non bloccano i tocchi: il resto del foglio resta leggibile e toccabile.
struct TourCoach: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let step: TourState.Step
    let target: CGRect?
    let size: CGSize
    var insets = EdgeInsets()
    /// Il riquadro vola da un controllo all'altro (solo nel cambio di passo, non mentre la pagina scorre).
    var fly = false

    private let tour = TourState.shared
    private var up: Bool { step.up }

    private var hole: CGRect? {
        guard let r = target, r.width > 0 else { return nil }
        let x = max(3, r.minX - 6), y = max(3, r.minY - 6)
        return CGRect(x: x, y: y, width: max(0, min(size.width - 3, r.maxX + 6) - x), height: max(0, min(size.height - 3, r.maxY + 6) - y))
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            if let hole {
                let dim = Color(red: 4 / 255, green: 7 / 255, blue: 14 / 255).opacity(up ? 0.55 : 0.78)
                HoleShape(hole: hole)
                    .fill(dim, style: FillStyle(eoFill: true))
                    .allowsHitTesting(false)
                if !up { blockers(hole) }
                ring(hole)
            }
            if !up || hole != nil {
                card(hole)
                    .animation(reduceMotion ? nil : .easeOut(duration: 0.35), value: step)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .animation(fly && !reduceMotion ? .timingCurve(0.23, 1, 0.32, 1, duration: 0.45) : nil, value: hole)
    }

    /// Quattro fasce attorno al buco: bloccano i tocchi fuori, e dentro il buco arrivano al controllo vero.
    private func blockers(_ h: CGRect) -> some View {
        func band(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ hh: CGFloat) -> some View {
            Color.black.opacity(0.001)
                .frame(width: max(0, w), height: max(0, hh))
                .contentShape(Rectangle())
                .position(x: x + max(0, w) / 2, y: y + max(0, hh) / 2)
        }
        return ZStack(alignment: .topLeading) {
            band(0, 0, size.width, h.minY)
            band(0, h.maxY, size.width, size.height - h.maxY)
            band(0, h.minY, h.minX, h.height)
            band(h.maxX, h.minY, size.width - h.maxX, h.height)
        }
    }

    private func ring(_ hole: CGRect) -> some View {
        let accent = settings.accentColor
        return TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { ctx in
            let p = reduceMotion ? 1 : Curve.bezier((0, 0, 0.58, 1), (ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.8)) / 1.8)
            ZStack {
                RoundedRectangle(cornerRadius: 16 + 8 * p, style: .continuous)
                    .strokeBorder(accent.opacity(0.55 * (1 - p)), lineWidth: 16 * p)
                    .frame(width: hole.width + 16 * p, height: hole.height + 16 * p)
                    .opacity(reduceMotion ? 0 : 1)
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(accent, lineWidth: 2)
                    .frame(width: hole.width, height: hole.height)
                    .shadow(color: accent.opacity(0.45), radius: 12)
            }
            .position(x: hole.midX, y: hole.midY)
        }
        .allowsHitTesting(false)
    }

    /// Fuori dal buco: sotto se c'è posto, sopra altrimenti, centrata sul controllo; senza controllo in basso, senza velo.
    @ViewBuilder
    private func card(_ hole: CGRect?) -> some View {
        let w = min(380, size.width - 32)
        let top = insets.top + 8, bottom = size.height - insets.bottom - 8
        if let hole {
            let left = max(16, min(hole.midX - w / 2, size.width - 16 - w))
            let below = hole.maxY + 14
            let roomBelow = bottom - below, roomAbove = hole.minY - 14 - top
            let under = roomBelow >= 170 || (roomAbove < 170 && roomBelow >= roomAbove)
            // poco posto (SE, foglio basso): la carta lascia la riga di spiegazione e si stringe, così sta comunque fuori dal riquadro
            let room = max(80, min(under ? roomBelow : roomAbove, size.height - 32))
            VStack(spacing: 0) {
                if under {
                    Color.clear.frame(height: below)
                    placed(left: left, width: w, tight: room < 150)
                    Spacer(minLength: 0)
                } else {
                    Spacer(minLength: 0)
                    placed(left: left, width: w, tight: room < 150)
                    Color.clear.frame(height: size.height - hole.minY + 14)
                }
            }
            .frame(width: size.width, height: size.height)
        } else {
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                placed(left: (size.width - w) / 2, width: w, tight: false)
                Color.clear.frame(height: insets.bottom + 24)
            }
            .frame(width: size.width, height: size.height)
        }
    }

    private func placed(left: CGFloat, width: CGFloat, tight: Bool) -> some View {
        HStack(spacing: 0) {
            Color.clear.frame(width: left, height: 0)
            cardBody(width: width, tight: tight)
            Spacer(minLength: 0)
        }
    }

    private func cardBody(width: CGFloat, tight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(tour.title(step))
                .font(Theme.rounded(tight ? 16 : 18, .bold))
                .foregroundStyle(Theme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            if let text = tour.text(step), !tight {
                Text(text)
                    .font(Theme.rounded(14, .medium))
                    .foregroundStyle(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4).padding(.bottom, 12)
            }
            HStack {
                Button("Salta") { tour.skip(settings) }
                    .font(Theme.rounded(15, .semibold))
                    .foregroundStyle(Theme.textSecondary)
                    .frame(minHeight: 44)
                    .padding(.leading, -8).padding(.horizontal, 8)
                Spacer(minLength: 8)
                if let next = step.next {
                    Button {
                        Haptics.tap()
                        tour.step = next
                    } label: {
                        HStack(spacing: 4) { Text("Avanti"); Image(systemName: "chevron.right").font(.system(size: 13, weight: .bold)) }
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor, fullWidth: false))
                    .fixedSize()
                } else if !up {
                    Text("Fallo per continuare")
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            .padding(.top, tight || tour.text(step) == nil ? 6 : 0)
        }
        .padding(16)
        .frame(width: width, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(hex: 0x151A28)))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(settings.accentColor.opacity(0.4), lineWidth: 1))
        .shadow(color: .black.opacity(0.6), radius: 24, y: 12)
        .id(step)
        .transition(.opacity.combined(with: .offset(y: reduceMotion ? 0 : 8)))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(tour.title(step))
    }
}

/// I passi che stanno dentro un foglio o la sessione.
private struct TourMarks: View {
    let steps: [TourState.Step]
    let rects: [String: CGRect]
    let size: CGSize
    let insets: EdgeInsets

    private let tour = TourState.shared

    var body: some View {
        if tour.phase == .marks, steps.contains(tour.step) {
            let target = tour.step.targets.lazy.compactMap { rects[$0] }.first
            // un passo dentro un foglio senza il suo controllo (non c'è ancora) non mostra niente
            if !(tour.step.up && target == nil) {
                TourCoach(step: tour.step, target: target, size: size, insets: insets)
            }
        }
    }
}

// MARK: - Il giro sulle schermate vere

/// Le schede e «Avvia allenamento», sopra la barra delle schede; gli altri passi stanno nei loro fogli (`tourMarks`).
struct TourOverlay: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// I controlli segnati con `tourTarget`, già in coordinate dello schermo.
    let rects: [String: CGRect]
    let size: CGSize

    private let tour = TourState.shared
    @State private var tick = 0
    @State private var fly = false

    private var window: UIWindow? {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first?.keyWindow
    }

    private var insets: EdgeInsets {
        let i = window?.safeAreaInsets ?? .zero
        return EdgeInsets(top: i.top, leading: i.left, bottom: i.bottom, trailing: i.right)
    }

    var body: some View {
        Group {
            if tour.phase == .marks, tour.step == .tabs || tour.step == .start {
                TourCoach(step: tour.step, target: tour.step == .tabs ? tabBar : rects["start"], size: size, insets: insets, fly: fly)
            } else if tour.phase == .cheer {
                TourCheer(size: size, top: insets.top)
            }
        }
        .task(id: tour.step) {
            fly = true
            try? await Task.sleep(for: .milliseconds(520))
            fly = false
            // la barra è fatta di pulsanti veri e non passa i suoi riquadri: si rilegge finché è il passo
            while tour.step == .tabs, !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                tick += 1
            }
        }
    }

    /// Dove sta davvero la barra delle schede (su iOS 26 è una capsula flottante): l'unione dei pulsanti veri.
    private var tabBar: CGRect? {
        _ = tick
        guard let window else { return nil }
        var buttons: [CGRect] = []
        func walk(_ view: UIView) {
            if String(describing: type(of: view)).contains("TabBarButton") { buttons.append(view.convert(view.bounds, to: nil)) } else { view.subviews.forEach(walk) }
        }
        walk(window)
        if let first = buttons.first {
            let r = buttons.dropFirst().reduce(first) { $0.union($1) }
            return r.insetBy(dx: -4, dy: -2)
        }
        return CGRect(x: 8, y: size.height - insets.bottom - 70, width: size.width - 16, height: 62)
    }
}

/// La prima serie fatta, in alto.
private struct TourCheer: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let size: CGSize
    let top: CGFloat
    @State private var drawn = false

    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                Circle().fill(Theme.positive.opacity(0.16))
                Circle().strokeBorder(Theme.positive, lineWidth: 2.5)
                Image(systemName: "checkmark")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundStyle(Theme.positive)
                    .scaleEffect(drawn || reduceMotion ? 1 : 0.4)
                    .opacity(drawn || reduceMotion ? 1 : 0)
            }
            .frame(width: 48, height: 48)
            .padding(.bottom, 4)
            Text("Prima serie fatta").font(Theme.rounded(18, .bold)).foregroundStyle(Theme.textPrimary)
            Text("Ora sai come si fa.")
                .font(Theme.rounded(14, .medium)).foregroundStyle(Theme.textSecondary)
                .padding(.bottom, 10)
            Button { Haptics.tap(); TourState.shared.end() } label: { Text("Fine") }
                .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
        }
        .padding(16)
        .frame(maxWidth: 380)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(hex: 0x151A28)))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(settings.accentColor.opacity(0.4), lineWidth: 1))
        .shadow(color: .black.opacity(0.6), radius: 24, y: 12)
        .padding(.horizontal, 16)
        .padding(.top, top + 16)
        .frame(width: size.width, height: size.height, alignment: .top)
        .transition(.opacity)
        .onAppear { withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.5).delay(0.2)) { drawn = true } }
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Suggerimenti del primo macchinario

extension View {
    /// Un suggerimento sul controllo `target` (segnato con `tourTarget`), dentro la schermata stessa: il tutorial principale sta
    /// sotto il foglio, quindi i due non si sovrappongono mai. A differenza del tutorial non blocca i tocchi: solo l'anello
    /// attorno al controllo e un riquadro con «Ok» (passo dopo passo) o «Fine» (ultimo), più «Salta» per spegnerli per sempre.
    func machineTip(_ target: String, show: Bool, title: String, text: String, last: Bool = false, onNext: @escaping () -> Void) -> some View {
        overlayPreferenceValue(TourTargetKey.self) { anchors in
            GeometryReader { geo in
                if show, let anchor = anchors[target] {
                    MachineTipCard(hole: geo[anchor].insetBy(dx: -8, dy: -8), size: geo.size, title: title, text: text, last: last, onNext: onNext)
                }
            }
        }
    }
}

private struct MachineTipCard: View {
    @Environment(AppSettings.self) private var settings
    let hole: CGRect
    let size: CGSize
    let title: String
    let text: String
    let last: Bool
    let onNext: () -> Void

    var body: some View {
        let below = hole.midY < size.height / 2
        ZStack(alignment: below ? .top : .bottom) {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(settings.accentColor, lineWidth: 2)
                .shadow(color: settings.accentColor.opacity(0.6), radius: 14)
                .frame(width: hole.width, height: hole.height)
                .position(x: hole.midX, y: hole.midY)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .allowsHitTesting(false)
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(Theme.rounded(17, .bold))
                Text(text).font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                HStack {
                    Button("Salta") { settings.machineTipsSeen = true }
                        .font(Theme.rounded(13, .bold)).foregroundStyle(settings.accentColor)
                    Spacer()
                    Button { if last { settings.machineTipsSeen = true }; onNext() } label: { Text(last ? "Fine" : "Ok").padding(.horizontal, 18) }
                        .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor, fullWidth: false))
                }
                .padding(.top, 4)
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color(hex: 0x151A28)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(settings.accentColor.opacity(0.4), lineWidth: 1))
            .shadow(color: .black.opacity(0.5), radius: 20, y: 8)
            .padding(.horizontal, 16)
            .frame(maxWidth: 440)
            .padding(below ? .top : .bottom, below ? hole.maxY + 12 : size.height - hole.minY + 12)
        }
        .frame(width: size.width, height: size.height)
    }
}
