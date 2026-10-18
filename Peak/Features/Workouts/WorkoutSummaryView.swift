import SwiftUI
import SwiftData

/// Riepilogo di fine sessione, con i record ottenuti.
struct WorkoutSummaryView: View {
    /// Una vista per volta della figura dei muscoli della seduta.
    @State private var figureSide: AnatomyView = .front

    /// I muscoli di tutti gli esercizi della seduta, senza doppioni.
    private var workoutMuscles: [MuscleId] {
        var seen = Set<MuscleId>()
        var out: [MuscleId] = []
        for exercise in workout.exercisesInOrder {
            for muscle in exercise.figureMuscles where !seen.contains(muscle) {
                seen.insert(muscle)
                out.append(muscle)
            }
        }
        return out
    }
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    @Query private var exercises: [Exercise]
    @Query(sort: \Workout.date, order: .reverse) private var allWorkouts: [Workout]
    @State private var showingSaveTemplate = false

    let workout: Workout
    let onDone: () -> Void

    private var records: [WorkoutSet] { RecordService.personalRecords(in: workout) }

    // ---- la settimana di questo allenamento: giorni fatti, obiettivo, settimane di fila ----
    private var calendar: Calendar { var c = Calendar.current; c.firstWeekday = 2; return c }
    private var doneUntilNow: [Workout] { allWorkouts.filter { !$0.isActive && $0.date <= workout.date } }
    private var thisWeek: [Workout] {
        let start = calendar.dateInterval(of: .weekOfYear, for: workout.date)?.start ?? workout.date
        return doneUntilNow.filter { $0.date >= start }
    }
    /// 0 = lunedì.
    private func weekday(_ d: Date) -> Int { (Calendar.current.component(.weekday, from: d) + 5) % 7 }
    private var goal: Int { settings.weeklyGoal > 0 ? settings.weeklyGoal : Stats.weeklyGoal(workouts: doneUntilNow, now: workout.date) }
    private var streakNow: Int { Stats.weeklyStreak(workouts: doneUntilNow, now: workout.date) }
    private var streakBefore: Int { Stats.weeklyStreak(workouts: doneUntilNow.filter { $0.uuid != workout.uuid }, now: workout.date) }

    /// Le medaglie che questo allenamento ha fatto salire: prima e dopo.
    private var movedMedals: [(after: Medal, before: Medal)] {
        let friends = SocialStore.shared.friendCount
        let before = Medals.at(workouts: allWorkouts, exercises: exercises, friends: friends, until: workout.date, inclusive: false)
        let after = Medals.at(workouts: allWorkouts, exercises: exercises, friends: friends, until: workout.date, inclusive: true)
        return zip(after, before).filter { $0.value > $1.value }.map { ($0, $1) }
    }

    private var weekCard: some View {
        let days = Set(thisWeek.map { weekday($0.date) })
        let today = weekday(workout.date)
        let letters = ["L", "M", "M", "G", "V", "S", "D"]
        return GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Image(systemName: "flame.fill").font(.system(size: 20, weight: .bold)).foregroundStyle(settings.accentColor)
                    Text("\(streakNow)").font(Theme.rounded(24, .heavy))
                    Text(streakNow == 1 ? "settimana di fila" : "settimane di fila")
                        .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary)
                    Spacer()
                    if streakNow > streakBefore { PRBadge(text: "+1") }
                }
                HStack {
                    ForEach(0..<7, id: \.self) { i in
                        VStack(spacing: 4) {
                            Image(systemName: days.contains(i) ? "checkmark" : "")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(Color.black.opacity(0.8))
                                .frame(width: 34, height: 34)
                                .background(Circle().fill(days.contains(i) ? settings.accentColor : Color.white.opacity(0.08)))
                                .overlay(Circle().strokeBorder(settings.accentColor.opacity(i == today ? 0.8 : 0), lineWidth: 2).padding(-3))
                            Text(letters[i]).font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(thisWeek.count >= goal ? "Obiettivo raggiunto!" : "Obiettivo della settimana")
                            .font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                        Spacer()
                        Text("\(thisWeek.count) / \(goal)").font(Theme.rounded(13, .bold))
                    }
                    HStack(spacing: 6) {
                        ForEach(0..<max(1, goal), id: \.self) { k in
                            Capsule().fill(k < thisWeek.count ? settings.accentColor : Color.white.opacity(0.1)).frame(height: 12)
                        }
                    }
                    if thisWeek.count > goal {
                        Text("+\(thisWeek.count - goal) oltre l'obiettivo").font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
                    }
                }
            }
        }
    }

    private var medalsCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                SectionHeader(title: "Medaglie")
                ForEach(movedMedals, id: \.after.id) { pair in
                    let leveled = pair.after.tier > pair.before.tier
                    let from = leveled ? 0 : pair.before.progress
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Image(systemName: pair.after.kind.symbol).foregroundStyle(settings.accentColor)
                            Text(pair.after.kind.title).font(Theme.rounded(14, .bold))
                            Spacer()
                            if leveled { PRBadge(text: ["", "BRONZO", "ARGENTO", "ORO"][pair.after.tier]) }
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.white.opacity(0.1))
                                Capsule().fill(settings.accentColor.opacity(0.55)).frame(width: geo.size.width * from)
                                Capsule().fill(settings.accentColor)
                                    .frame(width: geo.size.width * pair.after.progress)
                                    .mask(Rectangle().padding(.leading, geo.size.width * from))
                            }
                        }
                        .frame(height: 8)
                    }
                }
            }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 6) {
                        Image(systemName: records.isEmpty ? "checkmark.circle.fill" : "trophy.fill")
                            .font(.system(size: 44, weight: .semibold))
                            .foregroundStyle(records.isEmpty ? settings.accentColor : Theme.positive)
                            .padding(.top, 12)
                        Text(records.isEmpty ? "Allenamento concluso" : "Nuovi massimali!")
                            .font(Theme.rounded(24, .bold))
                            .foregroundStyle(Theme.textPrimary)
                        Text(Fmt.dateTime(workout.date))
                            .font(Theme.rounded(12, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                    .padding(.bottom, 4)

                    HStack(spacing: 10) {
                        StatTile(
                            symbol: "clock",
                            title: "Durata",
                            value: workout.duration.map { Fmt.duration($0) } ?? "—",
                            tint: settings.accentColor
                        )
                        StatTile(
                            symbol: "scalemass",
                            title: "Volume",
                            value: Fmt.volume(workout.totalVolume, unit: settings.unit),
                            tint: settings.accentColor
                        )
                    }

                    HStack(spacing: 10) {
                        StatTile(
                            symbol: "list.number",
                            title: "Serie",
                            value: "\(workout.workingSetCount)",
                            tint: settings.accentColor
                        )
                        StatTile(
                            symbol: "dumbbell",
                            title: "Esercizi",
                            value: "\(workout.exercisesInOrder.count)",
                            tint: settings.accentColor
                        )
                    }

                    weekCard
                    if !movedMedals.isEmpty { medalsCard }

                    if !workoutMuscles.isEmpty {
                        GlassCard {
                            VStack(spacing: 12) {
                                SectionHeader(title: "Muscoli lavorati")
                                MuscleFigureView(muscles: workoutMuscles, view: figureSide, zoom: false)
                                    .frame(maxWidth: 220)
                                Button(figureSide == .front ? "Vedi di schiena" : "Vedi di fronte") {
                                    figureSide = figureSide == .front ? .back : .front
                                }
                                .buttonStyle(.bordered)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }

                    if !records.isEmpty {
                        GlassCard(tint: Theme.positive) {
                            VStack(alignment: .leading, spacing: 12) {
                                SectionHeader(title: "Record della giornata", trailing: "\(records.count)")
                                ForEach(records) { set in
                                    HStack(spacing: 10) {
                                        Image(systemName: set.exercise?.muscleGroup.symbol ?? "dumbbell")
                                            .font(.system(size: 12, weight: .semibold))
                                            .foregroundStyle(Theme.positive)
                                            .frame(width: 28, height: 28)
                                            .background(Theme.positive.opacity(0.14), in: RoundedRectangle(cornerRadius: 9, style: .continuous))

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(set.exercise?.name ?? "Esercizio rimosso")
                                                .font(Theme.rounded(14, .semibold))
                                                .foregroundStyle(Theme.textPrimary)
                                            Text(Fmt.setLine(weight: set.weight, reps: set.reps, unit: settings.unit))
                                                .font(Theme.rounded(11, .medium))
                                                .foregroundStyle(Theme.textTertiary)
                                        }

                                        Spacer()

                                        Text(Fmt.weightValue(set.weight, unit: settings.unit))
                                            .font(Theme.rounded(15, .bold))
                                            .foregroundStyle(Theme.positive)
                                    }
                                }
                            }
                        }
                    }

                    if !workout.setList.isEmpty {
                        Button {
                            showingSaveTemplate = true
                        } label: {
                            Label("Salva come giorno", systemImage: "list.bullet")
                        }
                        .buttonStyle(SecondaryButtonStyle())
                        .padding(.top, 4)
                    }

                    Button {
                        dismiss()
                        onDone()
                    } label: {
                        Text("Fine")
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                    .padding(.top, 4)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 28)
            }
            .screenBackground(settings.accentColor)
            .navigationBarTitleDisplayMode(.inline)
            .interactiveDismissDisabled()
            .sheet(isPresented: $showingSaveTemplate) {
                let known = Set(exercises.map(\.uuid))
                TemplateEditorView(initialName: workout.name, initialItems: workout.items(.done, exists: { known.contains($0) }))
            }
        }
    }
}
