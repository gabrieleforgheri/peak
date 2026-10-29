import SwiftUI
import SwiftData

struct WorkoutsListView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings

    @Query(sort: \Workout.date, order: .reverse) private var workouts: [Workout]
    @Query(sort: \WorkoutTemplate.sortIndex) private var templates: [WorkoutTemplate]
    @Query(sort: \WorkoutProgram.sortIndex) private var programs: [WorkoutProgram]
    @Query private var exercises: [Exercise]

    @State private var path: [Workout] = []
    @State private var showingFocusPicker = false
    @State private var editingTemplate: WorkoutTemplate?
    @State private var showingNewTemplate = false
    @State private var editingProgram: WorkoutProgram?
    @State private var showingNewProgram = false
    @State private var choosingGoal = false

    private let nav = AppNav.shared
    private let insights = Insights.shared
    private let sync = SyncService.shared

    private var displayName: String {
        let full = sync.credentials?.name ?? ""
        return full.split(separator: " ").first.map(String.init) ?? (full.isEmpty ? "Peak" : full)
    }

    /// Il giorno di oggi, se la scheda in uso ha i giorni fissi.
    private var todayTemplate: WorkoutTemplate? { Programs.today(program: program, templates: templates) }

    private var program: WorkoutProgram? { Programs.active(programs, settings: settings) }
    private var days: [WorkoutTemplate] { Programs.days(of: program, in: templates) }

    private var activeWorkout: Workout? { workouts.first(where: \.isActive) }

    var body: some View {
        let finishedWorkouts = workouts.filter { !$0.isActive }
        NavigationStack(path: $path) {
            List {
                if let s = insights.stats {
                    Section {
                        WorkoutHero(name: displayName, streak: s.streak, weekSessions: s.weekSessions, goal: s.goal, onAvatar: { nav.tab = .me }, onChangeGoal: { choosingGoal = true })
                            .appear(0)
                            .plainListRow()
                            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 0, trailing: 16))
                    }
                }

                Section {
                    startCard
                        .appear(1)
                        .plainListRow()
                        .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 10, trailing: 16))
                }

                Section {
                    if programs.count > 1 {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(programs) { p in
                                    Button { settings.activeProgram = p.uuid.uuidString; Haptics.tap() } label: {
                                        Text(p.name).font(Theme.rounded(13, .semibold))
                                            .padding(.horizontal, 12).padding(.vertical, 8)
                                            .background(p.uuid == program?.uuid ? settings.accentColor.opacity(0.22) : Theme.card, in: Capsule())
                                            .foregroundStyle(p.uuid == program?.uuid ? settings.accentColor : Theme.textSecondary)
                                    }
                                    .buttonStyle(PressStyle())
                                }
                            }
                        }
                        .appear(2)
                        .plainListRow()
                    }
                    if let program {
                        HStack {
                            Text(program.name).font(Theme.rounded(15, .bold)).lineLimit(1)
                            Spacer()
                            Button("Modifica scheda") { editingProgram = program }
                                .font(Theme.rounded(13, .bold)).foregroundStyle(settings.accentColor).buttonStyle(.plain)
                        }
                        .appear(2)
                        .plainListRow()
                    }
                    if days.isEmpty {
                        Text("Un giorno è una lista di esercizi con serie e ripetizioni: lo scegli quando inizi e li trovi già pronti.")
                            .font(Theme.rounded(13, .medium))
                            .foregroundStyle(Theme.textTertiary)
                            .plainListRow()
                    } else {
                        ForEach(Array(days.enumerated()), id: \.element.persistentModelID) { i, template in
                            Button {
                                editingTemplate = template
                            } label: {
                                GlassCard(padding: 14) {
                                    HStack(spacing: 12) {
                                        TemplateIconView(icon: template.iconRaw, name: template.name, size: 20)
                                            .frame(width: 30)
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(template.name)
                                                .font(Theme.rounded(15, .bold))
                                                .foregroundStyle(Theme.textPrimary)
                                            Text(template.items.count == 1 ? "1 esercizio" : "\(template.items.count) esercizi")
                                                .font(Theme.rounded(11, .medium))
                                                .foregroundStyle(Theme.textTertiary)
                                        }
                                        Spacer()
                                        if program?.useDays == true {
                                            Text(template.weekday >= 0 ? Programs.weekdays[template.weekday] : "Nessuno")
                                                .font(Theme.rounded(11, .bold))
                                                .foregroundStyle(template.weekday >= 0 ? settings.accentColor : Theme.textTertiary)
                                        }
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 12, weight: .semibold))
                                            .foregroundStyle(Theme.textTertiary)
                                    }
                                }
                            }
                            .buttonStyle(PressStyle())
                            .appear(3 + i, key: "day-\(template.uuid)")
                            .plainListRow()
                        }
                    }
                    Button { showingNewTemplate = true } label: { Label("Nuovo giorno", systemImage: "plus") }
                        .buttonStyle(SecondaryButtonStyle())
                        .tourTarget("new-template")
                        .plainListRow()
                } header: {
                    HStack {
                        SectionHeader(title: "Scheda")
                        Button("Nuova scheda") { showingNewProgram = true }
                            .font(Theme.rounded(13, .bold))
                            .foregroundStyle(settings.accentColor)
                            .buttonStyle(.plain)
                    }
                    .textCase(nil)
                    .padding(.horizontal, 2)
                }

                if finishedWorkouts.isEmpty {
                    Section {
                        Text("Gli allenamenti conclusi finiscono qui, con volume e record della giornata.")
                            .font(Theme.rounded(13, .medium))
                            .foregroundStyle(Theme.textTertiary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                            .plainListRow()
                    }
                } else {
                    Section {
                        SectionHeader(title: "Storico", trailing: "\(finishedWorkouts.count)")
                            .plainListRow()
                    }
                    MonthSections(
                        items: finishedWorkouts,
                        date: \.date,
                        count: { $0 == 1 ? "1 allenamento" : "\($0) allenamenti" }
                    ) { workout in
                        ZStack {
                            NavigationLink(value: workout) { EmptyView() }.opacity(0)
                            WorkoutRow(workout: workout)
                        }
                        .appear(key: "wk-\(workout.uuid)")
                        .plainListRow()
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                delete(workout)
                            } label: {
                                Label("Elimina", systemImage: "trash")
                            }
                        }
                    }
                }
            }
            .listStyle(.plain)
            .screenBackground(settings.accentColor)
            .navigationTitle("Allenamenti")
            // niente titolo grande: in cima c'è già la montagna; il titolo resta per il tasto indietro
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Workout.self) { workout in
                WorkoutSessionView(workout: workout)
            }
            .onChange(of: settings.weeklyGoal) { Insights.shared.refresh() }
            .fullScreenCover(isPresented: $choosingGoal) {
                GoalPickerView(current: settings.weeklyGoal, suggested: Stats.weeklyGoal(workouts: finishedWorkouts)) { choosingGoal = false }
            }
            .sheet(isPresented: $showingFocusPicker) {
                FocusPickerSheet(confirmTitle: "Inizia", onStartTemplate: { startWorkout(template: $0) }) { name, focus in
                    startWorkout(name: name, focus: focus)
                }
            }
            .onAppear(perform: consumeNavRequests)
            .onChange(of: AppNav.shared.templateToStart) { consumeNavRequests() }
            .onChange(of: AppNav.shared.resumeActive) { consumeNavRequests() }
            .sheet(item: $editingTemplate) { template in
                TemplateEditorView(template: template)
            }
            .sheet(isPresented: $showingNewTemplate) {
                TemplateEditorView()
            }
            .sheet(item: $editingProgram) { ProgramEditorView(program: $0) }
            .sheet(isPresented: $showingNewProgram) { ProgramEditorView() }
        }
    }

    /// La sessione in corso in una card; altrimenti il pulsante per partire, senza cornice sotto la montagna.
    @ViewBuilder
    private var startCard: some View {
        if let activeWorkout {
            GlassCard(padding: 18, tint: settings.accentColor) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(settings.accentColor)
                            .frame(width: 8, height: 8)
                        Text("SESSIONE IN CORSO")
                            .font(Theme.rounded(11, .bold))
                            .tracking(0.8)
                            .foregroundStyle(settings.accentColor)
                        Spacer()
                        Text(Fmt.dateTime(activeWorkout.date))
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }

                    if !activeWorkout.name.isEmpty {
                        Text(activeWorkout.name)
                            .font(Theme.rounded(19, .bold))
                            .foregroundStyle(Theme.textPrimary)
                    }

                    HStack(spacing: 18) {
                        MiniStat(value: "\(activeWorkout.workingSetCount)", label: "serie")
                        MiniStat(value: Fmt.volume(activeWorkout.totalVolume, unit: settings.unit), label: "volume")
                        MiniStat(value: "\(activeWorkout.exercisesInOrder.count)", label: "esercizi")
                    }

                    Button {
                        path.append(activeWorkout)
                    } label: {
                        Label("Riprendi allenamento", systemImage: "play.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                }
            }
        } else if let today = todayTemplate {
            VStack(spacing: 10) {
                NextWorkoutBar(
                    title: "Avvia \(today.name)",
                    subtitle: today.items.count == 1 ? "1 esercizio" : "\(today.items.count) esercizi",
                    symbol: TemplateIcon.symbol(TemplateIcon.resolved(today.iconRaw, name: today.name))
                ) { startWorkout(template: today) }
                .tourTarget("start")

                Button("oppure scegli tu") { showingFocusPicker = true }
                    .font(Theme.rounded(13, .bold)).foregroundStyle(settings.accentColor).buttonStyle(.plain)
            }
        } else {
            Button {
                showingFocusPicker = true
            } label: {
                Label("Avvia allenamento", systemImage: "bolt.fill")
            }
            .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
            .tourTarget("start")
        }
    }

    /// Dall'esterno (il tutorial, un collegamento) possono chiedere di riprendere la sessione o di avviare un giorno: si fa appena questa scheda è in vista.
    private func consumeNavRequests() {
        let nav = AppNav.shared
        if nav.resumeActive {
            nav.resumeActive = false
            if let activeWorkout, path.last !== activeWorkout { path.append(activeWorkout) }
        }
        if let id = nav.templateToStart {
            nav.templateToStart = nil
            if let template = templates.first(where: { $0.uuid == id }) { startWorkout(template: template) } else { showingFocusPicker = true }
        }
    }

    private func startWorkout(name: String, focus: Set<MuscleGroup>) {
        let workout = Workout()
        workout.name = name
        workout.focus = focus
        begin(workout)
    }

    /// Con una scheda: lista già pronta (una copia), il suo nome, i gruppi presi dagli esercizi.
    private func startWorkout(template: WorkoutTemplate) {
        let known = Dictionary(exercises.map { ($0.uuid, $0) }, uniquingKeysWith: { first, _ in first })
        let items = template.items.filter { known[$0.exercise] != nil }
        let workout = Workout()
        workout.name = template.name
        workout.templateRaw = template.uuid.uuidString
        workout.plan = items
        workout.focus = Set(items.compactMap { known[$0.exercise]?.muscleGroup })
        begin(workout)
    }

    private func begin(_ workout: Workout) {
        context.insert(workout)
        try? context.save()
        WorkoutActivity.start(for: workout, unit: settings.unit)
        Haptics.commit()
        path.append(workout)
    }

    private func delete(_ workout: Workout) {
        withAnimation {
            RecordService.delete(workout, context: context)
        }
        Haptics.warning()
    }
}

// MARK: - Riga

private struct WorkoutRow: View {
    @Environment(AppSettings.self) private var settings
    let workout: Workout

    private var prCount: Int { workout.setList.filter(\.isPersonalRecord).count }

    private var groups: [MuscleGroup] {
        var seen: [MuscleGroup] = []
        for exercise in workout.exercisesInOrder where !seen.contains(exercise.muscleGroup) {
            seen.append(exercise.muscleGroup)
        }
        return seen
    }

    var body: some View {
        GlassCard(padding: 14) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(workout.name.isEmpty ? Fmt.relativeDay(workout.date) : workout.name)
                            .font(Theme.rounded(15, .bold))
                            .foregroundStyle(Theme.textPrimary)
                        if !workout.name.isEmpty {
                            Text(Fmt.relativeDay(workout.date))
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                    }
                    if prCount > 0 {
                        PRBadge(text: prCount == 1 ? "1 PR" : "\(prCount) PR")
                    }
                    Spacer()
                    if let duration = workout.duration {
                        Text(Fmt.duration(duration))
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }

                if !workout.note.isEmpty {
                    Text(workout.note)
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(2)
                }

                HStack(spacing: 16) {
                    MiniStat(value: "\(workout.workingSetCount)", label: "serie")
                    MiniStat(value: Fmt.volume(workout.totalVolume, unit: settings.unit), label: "volume")
                    Spacer()
                    HStack(spacing: 5) {
                        ForEach(groups.prefix(4), id: \.self) { group in
                            Image(systemName: group.symbol)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(group.tint)
                        }
                    }
                }
            }
        }
    }
}

struct MiniStat: View {
    let value: String
    let label: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(value)
                .font(Theme.rounded(15, .bold))
                .foregroundStyle(Theme.textPrimary)
                .contentTransition(.numericText()) // scorre dentro l'`animation` della sessione
            Text(label)
                .font(Theme.rounded(11, .medium))
                .foregroundStyle(Theme.textTertiary)
        }
    }
}
