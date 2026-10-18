import SwiftUI
import Charts

// Le parti della Home. Ognuna riceve già i numeri: i conti stanno in `HomeStats`.

/// I numeri della Home, ricavati dagli allenamenti conclusi e dai macchinari.
struct HomeStats {
    let finished: Int
    let weekSessions: Int
    let streak: Int
    let goal: Int
    let totalVolume: Double
    let recordCount: Int
    let machinesUsed: Int
    let bestOneRm: Double
    /// Serie di lavoro per giorno (inizio giornata → serie).
    let perDay: [Date: Int]
    /// Volume delle ultime 12 settimane, dalla più vecchia; anche le vuote.
    let weeks: [(start: Date, volume: Double)]
    let groups: [Stats.GroupVolume]
    let last98Days: Int

    /// `goal`: l'obiettivo scelto (0 = non ancora: si propone quello che fai di solito).
    init(workouts: [Workout], exercises: [Exercise], chosenGoal: Int = 0, now: Date = Date(), calendar: Calendar = .current) {
        var cal = calendar
        cal.firstWeekday = 2
        let done = workouts.filter { !$0.isActive }
        finished = done.count
        let weekStart = cal.dateInterval(of: .weekOfYear, for: now)?.start ?? now
        weekSessions = done.filter { $0.date >= weekStart }.count
        streak = Stats.weeklyStreak(workouts: done, calendar: cal, now: now)
        goal = chosenGoal > 0 ? chosenGoal : Stats.weeklyGoal(workouts: done, calendar: cal, now: now)
        totalVolume = done.reduce(0) { $0 + $1.totalVolume }
        recordCount = exercises.reduce(0) { $0 + $1.recordList.count }
        let used = exercises.filter { !$0.recordList.isEmpty || !$0.setList.isEmpty }
        machinesUsed = used.count
        var best = 0.0
        for e in used {
            for r in e.recordList { best = max(best, r.estimatedOneRepMax) }
            for s in e.setList where !s.isWarmup { best = max(best, s.estimatedOneRepMax) }
        }
        bestOneRm = best
        var days: [Date: Int] = [:]
        for w in done { days[cal.startOfDay(for: w.date), default: 0] += w.workingSetCount }
        perDay = days
        // L'inizio settimana di ogni allenamento si calcola una volta sola (il Calendar è lento).
        var volumeByWeek: [Date: Double] = [:]
        for w in done {
            if let start = cal.dateInterval(of: .weekOfYear, for: w.date)?.start { volumeByWeek[start, default: 0] += w.totalVolume }
        }
        weeks = (0..<12).map { i in
            let start = cal.date(byAdding: .weekOfYear, value: -(11 - i), to: weekStart) ?? weekStart
            return (start, volumeByWeek[start] ?? 0)
        }
        groups = Stats.volumeByGroup(workouts: done, days: 30, now: now)
        last98Days = done.filter { $0.date >= now.addingTimeInterval(-98 * 86_400) }.count
    }
}

// MARK: - Eroe: la montagna e una frase

struct HomeHero: View {
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
    /// Quello che la Home mostra: parte da quanto è già stato festeggiato (`Celebrated`) e sale al valore vero se c'è una novità
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
                        Text(name).font(Theme.rounded(22, .heavy))
                    }
                    Spacer()
                    Button(action: onAvatar) {
                        Text(String(name.prefix(1)).uppercased())
                            .font(Theme.rounded(17, .heavy))
                            .foregroundStyle(Color.black.opacity(0.8))
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(LinearGradient(colors: [settings.accentColor, settings.accentColor.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing)))
                    }
                    .buttonStyle(.plain)
                    .tourTarget("avatar")
                    .accessibilityLabel("Impostazioni")
                }

                Text(Motivation.phrases[index].replacingOccurrences(of: ", ", with: ",\n"))
                    .id(index)
                    .font(Theme.rounded(28, .heavy))
                    .tracking(-0.8)
                    .lineSpacing(-2)
                    .shadow(color: settings.accentColor.opacity(0.45), radius: 14)
                    // mai troncata: va a capo quanto serve e lascia libera la vetta a destra
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.trailing, 100)
                    .frame(minHeight: 96, alignment: .topLeading)
                    .padding(.top, 22)
                    .transition(.opacity.combined(with: .offset(y: 10)))

                Spacer(minLength: 12)

                goalBar

                HStack(spacing: 8) {
                    pill("\(shownStreak)", "settimane di fila", symbol: "flame.fill")
                    if weekSessions > goal { pill(nil, "+\(weekSessions - goal) oltre l'obiettivo") }
                }
                .padding(.top, 12)
            }
            .padding(18)
        }
        .frame(minHeight: 320)
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(settings.accentColor.opacity(0.3), lineWidth: 1))
        .task(id: reduceMotion) {
            guard !reduceMotion else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(10))
                guard !Task.isCancelled else { return }
                withAnimation(.easeInOut(duration: 0.6)) { index = (index + 1) % Motivation.phrases.count }
            }
        }
        // festeggia solo ciò che non è ancora stato festeggiato e solo quando la Home si vede (le altre schede restano vive sotto)
        .task(id: [weekSessions, streak, scenePhase == .active ? 1 : 0, nav.tab == .home ? 1 : 0]) { await celebrate() }
        .onChange(of: weekSessions) { catchUp() }
        .onChange(of: streak) { catchUp() }
        .onChange(of: nav.tab) { catchUp() }
    }

    /// Se i numeri sono saliti mentre la Home non si vedeva e il riepilogo li ha già festeggiati, la barra li segue senza festa.
    private func catchUp() {
        if (Celebrated.week(start: Celebrated.weekStart()) ?? 0) >= weekSessions { shownWeek = weekSessions }
        if (Celebrated.streak ?? 0) >= streak { shownStreak = streak }
    }

    /// Una novità non ancora vista: la barra dell'obiettivo si riempie dal vecchio al nuovo valore (una nota per segmento,
    /// più alta a ogni giorno), `Obiettivo raggiunto` se l'ha appena raggiunto, e la fiamma si scuote mentre la serie sale.
    /// Prima volta in assoluto, nessun aumento o calo: si allinea e basta. I valori nuovi si salvano subito, così non si ripete.
    @MainActor
    private func celebrate() async {
        // fuori vista non si fa e non si salva nulla: la festa tocca quando la Home si guarda davvero
        guard scenePhase == .active, nav.tab == .home else { return }
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
                try await Task.sleep(for: .seconds(0.4)) // il tempo di vedere la Home, poi parte
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
        .buttonStyle(.plain)
    }
}

// MARK: - Numeri

struct StatStrip: View {
    @Environment(AppSettings.self) private var settings
    let stats: HomeStats

    var body: some View {
        HStack(spacing: 8) {
            cell(String(stats.finished), "Allenamenti")
            cell(Fmt.volume(stats.totalVolume, unit: settings.unit), "Sollevati")
            cell(String(stats.recordCount), "Record")
            cell(String(stats.streak), "Settimane di fila", accent: true, symbol: "flame.fill")
        }
    }

    private func cell(_ value: String, _ label: String, accent: Bool = false, symbol: String? = nil) -> some View {
        VStack(spacing: 2) {
            HStack(spacing: 3) {
                if let symbol { Image(systemName: symbol).font(.system(size: 15, weight: .bold)) }
                Text(value).font(Theme.rounded(19, .heavy)).minimumScaleFactor(0.6).lineLimit(1).rolls(on: value)
            }
            .foregroundStyle(accent ? settings.accentColor : Theme.textPrimary)
            Text(label).font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary).multilineTextAlignment(.center).lineLimit(2)
        }
        .frame(maxWidth: .infinity, minHeight: 64)
        .padding(.horizontal, 4)
        .glassSurface(radius: 16)
    }
}

// MARK: - Attività

struct HeatmapCard: View {
    @Environment(AppSettings.self) private var settings
    let perDay: [Date: Int]
    let count: Int
    private let weeks = 14

    private var columns: [[(date: Date, sets: Int)]] {
        var cal = Calendar.current
        cal.firstWeekday = 2
        let today = Date()
        let monday = cal.dateInterval(of: .weekOfYear, for: today)?.start ?? today
        let first = cal.date(byAdding: .weekOfYear, value: -(weeks - 1), to: monday) ?? monday
        return (0..<weeks).map { c in
            (0..<7).map { r in
                let d = cal.date(byAdding: .day, value: c * 7 + r, to: first) ?? first
                return (d, d > today ? -1 : (perDay[cal.startOfDay(for: d)] ?? 0))
            }
        }
    }

    private func color(_ n: Int) -> Color {
        switch n {
        case ..<0: return .clear
        case 0: return Color.white.opacity(0.06)
        case 1..<8: return settings.accentColor.opacity(0.3)
        case 8..<16: return settings.accentColor.opacity(0.55)
        case 16..<24: return settings.accentColor.opacity(0.8)
        default: return settings.accentColor
        }
    }

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                // cella quadrata calcolata sulla larghezza: lettere dei giorni a sinistra, mesi sopra
                GeometryReader { geo in
                    let gap: CGFloat = 4
                    let label: CGFloat = 14
                    let cell = (geo.size.width - label - 6 - gap * CGFloat(weeks - 1)) / CGFloat(weeks)
                    VStack(alignment: .leading, spacing: gap) {
                        HStack(spacing: 0) {
                            Color.clear.frame(width: label + 6, height: 12)
                            ZStack(alignment: .leading) {
                                ForEach(Array(columns.enumerated()), id: \.offset) { i, column in
                                    let month = Calendar.current.component(.month, from: column[0].date)
                                    let prev = i > 0 ? Calendar.current.component(.month, from: columns[i - 1][0].date) : -1
                                    if month != prev {
                                        Text(column[0].date.formatted(.dateTime.month(.abbreviated)))
                                            .font(Theme.rounded(9, .medium)).foregroundStyle(Theme.textTertiary)
                                            .fixedSize()
                                            .offset(x: CGFloat(i) * (cell + gap))
                                    }
                                }
                            }
                            .frame(height: 12, alignment: .leading)
                        }
                        HStack(alignment: .top, spacing: 6) {
                            VStack(spacing: gap) {
                                ForEach(0..<7, id: \.self) { r in
                                    Text(["L", "M", "M", "G", "V", "S", "D"][r])
                                        .font(Theme.rounded(9, .medium)).foregroundStyle(Theme.textTertiary)
                                        .frame(width: label, height: cell)
                                }
                            }
                            HStack(spacing: gap) {
                                ForEach(Array(columns.enumerated()), id: \.offset) { _, column in
                                    VStack(spacing: gap) {
                                        ForEach(Array(column.enumerated()), id: \.offset) { _, c in
                                            RoundedRectangle(cornerRadius: 4, style: .continuous)
                                                .fill(color(c.sets))
                                                .frame(width: cell, height: cell)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .frame(height: 12 + 4 + 7 * 22 + 6 * 4)
                Text("Una colonna = una settimana, un quadratino = un giorno")
                    .font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
                Text(count == 1 ? "1 allenamento in 14 settimane" : "\(count) allenamenti in 14 settimane")
                    .font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
            }
        }
    }
}

struct WeeklyBarsCard: View {
    @Environment(AppSettings.self) private var settings
    let weeks: [(start: Date, volume: Double)]

    var body: some View {
        let top = max(1, weeks.map(\.volume).max() ?? 1)
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .bottom, spacing: 6) {
                    ForEach(Array(weeks.enumerated()), id: \.offset) { i, w in
                        let now = i == weeks.count - 1
                        RoundedRectangle(cornerRadius: 5, style: .continuous)
                            .fill(w.volume == 0 ? AnyShapeStyle(Color.white.opacity(0.07)) : AnyShapeStyle(LinearGradient(
                                colors: [settings.accentColor.opacity(now ? 0.5 : 0.25), settings.accentColor.opacity(now ? 1 : 0.7)], startPoint: .bottom, endPoint: .top)))
                            .frame(height: max(4, CGFloat(w.volume / top) * 100))
                            .shadow(color: now ? settings.accentColor.opacity(0.45) : .clear, radius: 8)
                    }
                }
                .frame(height: 110, alignment: .bottom)
                // asse delle settimane: il giorno in cui inizia la settimana, una barra su due
                HStack(spacing: 6) {
                    ForEach(Array(weeks.enumerated()), id: \.offset) { i, w in
                        Text(i % 2 == (weeks.count - 1) % 2 ? Fmt.shortDate(w.start) : "")
                            .font(Theme.rounded(8, .medium)).foregroundStyle(Theme.textTertiary)
                            .lineLimit(1).minimumScaleFactor(0.6).frame(maxWidth: .infinity)
                    }
                }
                HStack {
                    Text("1 barra = 1 settimana").foregroundStyle(Theme.textTertiary)
                    Spacer()
                    Text("questa settimana: \(Fmt.volume(weeks.last?.volume ?? 0, unit: settings.unit))").foregroundStyle(Theme.textSecondary)
                }
                .font(Theme.rounded(11, .medium))
            }
        }
    }
}

struct WorkDonutCard: View {
    @Environment(AppSettings.self) private var settings
    let groups: [Stats.GroupVolume]

    var body: some View {
        let total = max(1, groups.reduce(0) { $0 + $1.volume })
        GlassCard {
            HStack(spacing: 18) {
                ZStack {
                    Circle().stroke(Color.white.opacity(0.06), lineWidth: 14)
                    ForEach(Array(slices(total).enumerated()), id: \.offset) { _, s in
                        Circle().trim(from: s.from, to: s.to).stroke(s.color, style: StrokeStyle(lineWidth: 14, lineCap: .butt)).rotationEffect(.degrees(-90))
                    }
                    Text(Fmt.volume(total, unit: settings.unit)).font(Theme.rounded(15, .heavy))
                }
                .frame(width: 128, height: 128)
                VStack(spacing: 8) {
                    ForEach(groups) { g in
                        HStack {
                            Circle().fill(g.group.tint).frame(width: 10, height: 10)
                            Text(g.group.title).font(Theme.rounded(13, .medium))
                            Spacer()
                            Text("\(Int((g.volume / total * 100).rounded()))%").font(Theme.rounded(13, .bold))
                        }
                    }
                    Text("Ultimi 30 giorni").font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private func slices(_ total: Double) -> [(from: CGFloat, to: CGFloat, color: Color)] {
        var at = 0.0
        return groups.map { g in
            let from = at
            at += g.volume / total
            return (CGFloat(from), CGFloat(max(from, at - 0.006)), g.group.tint)
        }
    }
}

struct LiftChartCard: View {
    @Environment(AppSettings.self) private var settings
    let exercise: Exercise

    private var points: [(date: Date, oneRm: Double)] {
        exercise.recordsByDateDesc.reversed().suffix(12).map { ($0.date, $0.estimatedOneRepMax) }
    }

    var body: some View {
        let pts = points
        let lo = (pts.map(\.oneRm).min() ?? 0) * 0.97
        let hi = (pts.map(\.oneRm).max() ?? 1) * 1.03
        GlassCard {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(exercise.name).font(Theme.rounded(15, .bold))
                    Spacer()
                    if let a = pts.first, let b = pts.last {
                        Text("\(Fmt.weight(a.oneRm, unit: settings.unit)) → \(Fmt.weight(b.oneRm, unit: settings.unit))")
                            .font(Theme.rounded(11, .bold)).foregroundStyle(Theme.positive)
                            .padding(.horizontal, 8).padding(.vertical, 3).background(Capsule().fill(Theme.positive.opacity(0.15)))
                    }
                }
                Text("1RM stimato").font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                Chart {
                    ForEach(Array(pts.enumerated()), id: \.offset) { _, p in
                        LineMark(x: .value("Data", p.date), y: .value("1RM", p.oneRm)).foregroundStyle(settings.accentColor).interpolationMethod(.monotone)
                        PointMark(x: .value("Data", p.date), y: .value("1RM", p.oneRm)).foregroundStyle(settings.accentColor)
                    }
                }
                .chartYScale(domain: lo...hi)
                .chartYAxis(.hidden)
                .frame(height: 150)
            }
        }
    }
}

// MARK: - Medaglie

/// Una medaglia in elenco: cosa misura, a che livello sei, cosa manca per il prossimo. Come nel web.
struct MedalRow: View {
    @Environment(AppSettings.self) private var settings
    let medal: Medal

    private var tierName: String { ["da conquistare", "Bronzo", "Argento", "Oro"][medal.tier] }

    private func format(_ v: Double) -> String {
        switch medal.kind {
        case .volume: return Fmt.volume(v, unit: settings.unit)
        case .strength: return Fmt.weight(v, unit: settings.unit)
        default: return String(Int(v.rounded()))
        }
    }

    private var nextLine: String {
        guard let next = medal.next else { return "Oro conquistato." }
        let names = ["Bronzo", "Argento", "Oro"]
        return "Mancano \(format(next - medal.value)) per \(names[medal.tier])."
    }

    private var tiersLine: String {
        let t = medal.kind.tiers
        return "Bronzo \(format(t[0])) · Argento \(format(t[1])) · Oro \(format(t[2]))"
    }

    var body: some View {
        GlassCard(padding: 14) {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Image(systemName: medal.kind.symbol)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(settings.accentColor)
                    Text(medal.kind.title)
                        .font(Theme.rounded(15, .bold))
                        .foregroundStyle(Theme.textPrimary)
                    Spacer()
                    Text(tierName)
                        .font(Theme.rounded(12, .bold))
                        .foregroundStyle(settings.accentColor)
                }
                Text(medal.kind.info)
                    .font(Theme.rounded(12, .medium))
                    .foregroundStyle(Theme.textSecondary)
                Text(tiersLine)
                    .font(Theme.rounded(11, .medium))
                    .foregroundStyle(Theme.textTertiary)
                ProgressView(value: medal.progress)
                    .tint(settings.accentColor)
                Text(nextLine)
                    .font(Theme.rounded(12, .medium))
                    .foregroundStyle(Theme.textSecondary)
            }
        }
    }
}

/// Una medaglia come sul sito: disco metallico col simbolo, anello che si riempie verso il livello dopo, nome e livello.
struct MedalChip: View {
    @Environment(AppSettings.self) private var settings
    let medal: Medal

    private var palette: (tint: Color, hi: Color, lo: Color, ink: Color) {
        switch medal.tier {
        case 1: return (Color(hex: 0xE0955A), Color(hex: 0xF1B27A), Color(hex: 0xA35F2A), Color(hex: 0x3A1D08))
        case 2: return (Color(hex: 0xDFE4EC), Color(hex: 0xF4F6FA), Color(hex: 0x98A1B1), Color(hex: 0x232A36))
        case 3: return (Color(hex: 0xFFD45E), Color(hex: 0xFFE69A), Color(hex: 0xD39A14), Color(hex: 0x3D2A00))
        default: return (settings.accentColor, Color.white.opacity(0.08), Color.white.opacity(0.03), Theme.textTertiary)
        }
    }

    private var caption: String {
        let tier = ["", "Bronzo", "Argento", "Oro"][medal.tier]
        guard let next = medal.next else { return tier }
        let fmt: (Double) -> String = { v in
            switch medal.kind {
            case .volume: return Fmt.volume(v, unit: settings.unit)
            case .strength: return Fmt.weight(v, unit: settings.unit)
            default: return String(Int(v.rounded()))
            }
        }
        return (tier.isEmpty ? "" : tier + " · ") + "\(fmt(medal.value))/\(fmt(next))"
    }

    var body: some View {
        let p = palette
        VStack(spacing: 6) {
            ZStack {
                Circle().stroke(Color.white.opacity(0.1), lineWidth: 4)
                Circle().trim(from: 0, to: medal.progress).stroke(p.tint, style: StrokeStyle(lineWidth: 4, lineCap: .butt)).rotationEffect(.degrees(-90))
                Circle()
                    .fill(RadialGradient(colors: [p.hi, p.lo], center: UnitPoint(x: 0.32, y: 0.26), startRadius: 0, endRadius: 50))
                    .padding(6)
                    .shadow(color: medal.tier == 0 ? .clear : p.tint.opacity(0.4), radius: 8, y: 4)
                Image(systemName: medal.kind.symbol)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(p.ink)
            }
            .frame(width: 76, height: 76)
            Text(medal.kind.title).font(Theme.rounded(13, .bold)).lineLimit(1)
            Text(caption).font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
                .multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.8)
        }
        .frame(width: 104)
    }
}
