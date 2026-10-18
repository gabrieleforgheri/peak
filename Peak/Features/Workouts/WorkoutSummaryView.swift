import SwiftUI
import SwiftData

/// Riepilogo di fine sessione, con i record ottenuti.
struct WorkoutSummaryView: View {
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

    /// Tutto quello che il riepilogo mostra e che costa un giro sul database: si calcola una volta
    /// sola (i dati non cambiano mentre il riepilogo è aperto) e poi si disegna da qui.
    private struct Snapshot {
        /// I giorni della settimana con un allenamento (0 = lunedì) e il giorno di questo allenamento.
        let days: Set<Int>
        let today: Int
        let goal: Int
        /// Gli allenamenti della settimana, questo compreso.
        let thisWeek: Int
        let streakNow: Int
        let streakBefore: Int
        /// Le medaglie che questo allenamento ha fatto salire: dopo e prima.
        let movedMedals: [(after: Medal, before: Medal)]
        let records: [WorkoutSet]
    }

    /// Fissata alla prima comparsa; fino ad allora si calcola al volo, così il primo disegno è già completo.
    @State private var cached: Snapshot?

    /// 0 = lunedì.
    private func weekday(_ d: Date) -> Int { (Calendar.current.component(.weekday, from: d) + 5) % 7 }

    private func makeSnapshot() -> Snapshot {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        let done = allWorkouts.filter { !$0.isActive && $0.date <= workout.date }
        let start = calendar.dateInterval(of: .weekOfYear, for: workout.date)?.start ?? workout.date
        let week = done.filter { $0.date >= start }
        let friends = SocialStore.shared.friendCount
        let before = Medals.at(workouts: allWorkouts, exercises: exercises, friends: friends, until: workout.date, inclusive: false)
        let after = Medals.at(workouts: allWorkouts, exercises: exercises, friends: friends, until: workout.date, inclusive: true)
        return Snapshot(
            days: Set(week.map { weekday($0.date) }),
            today: weekday(workout.date),
            goal: settings.weeklyGoal > 0 ? settings.weeklyGoal : Stats.weeklyGoal(workouts: done, now: workout.date),
            thisWeek: week.count,
            streakNow: Stats.weeklyStreak(workouts: done, now: workout.date),
            streakBefore: Stats.weeklyStreak(workouts: done.filter { $0.uuid != workout.uuid }, now: workout.date),
            movedMedals: zip(after, before).filter { $0.value > $1.value }.map { ($0, $1) },
            records: RecordService.personalRecords(in: workout)
        )
    }

    private func weekCard(_ snap: Snapshot) -> some View {
        let letters = ["L", "M", "M", "G", "V", "S", "D"]
        return GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Image(systemName: "flame.fill").font(.system(size: 20, weight: .bold)).foregroundStyle(settings.accentColor)
                    Text("\(snap.streakNow)").font(Theme.rounded(24, .heavy))
                    Text(snap.streakNow == 1 ? "settimana di fila" : "settimane di fila")
                        .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary)
                    Spacer()
                    if snap.streakNow > snap.streakBefore { PRBadge(text: "+1") }
                }
                HStack {
                    ForEach(0..<7, id: \.self) { i in
                        VStack(spacing: 4) {
                            Image(systemName: snap.days.contains(i) ? "checkmark" : "")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(Color.black.opacity(0.8))
                                .frame(width: 34, height: 34)
                                .background(Circle().fill(snap.days.contains(i) ? settings.accentColor : Color.white.opacity(0.08)))
                                .overlay(Circle().strokeBorder(settings.accentColor.opacity(i == snap.today ? 0.8 : 0), lineWidth: 2).padding(-3))
                            Text(letters[i]).font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(snap.thisWeek >= snap.goal ? "Obiettivo raggiunto!" : "Obiettivo della settimana")
                            .font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                        Spacer()
                        Text("\(snap.thisWeek) / \(snap.goal)").font(Theme.rounded(13, .bold))
                    }
                    HStack(spacing: 6) {
                        ForEach(0..<max(1, snap.goal), id: \.self) { k in
                            Capsule().fill(k < snap.thisWeek ? settings.accentColor : Color.white.opacity(0.1)).frame(height: 12)
                        }
                    }
                    if snap.thisWeek > snap.goal {
                        Text("+\(snap.thisWeek - snap.goal) oltre l'obiettivo").font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
                    }
                }
            }
        }
    }

    private func medalsCard(_ snap: Snapshot) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                SectionHeader(title: "Medaglie")
                ForEach(snap.movedMedals, id: \.after.id) { pair in
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
        let snap = cached ?? makeSnapshot()
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 6) {
                        Image(systemName: snap.records.isEmpty ? "checkmark.circle.fill" : "trophy.fill")
                            .font(.system(size: 44, weight: .semibold))
                            .foregroundStyle(snap.records.isEmpty ? settings.accentColor : Theme.positive)
                            .padding(.top, 12)
                        Text(snap.records.isEmpty ? "Allenamento concluso" : "Nuovi massimali!")
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

                    weekCard(snap)
                    if !snap.movedMedals.isEmpty { medalsCard(snap) }

                    if !workoutMuscles.isEmpty {
                        GlassCard {
                            VStack(spacing: 12) {
                                SectionHeader(title: "Muscoli lavorati")
                                // davanti e dietro affiancati: c'è posto per entrambi
                                HStack(alignment: .top, spacing: 8) {
                                    MuscleFigureView(muscles: workoutMuscles, view: .front, zoom: false)
                                    MuscleFigureView(muscles: workoutMuscles, view: .back, zoom: false)
                                }
                                .frame(maxWidth: 340)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }

                    if !snap.records.isEmpty {
                        GlassCard(tint: Theme.positive) {
                            VStack(alignment: .leading, spacing: 12) {
                                SectionHeader(title: "Record della giornata", trailing: "\(snap.records.count)")
                                ForEach(snap.records) { set in
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
            .onAppear { cached = snap }
            .sheet(isPresented: $showingSaveTemplate) {
                let known = Set(exercises.map(\.uuid))
                TemplateEditorView(initialName: workout.name, initialItems: workout.items(.done, exists: { known.contains($0) }))
            }
        }
    }
}
