import SwiftUI
import SwiftData

/// Il primo avvio di un'app vuota, a tutto schermo e senza scorciatoie: benvenuto, quanti giorni, quali gruppi, quali macchinari,
/// scheda pronta. Come `Onboarding.svelte`. I dati si creano entrando nell'ultima schermata (e si tolgono se torni indietro);
/// finisce con `onFinish`, che fa partire il tutorial.
struct OnboardingView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var onFinish: () -> Void

    private let last = 4
    @State private var step = 0
    /// 1 = avanti, -1 = indietro: da che parte scorre lo schermo.
    @State private var dir: CGFloat = 1
    /// La montagna si disegna una volta sola; tornando al benvenuto è già finita.
    @State private var heroSeen = false
    /// 0...3: il titolo, la riga e il pulsante entrano dopo la salita (2,5 s), uno dopo l'altro.
    @State private var late = 0
    @State private var busy = false

    @State private var goal = 0
    @State private var groups: Set<MuscleGroup> = []
    /// I macchinari scelti fra i suggeriti (id del catalogo) e quelli aggiunti dalla ricerca.
    @State private var on: Set<String> = []
    @State private var extras: [CatalogEntry] = []
    @State private var seeded: Set<MuscleGroup> = []
    @State private var searching = false

    @State private var plan: Onboarding.Made?
    @State private var made: [(group: MuscleGroup, name: String, count: Int)] = []
    @State private var composed = false
    @State private var pending: Task<Void, Never>?
    @State private var connecting = false

    private var picked: [MuscleGroup] { MuscleGroup.allCases.filter(groups.contains) }
    private func chosen(_ g: MuscleGroup) -> [CatalogEntry] { (Onboarding.suggested[g] ?? []).filter(on.contains).compactMap { Catalog.entry(id: $0) } }
    private func extras(of g: MuscleGroup) -> [CatalogEntry] { extras.filter { $0.muscleGroup == g } }
    private var total: Int { picked.reduce(0) { $0 + chosen($1).count + extras(of: $1).count } }

    var body: some View {
        GeometryReader { geo in
            let colW = min(geo.size.width - 48, 460)
            Group {
                if connecting {
                    ConnectFlowView(firstRun: true, onFinish: connected, onCancel: { connecting = false })
                } else {
                    VStack(spacing: 0) {
                        header
                        ZStack {
                            pane(geo: geo, colW: colW)
                                .id(step)
                                .transition(slide)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                    }
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .background(Theme.background(accent: settings.accentColor))
        .sheet(isPresented: $searching) {
            CatalogPickerSheet(title: "Cerca nel catalogo", allowsCustom: false) { entry, _ in
                if let entry { add(entry) }
            }
        }
        .task {
            // il benvenuto: titolo, riga e pulsante arrivano a salita finita
            guard !reduceMotion else { late = 3; return }
            try? await Task.sleep(for: .milliseconds(2500))
            late = 1
            try? await Task.sleep(for: .milliseconds(150))
            late = 2
            try? await Task.sleep(for: .milliseconds(200))
            late = 3
        }
        .onDisappear { pending?.cancel() }
    }

    // MARK: - Intestazione e passaggi

    private var header: some View {
        HStack(spacing: 12) {
            Button { back() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Theme.textSecondary)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Indietro")
            .padding(.leading, -10)

            Capsule().fill(Color.white.opacity(0.12)).frame(height: 4)
                .overlay(alignment: .leading) {
                    GeometryReader { g in
                        Capsule().fill(settings.accentColor)
                            .frame(width: g.size.width * CGFloat(step) / CGFloat(last))
                            .animation(reduceMotion ? nil : .timingCurve(0.23, 1, 0.32, 1, duration: 0.35), value: step)
                    }
                }
                .accessibilityElement()
                .accessibilityLabel("Passo \(step) di \(last)")
            Color.clear.frame(width: 34, height: 1)
        }
        .padding(.horizontal, 24)
        .frame(height: 56)
        .opacity(step == 0 ? 0 : 1)
        .allowsHitTesting(step != 0)
    }

    /// Scorre di poco e sfuma (350 ms); con «riduci movimento» solo una dissolvenza breve.
    private var slide: AnyTransition {
        reduceMotion ? .opacity : .asymmetric(
            insertion: .modifier(active: Shift(x: 36 * dir, o: 0), identity: Shift(x: 0, o: 1)),
            removal: .modifier(active: Shift(x: -36 * dir, o: 0), identity: Shift(x: 0, o: 1))
        )
    }

    private struct Shift: ViewModifier {
        let x: CGFloat
        let o: Double
        func body(content: Content) -> some View { content.offset(x: x).opacity(o) }
    }

    private func go(_ to: Int) {
        guard !busy else { return }
        busy = true
        if step == 0 { heroSeen = true; late = 3 }
        dir = to > step ? 1 : -1
        // la direzione deve essere già nel disegno quando parte il cambio, così anche lo schermo che esce la conosce
        Task { @MainActor in
            withAnimation(reduceMotion ? .easeOut(duration: 0.16) : .timingCurve(0.23, 1, 0.32, 1, duration: 0.35)) { step = to }
            try? await Task.sleep(for: .milliseconds(reduceMotion ? 170 : 360))
            busy = false
        }
    }

    private func back() {
        pending?.cancel()
        if step == last, let plan {
            Onboarding.undoPlan(plan, context: context, settings: settings)
            self.plan = nil
            composed = false
        }
        Haptics.tap()
        go(step - 1)
    }

    /// Il collegamento a yG, se ce n'è già uno: con dati sul server il primo avvio è finito, altrimenti si parte da zero.
    private func connected() {
        connecting = false
        if Onboarding.hasData(context) { onFinish() } else { goal = settings.weeklyGoal; go(1) }
    }

    // MARK: - Gli schermi

    @ViewBuilder
    private func pane(geo: GeometryProxy, colW: CGFloat) -> some View {
        switch step {
        case 0: welcome(geo: geo, colW: colW)
        case 1: days(geo: geo, colW: colW)
        case 2: groupsStep(geo: geo, colW: colW)
        case 3: machines(geo: geo, colW: colW)
        default: ready(geo: geo, colW: colW)
        }
    }

    /// Il corpo scorre se non ci sta (SE, testi grandi); `centered` lo mette a metà altezza come `ob-mid`.
    private func scrolling<C: View>(geo: GeometryProxy, colW: CGFloat, centered: Bool = true, hero: Bool = false, @ViewBuilder _ content: () -> C) -> some View {
        ScrollView {
            content()
                .frame(maxWidth: hero ? min(geo.size.width - 48, 640) : colW, alignment: .leading)
                .frame(maxWidth: .infinity, minHeight: max(0, geo.size.height - 56 - 100), alignment: centered ? .center : .top)
                .padding(.top, 12).padding(.bottom, 16)
                .padding(.horizontal, 24)
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    private func foot(_ title: String, enabled: Bool = true, colW: CGFloat, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).frame(minHeight: 24)
        }
        .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
        .frame(maxWidth: colW)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 24)
        .padding(.top, 12).padding(.bottom, 8)
    }

    private func title(_ text: String, _ width: CGFloat) -> some View {
        let size = min(34, max(26, width * 0.074))
        return Text(text)
            .font(.system(size: size, weight: .black, design: .rounded))
            .tracking(-0.03 * size)
            .lineSpacing(0)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }

    private func sub(_ text: String) -> some View {
        Text(text)
            .font(Theme.rounded(16, .regular))
            .foregroundStyle(Theme.textSecondary)
            .padding(.top, 8)
    }

    // 1. il benvenuto: la montagna che si disegna
    private func welcome(geo: GeometryProxy, colW: CGFloat) -> some View {
        let word = min(60, max(44, geo.size.width * 0.13))
        return VStack(spacing: 0) {
            scrolling(geo: geo, colW: colW, hero: true) {
                VStack(spacing: 20) {
                    SummitDrawView(still: heroSeen)
                        .frame(maxWidth: 640)
                        .frame(maxHeight: min(geo.size.height * 0.5, 460))
                    VStack(spacing: 10) {
                        Text("Peak")
                            .font(.system(size: word, weight: .black, design: .rounded))
                            .tracking(-0.04 * word)
                            .foregroundStyle(Theme.textPrimary)
                            .modifier(Rise(on: late >= 1, reduceMotion: reduceMotion))
                        Text("Ogni allenamento, un po' più in alto.")
                            .font(Theme.rounded(18, .regular))
                            .foregroundStyle(Theme.textSecondary)
                            .multilineTextAlignment(.center)
                            .modifier(Rise(on: late >= 2, reduceMotion: reduceMotion))
                    }
                    .padding(.top, 4)
                }
                .frame(maxWidth: .infinity)
            }
            VStack(spacing: 6) {
                foot("Iniziamo", colW: colW) { Haptics.tap(); go(1) }
                    .modifier(Rise(on: late >= 3, reduceMotion: reduceMotion))
                // chi ha già un account yG lo ritrova qui: niente scelte sui dati, si prende quello che c'è sul server
                Button("Ho già un account yG") { connecting = true }
                    .font(Theme.rounded(14, .semibold))
                    .foregroundStyle(Theme.textTertiary)
                    .frame(minHeight: 36)
                    .opacity(late >= 3 ? 1 : 0)
                    .animation(reduceMotion ? nil : .easeOut(duration: 0.4), value: late)
            }
            .padding(.bottom, 4)
        }
    }

    // 2. i giorni: si tocca un numero e dopo un attimo si va avanti
    private func days(geo: GeometryProxy, colW: CGFloat) -> some View {
        let side = (colW - 30) / 4
        return scrolling(geo: geo, colW: colW) {
            VStack(alignment: .leading, spacing: 0) {
                title("Quante volte ti alleni?", geo.size.width)
                sub("a settimana")
                VStack(spacing: 10) {
                    HStack(spacing: 10) { ForEach(1...4, id: \.self) { numberTile($0, side: side) } }
                    HStack(spacing: 10) { ForEach(5...7, id: \.self) { numberTile($0, side: side) } }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 28)
            }
        }
    }

    private func numberTile(_ n: Int, side: CGFloat) -> some View {
        let isOn = goal == n
        return Button {
            goal = n
            settings.weeklyGoal = n
            SoundFX.playDay(n - 1)
            Haptics.tap()
            pending?.cancel()
            pending = Task {
                try? await Task.sleep(for: .milliseconds(250))
                guard !Task.isCancelled else { return }
                go(2)
            }
        } label: {
            Text("\(n)")
                .font(.system(size: 34, weight: .black, design: .rounded)).monospacedDigit()
                .foregroundStyle(isOn ? Theme.bg : Theme.textPrimary)
                .frame(width: side, height: side)
                .background(isOn ? settings.accentColor : Theme.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(isOn ? settings.accentColor : Theme.strokeStrong, lineWidth: 2))
                .shadow(color: isOn ? settings.accentColor.opacity(0.35) : .clear, radius: 14)
        }
        .buttonStyle(Press(scale: 0.93))
        .animation(reduceMotion ? nil : .spring(duration: 0.3, bounce: 0.4), value: isOn)
        .accessibilityLabel("\(n)")
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    // 3. i gruppi
    private func groupsStep(geo: GeometryProxy, colW: CGFloat) -> some View {
        VStack(spacing: 0) {
            scrolling(geo: geo, colW: colW) {
                VStack(alignment: .leading, spacing: 0) {
                    title("Cosa alleni?", geo.size.width)
                    sub("Un giorno per ogni gruppo.")
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                        ForEach(MuscleGroup.allCases) { g in groupTile(g) }
                    }
                    .padding(.top, 24)
                }
            }
            foot("Continua", enabled: !groups.isEmpty, colW: colW) { toMachines() }
        }
    }

    private func groupTile(_ g: MuscleGroup) -> some View {
        let isOn = groups.contains(g)
        return Button {
            if isOn { groups.remove(g) } else { groups.insert(g) }
            Haptics.tap()
        } label: {
            VStack(spacing: 8) {
                TemplateIconView(icon: "muscle:\(g.rawValue)", name: "", size: 30)
                    .frame(height: 36)
                Text(g.title)
                    .font(Theme.rounded(14, .bold))
                    .foregroundStyle(Theme.textPrimary)
                    .lineLimit(1).minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, minHeight: 104)
            .padding(.horizontal, 6).padding(.vertical, 10)
            .background(isOn ? g.tint.opacity(0.16) : Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(isOn ? g.tint : Theme.strokeStrong, lineWidth: 2))
            .overlay(alignment: .topTrailing) { check(isOn, g.tint).padding(8) }
        }
        .buttonStyle(Press(scale: 0.96))
        .animation(reduceMotion ? nil : .snappy(duration: 0.2), value: isOn)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private func check(_ isOn: Bool, _ tint: Color) -> some View {
        ZStack {
            Circle().strokeBorder(Theme.strokeStrong, lineWidth: 1.5).opacity(isOn ? 0 : 1)
            Circle().fill(tint).opacity(isOn ? 1 : 0)
            Image(systemName: "checkmark").font(.system(size: 11, weight: .heavy)).foregroundStyle(Theme.bg).opacity(isOn ? 1 : 0)
        }
        .frame(width: 20, height: 20)
        .scaleEffect(isOn ? 1 : 0.8)
    }

    /// Ogni gruppo, la prima volta, parte con i suoi primi tre già scelti.
    private func toMachines() {
        for g in picked where !seeded.contains(g) {
            seeded.insert(g)
            for id in (Onboarding.suggested[g] ?? []).prefix(Onboarding.preselected) { on.insert(id) }
        }
        go(3)
    }

    // 4. i macchinari
    private func machines(geo: GeometryProxy, colW: CGFloat) -> some View {
        VStack(spacing: 0) {
            scrolling(geo: geo, colW: colW, centered: false) {
                VStack(alignment: .leading, spacing: 0) {
                    title("I tuoi macchinari", geo.size.width)
                    sub("Tocca quelli che hai in palestra.")
                    if Catalog.entries.isEmpty {
                        Text("Il catalogo non si carica: serve la connessione.")
                            .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textPrimary)
                            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.orange.opacity(0.16), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .padding(.top, 16)
                    }
                    ForEach(picked) { g in section(g) }
                    Button { searching = true } label: { Label("Cerca nel catalogo", systemImage: "magnifyingglass") }
                        .buttonStyle(SecondaryButtonStyle())
                        .padding(.top, 22)
                }
            }
            foot("Continua", enabled: total > 0, colW: colW) { toReady() }
        }
    }

    private func section(_ g: MuscleGroup) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Circle().fill(g.tint).frame(width: 10, height: 10)
                Text(g.title.uppercased())
                    .font(Theme.rounded(13, .heavy)).tracking(1)
                    .foregroundStyle(g.tint)
                Spacer()
                Text("\(chosen(g).count + extras(of: g).count)")
                    .font(Theme.rounded(13, .heavy)).monospacedDigit()
                    .foregroundStyle(Theme.textTertiary)
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), spacing: 10)], spacing: 10) {
                ForEach(Onboarding.suggested[g] ?? [], id: \.self) { id in
                    if let e = Catalog.entry(id: id) {
                        machineTile(e, isOn: on.contains(id)) {
                            if on.contains(id) { on.remove(id) } else { on.insert(id) }
                        }
                    }
                }
                ForEach(extras(of: g)) { e in
                    machineTile(e, isOn: true) { extras.removeAll { $0.id == e.id } }
                }
            }
        }
        .padding(.top, 22)
    }

    private func machineTile(_ e: CatalogEntry, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            VStack(spacing: 8) {
                ZStack {
                    e.muscleGroup.tint.opacity(0.14)
                    MachineGlyphView(glyph: e.machineGlyph, tint: e.muscleGroup.tint, size: 56 * 0.64)
                }
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                Text(verbatim: e.name)
                    .font(Theme.rounded(13, .bold))
                    .foregroundStyle(Theme.textPrimary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, minHeight: 118)
            .padding(.horizontal, 8).padding(.top, 12).padding(.bottom, 10)
            .background(isOn ? settings.accentColor.opacity(0.1) : Theme.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(isOn ? settings.accentColor : Theme.strokeStrong, lineWidth: 2))
            .overlay(alignment: .topTrailing) { check(isOn, settings.accentColor).padding(8) }
        }
        .buttonStyle(Press(scale: 0.97))
        .animation(reduceMotion ? nil : .snappy(duration: 0.2), value: isOn)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    /// Un macchinario preso dalla ricerca finisce nel suo gruppo; se il gruppo non era scelto, si aggiunge (e parte vuoto).
    private func add(_ entry: CatalogEntry) {
        let g = entry.muscleGroup
        if !groups.contains(g) { groups.insert(g); seeded.insert(g) }
        if (Onboarding.suggested[g] ?? []).contains(entry.id) { on.insert(entry.id) }
        else if !extras.contains(where: { $0.id == entry.id }) { extras.append(entry) }
    }

    // 5. i dati: una scheda con un giorno per gruppo, nell'ordine di sempre
    private func toReady() {
        let list = picked.map { g in
            Onboarding.Day(group: g, name: g.title.tr, machines: chosen(g) + extras(of: g))
        }.filter { !$0.machines.isEmpty }
        plan = Onboarding.createPlan(name: "La mia scheda".tr, days: list, context: context, settings: settings)
        made = list.map { ($0.group, $0.name, $0.machines.count) }
        SoundFX.play(.goal)
        Haptics.success()
        go(last)
    }

    private func ready(geo: GeometryProxy, colW: CGFloat) -> some View {
        VStack(spacing: 0) {
            scrolling(geo: geo, colW: colW) {
                VStack(alignment: .leading, spacing: 0) {
                    title("Tutto pronto", geo.size.width)
                    sub("Ecco la tua scheda.")
                    VStack(spacing: 10) {
                        ForEach(Array(made.enumerated()), id: \.offset) { i, d in dayRow(d, i) }
                    }
                    .padding(.top, 24)
                }
            }
            foot("Fai il giro", colW: colW) { Haptics.tap(); onFinish() }
        }
        .onAppear { composed = true }
    }

    /// I giorni si compongono uno dopo l'altro.
    private func dayRow(_ d: (group: MuscleGroup, name: String, count: Int), _ i: Int) -> some View {
        HStack(spacing: 14) {
            TemplateIconView(icon: "muscle:\(d.group.rawValue)", name: "", size: 22)
                .frame(width: 44, height: 44)
                .background(d.group.tint.opacity(0.2), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text(verbatim: d.name).font(Theme.rounded(17, .bold)).foregroundStyle(Theme.textPrimary).lineLimit(1)
                Text(d.count == 1 ? "1 macchinario" : "\(d.count) macchinari")
                    .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textTertiary)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .frame(minHeight: 68)
        .background(d.group.tint.opacity(0.1), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(d.group.tint.opacity(0.4), lineWidth: 1))
        .opacity(composed || reduceMotion ? 1 : 0)
        .offset(y: composed || reduceMotion ? 0 : 16)
        .scaleEffect(composed || reduceMotion ? 1 : 0.96)
        .animation(reduceMotion ? nil : .timingCurve(0.23, 1, 0.32, 1, duration: 0.52).delay(0.28 + Double(i) * 0.08), value: composed)
    }
}

// MARK: - Pezzi

/// Entra dal basso (10 pt) con una dissolvenza; con «riduci movimento» compare e basta.
private struct Rise: ViewModifier {
    let on: Bool
    let reduceMotion: Bool
    func body(content: Content) -> some View {
        content
            .opacity(on ? 1 : 0)
            .offset(y: on || reduceMotion ? 0 : 10)
            .animation(reduceMotion ? nil : .timingCurve(0.23, 1, 0.32, 1, duration: 0.6), value: on)
    }
}

/// Il tasto che si fa un po' più piccolo mentre lo premi.
private struct Press: ButtonStyle {
    let scale: CGFloat
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
