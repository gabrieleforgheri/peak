import SwiftUI
import Charts

/// L'andamento di un macchinario alla volta: il miglior 1RM stimato di ogni giorno in cui l'hai allenato.
enum Progression1RM {
    struct Point: Identifiable { let date: Date; let oneRm: Double; var id: Date { date } }

    /// Un punto per giorno (il migliore fra massimali e serie di lavoro), dal più vecchio.
    static func points(_ exercise: Exercise) -> [Point] {
        let cal = Calendar.current
        var best: [Date: Double] = [:]
        for r in exercise.recordList { best[cal.startOfDay(for: r.date), default: 0] = max(best[cal.startOfDay(for: r.date)] ?? 0, r.estimatedOneRepMax) }
        for s in exercise.setList where !s.isWarmup && s.workout?.isActive == false {
            let d = cal.startOfDay(for: s.workout?.date ?? s.createdAt)
            best[d] = max(best[d] ?? 0, s.estimatedOneRepMax)
        }
        return best.map { Point(date: $0.key, oneRm: $0.value) }.sorted { $0.date < $1.date }
    }
}

struct ProgressionCard: View {
    @Environment(AppSettings.self) private var settings
    /// Già calcolate da `Insights`: qui non si ricalcola niente.
    let series: [Insights.Series]

    @State private var pickedID: UUID?
    @State private var range: ChartRange = .all

    private func availableRanges(for points: [Progression1RM.Point]) -> [ChartRange] {
        guard let earliest = points.map(\.date).min() else { return [.all] }
        let spanDays = max(0, Date().timeIntervalSince(earliest) / 86_400)
        return ChartRange.available(forSpanDays: spanDays)
    }

    private func filteredPoints(for points: [Progression1RM.Point], activeRange: ChartRange) -> [Progression1RM.Point] {
        guard let days = activeRange.days else { return points }
        let cutoff = Date().addingTimeInterval(-days * 86_400)
        return points.filter { $0.date >= cutoff }
    }

    var body: some View {
        let current = series.first { $0.id == pickedID } ?? series.first
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(series) { s in
                            let picked = s.id == current?.id
                            Button { pickedID = s.id; Haptics.tap() } label: {
                                Text(s.exercise.name).font(Theme.rounded(12, .semibold)).lineLimit(1)
                                    .padding(.horizontal, 12).padding(.vertical, 7)
                                    .background(picked ? settings.accentColor.opacity(0.22) : Theme.card, in: Capsule())
                                    .overlay(Capsule().strokeBorder(picked ? settings.accentColor.opacity(0.7) : Theme.stroke, lineWidth: 1))
                                    .foregroundStyle(picked ? settings.accentColor : Theme.textSecondary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                if let current {
                    let avail = availableRanges(for: current.points)
                    let active = avail.contains(range) ? range : (avail.first ?? .all)
                    if avail.count > 1 {
                        Picker("Intervallo", selection: $range) {
                            ForEach(avail) { Text(LocalizedStringKey($0.title)).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }
                    let filtered = filteredPoints(for: current.points, activeRange: active)
                    chart(filtered)
                }
            }
        }
    }

    @ViewBuilder
    private func chart(_ pts: [Progression1RM.Point]) -> some View {
        if pts.count < 2 {
            Text("Dati non sufficienti per questo intervallo")
                .font(Theme.rounded(12, .medium))
                .foregroundStyle(Theme.textTertiary)
                .frame(maxWidth: .infinity, alignment: .center)
                .frame(height: 120)
        } else {
            let unit = settings.unit
            let lo = (pts.map(\.oneRm).min() ?? 0) * 0.95
            let hi = (pts.map(\.oneRm).max() ?? 1) * 1.05
            HStack {
                Text("1RM stimato · un punto per allenamento").font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                Spacer()
                if let a = pts.first, let b = pts.last {
                    let up = b.oneRm >= a.oneRm
                    Text("\(Fmt.weight(a.oneRm, unit: unit)) → \(Fmt.weight(b.oneRm, unit: unit))")
                        .font(Theme.rounded(11, .bold)).foregroundStyle(up ? Theme.positive : Theme.negative)
                        .padding(.horizontal, 8).padding(.vertical, 3).background(Capsule().fill((up ? Theme.positive : Theme.negative).opacity(0.15)))
                }
            }
            Chart(pts) { p in
                LineMark(x: .value("Data", p.date), y: .value("1RM", unit.fromKilograms(p.oneRm)))
                    .foregroundStyle(settings.accentColor).interpolationMethod(.monotone)
                PointMark(x: .value("Data", p.date), y: .value("1RM", unit.fromKilograms(p.oneRm)))
                    .foregroundStyle(settings.accentColor).symbolSize(34)
            }
            .chartYScale(domain: unit.fromKilograms(lo)...unit.fromKilograms(hi))
            .chartYAxis {
                AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { v in
                    AxisGridLine().foregroundStyle(Theme.stroke)
                    AxisValueLabel { if let n = v.as(Double.self) { Text(Int(n.rounded()).formatted()).font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary) } }
                }
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 4)) { v in
                    AxisValueLabel { if let d = v.as(Date.self) { Text(Fmt.shortDate(d)).font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary) } }
                }
            }
            .frame(height: 170)
        }
    }
}
