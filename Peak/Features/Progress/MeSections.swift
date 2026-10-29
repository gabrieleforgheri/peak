import SwiftUI
import SwiftData
import Charts

// Le parti di Me. Ognuna riceve già i numeri: i conti stanno in `HomeStats`.

/// I numeri di Me, ricavati dagli allenamenti conclusi e dai macchinari.
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
        recordCount = exercises.reduce(0) { $0 + $1.twoArmRecords.count }
        let used = exercises.filter { !$0.twoArmRecords.isEmpty || !$0.setList.isEmpty }
        machinesUsed = used.count
        var best = 0.0
        for e in used {
            for r in e.twoArmRecords { best = max(best, r.estimatedOneRepMax) }
            for s in e.setList where !s.isWarmup && !s.singleArm { best = max(best, s.estimatedOneRepMax) }
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
                                // il mese sta sopra la colonna che contiene il giorno 1 (guardo la domenica), non sopra il primo lunedì
                                ForEach(Array(columns.enumerated()), id: \.offset) { i, column in
                                    let month = Calendar.current.component(.month, from: column[6].date)
                                    let prev = i > 0 ? Calendar.current.component(.month, from: columns[i - 1][6].date) : -1
                                    if month != prev {
                                        Text(column[6].date.formatted(.dateTime.month(.abbreviated)))
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
                                    Text(Calendar.current.veryShortStandaloneWeekdaySymbols[(r + 1) % 7])
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
                                                .overlay {
                                                    // il giorno 1 del mese è bordato: si vede dove inizia il mese dentro la settimana
                                                    if Calendar.current.component(.day, from: c.date) == 1 {
                                                        RoundedRectangle(cornerRadius: 4, style: .continuous).strokeBorder(Theme.textSecondary, lineWidth: 1.5)
                                                    }
                                                }
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

    @State private var range: ChartRange = .all

    private var availableRanges: [ChartRange] {
        guard let earliest = exercise.recordsByDateDesc.map(\.date).min() else { return [.all] }
        let spanDays = max(0, Date().timeIntervalSince(earliest) / 86_400)
        return ChartRange.available(forSpanDays: spanDays)
    }

    private var activeRange: ChartRange {
        availableRanges.contains(range) ? range : (availableRanges.first ?? .all)
    }

    private var points: [(date: Date, oneRm: Double)] {
        let cutoff = activeRange.days.map { Date().addingTimeInterval(-$0 * 86_400) }
        return exercise.recordsByDateDesc
            .filter { cutoff == nil || $0.date >= cutoff! }
            .reversed()
            .map { ($0.date, $0.estimatedOneRepMax) }
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

                if availableRanges.count > 1 {
                    Picker("Intervallo", selection: $range) {
                        ForEach(availableRanges) { Text(LocalizedStringKey($0.title)).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }

                if pts.count >= 2 {
                    Chart {
                        ForEach(Array(pts.enumerated()), id: \.offset) { _, p in
                            LineMark(x: .value("Data", p.date), y: .value("1RM", p.oneRm)).foregroundStyle(settings.accentColor).interpolationMethod(.monotone)
                            PointMark(x: .value("Data", p.date), y: .value("1RM", p.oneRm)).foregroundStyle(settings.accentColor)
                        }
                    }
                    .chartYScale(domain: lo...hi)
                    .chartYAxis(.hidden)
                    .frame(height: 150)
                } else {
                    Text("Dati non sufficienti per questo intervallo")
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textTertiary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .frame(height: 80)
                }
            }
        }
    }
}

// MARK: - Medaglie

/// La curva «ease-out» del web (--ease-out).
private func easeOut(_ seconds: Double) -> Animation { .timingCurve(0.23, 1, 0.32, 1, duration: seconds) }

/// Il valore di una medaglia come si legge: volume in tonnellate, forza nell'unità scelta, il resto intero.
private func medalValue(_ kind: MedalID, _ v: Double, unit: WeightUnit) -> String {
    switch kind {
    case .volume: return Fmt.volume(v, unit: unit)
    case .strength: return Fmt.weight(v, unit: unit)
    default: return String(Int(v.rounded()))
    }
}

private let tierNames = ["da conquistare", "Bronzo", "Argento", "Oro"]
private func metalTint(_ tier: Int) -> Color { [Theme.textTertiary, Theme.bronze, Theme.silver, Theme.gold][tier] }

/// Una medaglia come sul sito: disco metallico col simbolo e un anello sottile che si riempie verso il livello dopo
/// (a oro preso è pieno). `sweep`: all'apertura l'anello parte da vuoto e si riempie.
struct MedalDisc: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let medal: Medal
    var size: CGFloat = 52
    var sweep = false
    @State private var swept = false

    private var palette: (tint: Color, hi: Color, lo: Color, ink: Color) {
        switch medal.tier {
        case 1: return (Theme.bronze, Color(hex: 0xF1B27A), Color(hex: 0xA35F2A), Color(hex: 0x3A1D08))
        case 2: return (Theme.silver, Color(hex: 0xF4F6FA), Color(hex: 0x98A1B1), Color(hex: 0x232A36))
        case 3: return (Theme.gold, Color(hex: 0xFFE69A), Color(hex: 0xD39A14), Color(hex: 0x3D2A00))
        default: return (settings.accentColor, Color.white.opacity(0.08), Color.white.opacity(0.03), Theme.textTertiary)
        }
    }

    var body: some View {
        let p = palette
        let line = size * 0.045
        ZStack {
            Circle().stroke(Color.white.opacity(0.1), lineWidth: line).padding(size * 0.04)
            Circle().trim(from: 0, to: sweep && !swept ? 0 : medal.frac)
                .stroke(p.tint, style: StrokeStyle(lineWidth: line, lineCap: .butt))
                .rotationEffect(.degrees(-90)).padding(size * 0.04)
            Circle()
                .fill(RadialGradient(colors: [p.hi, p.lo], center: UnitPoint(x: 0.32, y: 0.26), startRadius: 0, endRadius: size * 0.5))
                .padding(size * 0.11)
                .shadow(color: medal.tier == 0 ? .clear : p.tint.opacity(0.3), radius: size * 0.12, y: size * 0.06)
            Image(systemName: medal.kind.symbol)
                .font(.system(size: size * 0.4, weight: .bold))
                .foregroundStyle(p.ink)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
        .onAppear {
            guard sweep, !swept else { return }
            if reduceMotion { swept = true } else { withAnimation(easeOut(0.7).delay(0.12)) { swept = true } }
        }
    }
}

/// La sezione Medaglie di Me: le tre più vicine al livello dopo, le altre dietro «Mostra tutte». Toccando una riga si apre il foglio.
struct MedalsCard: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let medals: [Medal]
    @State private var showAll = false
    @State private var open: MedalID?

    private var divider: some View { Rectangle().fill(Theme.stroke).frame(height: 1) }

    var body: some View {
        let list = Medals.ordered(medals)
        GlassCard(padding: 0) {
            VStack(spacing: 0) {
                ForEach(Array(list.prefix(3).enumerated()), id: \.element.id) { i, md in
                    if i > 0 { divider }
                    row(md)
                }
                if showAll {
                    VStack(spacing: 0) {
                        ForEach(list.dropFirst(3)) { md in
                            divider
                            row(md)
                        }
                    }
                    .transition(.opacity.combined(with: .offset(y: 8)))
                }
                if list.count > 3 {
                    divider
                    Button {
                        if reduceMotion { showAll.toggle() } else { withAnimation(easeOut(0.3)) { showAll.toggle() } }
                    } label: {
                        Text(showAll ? "Mostra meno" : "Mostra tutte (\(list.count))")
                            .font(Theme.rounded(14, .semibold))
                            .foregroundStyle(Theme.textSecondary)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, Theme.padding)
        }
        .sheet(item: $open) { kind in
            if let md = medals.first(where: { $0.kind == kind }) { MedalSheet(medal: md, medals: medals) }
        }
    }

    private func row(_ md: Medal) -> some View {
        Button { open = md.kind } label: {
            HStack(spacing: 14) {
                MedalDisc(medal: md)
                VStack(alignment: .leading, spacing: 5) {
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(md.kind.title).font(Theme.rounded(15, .bold)).foregroundStyle(Theme.textPrimary).lineLimit(1)
                        Spacer(minLength: 0)
                        Text(tierNames[md.tier])
                            .font(Theme.rounded(12, md.tier == 0 ? .semibold : .heavy))
                            .foregroundStyle(metalTint(md.tier))
                    }
                    MedalBar(frac: md.frac)
                    Text(progressLine(md))
                        .font(Theme.rounded(11, .medium).monospacedDigit())
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            .padding(.vertical, 12)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressFade())
    }

    /// «12 / 25», o solo il valore a oro preso.
    private func progressLine(_ md: Medal) -> String {
        let value = medalValue(md.kind, md.value, unit: settings.unit)
        guard let next = md.next else { return value }
        return "\(value) / \(medalValue(md.kind, next, unit: settings.unit))"
    }
}

/// La riga si attenua mentre la premi.
private struct PressFade: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// La barretta sotto il nome: si riempie da sinistra quando compare.
private struct MedalBar: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let frac: Double
    @State private var grown = false

    var body: some View {
        Capsule().fill(Theme.stroke)
            .frame(height: 4)
            .overlay(alignment: .leading) {
                Rectangle().fill(settings.accentColor)
                    .scaleEffect(x: grown ? max(frac, 0.0001) : 0.0001, anchor: .leading)
            }
            .clipShape(Capsule())
            .onAppear {
                if reduceMotion { grown = true } else { withAnimation(easeOut(0.5)) { grown = true } }
            }
    }
}

/// Un numero che scorre da quello che si vede al nuovo (come `countUp` del web).
private struct CountText: View, Animatable {
    var value: Double
    let format: (Double) -> String
    var animatableData: Double {
        get { value }
        set { value = newValue }
    }
    var body: some View { Text(format(value)) }
}

/// Il foglio di una medaglia: grande, con anello e valore che salgono all'apertura, il percorso bronzo/argento/oro e la data
/// in cui ogni livello è stato preso (ricavata dalla storia).
struct MedalSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(AppSettings.self) private var settings
    @Query private var workouts: [Workout]
    @Query private var exercises: [Exercise]

    let medal: Medal
    /// Tutte le medaglie di adesso: da qui si capisce fino a che livello cercare le date.
    let medals: [Medal]
    @State private var dates: [Date?] = [nil, nil, nil]
    @State private var appeared = false

    private func fmt(_ v: Double) -> String { medalValue(medal.kind, v, unit: settings.unit) }

    /// Quanto di ogni tratto del percorso è fatto: il tratto verso il bronzo parte da 0, gli altri dalla soglia sotto.
    private func fill(_ i: Int) -> Double {
        let t = medal.kind.tiers
        let from = i == 0 ? 0 : t[i - 1]
        return max(0, min(1, (medal.value - from) / (t[i] - from)))
    }

    private var nextLine: String {
        guard let next = medal.next else { return "Oro conquistato." }
        return "Mancano \(fmt(next - medal.value)) per \(tierNames[medal.tier + 1])."
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    hero.padding(.bottom, 18)
                    path
                }
                .padding(16)
            }
            .background(Theme.bg)
            .navigationTitle(medal.kind.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Chiudi") { dismiss() }
                        .font(Theme.rounded(15, .semibold))
                        .foregroundStyle(settings.accentColor)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .task {
            let all = Medals.dates(workouts: workouts, exercises: exercises, friends: SocialStore.shared.friendCount, current: medals)
            dates = all[medal.kind] ?? dates
        }
        .onAppear {
            if reduceMotion { appeared = true } else { withAnimation(easeOut(0.8).delay(0.12)) { appeared = true } }
        }
    }

    private var hero: some View {
        VStack(spacing: 4) {
            Text(medal.kind.info)
                .font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                .frame(maxWidth: 260).padding(.bottom, 12)
            MedalDisc(medal: medal, size: 112, sweep: true)
            CountText(value: appeared ? medal.value : 0, format: { fmt($0) })
                .font(Theme.rounded(34, .heavy).monospacedDigit()).foregroundStyle(Theme.textPrimary)
                .padding(.top, 12)
            Text(tierNames[medal.tier])
                .font(Theme.rounded(12, .heavy)).tracking(0.9).textCase(.uppercase)
                .foregroundStyle(metalTint(medal.tier))
            Text(nextLine).font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .multilineTextAlignment(.center)
    }

    /// Dal basso (zero) in su: bronzo, argento, oro. Il tratto si riempie fino al valore di adesso.
    private var path: some View {
        VStack(spacing: 0) {
            ForEach([2, 1, 0], id: \.self) { i in
                pathRow(i)
            }
        }
        .padding(.horizontal, 4)
    }

    private func pathRow(_ i: Int) -> some View {
        let on = medal.tier > i
        let lit = on && appeared
        let tint = metalTint(i + 1)
        let delay = Double(i) * 0.2
        return HStack(alignment: .top, spacing: 10) {
            Circle()
                .strokeBorder(lit ? tint : Theme.strokeStrong, lineWidth: 2)
                .background(Circle().fill(lit ? tint : .clear))
                .shadow(color: lit ? tint.opacity(0.5) : .clear, radius: 6)
                .frame(width: 14, height: 14)
                .frame(width: 28)
                .padding(.top, 4)
                .animation(reduceMotion ? nil : easeOut(0.25).delay(delay + 0.45), value: appeared)
            VStack(alignment: .leading, spacing: 0) {
                Text(tierNames[i + 1]).font(Theme.rounded(15, .bold)).foregroundStyle(on ? tint : Theme.textPrimary)
                Text(fmt(medal.kind.tiers[i])).font(Theme.rounded(12, .medium).monospacedDigit()).foregroundStyle(Theme.textSecondary)
            }
            .padding(.top, 1)
            Spacer(minLength: 0)
            if on, let d = dates[i] {
                Text(Fmt.date(d)).font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary).padding(.top, 3)
            }
        }
        .frame(minHeight: 72, alignment: .top)
        .background(alignment: .topLeading) {
            // il tratto sotto il nodo, fino al nodo di sotto
            GeometryReader { g in
                Capsule().fill(Theme.stroke)
                    .overlay(alignment: .bottom) {
                        Rectangle().fill(tint)
                            .scaleEffect(y: appeared ? max(fill(i), 0.0001) : 0.0001, anchor: .bottom)
                            .animation(reduceMotion ? nil : easeOut(0.4).delay(delay + 0.15), value: appeared)
                    }
                    .clipShape(Capsule())
                    .frame(width: 3, height: max(0, g.size.height - 20 + (i == 0 ? 0 : 4)))
                    .offset(x: 12.5, y: 20)
            }
        }
        .opacity(on ? 1 : 0.55)
    }
}
