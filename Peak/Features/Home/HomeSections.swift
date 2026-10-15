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
        weeks = (0..<12).map { i in
            let start = cal.date(byAdding: .weekOfYear, value: -(11 - i), to: weekStart) ?? weekStart
            let volume = done.filter { cal.dateInterval(of: .weekOfYear, for: $0.date)?.start == start }.reduce(0) { $0 + $1.totalVolume }
            return (start, volume)
        }
        groups = Stats.volumeByGroup(workouts: done, days: 30, now: now)
        last98Days = done.filter { $0.date >= now.addingTimeInterval(-98 * 86_400) }.count
    }
}

// MARK: - Eroe: la montagna e una frase

struct HomeHero: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let name: String
    let streak: Int
    let weekSessions: Int
    let goal: Int
    var onAvatar: () -> Void
    var onChangeGoal: () -> Void

    @State private var index = Motivation.startIndex()

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
                }

                Text(Motivation.phrases[index])
                    .id(index)
                    .font(Theme.rounded(28, .heavy))
                    .tracking(-0.8)
                    .lineSpacing(-2)
                    .shadow(color: settings.accentColor.opacity(0.45), radius: 14)
                    .frame(maxWidth: 270, alignment: .leading)
                    .frame(minHeight: 96, alignment: .topLeading)
                    .padding(.top, 22)
                    .transition(.opacity.combined(with: .offset(y: 10)))

                Spacer(minLength: 12)

                goalBar

                HStack(spacing: 8) {
                    pill("\(streak)", "settimane di fila")
                    if weekSessions > goal { pill(nil, "+\(weekSessions - goal) oltre l'obiettivo") }
                }
                .padding(.top, 12)
            }
            .padding(18)
        }
        .frame(height: 320)
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(settings.accentColor.opacity(0.3), lineWidth: 1))
        .task(id: reduceMotion) {
            guard !reduceMotion else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(10))
                guard !Task.isCancelled else { return }
                withAnimation(.easeInOut(duration: 0.6)) { index = (index + 1) % Motivation.phrases.count }
            }
        }
    }

    /// L'obiettivo della settimana: un segmento per allenamento.
    private var goalBar: some View {
        VStack(spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(weekSessions >= goal ? "Obiettivo raggiunto!" : "Obiettivo della settimana")
                    .font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                Spacer()
                Text("\(weekSessions) / \(goal)").font(Theme.rounded(15, .heavy)).monospacedDigit()
            }
            HStack(spacing: 6) {
                ForEach(0..<goal, id: \.self) { k in
                    Capsule()
                        .fill(k < weekSessions
                              ? AnyShapeStyle(LinearGradient(colors: [settings.accentColor.opacity(0.7), settings.accentColor], startPoint: .leading, endPoint: .trailing))
                              : AnyShapeStyle(Color.white.opacity(0.14)))
                        .frame(height: 12)
                        .shadow(color: k == weekSessions - 1 ? settings.accentColor.opacity(0.7) : .clear, radius: 7)
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

    private func pill(_ number: String?, _ text: String) -> some View {
        HStack(spacing: 4) {
            if let number { Text(number).font(Theme.rounded(14, .heavy)).foregroundStyle(settings.accentColor) }
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
                Text(value).font(Theme.rounded(19, .heavy)).minimumScaleFactor(0.6).lineLimit(1)
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
                HStack(spacing: 4) {
                    ForEach(Array(columns.enumerated()), id: \.offset) { _, column in
                        VStack(spacing: 4) {
                            ForEach(Array(column.enumerated()), id: \.offset) { _, cell in
                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                    .fill(color(cell.sets))
                                    .aspectRatio(1, contentMode: .fit)
                            }
                        }
                    }
                }
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
                HStack {
                    Text(weeks.first.map { Fmt.shortDate($0.start) } ?? "").foregroundStyle(Theme.textTertiary)
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

struct MedalBadge: View {
    @Environment(AppSettings.self) private var settings
    let medal: Medal

    private var colors: (hi: Color, lo: Color, tint: Color, ink: Color) {
        switch medal.tier {
        case 1: return (Color(hex: 0xF1B27A), Color(hex: 0xA35F2A), Color(hex: 0xE0955A), Color(hex: 0x3A1D08))
        case 2: return (Color(hex: 0xF4F6FA), Color(hex: 0x98A1B1), Color(hex: 0xDFE4EC), Color(hex: 0x232A36))
        case 3: return (Color(hex: 0xFFE69A), Color(hex: 0xD39A14), Color(hex: 0xFFD45E), Color(hex: 0x3D2A00))
        default: return (Color.white.opacity(0.08), Color.white.opacity(0.03), settings.accentColor, Theme.textTertiary)
        }
    }

    private func format(_ v: Double) -> String {
        switch medal.kind {
        case .volume: return Fmt.volume(v, unit: settings.unit)
        case .strength: return Fmt.weight(v, unit: settings.unit)
        default: return String(Int(v.rounded()))
        }
    }

    private var caption: String {
        let names = ["", "Bronzo", "Argento", "Oro"]
        guard let next = medal.next else { return "Oro" }
        let progress = "\(format(medal.value))/\(format(next))"
        return medal.tier == 0 ? progress : "\(names[medal.tier]) · \(progress)"
    }

    var body: some View {
        let c = colors
        VStack(spacing: 6) {
            ZStack {
                Circle().stroke(Color.white.opacity(0.1), lineWidth: 4)
                Circle().trim(from: 0, to: medal.progress).stroke(c.tint, style: StrokeStyle(lineWidth: 4, lineCap: .round)).rotationEffect(.degrees(-90))
                Circle().fill(RadialGradient(colors: [c.hi, c.lo], center: .init(x: 0.32, y: 0.26), startRadius: 0, endRadius: 40)).padding(7)
                    .shadow(color: medal.tier == 0 ? .clear : c.tint.opacity(0.35), radius: 8, y: 4)
                Image(systemName: medal.kind.symbol).font(.system(size: 22, weight: .bold)).foregroundStyle(c.ink)
            }
            .frame(width: 76, height: 76)
            Text(medal.kind.title).font(Theme.rounded(12, .bold)).multilineTextAlignment(.center).lineLimit(2)
            Text(caption).font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary).multilineTextAlignment(.center).lineLimit(2)
        }
        .frame(width: 100)
    }
}
