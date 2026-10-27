import SwiftUI
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
