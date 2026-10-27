import SwiftUI
import SwiftData

/// La sessione di allenamento: si aggiungono esercizi e serie, i record scattano da soli.
struct WorkoutSessionView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
    @State private var viewingExercise: Exercise?
    @State private var showingSummary = false
    @State private var showingFinishConfirm = false
    @State private var healthMessage: String?
    @State private var sendingToHealth = false
    /// Le serie che c'erano già: una serie che compare e non è qui dentro è nuova (dal telefono o dal Watch).
    @State private var knownSets: Set<UUID> = []
    /// La serie dell'ultimo record e quante feste di record ci sono state (il contatore fa ripartire coriandoli, toast e riga).
    @State private var recordSet: UUID?
    @State private var recordTick = 0

    private var canUseWatch: Bool {
        workout.isActive && settings.watchEnabled && WatchBridge.shared.isWatchAppInstalled
    }

    private var recordCount: Int {
        workout.setList.filter(\.isPersonalRecord).count
    }

    private var exerciseByUUID: [UUID: Exercise] {
        Dictionary(allExercises.map { ($0.uuid, $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// La lista di oggi: il piano (scheda o esercizi aggiunti) e, in coda, gli esercizi con serie che nel piano non ci sono.
    private func items(_ known: [UUID: Exercise]) -> [TemplateItem] {
        workout.items(exists: { known[$0] != nil })
    }

    /// Scrive la lista di oggi nel piano (anche gli esercizi fuori piano diventano righe).
    private func editPlan(_ change: ([TemplateItem]) -> [TemplateItem]) {
        withAnimation(.snappy) { workout.plan = change(items(exerciseByUUID)) }
        try? context.save()
        WorkoutActivity.update(for: workout, unit: settings.unit)
    }

    var body: some View {
        // Una volta sola per ridisegno: il dizionario degli esercizi e la lista di oggi (decodifica il piano e ordina le serie).
        let known = exerciseByUUID
        let list = items(known)
        ScrollView {
            VStack(spacing: 14) {
                header

                if let restEnd = RestTimer.shared.end {
                    restCard(until: restEnd)
                }

                ForEach(list) { item in
                    if let exercise = known[item.exercise] {
                    let sets = workout.sets(for: exercise)
                    ExerciseSessionCard(
                        exercise: exercise,
                        goal: Plan.targetLine(
                            item,
                            done: sets.filter { !$0.isWarmup }.count,
                            lastKilograms: nil,
                            settings: settings
                        ),
                        previousSets: previousWorkingSets(for: exercise),
                        previousDays: previousDays(for: exercise),
                        sets: sets,
                        recordSet: recordSet,
                        recordTick: recordTick,
                        onMove: workout.isActive ? { step in editPlan { Plan.moving($0, exercise.uuid, by: step) } } : nil,
                        onReplace: workout.isActive && sets.isEmpty ? { replacing = exercise } : nil,
                        onRemove: workout.isActive && sets.isEmpty ? { editPlan { Plan.removing($0, exercise.uuid) } } : nil,
                        isCurrent: exercise.persistentModelID == currentExerciseID,
                        onSelect: { select(exercise) },
                        onAddSet: { addSetTarget = exercise },
                        onStartOnWatch: canUseWatch ? { WatchBridge.shared.start(exercise, in: workout) } : nil,
                        onRepeatLast: { repeatLastSet(for: exercise) },
                        onEditSet: { editingSet = $0 },
                        onDeleteSet: { delete($0) },
                        onDetail: { viewingExercise = exercise }
                    )
                    }
                }

                Button {
                    showingPicker = true
                } label: {
                    Label("Aggiungi esercizio", systemImage: "plus")
                }
                .buttonStyle(SecondaryButtonStyle())

                if !list.isEmpty {
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
                    // il popup nasce dal bottone, non dalla barra in alto
                    .confirmationDialog(
                        "Terminare l'allenamento?",
                        isPresented: $showingFinishConfirm,
                        titleVisibility: .visible
                    ) {
                        Button("Termina") { finish() }
                        Button("Continua ad allenarti", role: .cancel) {}
                    }
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
            // una serie in più o in meno (anche dal Watch): le righe entrano e escono piano, il resto scorre di conseguenza
            .animation(.easeOut(duration: reduceMotion ? 0.15 : 0.22), value: workout.setList.count)
        }
        .confettiLayer(["record": recordTick])
        .celebrationToast("Nuovo massimale!", trigger: recordTick)
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
                if list.contains(where: { $0.exercise == new.uuid }) {
                    Haptics.warning()
                } else {
                    editPlan { Plan.replacing($0, from: old.uuid, to: new.uuid) }
                    select(new)
                }
            }
        }
        .sheet(isPresented: $showingSaveTemplate) {
            TemplateEditorView(initialName: workout.name, initialItems: workout.items(.done, exists: { known[$0] != nil }))
        }
        .sheet(item: $addSetTarget) { exercise in
            AddSetSheet(
                exercise: exercise,
                mode: .add(lastSet: lastSet(for: exercise), singleArm: workout.defaultSingleArm(for: exercise))
            ) { weight, reps, isWarmup, singleArm in
                addSet(to: exercise, weight: weight, reps: reps, isWarmup: isWarmup, singleArm: singleArm)
            }
        }
        .sheet(item: $editingSet) { set in
            if let exercise = set.exercise {
                AddSetSheet(exercise: exercise, mode: .edit(set)) { weight, reps, isWarmup, singleArm in
                    update(set, weight: weight, reps: reps, isWarmup: isWarmup, singleArm: singleArm)
                }
            }
        }
        .sheet(item: $viewingExercise) { exercise in
            NavigationStack {
                ExerciseDetailView(exercise: exercise)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("Chiudi") { viewingExercise = nil }
                        }
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
        .onAppear {
            WorkoutActivity.start(for: workout, unit: settings.unit)
            knownSets = Set(workout.setList.map(\.uuid))
        }
        .onChange(of: workout.setList.count) { setsChanged() }
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
                templateToUpdate = nil
                presentSummary()
            }
            Button("No", role: .cancel) {
                templateToUpdate = nil
                presentSummary()
            }
        }
        .alert(
            "App Salute",
            isPresented: Binding(get: { healthMessage != nil }, set: { if !$0 { healthMessage = nil } })
        ) {
            Button("Ok", role: .cancel) {}
        } message: {
            Text(healthMessage ?? "")
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
                // Si salva a fine digitazione, non a ogni tasto: ogni salvataggio fa rileggere tutte le schede e parte la sincronizzazione.
                .task(id: workout.note) {
                    try? await Task.sleep(for: .seconds(1))
                    guard !Task.isCancelled else { return }
                    try? context.save()
                }
            }
        }
    }

    // MARK: - Azioni

    private func previousWorkingSets(for exercise: Exercise) -> [WorkoutSet] {
        let otherSets = exercise.setList.filter { !$0.isWarmup && $0.workout?.uuid != workout.uuid }
        guard let lastWk = otherSets.compactMap(\.workout).max(by: { $0.date < $1.date }) else { return [] }
        return lastWk.setList.filter { $0.exercise?.uuid == exercise.uuid && !$0.isWarmup }.sorted { $0.order < $1.order }
    }

    /// Gli ultimi 3 allenamenti (giorni) con questo esercizio, escluso quello in corso, dal più recente; riscaldamento fuori.
    private func previousDays(for exercise: Exercise) -> [(date: Date, sets: [WorkoutSet])] {
        let others = exercise.setList.filter { !$0.isWarmup && $0.workout?.uuid != workout.uuid }
        var seen = Set<UUID>()
        let wks = others.compactMap(\.workout).filter { seen.insert($0.uuid).inserted }
            .sorted { $0.date > $1.date }.prefix(3)
        return wks.map { wk in
            (wk.date, others.filter { $0.workout?.uuid == wk.uuid }.sorted { $0.order < $1.order })
        }
    }

    private func lastSet(for exercise: Exercise) -> WorkoutSet? {
        workout.sets(for: exercise).filter { !$0.isWarmup }.last
            ?? exercise.setList.filter { !$0.isWarmup }.max { $0.createdAt < $1.createdAt }
            ?? workout.sets(for: exercise).last
    }

    private func addSet(to exercise: Exercise, weight: Double, reps: Int, isWarmup: Bool, singleArm: Bool) {
        let isRecord = RecordService.addSet(
            to: workout,
            exercise: exercise,
            weight: weight,
            reps: reps,
            isWarmup: isWarmup,
            singleArm: singleArm,
            context: context
        )
        startRest(for: exercise)
        currentExerciseID = exercise.persistentModelID
        WatchBridge.shared.show(exercise, in: workout)

        if isRecord { Haptics.personalRecord() } else { Haptics.commit() }
    }

    /// Il solo punto in cui si accorge di una serie nuova, da qualsiasi parte arrivi (+ Serie, ripeti, Watch): record o serie normale.
    /// Le vibrazioni delle serie fatte dal telefono restano in `addSet`; qui solo suono e animazioni.
    private func setsChanged() {
        let sets = workout.setList
        let added = sets.filter { !knownSets.contains($0.uuid) }
        knownSets = Set(sets.map(\.uuid))
        guard workout.isActive, !added.isEmpty else { return }
        if let record = added.first(where: \.isPersonalRecord) { celebrateRecord(record) } else { SoundFX.play(.set) }
    }

    /// La festa del massimale: riga che si accende e badge che rimbalza (nelle righe), coriandoli, toast, suono.
    private func celebrateRecord(_ set: WorkoutSet) {
        recordSet = set.uuid
        recordTick += 1
        SoundFX.play(.record)
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
        addSet(to: exercise, weight: last.weight, reps: last.reps, isWarmup: last.isWarmup, singleArm: last.singleArm)
    }

    /// Applica la correzione e ricalcola i record del macchinario: una serie corretta al ribasso
    /// non deve lasciarsi dietro il massimale che aveva fatto scattare, e le serie dopo di lei
    /// si ricontrollano (una che prima non lo era può diventarlo).
    private func update(_ set: WorkoutSet, weight: Double, reps: Int, isWarmup: Bool, singleArm: Bool) {
        set.weight = weight
        set.reps = reps
        set.isWarmup = isWarmup
        set.singleArm = singleArm
        if let exercise = set.exercise { RecordService.recompute(exercise, context: context) }
        let isRecord = set.isPersonalRecord
        try? context.save()
        WorkoutActivity.update(for: workout, unit: settings.unit)
        if isRecord { celebrateRecord(set) } // una correzione che è massimale: festa
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
        SoundFX.play(.remove)
    }

    /// La scheda di partenza, se la lista di oggi è diversa da lei (e lei esiste ancora).
    private func changedTemplate() -> WorkoutTemplate? {
        let known = exerciseByUUID
        let exists: (UUID) -> Bool = { known[$0] != nil }
        guard let id = workout.templateUUID, let template = templates.first(where: { $0.uuid == id }),
              Plan.differs(today: workout.items(.targets, exists: exists), template: template.items, exists: exists) else { return nil }
        return template
    }

    private func presentSummary() {
        Task { @MainActor in
            // Lascia completare l'animazione di chiusura del dialog / alert UIKit prima di presentare il foglio
            try? await Task.sleep(for: .milliseconds(350))
            showingSummary = true
        }
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
        if let changed {
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(350))
                templateToUpdate = changed
            }
        } else {
            presentSummary()
        }
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let exercise: Exercise
    /// Obiettivo della scheda («2/4 serie · 8–10 rip · 60 kg»), vuoto se non c'è.
    let goal: String
    let previousSets: [WorkoutSet]
    /// Gli ultimi 3 allenamenti, per la seconda pagina di «Scorso».
    let previousDays: [(date: Date, sets: [WorkoutSet])]
    let sets: [WorkoutSet]
    /// La serie dell'ultimo record e il contatore delle feste: la sua riga si accende a ogni cambio.
    let recordSet: UUID?
    let recordTick: Int
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
    let onDetail: () -> Void

    @State private var showingMuscleDetail = false
    /// Pagina visibile di «Scorso» (0 = ultime serie, 1 = ultimi allenamenti), per i puntini.
    @State private var prevPage: Int? = 0

    var body: some View {
        GlassCard(padding: 16, tint: isCurrent ? exercise.muscleGroup.tint : nil) {
            VStack(alignment: .leading, spacing: 14) {
                // SEZIONE SUPERIORE: Due colonne
                // Sinistra: Titolo (grande), IN CORSO, Goal, e ultime 3 serie dello scorso allenamento
                // Destra: in alto menu/watch, al centro figura muscoli con pulsante "Muscoli ↗" sotto
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(exercise.name)
                                .font(Theme.rounded(22, .bold))
                                .foregroundStyle(Theme.textPrimary)
                                .lineLimit(2)
                                .fixedSize(horizontal: false, vertical: true)
                            if isCurrent {
                                Text("IN CORSO")
                                    .font(Theme.rounded(10, .bold))
                                    .tracking(0.7)
                                    .foregroundStyle(exercise.muscleGroup.tint)
                            }
                        }

                        if !goal.isEmpty {
                            Text(goal)
                                .font(Theme.rounded(12, .semibold))
                                .foregroundStyle(Theme.textSecondary)
                        }

                        // Scorso: due pagine da scorrere, le ultime serie (fino a 3) e gli ultimi 3 allenamenti in riga da taccuino
                        let shownPrev = Array(previousSets.prefix(3))
                        if !shownPrev.isEmpty {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("Scorso:".tr)
                                    .font(Theme.rounded(11, .bold))
                                    .foregroundStyle(Theme.textTertiary)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(alignment: .top, spacing: 0) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            ForEach(Array(shownPrev.enumerated()), id: \.element.uuid) { idx, prev in
                                                HStack(spacing: 6) {
                                                    Text("\(idx + 1).")
                                                        .font(Theme.rounded(11, .semibold))
                                                        .foregroundStyle(Theme.textTertiary)
                                                        .frame(width: 16, alignment: .leading)
                                                    if exercise.weightDisplay == "relative", !exercise.perSide, exercise.barWeight > 0 {
                                                        let relWeight = max(0, prev.weight - exercise.barWeight)
                                                        Text(Fmt.setLine(weight: relWeight, reps: prev.reps, unit: settings.unit, singleArm: prev.singleArm))
                                                            .font(Theme.rounded(12, .medium))
                                                            .foregroundStyle(Theme.textSecondary)
                                                    } else {
                                                        Text(Fmt.setLine(weight: prev.weight, reps: prev.reps, unit: settings.unit, singleArm: prev.singleArm))
                                                            .font(Theme.rounded(12, .medium))
                                                            .foregroundStyle(Theme.textSecondary)
                                                    }
                                                }
                                            }
                                        }
                                        .containerRelativeFrame(.horizontal, alignment: .leading)
                                        .id(0)

                                        VStack(alignment: .leading, spacing: 2) {
                                            ForEach(Array(previousDays.enumerated()), id: \.offset) { _, day in
                                                let relative = exercise.weightDisplay == "relative" && !exercise.perSide && exercise.barWeight > 0
                                                let line = Fmt.notebookLine(
                                                    day.sets.map { (weight: relative ? max(0, $0.weight - exercise.barWeight) : $0.weight, reps: $0.reps, singleArm: $0.singleArm) },
                                                    unit: settings.unit
                                                )
                                                (Text(Fmt.shortDate(day.date)).foregroundStyle(Theme.textTertiary) + Text("  \(line)"))
                                                    .font(Theme.rounded(12, .medium))
                                                    .foregroundStyle(Theme.textSecondary)
                                            }
                                        }
                                        .containerRelativeFrame(.horizontal, alignment: .leading)
                                        .id(1)
                                    }
                                    .scrollTargetLayout()
                                }
                                .scrollTargetBehavior(.paging)
                                .scrollPosition(id: $prevPage)

                                HStack(spacing: 4) {
                                    ForEach(0..<2, id: \.self) { i in
                                        Circle()
                                            .fill(Theme.textTertiary.opacity(prevPage == i ? 0.9 : 0.35))
                                            .frame(width: 5, height: 5)
                                    }
                                }
                                .accessibilityHidden(true)
                            }
                            .padding(.top, 2)
                        } else {
                            Text("Nessun allenamento precedente".tr)
                                .font(Theme.rounded(12, .medium))
                                .foregroundStyle(Theme.textTertiary)
                                .padding(.top, 2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onSelect)

                    // Colonna destra: Menu/Watch in alto + Figura muscoli centrata con "Muscoli ↗" sotto
                    VStack(alignment: .trailing, spacing: 10) {
                        HStack(spacing: 6) {
                            Menu {
                                Button {
                                    onDetail()
                                } label: {
                                    Label("Vedi macchinario", systemImage: "info.circle")
                                }

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

                        // Riquadro muscolo cliccabile + etichetta Muscoli SOTTO la figura
                        if !exercise.figureMuscles.isEmpty {
                            Button {
                                showingMuscleDetail = true
                            } label: {
                                VStack(spacing: 5) {
                                    ExerciseFigure(exercise: exercise, size: 68)

                                    HStack(spacing: 3) {
                                        Text("Muscoli".tr)
                                            .font(Theme.rounded(11, .semibold))
                                        Image(systemName: "arrow.up.right")
                                            .font(.system(size: 8, weight: .bold))
                                    }
                                    .foregroundStyle(exercise.muscleGroup.tint)
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 3)
                                    .background(exercise.muscleGroup.tint.opacity(0.14), in: Capsule())
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Mappa muscolare".tr)
                        }
                    }
                    .frame(width: 86)
                }

                // LISTA DELLE SERIE DI OGGI
                if sets.isEmpty {
                    Text("Nessuna serie registrata")
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textTertiary)
                } else {
                    VStack(spacing: 6) {
                        ForEach(Array(sets.enumerated()), id: \.element.uuid) { index, set in
                            SetRow(index: index, set: set, pulse: set.uuid == recordSet ? recordTick : 0)
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
                                .transition(reduceMotion ? .opacity : .asymmetric(
                                    insertion: .opacity.combined(with: .offset(y: 6)),
                                    removal: .opacity.combined(with: .scale(scale: 0.98))
                                ))
                        }
                    }
                }

                // PULSANTI AZIONE (Serie e Ripeti)
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
                            let repeatText: String = {
                                if exercise.weightDisplay == "relative", !exercise.perSide, exercise.barWeight > 0 {
                                    let relWeight = max(0, last.weight - exercise.barWeight)
                                    return Fmt.setLine(weight: relWeight, reps: last.reps, unit: settings.unit, singleArm: last.singleArm)
                                }
                                return Fmt.setLine(weight: last.weight, reps: last.reps, unit: settings.unit, singleArm: last.singleArm)
                            }()
                            Label(repeatText, systemImage: "arrow.counterclockwise")
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
            .background {
                ExerciseCardBackground(exercise: exercise)
                    .padding(-16)
            }
        }
        .sheet(isPresented: $showingMuscleDetail) {
            MuscleDetailSheet(exercise: exercise)
        }
    }
}

/// Una riga di serie. Quando `pulse` sale (è la serie di un nuovo massimale) lo sfondo verde si accende e torna in ~0,6 s e il badge PR rimbalza.
private struct SetRow: View {
    @Environment(AppSettings.self) private var settings

    let index: Int
    let set: WorkoutSet
    /// Cambia a ogni festa su questa serie; 0 = nessuna.
    let pulse: Int
    @State private var tick = 0

    var body: some View {
        HStack(spacing: 10) {
            Text("\(index + 1)")
                .font(Theme.rounded(11, .bold))
                .foregroundStyle(Theme.textTertiary)
                .frame(width: 18)

            if let ex = set.exercise, ex.weightDisplay == "relative", !ex.perSide, ex.barWeight > 0 {
                let relWeight = max(0, set.weight - ex.barWeight)
                HStack(spacing: 4) {
                    Text(Fmt.setLine(weight: relWeight, reps: set.reps, unit: settings.unit, singleArm: set.singleArm))
                        .font(Theme.rounded(15, .semibold))
                        .foregroundStyle(set.isWarmup ? Theme.textSecondary : Theme.textPrimary)
                    Text("(tot. \(Fmt.weight(set.weight, unit: settings.unit)))")
                        .font(Theme.rounded(11, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }
            } else {
                Text(Fmt.setLine(weight: set.weight, reps: set.reps, unit: settings.unit, singleArm: set.singleArm))
                    .font(Theme.rounded(15, .semibold))
                    .foregroundStyle(set.isWarmup ? Theme.textSecondary : Theme.textPrimary)
            }

            if set.isPersonalRecord { PRBadge().bounce(trigger: tick, peak: 1.35) }
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
        .keyframeAnimator(initialValue: 0.0, trigger: tick) { view, glow in
            view.overlay {
                RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Theme.positive.opacity(0.32 * glow))
            }
        } keyframes: { _ in
            KeyframeTrack {
                CubicKeyframe(1, duration: 0.15)
                CubicKeyframe(0, duration: 0.45)
            }
        }
        .onChange(of: pulse) { if pulse > 0 { tick += 1 } }
    }
}
