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
}

/// Il tutorial sulle schermate vere: un riquadro luminoso su un controllo alla volta. I passi «da fare» (aggiungi un macchinario,
/// crea una scheda) aspettano che lo fai davvero e si saltano da soli se l'hai già fatto.
struct TourOverlay: View {
    @Environment(AppSettings.self) private var settings
    @Query private var exercises: [Exercise]
    @Query private var templates: [WorkoutTemplate]

    private let nav = AppNav.shared
    /// I controlli segnati con `tourTarget`, già in coordinate dello schermo.
    let rects: [String: CGRect]
    let size: CGSize

    private struct Step {
        let id: String
        let tab: AppNav.Tab
        /// Un controllo segnato con `tourTarget`, oppure la posizione di una scheda della barra in basso.
        let target: String?
        let tabIndex: Int?
        let title: String
        let text: String
        var done: ((TourOverlay) -> Bool)? = nil
        var optional = false
        /// Dal giro numero N in poi: chi ha già finito il tutorial vede solo i passi più nuovi di quello che ha visto.
        var since = 1
    }

    private var steps: [Step] { settings.tutorialDone ? allSteps.filter { $0.since > settings.tourSeen } : allSteps }

    private let allSteps: [Step] = [
        Step(id: "welcome", tab: .home, target: nil, tabIndex: nil, title: "Benvenuto in Peak",
             text: "Un minuto per fare il giro: ti mostro dove sta ogni cosa e ti faccio creare il primo macchinario e la prima scheda."),
        Step(id: "home", tab: .home, target: nil, tabIndex: 0, title: "La tua Home",
             text: "Qui trovi la tua settimana, i grafici, le medaglie e le novità dei tuoi amici. La barra sotto la frase è il tuo obiettivo."),
        Step(id: "medals", tab: .home, target: nil, tabIndex: 0, title: "Medaglie e obiettivo",
             text: "Le medaglie hanno tre livelli e ti dicono cosa manca per il prossimo. L'obiettivo è quanti allenamenti ti dai a settimana.", since: 2),
        Step(id: "settings", tab: .home, target: "avatar", tabIndex: nil, title: "Impostazioni",
             text: "Toccando la tua iniziale in alto a destra apri le impostazioni: unità, colore, timer di recupero, backup e il tuo account yG. Il tutorial lo rivedi da Generale."),
        Step(id: "workout", tab: .workouts, target: nil, tabIndex: 1, title: "Allenamento",
             text: "Qui alleni: scegli una scheda o parti a mano e registra le serie una per una."),
        Step(id: "template", tab: .workouts, target: "new-template", tabIndex: nil, title: "Crea la tua prima scheda",
             text: "Una scheda è una lista di esercizi con serie e ripetizioni: la scegli quando inizi e li trovi già pronti.", done: { $0.hasTemplate }),
        Step(id: "progress", tab: .progress, target: nil, tabIndex: 2, title: "Progressi",
             text: "I tuoi numeri nel tempo: volume, record, peso corporeo."),
        Step(id: "machines", tab: .machines, target: nil, tabIndex: 3, title: "Macchinari",
             text: "Qui tieni i tuoi macchinari e i loro massimali. Si aggiungono cercando in un catalogo: così il tuo «Chest Press» è lo stesso dei tuoi amici e potete fare la classifica."),
        Step(id: "add-machine", tab: .machines, target: "add-machine", tabIndex: nil, title: "Aggiungi il primo macchinario",
             text: "Tocca + e cerca nel catalogo. Poi gli puoi dare un nome tuo.", done: { $0.hasMachine }),
        Step(id: "figures", tab: .machines, target: nil, tabIndex: 3, title: "Figure dei muscoli",
             text: "Ogni macchinario mostra i muscoli che lavorano: la figura si ritaglia sulla zona. Nel riepilogo di una seduta vedi tutti i muscoli lavorati.", since: 2),
        Step(id: "friends", tab: .friends, target: nil, tabIndex: 4, title: "Amici",
             text: "Invita gli amici con un link o un QR: vedete i massimali e le schede l'uno dell'altro e fate la classifica."),
        Step(id: "invite", tab: .friends, target: "invite", tabIndex: nil, title: "Invita un amico",
             text: "Quando vuoi. Il link vale una volta sola.", optional: true)
    ]

    @State private var at = 0

    private var hasMachine: Bool { exercises.contains { !$0.isArchived } }
    private var hasTemplate: Bool { !templates.isEmpty }
    private var step: Step { steps[min(at, steps.count - 1)] }
    private var isLast: Bool { at == steps.count - 1 }

    private var safeTop: CGFloat {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first?.keyWindow?.safeAreaInsets.top ?? 0
    }

    private var bottomInset: CGFloat {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first?.keyWindow?.safeAreaInsets.bottom ?? 0
    }

    /// Il riquadro da evidenziare, in coordinate dello schermo.
    private var hole: CGRect? {
        if let id = step.target, let rect = rects[id] { return rect }
        // il «+» sta nella barra del titolo, che non sempre passa il suo riquadro: in alto a destra, sotto la barra di stato
        if step.target == "add-machine" { return CGRect(x: size.width - 68, y: safeTop, width: 52, height: 44) }
        if let index = step.tabIndex {
            return tabButtonFrame(index)
        }
        return nil
    }

    /// Dove sta davvero la scheda `index` nella barra (che su iOS 26 è una capsula flottante): si legge dai pulsanti veri.
    private func tabButtonFrame(_ index: Int) -> CGRect? {
        guard let window = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first?.keyWindow else { return nil }
        var buttons: [UIView] = []
        func walk(_ view: UIView) {
            if String(describing: type(of: view)).contains("TabBarButton") { buttons.append(view) } else { view.subviews.forEach(walk) }
        }
        walk(window)
        let sorted = buttons.sorted { $0.convert($0.bounds, to: nil).minX < $1.convert($1.bounds, to: nil).minX }
        guard sorted.indices.contains(index) else { return nil }
        return sorted[index].convert(sorted[index].bounds, to: nil)
    }

    var body: some View {
        let h = hole.map { $0.insetBy(dx: -8, dy: -8) }
        ZStack(alignment: .topLeading) {
            veil(h)
            if let h {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(settings.accentColor, lineWidth: 2)
                    .shadow(color: settings.accentColor.opacity(0.6), radius: 14)
                    .frame(width: h.width, height: h.height)
                    .position(x: h.midX, y: h.midY)
                    .allowsHitTesting(false)
            }
            card(h)
        }
        .onAppear { go() }
        .onChange(of: at) { go() }
        .onChange(of: hasMachine) { advanceIfDone() }
        .onChange(of: hasTemplate) { advanceIfDone() }
    }

    // MARK: - Pezzi

    /// Quattro fasce attorno al buco: bloccano i tocchi fuori, e dentro il buco arrivano al controllo vero.
    @ViewBuilder
    private func veil(_ hole: CGRect?) -> some View {
        let dim = Color(red: 0.016, green: 0.027, blue: 0.055).opacity(0.78)
        if let hole {
            let top = max(0, hole.minY), bottom = min(size.height, hole.maxY)
            let left = max(0, hole.minX), right = min(size.width, hole.maxX)
            ZStack(alignment: .topLeading) {
                dim.frame(width: size.width, height: top).position(x: size.width / 2, y: top / 2)
                dim.frame(width: size.width, height: max(0, size.height - bottom)).position(x: size.width / 2, y: bottom + max(0, size.height - bottom) / 2)
                dim.frame(width: left, height: max(0, bottom - top)).position(x: left / 2, y: top + max(0, bottom - top) / 2)
                dim.frame(width: max(0, size.width - right), height: max(0, bottom - top)).position(x: right + max(0, size.width - right) / 2, y: top + max(0, bottom - top) / 2)
            }
            .contentShape(Rectangle())
        } else {
            dim.frame(width: size.width, height: size.height).contentShape(Rectangle())
        }
    }

    private func card(_ hole: CGRect?) -> some View {
        let showBelow = hole.map { size.height - $0.maxY > 230 } ?? true
        return VStack(alignment: .leading, spacing: 6) {
            Text("\(at + 1) / \(steps.count)").font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
            Text(step.title).font(Theme.rounded(20, .bold))
            Text(step.text).font(Theme.rounded(14, .medium)).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
            HStack {
                Button("Salta il tutorial") { finish() }.font(Theme.rounded(14, .bold)).foregroundStyle(settings.accentColor)
                Spacer()
                if step.done == nil || step.optional {
                    Button { advance() } label: { Text(isLast ? "Fine" : "Avanti").padding(.horizontal, 22) }.buttonStyle(PrimaryButtonStyle(tint: settings.accentColor)).fixedSize()
                } else {
                    Text("Fallo per continuare").font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textTertiary)
                }
            }
            .padding(.top, 8)
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(hex: 0x151A28)))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(settings.accentColor.opacity(0.4), lineWidth: 1))
        .shadow(color: .black.opacity(0.5), radius: 24, y: 10)
        .padding(.horizontal, 16)
        .frame(maxWidth: 440)
        .frame(maxWidth: .infinity)
        .position(x: size.width / 2, y: cardY(hole, below: showBelow))
    }

    private func cardY(_ hole: CGRect?, below: Bool) -> CGFloat {
        guard let hole else { return size.height / 2 }
        return below ? hole.maxY + 14 + 100 : hole.minY - 14 - 100
    }

    // MARK: - Andamento

    private func go() { nav.tab = step.tab }

    private func advance() {
        var n = at + 1
        while n < steps.count, steps[n].done?(self) == true { n += 1 } // chi l'ha già fatto salta il passo
        if n >= steps.count { finish() } else { at = n }
    }

    private func advanceIfDone() {
        guard step.done?(self) == true else { return }
        let id = step.id
        Task {
            try? await Task.sleep(for: .milliseconds(700))
            if step.id == id { advance() }
        }
    }

    private func finish() {
        settings.tutorialDone = true
        settings.tourSeen = 2
        Haptics.success()
    }
}
