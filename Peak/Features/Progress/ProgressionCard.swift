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
    let exercises: [Exercise]

    @State private var pickedID: UUID?

    private var candidates: [Exercise] {
        exercises.filter { !$0.isArchived && Progression1RM.points($0).count >= 2 }
            .sorted { ($0.bestRecord?.estimatedOneRepMax ?? 0) > ($1.bestRecord?.estimatedOneRepMax ?? 0) }
    }
    private var current: Exercise? { candidates.first { $0.uuid == pickedID } ?? candidates.first }

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(candidates) { e in
                            Button { pickedID = e.uuid; Haptics.tap() } label: {
                                Text(e.name).font(Theme.rounded(12, .semibold)).lineLimit(1)
                                    .padding(.horizontal, 12).padding(.vertical, 7)
                                    .background(e.uuid == current?.uuid ? settings.accentColor.opacity(0.22) : Theme.card, in: Capsule())
                                    .overlay(Capsule().strokeBorder(e.uuid == current?.uuid ? settings.accentColor.opacity(0.7) : Theme.stroke, lineWidth: 1))
                                    .foregroundStyle(e.uuid == current?.uuid ? settings.accentColor : Theme.textSecondary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                if let e = current { chart(e) }
            }
        }
    }

    @ViewBuilder
    private func chart(_ e: Exercise) -> some View {
        let pts = Progression1RM.points(e)
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
