import SwiftUI
import SwiftData

/// Il tuo profilo: tutto quello che la Home mostra in breve, per esteso. In alto a destra le impostazioni.
struct MeView: View {
    @Environment(AppSettings.self) private var settings

    @Query private var weights: [BodyWeightEntry]

    private let nav = AppNav.shared
    private let insights = Insights.shared
    private let social = SocialStore.shared
    private let sync = SyncService.shared

    @State private var showSettings = false

    private var hasTrainingData: Bool { (insights.stats?.finished ?? 0) > 0 || !insights.latestRecords.isEmpty }

    private var fullName: String { sync.credentials?.name ?? "" }
    private func medals(_ s: HomeStats) -> [Medal] {
        Medals.list(MedalInput(
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
        .fullScreenCover(isPresented: $showSettings) { SettingsView() }
    }

    private var header: some View {
        HStack(spacing: 14) {
            Text(String(fullName.first ?? "P").uppercased())
                .font(Theme.rounded(28, .heavy))
                .foregroundStyle(Color.black.opacity(0.8))
                .frame(width: 64, height: 64)
                .background(Circle().fill(LinearGradient(colors: [settings.accentColor, settings.accentColor.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing)))
                .shadow(color: settings.accentColor.opacity(0.35), radius: 10, y: 6)
            Text(fullName.isEmpty ? "Me" : fullName).font(Theme.rounded(26, .heavy)).lineLimit(1).minimumScaleFactor(0.7)
            Spacer()
            Button { showSettings = true } label: {
                Image(systemName: "gearshape")
                    .font(.system(size: 18, weight: .regular))
                    .frame(width: 44, height: 44)
                    .glassSurface(radius: 22)
            }
            .buttonStyle(.plain)
            .foregroundStyle(Theme.textPrimary)
            .accessibilityLabel("Impostazioni")
        }
        .padding(.top, 8)
    }

    private var friendsButton: some View {
        Button { nav.tab = .friends } label: {
            Label(social.friendCount > 0 ? (social.friendCount == 1 ? "1 amico" : "\(social.friendCount) amici") : "Aggiungi amici", systemImage: "person.2")
                .font(Theme.rounded(15, .semibold))
                .frame(maxWidth: .infinity, minHeight: 50)
                .glassSurface(radius: 16)
        }
        .buttonStyle(.plain)
        .foregroundStyle(Theme.textPrimary)
    }

    private func overview(_ s: HomeStats) -> some View {
        func cell(_ symbol: String, _ value: String, _ label: String) -> some View {
            HStack(spacing: 12) {
                Image(systemName: symbol).font(.system(size: 18, weight: .semibold)).foregroundStyle(settings.accentColor).frame(width: 26)
                VStack(alignment: .leading, spacing: 1) {
                    Text(value).font(Theme.rounded(22, .heavy)).lineLimit(1).minimumScaleFactor(0.6)
                    Text(label).font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
            }
        }
        return GlassCard {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible())], alignment: .leading, spacing: 18) {
                cell("flame", String(s.streak), "Settimane di fila")
                cell("dumbbell", String(s.finished), "Allenamenti")
                cell("scalemass", Fmt.volume(s.totalVolume, unit: settings.unit), "Sollevati")
                cell("trophy", String(s.recordCount), "Record")
            }
        }
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

                friendsButton

                if let s = insights.stats, hasTrainingData {
                    section("Panoramica") { overview(s) }
                    let medalList = medals(s)
                    let totals = Medals.totals(medalList)
                    section("Medaglie", trailing: "\(totals.earned) / \(totals.total)") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) { ForEach(medalList) { MedalChip(medal: $0) } }
                        }
                        VStack(spacing: 10) { ForEach(medalList) { MedalRow(medal: $0) } }
                    }
                    section("Attività", trailing: "ultime 14 settimane") { HeatmapCard(perDay: s.perDay, count: s.last98Days) }
                    section("Volume settimanale", trailing: "ultime 12 settimane") { WeeklyBarsCard(weeks: s.weeks) }
                    if !s.groups.isEmpty { section("Dove va il lavoro", trailing: "ultimi 30 giorni") { WorkDonutCard(groups: s.groups) } }
                }

                BodyWeightCard()

                if !insights.progression.isEmpty { section("Progressione") { ProgressionCard(series: insights.progression) } }
                if !insights.movers.isEmpty { moversCard }
                if !insights.latestRecords.isEmpty { recentRecordsCard }

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

                ForEach(insights.movers) { mover in
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

                ForEach(insights.latestRecords) { record in
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
