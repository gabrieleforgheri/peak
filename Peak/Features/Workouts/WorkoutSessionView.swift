import SwiftUI
import SwiftData

/// La sessione di allenamento: si aggiungono esercizi e serie, i record scattano da soli.
struct WorkoutSessionView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    @Bindable var workout: Workout

    @Query private var allExercises: [Exercise]
    @Query private var templates: [WorkoutTemplate]
    /// Scambio di un esercizio ancora senza serie.
    @State private var replacing: Exercise?
    @State private var showingSaveTemplate = false
    /// La scheda da cui si è partiti, quando a fine allenamento la lista è diversa: si chiede se aggiornarla.
    @State private var templateToUpdate: WorkoutTemplate?
    /// L'esercizio che stai facendo adesso: è quello mostrato sul Watch.
    @State private var currentExerciseID: PersistentIdentifier?
    @State private var showingPicker = false
    @State private var showingFocusPicker = false
    @State private var addSetTarget: Exercise?
    @State private var editingSet: WorkoutSet?
    @State private var showingSummary = false
    @State private var showingFinishConfirm = false
    @State private var healthMessage: String?
    @State private var sendingToHealth = false

    private var canUseWatch: Bool {
        workout.isActive && WatchBridge.shared.isWatchAppInstalled
    }

    private var recordCount: Int {
        workout.setList.filter(\.isPersonalRecord).count
    }

    private var exerciseByUUID: [UUID: Exercise] {
        Dictionary(allExercises.map { ($0.uuid, $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// La lista di oggi: il piano (scheda o esercizi aggiunti) e, in coda, gli esercizi con serie che nel piano non ci sono.
    private var items: [TemplateItem] {
        let known = exerciseByUUID
        return workout.items(exists: { known[$0] != nil })
    }

    /// Scrive la lista di oggi nel piano (anche gli esercizi fuori piano diventano righe).
    private func editPlan(_ change: ([TemplateItem]) -> [TemplateItem]) {
        withAnimation(.snappy) { workout.plan = change(items) }
        try? context.save()
        WorkoutActivity.update(for: workout, unit: settings.unit)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                header

                if let restEnd = RestTimer.shared.end {
                    restCard(until: restEnd)
                }

                ForEach(items) { item in
                    if let exercise = exerciseByUUID[item.exercise] {
                    let sets = workout.sets(for: exercise)
                    let last = exercise.setList.sorted { $0.createdAt > $1.createdAt }.first
                    ExerciseSessionCard(
                        exercise: exercise,
                        goal: Plan.targetLine(
                            item,
                            done: sets.filter { !$0.isWarmup }.count,
                            lastKilograms: last?.weight,
                            settings: settings
                        ),
                        sets: sets,
                        onMove: workout.isActive ? { step in editPlan { Plan.moving($0, exercise.uuid, by: step) } } : nil,
                        onReplace: workout.isActive && sets.isEmpty ? { replacing = exercise } : nil,
                        onRemove: workout.isActive && sets.isEmpty ? { editPlan { Plan.removing($0, exercise.uuid) } } : nil,
                        isCurrent: exercise.persistentModelID == currentExerciseID,
                        onSelect: { select(exercise) },
                        onAddSet: { addSetTarget = exercise },
                        onStartOnWatch: canUseWatch ? { WatchBridge.shared.start(exercise, in: workout) } : nil,
                        onRepeatLast: { repeatLastSet(for: exercise) },
                        onEditSet: { editingSet = $0 },
                        onDeleteSet: { delete($0) }
                    )
                    }
                }

                Button {
                    showingPicker = true
                } label: {
                    Label("Aggiungi esercizio", systemImage: "plus")
                }
                .buttonStyle(SecondaryButtonStyle())

                if !items.isEmpty {
                    Button {
                        showingSaveTemplate = true
                    } label: {
                        Label("Salva come giorno", systemImage: "list.bullet")
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }

                noteCard

                if workout.isActive {
                    Button {
                        showingFinishConfirm = true
                    } label: {
                        Label("Termina allenamento", systemImage: "checkmark.circle.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                    .padding(.top, 4)
                } else if settings.healthSyncEnabled {
                    // Serve per le sessioni chiuse prima di attivare Salute,
                    // o quando il primo invio è fallito.
                    Button {
                        sendToHealth(announceSuccess: true)
                    } label: {
                        Label(
                            sendingToHealth ? "Invio…" : "Invia a Salute",
                            systemImage: "heart.fill"
                        )
                    }
                    .buttonStyle(SecondaryButtonStyle())
                    .disabled(sendingToHealth)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 30)
        }
        .screenBackground(settings.accentColor)
        .navigationTitle(workout.name.isEmpty ? Fmt.relativeDay(workout.date) : workout.name)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingPicker) {
            ExercisePickerSheet(focus: workout.focus) { exercise in
                editPlan { Plan.adding($0, exercise.uuid) }
                // Solo la scelta: peso e ripetizioni li decidi dopo, sul Watch o con "+ Serie".
                select(exercise)
            }
        }
        .sheet(item: $replacing) { old in
            ExercisePickerSheet(focus: workout.focus) { new in
                if items.contains(where: { $0.exercise == new.uuid }) {
                    Haptics.warning()
                } else {
                    editPlan { Plan.replacing($0, from: old.uuid, to: new.uuid) }
                    select(new)
                }
            }
        }
        .sheet(isPresented: $showingSaveTemplate) {
            TemplateEditorView(initialName: workout.name, initialItems: workout.items(.done, exists: { exerciseByUUID[$0] != nil }))
        }
        .sheet(item: $addSetTarget) { exercise in
            AddSetSheet(
                exercise: exercise,
                mode: .add(lastSet: lastSet(for: exercise))
            ) { weight, reps, isWarmup in
                addSet(to: exercise, weight: weight, reps: reps, isWarmup: isWarmup)
            }
        }
        .sheet(item: $editingSet) { set in
            if let exercise = set.exercise {
                AddSetSheet(exercise: exercise, mode: .edit(set)) { weight, reps, isWarmup in
                    update(set, weight: weight, reps: reps, isWarmup: isWarmup)
                }
            }
        }
        .sheet(isPresented: $showingFocusPicker) {
            FocusPickerSheet(initialName: workout.name, initial: workout.focus, confirmTitle: "Salva") { name, focus in
                workout.name = name
                workout.focus = focus
                try? context.save()
            }
        }
        .onAppear { WorkoutActivity.start(for: workout, unit: settings.unit) }
        .task(id: RestTimer.shared.end) {
            guard let restEnd = RestTimer.shared.end else { return }
            try? await Task.sleep(for: .seconds(max(0, restEnd.timeIntervalSinceNow)))
            guard !Task.isCancelled else { return }
            Haptics.success()
            stopRest()
        }
        .sheet(isPresented: $showingSummary) {
            WorkoutSummaryView(workout: workout) { dismiss() }
        }
        .alert(
            "Aggiornare il giorno «\(templateToUpdate?.name ?? "")» con le modifiche di oggi?",
            isPresented: Binding(get: { templateToUpdate != nil }, set: { if !$0 { templateToUpdate = nil } })
        ) {
            Button("Aggiorna") {
                if let template = templateToUpdate { update(template) }
                showingSummary = true
            }
            Button("No", role: .cancel) { showingSummary = true }
        }
        .alert(
            "App Salute",
            isPresented: Binding(get: { healthMessage != nil }, set: { if !$0 { healthMessage = nil } })
        ) {
            Button("Ok", role: .cancel) {}
        } message: {
            Text(healthMessage ?? "")
        }
        .confirmationDialog(
            "Terminare l'allenamento?",
            isPresented: $showingFinishConfirm,
            titleVisibility: .visible
        ) {
            Button("Termina") { finish() }
            Button("Continua ad allenarti", role: .cancel) {}
        }
    }

    // MARK: - Testata

    private var header: some View {
        GlassCard(padding: 18, tint: workout.isActive ? settings.accentColor : nil) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    if workout.isActive {
                        HStack(spacing: 7) {
                            Circle().fill(settings.accentColor).frame(width: 7, height: 7)
                            Text("IN CORSO")
                                .font(Theme.rounded(11, .bold))
                                .tracking(0.8)
                                .foregroundStyle(settings.accentColor)
                        }
                    } else {
                        Text("CONCLUSO")
                            .font(Theme.rounded(11, .bold))
                            .tracking(0.8)
                            .foregroundStyle(Theme.textTertiary)
                    }
                    Spacer()
                    Text(Fmt.dateTime(workout.date))
                        .font(Theme.rounded(11, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }

                if workout.isActive {
                    TimelineView(.periodic(from: .now, by: 1)) { timeline in
                        Text(Fmt.duration(timeline.date.timeIntervalSince(workout.date)))
                            .font(Theme.display(34))
                            .foregroundStyle(Theme.textPrimary)
                    }
                } else if let duration = workout.duration {
                    Text(Fmt.duration(duration))
                        .font(Theme.display(34))
                        .foregroundStyle(Theme.textPrimary)
                }

                HStack(spacing: 20) {
                    MiniStat(value: "\(workout.workingSetCount)", label: "serie")
                    MiniStat(value: Fmt.volume(workout.totalVolume, unit: settings.unit), label: "volume")
                    if recordCount > 0 {
                        MiniStat(value: "\(recordCount)", label: "record")
                    }
                }

                focusRow
            }
        }
    }

    /// Cosa si allena oggi: filtra il selettore degli esercizi.
    private var focusRow: some View {
        HStack(spacing: 6) {
            let focus = MuscleGroup.allCases.filter(workout.focus.contains)
            if focus.isEmpty {
                Text("Tutti i gruppi")
                    .font(Theme.rounded(12, .medium))
                    .foregroundStyle(Theme.textTertiary)
            } else {
                ForEach(focus) { MuscleGroupChip(group: $0, compact: focus.count > 3) }
            }
            Spacer(minLength: 0)
            if workout.isActive {
                Button("Modifica") { showingFocusPicker = true }
                    .font(Theme.rounded(13, .bold))
                    .foregroundStyle(settings.accentColor)
                    .buttonStyle(.plain)
            }
        }
    }

    /// Come è andata: un promemoria che al prossimo allenamento vale più dei numeri.
    private var noteCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                SectionHeader(title: "Note")
                TextField(
                    "Come è andata? Sensazioni, dolori, regolazioni del sedile…",
                    text: $workout.note,
                    axis: .vertical
                )
                .font(Theme.rounded(14, .regular))
                .lineLimit(2...6)
                .onChange(of: workout.note) { _, _ in
                    try? context.save()
                }
            }
        }
    }

    // MARK: - Azioni

    private func lastSet(for exercise: Exercise) -> WorkoutSet? {
        workout.sets(for: exercise).last
            ?? exercise.setList.sorted { $0.createdAt > $1.createdAt }.first
    }

    private func addSet(to exercise: Exercise, weight: Double, reps: Int, isWarmup: Bool) {
        let isRecord = RecordService.addSet(
            to: workout,
            exercise: exercise,
            weight: weight,
            reps: reps,
            isWarmup: isWarmup,
            context: context
        )
        startRest(for: exercise)
        currentExerciseID = exercise.persistentModelID
        WatchBridge.shared.show(exercise, in: workout)

        if isRecord { Haptics.personalRecord() } else { Haptics.commit() }
    }

    // MARK: - Recupero

    private func restCard(until end: Date) -> some View {
        GlassCard(padding: 16, tint: settings.accentColor) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Recupero")
                        .font(Theme.rounded(12, .semibold))
                        .foregroundStyle(Theme.textTertiary)
                    Text(timerInterval: Date.now...max(end, .now), countsDown: true)
                        .font(Theme.rounded(30, .bold))
                        .monospacedDigit()
                        .foregroundStyle(settings.accentColor)
                }
                Spacer()
                Button("−15") { shiftRest(by: -15) }
                Button("+15") { shiftRest(by: 15) }
                Button("Salta") { stopRest() }
            }
            .font(Theme.rounded(14, .bold))
            .buttonStyle(.bordered)
        }
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    private func startRest(for exercise: Exercise) {
        if settings.restTimerEnabled, workout.isActive {
            RestTimer.shared.start(seconds: exercise.restSeconds > 0 ? exercise.restSeconds : settings.restSeconds)
        }
        WorkoutActivity.update(for: workout, unit: settings.unit)
    }

    private func shiftRest(by seconds: Int) {
        withAnimation(.snappy) { RestTimer.shared.shift(by: seconds) }
        WorkoutActivity.update(for: workout, unit: settings.unit)
        Haptics.tap()
    }

    private func stopRest() {
        withAnimation(.snappy) { RestTimer.shared.stop() }
        WorkoutActivity.update(for: workout, unit: settings.unit)
    }

    /// Segna l'esercizio come quello in corso e lo manda al Watch, senza chiedere nulla.
    private func select(_ exercise: Exercise) {
        withAnimation(.snappy) { currentExerciseID = exercise.persistentModelID }
        WatchBridge.shared.show(exercise, in: workout)
        Haptics.tap()
    }

    private func repeatLastSet(for exercise: Exercise) {
        guard let last = lastSet(for: exercise) else { return }
        addSet(to: exercise, weight: last.weight, reps: last.reps, isWarmup: last.isWarmup)
    }

    /// Applica la correzione e ricalcola i record del macchinario: una serie corretta al ribasso
    /// non deve lasciarsi dietro il massimale che aveva fatto scattare, e le serie dopo di lei
    /// si ricontrollano (una che prima non lo era può diventarlo).
    private func update(_ set: WorkoutSet, weight: Double, reps: Int, isWarmup: Bool) {
        set.weight = weight
        set.reps = reps
        set.isWarmup = isWarmup
        if let exercise = set.exercise { RecordService.recompute(exercise, context: context) }
        let isRecord = set.isPersonalRecord
        try? context.save()
        WorkoutActivity.update(for: workout, unit: settings.unit)
        if isRecord { Haptics.personalRecord() } else { Haptics.commit() }
    }

    /// Manda la sessione a Salute. Le date si leggono **qui**, sul main actor,
    /// e solo quelle attraversano il confine con HealthKit.
    private func sendToHealth(announceSuccess: Bool) {
        guard !workout.setList.isEmpty else {
            healthMessage = HealthService.HealthError.emptyWorkout.localizedDescription
            return
        }
        let start = workout.date
        let end = workout.endedAt ?? Date()

        sendingToHealth = true
        Task {
            do {
                try await HealthService.saveWorkout(start: start, end: end)
                sendingToHealth = false
                if announceSuccess {
                    healthMessage = "Allenamento registrato in Salute."
                    Haptics.success()
                }
            } catch {
                sendingToHealth = false
                healthMessage = error.localizedDescription
                Haptics.warning()
            }
        }
    }

    private func delete(_ set: WorkoutSet) {
        withAnimation {
            RecordService.delete(set, context: context)
        }
        WorkoutActivity.update(for: workout, unit: settings.unit)
        Haptics.warning()
    }

    /// La scheda di partenza, se la lista di oggi è diversa da lei (e lei esiste ancora).
    private func changedTemplate() -> WorkoutTemplate? {
        let known = exerciseByUUID
        let exists: (UUID) -> Bool = { known[$0] != nil }
        guard let id = workout.templateUUID, let template = templates.first(where: { $0.uuid == id }),
              Plan.differs(today: workout.items(.targets, exists: exists), template: template.items, exists: exists) else { return nil }
        return template
    }

    private func finish() {
        let changed = changedTemplate()
        workout.endedAt = Date()
        try? context.save()
        RestTimer.shared.stop()
        WorkoutActivity.end()
        WatchBridge.shared.clear()
        if !SyncService.shared.isConnected, let date = try? BackupService.writeAutoBackup(context: context) {
            settings.lastBackupDate = date
        }
        if settings.healthSyncEnabled {
            sendToHealth(announceSuccess: false)
        }

        Haptics.success()
        if let changed { templateToUpdate = changed } else { showingSummary = true }
    }

    /// Aggiorna la scheda con la lista di oggi, tenendo gli obiettivi scritti.
    private func update(_ template: WorkoutTemplate) {
        let known = exerciseByUUID
        template.items = workout.items(.targets, exists: { known[$0] != nil })
        try? context.save()
    }
}

// MARK: - Card di un esercizio nella sessione

private struct ExerciseSessionCard: View {
    @Environment(AppSettings.self) private var settings

    let exercise: Exercise
    /// Obiettivo della scheda («2/4 serie · 8–10 rip · 60 kg»), vuoto se non c'è.
    let goal: String
    let sets: [WorkoutSet]
    /// Menu dell'esercizio: `nil` = voce non disponibile (sposta solo a sessione in corso, sostituisci e togli
    /// solo senza serie).
    let onMove: ((Int) -> Void)?
    let onReplace: (() -> Void)?
    let onRemove: (() -> Void)?
    /// `true` se è l'esercizio che stai facendo (quello sul Watch).
    let isCurrent: Bool
    let onSelect: () -> Void
    let onAddSet: () -> Void
    /// `nil` se il Watch non c'è o è disattivato.
    let onStartOnWatch: (() -> Void)?
    let onRepeatLast: () -> Void
    let onEditSet: (WorkoutSet) -> Void
    let onDeleteSet: (WorkoutSet) -> Void

    var body: some View {
        GlassCard(padding: 16, tint: isCurrent ? exercise.muscleGroup.tint : nil) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    ExerciseThumbnail(exercise: exercise, size: 34, zoomable: true)
                    ExerciseFigure(exercise: exercise, size: 34)

                    VStack(alignment: .leading, spacing: 1) {
                        Text(exercise.name)
                            .font(Theme.rounded(16, .semibold))
                            .foregroundStyle(Theme.textPrimary)
                        if isCurrent {
                            Text("IN CORSO")
                                .font(Theme.rounded(10, .bold))
                                .tracking(0.7)
                                .foregroundStyle(exercise.muscleGroup.tint)
                        } else if let best = exercise.bestRecord {
                            Text("max \(Fmt.setLine(weight: best.weight, reps: best.reps, unit: settings.unit))")
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                        if !goal.isEmpty {
                            Text(goal)
                                .font(Theme.rounded(11, .semibold))
                                .foregroundStyle(Theme.textSecondary)
                        }
                    }
                    Spacer(minLength: 0)
                    if onMove != nil || onReplace != nil || onRemove != nil {
                        Menu {
                            if let onMove {
                                Button { onMove(-1) } label: { Label("Su", systemImage: "chevron.up") }
                                Button { onMove(1) } label: { Label("Giù", systemImage: "chevron.down") }
                            }
                            if let onReplace {
                                Button(action: onReplace) { Label("Sostituisci", systemImage: "arrow.left.arrow.right") }
                            }
                            if let onRemove {
                                Button(role: .destructive, action: onRemove) { Label("Togli dalla lista", systemImage: "trash") }
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 14, weight: .bold))
                                .frame(width: 34, height: 34)
                                .background(Theme.card, in: Circle())
                        }
                        .foregroundStyle(Theme.textSecondary)
                        .accessibilityLabel("Altro")
                    }
                    if let onStartOnWatch {
                        Button {
                            onStartOnWatch()
                            Haptics.tap()
                        } label: {
                            Image(systemName: "applewatch.radiowaves.left.and.right")
                                .font(.system(size: 16, weight: .semibold))
                                .frame(width: 36, height: 36)
                                .background(Theme.card, in: Circle())
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(settings.accentColor)
                        .accessibilityLabel("Avvia la serie sul Watch")
                    }
                }
                // Tocco sull'intestazione = "sto facendo questo": lo manda al Watch.
                .contentShape(Rectangle())
                .onTapGesture(perform: onSelect)

                if sets.isEmpty {
                    Text("Nessuna serie registrata")
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textTertiary)
                } else {
                    VStack(spacing: 6) {
                        ForEach(Array(sets.enumerated()), id: \.element.persistentModelID) { index, set in
                            HStack(spacing: 10) {
                                Text("\(index + 1)")
                                    .font(Theme.rounded(11, .bold))
                                    .foregroundStyle(Theme.textTertiary)
                                    .frame(width: 18)

                                Text(Fmt.setLine(weight: set.weight, reps: set.reps, unit: settings.unit))
                                    .font(Theme.rounded(15, .semibold))
                                    .foregroundStyle(set.isWarmup ? Theme.textSecondary : Theme.textPrimary)

                                if set.isPersonalRecord { PRBadge() }
                                if set.isWarmup {
                                    Text("risc.")
                                        .font(Theme.rounded(10, .semibold))
                                        .foregroundStyle(Theme.textTertiary)
                                }

                                Spacer()

                                if set.reps > 1 {
                                    Text("≈ \(Fmt.weight(set.estimatedOneRepMax, unit: settings.unit))")
                                        .font(Theme.rounded(11, .medium))
                                        .foregroundStyle(Theme.textTertiary)
                                }
                            }
                            .padding(.vertical, 5)
                            .padding(.horizontal, 8)
                            .background(
                                set.isPersonalRecord ? Theme.positive.opacity(0.10) : Color.clear,
                                in: RoundedRectangle(cornerRadius: 9, style: .continuous)
                            )
                            .contentShape(Rectangle())
                            .onTapGesture { onEditSet(set) }
                            .contextMenu {
                                Button {
                                    onEditSet(set)
                                } label: {
                                    Label("Modifica serie", systemImage: "pencil")
                                }
                                Button(role: .destructive) {
                                    onDeleteSet(set)
                                } label: {
                                    Label("Elimina serie", systemImage: "trash")
                                }
                            }
                        }
                    }
                }

                HStack(spacing: 8) {
                    Button(action: onAddSet) {
                        Label("Serie", systemImage: "plus")
                            .font(Theme.rounded(13, .bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(exercise.muscleGroup.tint.opacity(0.20), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                            .foregroundStyle(exercise.muscleGroup.tint)
                    }
                    .buttonStyle(.plain)

                    if let last = sets.last {
                        Button(action: onRepeatLast) {
                            Label(Fmt.setLine(weight: last.weight, reps: last.reps, unit: settings.unit), systemImage: "arrow.counterclockwise")
                                .font(Theme.rounded(13, .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 9)
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                                .foregroundStyle(Theme.textSecondary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}
