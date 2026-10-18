import SwiftUI
import SwiftData

/// La prima scheda: una montagna con una frase, poi cosa fare adesso, i numeri, l'attività, le medaglie e le novità degli amici.
struct HomeView: View {
    @Environment(AppSettings.self) private var settings
    @Query(sort: \Workout.date, order: .reverse) private var workouts: [Workout]
    @Query(sort: \Exercise.sortIndex) private var exercises: [Exercise]
    @Query(sort: \WorkoutTemplate.sortIndex) private var templates: [WorkoutTemplate]
    @Query(sort: \WorkoutProgram.sortIndex) private var programs: [WorkoutProgram]

    private let nav = AppNav.shared
    private let social = SocialStore.shared
    private let sync = SyncService.shared

    @State private var stats: HomeStats?
    @State private var choosingGoal = false

    private var active: Workout? { workouts.first(where: \.isActive) }
    /// Il giorno che tocca, nella scheda in uso.
    private var nextTemplate: WorkoutTemplate? {
        Programs.next(program: Programs.active(programs, settings: settings), templates: templates, workouts: workouts)
    }

    private var displayName: String {
        let full = sync.credentials?.name ?? ""
        return full.split(separator: " ").first.map(String.init) ?? (full.isEmpty ? "Peak" : full)
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
            ScrollView {
                VStack(spacing: 14) {
                    if let s = stats {
                        HomeHero(name: displayName, streak: s.streak, weekSessions: s.weekSessions, goal: s.goal, onAvatar: { nav.tab = .me }, onChangeGoal: { choosingGoal = true })
                        nextBar
                        StatStrip(stats: s)

                        medalsSection
                        recordsSection
                        newsSection
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 28)
            }
            .screenBackground(settings.accentColor)
            .toolbar(.hidden, for: .navigationBar)
        }
        .task(id: workouts.count + exercises.count + exercises.reduce(0) { $0 + $1.recordList.count }) {
            stats = HomeStats(workouts: workouts, exercises: exercises, chosenGoal: settings.weeklyGoal)
        }
        .onChange(of: settings.weeklyGoal) { stats = HomeStats(workouts: workouts, exercises: exercises, chosenGoal: settings.weeklyGoal) }
        .fullScreenCover(isPresented: $choosingGoal) {
            GoalPickerView(current: settings.weeklyGoal, suggested: Stats.weeklyGoal(workouts: workouts.filter { !$0.isActive })) { choosingGoal = false }
        }
        .task { await social.refresh() }
    }

    // MARK: - Pezzi

    private var nextBar: some View {
        let title: String
        let subtitle: String
        let symbol: String
        if let active {
            title = "Riprendi allenamento"
            subtitle = "\(active.workingSetCount) serie · \(Fmt.volume(active.totalVolume, unit: settings.unit))"
            symbol = "arrow.triangle.2.circlepath"
        } else if let next = nextTemplate {
            title = "Avvia \(next.name)"
            subtitle = next.items.count == 1 ? "1 esercizio" : "\(next.items.count) esercizi"
            symbol = TemplateIcon.symbol(TemplateIcon.resolved(next.iconRaw, name: next.name))
        } else {
            title = "Inizia un allenamento"
            subtitle = "Crea un giorno con i tuoi esercizi, oppure allenati a mano."
            symbol = "dumbbell.fill"
        }
        return NextWorkoutBar(title: title, subtitle: subtitle, symbol: symbol) {
            if active != nil { nav.resumeActive = true } else { nav.templateToStart = nextTemplate?.uuid }
            nav.tab = .workouts
        }
        .tourTarget("start")
    }

    private func section<Content: View>(_ title: String, hint: String? = nil, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(title: title).padding(.top, 6)
            if let hint {
                Text(hint).font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textTertiary)
            }
            content()
        }
    }

    private var medalsSection: some View {
        let totals = Medals.totals(medalList)
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                SectionHeader(title: "Medaglie")
                Button("\(totals.earned) / \(totals.total) · Vedi tutto") { nav.tab = .me }
                    .font(Theme.rounded(13, .bold)).foregroundStyle(settings.accentColor).buttonStyle(.plain)
            }
            .padding(.top, 6)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(medalList) { MedalChip(medal: $0) }
                }
            }
        }
    }

    private var recordsSection: some View {
        let latest = exercises.compactMap { e in e.recordsByDateDesc.first.map { (e, $0) } }.sorted { $0.1.date > $1.1.date }.prefix(4)
        return Group {
            if !latest.isEmpty {
                section("Record recenti") {
                    GlassCard(padding: 6) {
                        VStack(spacing: 0) {
                            ForEach(Array(latest.enumerated()), id: \.offset) { i, pair in
                                HStack(spacing: 12) {
                                    Image(systemName: "trophy.fill").font(.system(size: 14)).foregroundStyle(Color(hex: 0xFFD45E))
                                        .frame(width: 34, height: 34).background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color(hex: 0xFFD45E).opacity(0.14)))
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(pair.0.name).font(Theme.rounded(15, .bold)).lineLimit(1)
                                        Text(Fmt.relativeDay(pair.1.date)).font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
                                    }
                                    Spacer()
                                    Text(Fmt.setLine(weight: pair.1.weight, reps: pair.1.reps, unit: settings.unit)).font(Theme.rounded(14, .bold))
                                }
                                .padding(.horizontal, 8).padding(.vertical, 8)
                                if i < latest.count - 1 { Divider().overlay(Theme.stroke) }
                            }
                        }
                    }
                }
            }
        }
    }

    private var newsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                SectionHeader(title: "Novità degli amici")
                Button(social.friendCount > 0 ? "Amici" : "Invita") { nav.tab = .friends }
                    .font(Theme.rounded(13, .bold)).foregroundStyle(settings.accentColor).buttonStyle(.plain)
            }
            .padding(.top, 6)
            if !social.isAvailable {
                note("Collega l'account yG per vedere i record dei tuoi amici.")
            } else if let news = social.news, !news.isEmpty {
                GlassCard(padding: 6) {
                    VStack(spacing: 0) {
                        ForEach(Array(news.prefix(4).enumerated()), id: \.offset) { i, e in
                            NewsRow(event: e)
                            if i < min(4, news.count) - 1 { Divider().overlay(Theme.stroke) }
                        }
                    }
                }
            } else {
                note(social.friendCount > 0 ? "Nessun record dei tuoi amici negli ultimi giorni." : "Invita un amico: vedrai qui i suoi record e le classifiche.")
            }
        }
    }

    private func note(_ text: String) -> some View {
        Text(text).font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textTertiary).multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.vertical, 16)
    }
}

/// Una riga di novità: chi, su cosa, quanto. Anche nella scheda Amici.
struct NewsRow: View {
    @Environment(AppSettings.self) private var settings
    let event: NewsEvent

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text("@\(event.username) · \(Catalog.entry(id: event.catalog)?.it ?? event.name)").font(Theme.rounded(14, .bold)).lineLimit(1)
                Text(Fmt.relativeDay(event.date)).font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                Text(Fmt.setLine(weight: event.weight, reps: event.reps, unit: settings.unit)).font(Theme.rounded(14, .bold))
                if event.passedYou { Text("Ti ha superato").font(Theme.rounded(11, .semibold)).foregroundStyle(Theme.negative) }
            }
        }
        .padding(.horizontal, 8).padding(.vertical, 8)
    }
}
