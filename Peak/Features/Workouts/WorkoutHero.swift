import SwiftUI

// MARK: - Eroe: la montagna e una frase

struct WorkoutHero: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    private let nav = AppNav.shared

    let name: String
    let streak: Int
    let weekSessions: Int
    let goal: Int
    var onAvatar: () -> Void
    var onChangeGoal: () -> Void

    @State private var index = Motivation.startIndex()
    /// Quello che la scheda Allenamento mostra: parte da quanto è già stato festeggiato (`Celebrated`) e sale al valore vero se c'è una novità
    /// (un allenamento registrato dal web, arrivato con la sincronizzazione). Se non c'è niente di nuovo coincide con quello vero.
    @State private var shownWeek: Int
    @State private var shownStreak: Int
    @State private var flameTick = 0

    init(name: String, streak: Int, weekSessions: Int, goal: Int, onAvatar: @escaping () -> Void, onChangeGoal: @escaping () -> Void) {
        self.name = name
        self.streak = streak
        self.weekSessions = weekSessions
        self.goal = goal
        self.onAvatar = onAvatar
        self.onChangeGoal = onChangeGoal
        let seenWeek = Celebrated.week(start: Celebrated.weekStart())
        let seenStreak = Celebrated.streak
        _shownWeek = State(initialValue: min(weekSessions, seenWeek ?? weekSessions))
        _shownStreak = State(initialValue: min(streak, seenStreak ?? streak))
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        return hour < 12 ? "Buongiorno" : hour < 18 ? "Buon pomeriggio" : "Buonasera"
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(LinearGradient(colors: [settings.accentColor.opacity(0.10), Color(hex: 0x0A0E18).opacity(0.9)], startPoint: .top, endPoint: .bottom))
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(RadialGradient(colors: [settings.accentColor.opacity(0.35), .clear], center: .init(x: 0.76, y: 0.12), startRadius: 0, endRadius: 260))
            Mountain(accent: settings.accentColor)
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))

            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(greeting).font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary)
                        Text(name).font(Theme.rounded(19, .heavy))
                    }
                    Spacer()
                    Button(action: onAvatar) {
                        Text(String(name.prefix(1)).uppercased())
                            .font(Theme.rounded(15, .heavy))
                            .foregroundStyle(Color.black.opacity(0.8))
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(LinearGradient(colors: [settings.accentColor, settings.accentColor.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing)))
                    }
                    .buttonStyle(PressStyle())
                    .tourTarget("avatar")
                    .accessibilityLabel("Impostazioni")
                }

                Text(Motivation.phrases[index].replacingOccurrences(of: ", ", with: ",\n"))
                    .id(index)
                    .font(Theme.rounded(23, .heavy))
                    .tracking(-0.8)
                    .lineSpacing(-2)
                    .shadow(color: settings.accentColor.opacity(0.45), radius: 14)
                    // mai troncata: va a capo quanto serve e lascia libera la vetta a destra
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.trailing, 84)
                    .frame(minHeight: 66, alignment: .topLeading)
                    .padding(.top, 8)
                    .transition(.opacity.combined(with: .offset(y: 10)))

                Spacer(minLength: 8)

                goalBar

                HStack(spacing: 8) {
                    pill("\(shownStreak)", "settimane di fila", symbol: "flame.fill")
                    if weekSessions > goal { pill(nil, "+\(weekSessions - goal) oltre l'obiettivo") }
                }
                .padding(.top, 8)
            }
            .padding(14)
        }
        .frame(minHeight: 224)
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(settings.accentColor.opacity(0.3), lineWidth: 1))
        .task(id: reduceMotion) {
            guard !reduceMotion else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(7))
                guard !Task.isCancelled else { return }
                withAnimation(.easeInOut(duration: 0.6)) { index = (index + 1) % Motivation.phrases.count }
            }
        }
        // festeggia solo ciò che non è ancora stato festeggiato e solo quando la scheda Allenamento si vede (le altre schede restano vive sotto)
        .task(id: [weekSessions, streak, scenePhase == .active ? 1 : 0, nav.tab == .workouts ? 1 : 0]) { await celebrate() }
        .onChange(of: weekSessions) { catchUp() }
        .onChange(of: streak) { catchUp() }
        .onChange(of: nav.tab) { catchUp() }
    }

    /// Se i numeri sono saliti mentre la scheda Allenamento non si vedeva e il riepilogo li ha già festeggiati, la barra li segue senza festa.
    private func catchUp() {
        if (Celebrated.week(start: Celebrated.weekStart()) ?? 0) >= weekSessions { shownWeek = weekSessions }
        if (Celebrated.streak ?? 0) >= streak { shownStreak = streak }
    }

    /// Una novità non ancora vista: la barra dell'obiettivo si riempie dal vecchio al nuovo valore (una nota per segmento,
    /// più alta a ogni giorno), `Obiettivo raggiunto` se l'ha appena raggiunto, e la fiamma si scuote mentre la serie sale.
    /// Prima volta in assoluto, nessun aumento o calo: si allinea e basta. I valori nuovi si salvano subito, così non si ripete.
    @MainActor
    private func celebrate() async {
        // fuori vista non si fa e non si salva nulla: la festa tocca quando la scheda Allenamento si guarda davvero
        guard scenePhase == .active, nav.tab == .workouts else { return }
        let start = Celebrated.weekStart()
        let seenWeek = Celebrated.week(start: start)
        let seenStreak = Celebrated.streak
        Celebrated.store(week: weekSessions, start: start)
        Celebrated.streak = streak

        let weekUp = seenWeek.map { weekSessions > $0 } ?? false
        let streakUp = seenStreak.map { streak > $0 } ?? false
        guard weekUp || streakUp else {
            shownWeek = weekSessions
            shownStreak = streak
            return
        }
        do {
            if let seenWeek, weekUp {
                shownWeek = seenWeek
                try await Task.sleep(for: .seconds(0.4)) // il tempo di vedere la scheda Allenamento, poi parte
                let segments = min(weekSessions, goal) // la barra ha un segmento per allenamento, fino all'obiettivo
                if seenWeek < segments {
                    for k in seenWeek..<segments {
                        withAnimation(.spring(duration: 0.3, bounce: 0.15)) { shownWeek = k + 1 }
                        SoundFX.playDay(k)
                        Haptics.tap()
                        try await Task.sleep(for: .seconds(0.14))
                    }
                }
                withAnimation(.snappy) { shownWeek = weekSessions }
                if weekSessions >= goal, seenWeek < goal {
                    try await Task.sleep(for: .seconds(0.15))
                    SoundFX.play(.goal)
                    Haptics.success()
                    try await Task.sleep(for: .seconds(0.5))
                }
            } else {
                shownWeek = weekSessions
                try await Task.sleep(for: .seconds(0.4))
            }
            if streakUp {
                flameTick += 1
                withAnimation(.smooth(duration: 0.8)) { shownStreak = streak }
                SoundFX.play(.streak)
                Haptics.commit()
            } else {
                shownStreak = streak
            }
        } catch {
            // lasciata a metà (cambio scheda): si allinea senza festa
            shownWeek = weekSessions
            shownStreak = streak
        }
    }

    /// L'obiettivo della settimana: un segmento per allenamento.
    private var goalBar: some View {
        VStack(spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(shownWeek >= goal ? "Obiettivo raggiunto!" : "Obiettivo della settimana")
                    .font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                Spacer()
                Text("\(shownWeek) / \(goal)").font(Theme.rounded(15, .heavy)).monospacedDigit()
                    .contentTransition(.numericText(value: Double(shownWeek)))
            }
            HStack(spacing: 6) {
                ForEach(0..<goal, id: \.self) { k in
                    let filled = k < shownWeek
                    // il segmento pieno cresce da sinistra sopra quello vuoto
                    Capsule().fill(Color.white.opacity(0.14)).frame(height: 12)
                        .overlay {
                            Capsule()
                                .fill(LinearGradient(colors: [settings.accentColor.opacity(0.7), settings.accentColor], startPoint: .leading, endPoint: .trailing))
                                .scaleEffect(x: filled || reduceMotion ? 1 : 0.3, anchor: .leading)
                                .opacity(filled ? 1 : 0)
                        }
                        .shadow(color: k == shownWeek - 1 ? settings.accentColor.opacity(0.7) : .clear, radius: 7)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onChangeGoal)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Obiettivo della settimana, tocca per cambiarlo")
        .accessibilityValue("\(min(weekSessions, goal)) su \(goal)")
        .accessibilityAddTraits(.isButton)
    }


    private func pill(_ number: String?, _ text: String, symbol: String? = nil) -> some View {
        HStack(spacing: 4) {
            if let symbol {
                Image(systemName: symbol).font(.system(size: 12, weight: .bold)).foregroundStyle(settings.accentColor)
                    .bounce(trigger: flameTick, peak: 1.3, wobble: 10)
            }
            if let number {
                Text(number).font(Theme.rounded(14, .heavy)).foregroundStyle(settings.accentColor)
                    .contentTransition(.numericText(value: Double(number) ?? 0))
            }
            Text(text).font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(Capsule().fill(Color.black.opacity(0.35)))
        .overlay(Capsule().strokeBorder(Color.white.opacity(0.1), lineWidth: 1))
    }
}

/// Le creste, in basso a destra come sul web (`viewBox 360×220`, ancorate all'angolo).
private struct Mountain: View {
    let accent: Color

    var body: some View {
        Canvas { context, size in
            let s = max(size.width / 360, size.height / 220)
            context.translateBy(x: size.width - 360 * s, y: size.height - 220 * s)
            context.scaleBy(x: s, y: s)

            func poly(_ points: [(CGFloat, CGFloat)]) -> Path {
                var p = Path()
                p.move(to: CGPoint(x: points[0].0, y: points[0].1))
                for q in points.dropFirst() { p.addLine(to: CGPoint(x: q.0, y: q.1)) }
                p.closeSubpath()
                return p
            }
            context.fill(poly([(0, 150), (40, 122), (84, 140), (132, 92), (182, 128), (232, 72), (292, 122), (332, 96), (360, 114), (360, 220), (0, 220)]), with: .color(.white.opacity(0.05)))
            context.fill(
                poly([(0, 182), (58, 146), (112, 164), (168, 110), (214, 142), (262, 48), (304, 104), (338, 84), (360, 96), (360, 220), (0, 220)]),
                with: .linearGradient(Gradient(colors: [accent.opacity(0.38), accent.opacity(0.04)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 220))
            )
            context.fill(poly([(262, 48), (246, 78), (256, 72), (262, 82), (270, 72), (282, 80)]), with: .color(.white.opacity(0.85)))
            context.fill(poly([(0, 220), (0, 184), (70, 170), (132, 188), (202, 160), (262, 180), (330, 164), (360, 176), (360, 220)]), with: .color(.black.opacity(0.38)))
            var flag = Path()
            flag.move(to: CGPoint(x: 262, y: 48)); flag.addLine(to: CGPoint(x: 262, y: 26)); flag.addLine(to: CGPoint(x: 282, y: 33)); flag.addLine(to: CGPoint(x: 262, y: 40))
            context.fill(flag, with: .color(accent))
            context.stroke(flag, with: .color(accent), lineWidth: 1.5)
        }
        .allowsHitTesting(false)
    }
}


// MARK: - Barra «cosa faccio adesso»

struct NextWorkoutBar: View {
    @Environment(AppSettings.self) private var settings
    let title: String
    let subtitle: String
    let symbol: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: symbol)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Color.black.opacity(0.8))
                    .frame(width: 44, height: 44)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(settings.accentColor))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Theme.rounded(16, .bold)).foregroundStyle(Theme.textPrimary)
                    Text(subtitle).font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary).lineLimit(1)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.textTertiary)
            }
            .padding(12)
            .glassSurface(radius: 18)
        }
        .buttonStyle(PressStyle())
    }
}

