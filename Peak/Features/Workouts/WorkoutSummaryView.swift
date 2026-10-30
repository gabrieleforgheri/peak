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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @Query private var exercises: [Exercise]
    @Query(sort: \Workout.date, order: .reverse) private var allWorkouts: [Workout]
    @State private var showingSaveTemplate = false

    // La festa: ogni pezzo parte spento e `play` lo accende lungo la sequenza (una volta sola).
    @State private var played = false
    @State private var heroIn = false
    @State private var tilesIn = false
    @State private var counted = false
    @State private var daysFilled = 0
    @State private var ringTick = 0
    @State private var goalFilled = 0
    @State private var goalReached = false
    @State private var flameTick = 0
    @State private var streakRolled = false
    @State private var streakBadge = false
    @State private var medalsFilled = false
    @State private var tierUp = false
    @State private var recordsIn = false
    @State private var trophyTick = 0
    @State private var heroBurst = 0
    @State private var goalBurst = 0

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

    // MARK: - La sequenza

    private func pause(_ seconds: Double) async throws {
        try await Task.sleep(for: .seconds(seconds))
    }

    /// La festa, a partire dalla comparsa (t in secondi): 0 eroe, coriandoli e suono · 0,25 tessere e numeri · 0,8 i giorni della
    /// settimana uno a uno (ogni giorno una nota più alta) · le capsule dell'obiettivo · obiettivo raggiunto · serie di settimane ·
    /// medaglie · record. Ogni gruppo parte dopo il colpo principale del suono precedente. Se la vista sparisce a metà
    /// (tasto «Fine») il task si ferma da solo: lo stato che resta non viene più disegnato.
    @MainActor
    private func play(_ snap: Snapshot) async {
        guard !played else { return }
        played = true

        // la Home non rifà la festa di quello che il riepilogo sta per mostrare
        let weekStart = Celebrated.weekStart(of: workout.date)
        if weekStart == Celebrated.weekStart() {
            Celebrated.streak = snap.streakNow
            Celebrated.store(week: snap.thisWeek, start: weekStart)
        }
        if reduceMotion { counted = true } // i numeri si vedono già finali

        do {
            // 0,0
            heroIn = true
            heroBurst += 1
            SoundFX.play(.finish)

            // 0,25
            try await pause(0.25)
            tilesIn = true
            if !reduceMotion { withAnimation(.smooth(duration: 0.8)) { counted = true } }

            // 0,8: i giorni allenati, in ordine di settimana
            try await pause(0.55)
            for k in snap.days.sorted().indices {
                daysFilled = k + 1
                SoundFX.playDay(k)
                Haptics.tap()
                try await pause(0.14)
            }
            ringTick += 1
            try await pause(0.2)

            // le capsule dell'obiettivo
            let n = snap.thisWeek
            if n > 0 {
                let step = min(0.1, 0.7 / Double(n))
                for k in 1...n {
                    goalFilled = k
                    try await pause(step)
                }
            }
            if snap.thisWeek >= snap.goal, snap.thisWeek - 1 < snap.goal {
                try await pause(0.1)
                goalReached = true
                goalBurst += 1
                SoundFX.play(.goal)
                Haptics.success()
                try await pause(0.5)
            }

            // la serie di settimane
            if snap.streakNow > snap.streakBefore {
                flameTick += 1
                streakBadge = true
                if reduceMotion { streakRolled = true } else { withAnimation(.smooth(duration: 0.8)) { streakRolled = true } }
                SoundFX.play(.streak)
                Haptics.commit()
                try await pause(0.55)
            }

            // le medaglie: le barre salgono, poi il livello nuovo
            if !snap.movedMedals.isEmpty {
                medalsFilled = true
                if snap.movedMedals.contains(where: { $0.after.tier > $0.before.tier }) {
                    try await pause(0.5)
                    tierUp = true
                    SoundFX.play(.medal)
                    Haptics.commit()
                    try await pause(0.5)
                } else {
                    try await pause(0.4)
                }
            }

            // i record
            if !snap.records.isEmpty {
                recordsIn = true
                trophyTick += 1
                SoundFX.play(.record)
                Haptics.personalRecord()
            }
        } catch {
            // interrotta (la vista è sparita): niente da sistemare
        }
    }

    // MARK: - Pezzi

    private func weekCard(_ snap: Snapshot) -> some View {
        let letters = ["L", "M", "M", "G", "V", "S", "D"]
        let order = snap.days.sorted()
        let reached = snap.thisWeek >= snap.goal
        // se è stato questo allenamento a raggiungerlo, la scritta cambia al momento della festa
        let showReached = reached && (goalReached || snap.thisWeek - 1 >= snap.goal)
        let streakShown = snap.streakNow > snap.streakBefore && !streakRolled ? snap.streakBefore : snap.streakNow
        return GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Image(systemName: "flame.fill").font(.system(size: 20, weight: .bold)).foregroundStyle(settings.accentColor)
                        .bounce(trigger: flameTick, peak: 1.3, wobble: 10)
                    Text("\(streakShown)").font(Theme.rounded(24, .heavy))
                        .contentTransition(.numericText(value: Double(streakShown)))
                    Text(snap.streakNow == 1 ? "settimana di fila" : "settimane di fila")
                        .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary)
                    Spacer()
                    if snap.streakNow > snap.streakBefore { PRBadge(text: "+1").pop(streakBadge, from: 0.6) }
                }
                HStack {
                    ForEach(0..<7, id: \.self) { i in
                        VStack(spacing: 4) {
                            DayDot(
                                on: snap.days.contains(i) && (order.firstIndex(of: i) ?? 0) < daysFilled,
                                isToday: i == snap.today,
                                ringTick: ringTick
                            )
                            Text(letters[i]).font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(showReached ? "Obiettivo raggiunto!" : "Obiettivo della settimana")
                            .font(Theme.rounded(12, .medium)).foregroundStyle(showReached ? Theme.positive : Theme.textSecondary)
                            .id(showReached)
                            .transition(reduceMotion ? .opacity : .scale(scale: 0.8, anchor: .leading).combined(with: .opacity))
                        Spacer()
                        Text("\(goalFilled) / \(snap.goal)").font(Theme.rounded(13, .bold))
                            .contentTransition(.numericText(value: Double(goalFilled)))
                            .animation(reduceMotion ? nil : .smooth(duration: 0.3), value: goalFilled)
                    }
                    .animation(reduceMotion ? .easeOut(duration: 0.2) : .spring(duration: 0.5, bounce: 0.35), value: showReached)
                    HStack(spacing: 6) {
                        ForEach(0..<max(1, snap.goal), id: \.self) { k in
                            let filled = k < goalFilled
                            Capsule().fill(Color.white.opacity(0.1)).frame(height: 12)
                                .overlay {
                                    Capsule().fill(settings.accentColor)
                                        .scaleEffect(x: filled || reduceMotion ? 1 : 0.3, anchor: .leading)
                                        .opacity(filled ? 1 : 0)
                                }
                        }
                    }
                    .animation(reduceMotion ? .easeOut(duration: 0.2) : .spring(duration: 0.3, bounce: 0.15), value: goalFilled)
                    if snap.thisWeek > snap.goal {
                        Text("+\(snap.thisWeek - snap.goal) oltre l'obiettivo").font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
                            .enter(goalFilled >= snap.thisWeek, offset: 4)
                    }
                }
            }
        }
    }

    private func medalsCard(_ snap: Snapshot) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                SectionHeader(title: "Medaglie")
                ForEach(Array(snap.movedMedals.enumerated()), id: \.element.after.id) { index, pair in
                    let leveled = pair.after.tier > pair.before.tier
                    let from = leveled ? 0 : pair.before.progress
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Image(systemName: pair.after.kind.symbol).foregroundStyle(settings.accentColor)
                            Text(pair.after.kind.title).font(Theme.rounded(14, .bold))
                            Spacer()
                            if leveled { PRBadge(text: ["", "BRONZO", "ARGENTO", "ORO"][pair.after.tier]).pop(tierUp, from: 0.6) }
                        }
                        // la barra sale dal valore di prima a quello di adesso, una dopo l'altra
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.white.opacity(0.1))
                                Capsule().fill(settings.accentColor.opacity(0.55)).frame(width: geo.size.width * from)
                                Capsule().fill(settings.accentColor)
                                    .frame(width: geo.size.width * (medalsFilled ? pair.after.progress : from))
                                    .mask(Rectangle().padding(.leading, geo.size.width * from))
                            }
                        }
                        .frame(height: 8)
                        .animation(.easeOut(duration: 0.6).delay(0.15 * Double(index)), value: medalsFilled)
                    }
                }
            }
        }
    }

    private func recordsCard(_ snap: Snapshot) -> some View {
        GlassCard(tint: Theme.positive) {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Record della giornata", trailing: "\(snap.records.count)")
                ForEach(Array(snap.records.enumerated()), id: \.element.id) { index, set in
                    let delay = 0.06 * Double(index)
                    HStack(spacing: 10) {
                        Image(systemName: set.exercise?.muscleGroup.symbol ?? "dumbbell")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Theme.positive)
                            .frame(width: 28, height: 28)
                            .background(Theme.positive.opacity(0.14), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                            .pop(recordsIn, from: 0.6, delay: delay + 0.1)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(set.exercise?.displayName ?? "Esercizio rimosso")
                                .font(Theme.rounded(14, .semibold))
                                .foregroundStyle(Theme.textPrimary)
                            Text(Fmt.setLine(weight: set.weight, reps: set.reps, unit: settings.unit, singleArm: set.singleArm, group: set.exercise?.muscleGroup))
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }

                        Spacer()

                        Text(Fmt.weightValue(set.weight, unit: settings.unit))
                            .font(Theme.rounded(15, .bold))
                            .foregroundStyle(Theme.positive)
                    }
                    .enter(recordsIn, offset: 12, delay: delay)
                }
            }
        }
    }

    private func hero(_ snap: Snapshot) -> some View {
        VStack(spacing: 6) {
            Image(systemName: snap.records.isEmpty ? "checkmark.circle.fill" : "trophy.fill")
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(snap.records.isEmpty ? settings.accentColor : Theme.positive)
                .pop(heroIn, from: 0.6, rotation: -12)
                .bounce(trigger: trophyTick, peak: 1.2, wobble: 8)
                .padding(.top, 12)
            Text(snap.records.isEmpty ? "Allenamento concluso" : "Nuovi massimali!")
                .font(Theme.rounded(24, .bold))
                .foregroundStyle(Theme.textPrimary)
                .enter(heroIn, offset: 8, delay: 0.08)
            Text(Fmt.dateTime(workout.date))
                .font(Theme.rounded(12, .medium))
                .foregroundStyle(Theme.textTertiary)
                .enter(heroIn, offset: 8, delay: 0.14)
        }
        .padding(.bottom, 4)
        .confettiSpot("hero")
    }

    var body: some View {
        let snap = cached ?? makeSnapshot()
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    hero(snap)

                    // le tessere entrano a cascata (60 ms) e i numeri scorrono fino al valore vero
                    HStack(spacing: 10) {
                        StatTile(
                            symbol: "clock",
                            title: "Durata",
                            value: workout.duration.map { Fmt.duration($0) } ?? "—",
                            tint: settings.accentColor
                        )
                        .enter(tilesIn, delay: 0)
                        StatTile(
                            symbol: "scalemass",
                            title: "Volume",
                            value: Fmt.volume(counted ? workout.totalVolume : 0, unit: settings.unit),
                            tint: settings.accentColor
                        )
                        .enter(tilesIn, delay: 0.06)
                    }

                    HStack(spacing: 10) {
                        StatTile(
                            symbol: "list.number",
                            title: "Serie",
                            value: "\(counted ? workout.workingSetCount : 0)",
                            tint: settings.accentColor
                        )
                        .enter(tilesIn, delay: 0.12)
                        StatTile(
                            symbol: "dumbbell",
                            title: "Esercizi",
                            value: "\(counted ? workout.exercisesInOrder.count : 0)",
                            tint: settings.accentColor
                        )
                        .enter(tilesIn, delay: 0.18)
                    }

                    weekCard(snap).confettiSpot("week").zIndex(1)
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

                    if !snap.records.isEmpty { recordsCard(snap) }

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
            .confettiLayer(["hero": heroBurst, "week": goalBurst])
            .screenBackground(settings.accentColor)
            .navigationBarTitleDisplayMode(.inline)
            .interactiveDismissDisabled()
            .onAppear { cached = snap }
            .task { await play(snap) }
            .sheet(isPresented: $showingSaveTemplate) {
                let known = Set(exercises.map(\.uuid))
                TemplateEditorView(initialName: workout.name, initialItems: workout.items(.done, exists: { known.contains($0) }))
            }
        }
    }
}

/// Un giorno della settimana: il cerchio si riempie con un piccolo rimbalzo quando `on` diventa vero; l'anello di oggi pulsa.
private struct DayDot: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let on: Bool
    let isToday: Bool
    let ringTick: Int

    var body: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(Color.black.opacity(0.8))
            .opacity(on ? 1 : 0)
            .scaleEffect(on || reduceMotion ? 1 : 0.4)
            .frame(width: 34, height: 34)
            .background(Circle().fill(on ? settings.accentColor : Color.white.opacity(0.08)))
            .bounce(trigger: on ? 1 : 0, peak: 1.2)
            .overlay(
                Circle().strokeBorder(settings.accentColor.opacity(isToday ? 0.8 : 0), lineWidth: 2).padding(-3)
                    .bounce(trigger: ringTick, peak: 1.25)
            )
            .animation(reduceMotion ? .easeOut(duration: 0.2) : .spring(duration: 0.3, bounce: 0.15), value: on)
    }
}
