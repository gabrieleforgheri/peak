import SwiftUI
import SwiftData

/// Il tuo profilo: tutto quello che la Home mostra in breve, per esteso. In alto a destra le impostazioni.
struct MeView: View {
    @Environment(AppSettings.self) private var settings

    @Query(sort: \Workout.date, order: .reverse) private var workouts: [Workout]
    @Query(sort: \Exercise.sortIndex) private var exercises: [Exercise]
    @Query(sort: \MaxRecord.date, order: .reverse) private var records: [MaxRecord]
    @Query private var weights: [BodyWeightEntry]

    private let nav = AppNav.shared
    private let social = SocialStore.shared
    private let sync = SyncService.shared

    @State private var stats: HomeStats?
    @State private var showSettings = false

    private var finished: [Workout] { workouts.filter { !$0.isActive } }
    private var movers: [Stats.Mover] { Stats.topMovers(exercises: exercises, days: 90) }
    private var hasTrainingData: Bool { !finished.isEmpty || !records.isEmpty }

    private var fullName: String { sync.credentials?.name ?? "" }
    private var growingLift: Exercise? {
        exercises.filter { $0.recordList.count >= 3 }.max { ($0.bestRecord?.estimatedOneRepMax ?? 0) < ($1.bestRecord?.estimatedOneRepMax ?? 0) }
    }

    private var medalList: [Medal] {
        guard let s = stats else { return [] }
        return Medals.list(MedalInput(
            sessions: s.finished, records: s.recordCount, streak: s.streak, volumeKg: s.totalVolume,
            bestOneRm: s.bestOneRm, machines: s.machinesUsed, friends: social.friendCount
        ))
    }

    var body: some View {
        NavigationStack {
            content
                .screenBackground(settings.accentColor)
                .toolbar(.hidden, for: .navigationBar)
        }
        .task(id: workouts.count + exercises.count + records.count) {
            stats = HomeStats(workouts: workouts, exercises: exercises, chosenGoal: settings.weeklyGoal)
        }
        .fullScreenCover(isPresented: $showSettings) { SettingsView() }
    }

    private var header: some View {
        HStack(spacing: 14) {
            Text(String(fullName.first ?? "P").uppercased())
                .font(Theme.rounded(26, .heavy))
                .foregroundStyle(Color.black.opacity(0.8))
                .frame(width: 60, height: 60)
                .background(Circle().fill(settings.accentColor))
            VStack(alignment: .leading, spacing: 2) {
                Text(fullName.isEmpty ? "Me" : fullName).font(Theme.rounded(22, .heavy)).lineLimit(1)
                if let s = stats {
                    Text(s.streak == 1 ? "1 settimana di fila" : "\(s.streak) settimane di fila")
                        .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary)
                }
            }
            Spacer()
            Button { showSettings = true } label: {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 40, height: 40)
                    .glassSurface(radius: 20)
            }
            .buttonStyle(.plain)
            .foregroundStyle(Theme.textSecondary)
            .accessibilityLabel("Impostazioni")
        }
        .padding(.top, 8)
    }

    private func section<Content: View>(_ title: String, trailing: String? = nil, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(title: title, trailing: trailing).padding(.top, 6)
            content()
        }
    }

    /// Il peso corporeo si registra anche senza aver mai fatto un allenamento,
    /// quindi la sua card resta sempre a schermo.
    private var content: some View {
        ScrollView {
            VStack(spacing: 14) {
                header

                if let s = stats, hasTrainingData {
                    StatStrip(stats: s)
                    let totals = Medals.totals(medalList)
                    section("Medaglie", trailing: "\(totals.earned) / \(totals.total)") {
                        VStack(spacing: 10) { ForEach(medalList) { MedalRow(medal: $0) } }
                    }
                    section("Attività", trailing: "ultime 14 settimane") { HeatmapCard(perDay: s.perDay, count: s.last98Days) }
                    section("Volume settimanale", trailing: "ultime 12 settimane") { WeeklyBarsCard(weeks: s.weeks) }
                    if !s.groups.isEmpty { section("Dove va il lavoro", trailing: "ultimi 30 giorni") { WorkDonutCard(groups: s.groups) } }
                }

                BodyWeightCard()

                if let lift = growingLift { section("Il tuo sollevamento in crescita") { LiftChartCard(exercise: lift) } }
                if !movers.isEmpty { moversCard }
                if !records.isEmpty { recentRecordsCard }

                if !hasTrainingData { emptyHint }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 28)
        }
    }

    private var emptyHint: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 6) {
                Label("Ancora nessun allenamento", systemImage: "chart.line.uptrend.xyaxis")
                    .font(Theme.rounded(15, .bold))
                    .foregroundStyle(Theme.textPrimary)
                Text("Registra un massimale o completa una sessione: qui compariranno volumi, record e andamento.")
                    .font(Theme.rounded(12, .medium))
                    .foregroundStyle(Theme.textSecondary)
            }
        }
    }

    private var moversCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Cresciuti di più", trailing: "ultimi 90 giorni")

                ForEach(movers) { mover in
                    HStack(spacing: 12) {
                        ExerciseThumbnail(exercise: mover.exercise, size: 32)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(mover.exercise.name)
                                .font(Theme.rounded(14, .semibold))
                                .foregroundStyle(Theme.textPrimary)
                                .lineLimit(1)
                            Text(mover.previous > 0
                                 ? "\(Fmt.weight(mover.previous, unit: settings.unit)) → \(Fmt.weight(mover.current, unit: settings.unit))"
                                 : "primo massimale: \(Fmt.weight(mover.current, unit: settings.unit))")
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }

                        Spacer()

                        if mover.previous > 0 {
                            DeltaBadge(percent: mover.deltaPercent)
                        } else {
                            PRBadge(text: "NUOVO")
                        }
                    }
                }
            }
        }
    }

    private var recentRecordsCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Ultimi record")

                ForEach(records.prefix(8)) { record in
                    HStack(spacing: 12) {
                        Text(Fmt.shortDate(record.date))
                            .font(Theme.rounded(11, .semibold))
                            .foregroundStyle(Theme.textTertiary)
                            .frame(width: 52, alignment: .leading)

                        Text(record.exercise?.name ?? "Esercizio rimosso")
                            .font(Theme.rounded(14, .semibold))
                            .foregroundStyle(Theme.textPrimary)
                            .lineLimit(1)

                        Spacer()

                        Text(Fmt.setLine(weight: record.weight, reps: record.reps, unit: settings.unit))
                            .font(Theme.rounded(14, .bold))
                            .foregroundStyle(record.isEstimated ? Theme.textSecondary : Theme.textPrimary)
                    }
                }
            }
        }
    }
}
