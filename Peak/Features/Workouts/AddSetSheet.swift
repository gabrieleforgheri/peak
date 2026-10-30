import SwiftUI

/// Inserimento **o modifica** di una serie. In aggiunta parte dai valori
/// dell'ultima serie fatta su quel macchinario, in modifica da quelli della serie stessa.
struct AddSetSheet: View {
    enum Mode {
        /// Nuova serie, precompilata con l'ultima fatta su questo esercizio; parte sempre a due bracci (o gambe).
        case add(lastSet: WorkoutSet?)
        /// Correzione di una serie già registrata.
        case edit(WorkoutSet)
    }

    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    let exercise: Exercise
    let mode: Mode
    /// Peso, ripetizioni, riscaldamento, un braccio (o una gamba).
    let onSave: (Double, Int, Bool, Bool) -> Void

    private var isEditing: Bool {
        if case .edit = mode { return true }
        return false
    }

    @State private var weight: Double = 0
    @State private var reps: Int = 8
    @State private var isWarmup = false
    @State private var singleArm = false
    /// Il peso di prima del dimezzamento (in unità mostrata), per rimetterlo identico tornando a due bracci.
    @State private var armMemo: ArmSwitch.Memo?
    @State private var didPrefill = false
    @State private var isRecentSetsExpanded = false
    /// Giorni aperti nelle serie recenti: all'inizio solo il più recente.
    @State private var openDays: Set<Date> = []

    private var allPreviousSets: [WorkoutSet] {
        let sets = exercise.setList.filter { !$0.isWarmup }
        if case let .edit(currentSet) = mode {
            return sets.filter { $0.uuid != currentSet.uuid }.sorted { $0.createdAt > $1.createdAt }
        }
        return sets.sorted { $0.createdAt > $1.createdAt }
    }

    private var tint: Color { exercise.muscleGroup.tint }

    private var estimated: Double {
        OneRepMax.estimate(weight: weight, reps: reps)
    }

    private var warmupPlan: [Warmup.Step] { exercise.warmupPlan(unit: settings.unit) }

    /// Peso senza dischi, se l'esercizio si carica a dischi: bilanciere (anche panche e rack) o leva plate loaded.
    private var plateBase: Double? {
        guard settings.plateCalculatorEnabled else { return nil }
        // l'attrezzo: quello del catalogo (exercise.glyph lo usa già), oppure l'icona scelta; senza icona lo si deduce dal nome
        let glyph = exercise.glyph ?? .suggested(for: exercise.name, group: exercise.muscleGroup)
        return Plates.barBase(glyph: glyph, name: exercise.name, barWeight: exercise.barWeight, plateLoaded: exercise.plateLoaded)
    }

    /// Tre modi di leggere il campo peso: totale, solo dischi (tolta la sbarra), un lato (totale = 2 x + sbarra).
    /// `weight` resta sempre il totale in kg: cambia solo come lo si mostra e lo si digita.
    private var isSide: Bool { exercise.perSide }
    private var isRel: Bool {
        !isSide && exercise.weightDisplay == "relative" && (plateBase ?? 0) > 0
    }
    private var barWeightKg: Double {
        plateBase ?? 0
    }

    /// Totale in kg -> valore del campo (e delle serie recenti).
    private func fieldKg(_ total: Double) -> Double {
        if isSide { return max(0, (total - exercise.barWeight) / 2) }
        if isRel { return max(0, total - barWeightKg) }
        return total
    }
    /// Valore del campo in kg -> totale.
    private func totalKg(_ field: Double) -> Double {
        if isSide { return field * 2 + exercise.barWeight }
        if isRel { return field + barWeightKg }
        return field
    }

    private var stepperWeight: Binding<Double> {
        Binding(
            get: { fieldKg(weight) },
            set: { weight = totalKg($0) }
        )
    }

    /// Solo il tocco sul selettore (non il caricamento di una serie): metà o doppio, sulla griglia sbarra + k x passo.
    /// Si lavora nell'unità mostrata, dove il passo è quello nativo; per lato il totale sale di due passi alla volta.
    private var armBinding: Binding<Bool> {
        Binding(
            get: { singleArm },
            set: { newValue in
                let unit = settings.unit
                let barKg = isSide ? exercise.barWeight : barWeightKg
                let step = unit.displayStep(forKilograms: exercise.incrementStep) * (isSide ? 2 : 1)
                let result = ArmSwitch.weight(
                    toSingle: newValue, from: unit.fromKilograms(weight), memo: armMemo,
                    bar: unit.fromKilograms(barKg), step: step
                )
                armMemo = result.memo
                weight = unit.toKilograms(result.weight)
                singleArm = newValue
            }
        )
    }

    private var previousSetWeight: Double? {
        if case let .add(lastSet) = mode, let lastSet { return lastSet.weight }
        return allPreviousSets.first?.weight
    }
    private var delta: Double? {
        guard let prev = previousSetWeight else { return nil }
        let currentU = (settings.unit.fromKilograms(weight) * 100).rounded() / 100
        let prevU = (settings.unit.fromKilograms(prev) * 100).rounded() / 100
        return abs(currentU - prevU) > 0.01 ? (currentU - prevU) : nil
    }

    private var referenceRecord: MaxRecord? {
        if case let .edit(set) = mode {
            // il confronto è dentro la propria linea: a un braccio solo con i massimali a un braccio
            let others = (singleArm ? exercise.singleArmRecords : exercise.twoArmRecords).filter { $0.uuid != set.uuid }
            return others.reduce(nil) { best, record in
                guard let best else { return record }
                if MaxRanking.beats(weight: record.weight, reps: record.reps, than: (best.weight, best.reps)) {
                    return record
                }
                let samePerformance = abs(record.weight - best.weight) < MaxRanking.epsilon && record.reps == best.reps
                return samePerformance && record.date > best.date ? record : best
            }
        }
        return singleArm ? exercise.singleArmBestRecord : exercise.bestRecord
    }

    /// Il carico da battere, oppure — se lo stai già battendo — quello che stai facendo.
    private var reference: String {
        if isRecord { return Fmt.setLine(weight: weight, reps: reps, unit: settings.unit, singleArm: singleArm, group: exercise.muscleGroup) }
        guard let best = referenceRecord else { return "—" }
        return Fmt.setLine(weight: best.weight, reps: best.reps, unit: settings.unit, singleArm: singleArm, group: exercise.muscleGroup)
    }

    private var isRecord: Bool {
        guard !isWarmup else { return false }
        return MaxRanking.beats(
            weight: weight,
            reps: reps,
            than: referenceRecord.map { (weight: $0.weight, reps: $0.reps) }
        )
    }

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 14) {
                    // carico e ripetizioni insieme, per il riquadro del tutorial
                    VStack(spacing: 14) {
                    GlassCard(padding: 18, tint: tint) {
                        VStack(spacing: 14) {
                            HStack(alignment: .firstTextBaseline) {
                                SectionHeader(title: isSide ? "Per lato" : isRel ? "Carico dischi" : "Carico")
                                Spacer()
                                if let plateBase, plateBase > 0 {
                                    Text("Sbarra \(Fmt.weight(plateBase, unit: settings.unit))")
                                        .font(Theme.rounded(12, .semibold))
                                        .foregroundStyle(Theme.textTertiary)
                                }
                                if exercise.muscleGroup.hasSides {
                                    SideSegment(singleArm: armBinding, group: exercise.muscleGroup)
                                }
                            }
                            WeightStepper(kilograms: stepperWeight, step: exercise.incrementStep, unit: settings.unit, tint: tint)

                            if isSide {
                                Text("Totale \(Fmt.weight(weight, unit: settings.unit))")
                                    .font(Theme.rounded(12, .semibold))
                                    .foregroundStyle(Theme.textTertiary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }

                            if let plateBase, plateBase > 0, !isSide {
                                let relKg = max(0, weight - plateBase)
                                VStack(spacing: 4) {
                                    HStack {
                                        Text(isRel ? "Carico relativo (dischi)" : "Carico assoluto (totale)")
                                            .font(Theme.rounded(12, .medium))
                                            .foregroundStyle(Theme.textTertiary)
                                        Spacer()
                                        if isRel {
                                            Text("\(Fmt.weight(relKg, unit: settings.unit)) (\(Fmt.weight(relKg / 2, unit: settings.unit)) per lato)")
                                                .font(Theme.rounded(13, .bold))
                                                .foregroundStyle(settings.accentColor)
                                        } else {
                                            Text(Fmt.weight(weight, unit: settings.unit))
                                                .font(Theme.rounded(13, .bold))
                                                .foregroundStyle(Theme.textPrimary)
                                        }
                                    }
                                    HStack {
                                        Text(isRel ? "Carico assoluto (totale con sbarra)" : "Carico relativo (dischi)")
                                            .font(Theme.rounded(12, .medium))
                                            .foregroundStyle(Theme.textTertiary)
                                        Spacer()
                                        if isRel {
                                            Text(Fmt.weight(weight, unit: settings.unit))
                                                .font(Theme.rounded(13, .semibold))
                                                .foregroundStyle(Theme.textPrimary)
                                        } else {
                                            Text("\(Fmt.weight(relKg, unit: settings.unit)) (\(Fmt.weight(relKg / 2, unit: settings.unit)) per lato)")
                                                .font(Theme.rounded(13, .semibold))
                                                .foregroundStyle(settings.accentColor)
                                        }
                                    }
                                }
                                .padding(10)
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }

                            if let delta, abs(delta) > 0.01 {
                                HStack(spacing: 6) {
                                    Image(systemName: delta > 0 ? "arrow.up.right" : "arrow.down.right")
                                    let sign = delta > 0 ? "+" : ""
                                    let perSide = (plateBase != nil || isSide) ? " (\(sign)\(Fmt.weight(settings.unit.toKilograms(delta / 2), unit: settings.unit)) per lato)" : ""
                                    Text("\(sign)\(Fmt.weight(settings.unit.toKilograms(delta), unit: settings.unit))\(perSide) rispetto alla serie precedente")
                                        .font(Theme.rounded(12, .semibold))
                                }
                                .foregroundStyle(delta > 0 ? Theme.positive : Theme.negative)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background((delta > 0 ? Theme.positive : Theme.negative).opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            }
                        }
                    }

                    GlassCard(padding: 18) {
                        VStack(spacing: 16) {
                            SectionHeader(title: "Ripetizioni")
                            RepsStepper(reps: $reps, tint: tint)
                        }
                    }
                    }
                    .tourTarget("load")
                    .id("tour-load")

                    if let plateBase {
                        GlassCard(padding: 18) {
                            VStack(alignment: .leading, spacing: 12) {
                                SectionHeader(title: "Dischi per lato")
                                PlateBarView(
                                    load: Plates.load(total: weight, bar: plateBase, plates: settings.availablePlates),
                                    unit: settings.unit,
                                    tint: tint
                                )
                            }
                        }
                    }

                    GlassCard(padding: 16, tint: isRecord ? Theme.positive : nil) {
                        HStack(alignment: .firstTextBaseline) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(isRecord ? "QUESTA SERIE" : "MASSIMALE DA BATTERE")
                                    .font(Theme.rounded(10, .bold))
                                    .tracking(0.7)
                                    .foregroundStyle(Theme.textTertiary)
                                Text(reference)
                                    .font(Theme.display(24))
                                    .foregroundStyle(isRecord ? Theme.positive : Theme.textPrimary)
                                    .contentTransition(.numericText())
                                    .animation(.snappy, value: reference)
                                if reps > 1 {
                                    Text("questa serie ≈ \(Fmt.weight(estimated, unit: settings.unit)) di 1RM")
                                        .font(Theme.rounded(11, .medium))
                                        .foregroundStyle(Theme.textTertiary)
                                }
                            }
                            Spacer()
                            if isRecord { PRBadge(text: "NUOVO RECORD") }
                        }
                    }

                    recentSetsDropdown

                    if !warmupPlan.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            SectionHeader(title: "Riscaldamento consigliato")
                            HStack(spacing: 8) {
                                ForEach(warmupPlan) { step in
                                    Button {
                                        weight = step.weight
                                        reps = step.reps
                                        Haptics.tap()
                                    } label: {
                                        let dispWeight = fieldKg(step.weight)
                                        VStack(spacing: 2) {
                                            Text(step.percentLabel)
                                                .font(Theme.rounded(10, .bold))
                                                .foregroundStyle(Theme.textTertiary)
                                            Text(Fmt.setLine(weight: dispWeight, reps: step.reps, unit: settings.unit))
                                                .font(Theme.rounded(12, .bold))
                                                .foregroundStyle(Theme.textPrimary)
                                                .minimumScaleFactor(0.7)
                                                .lineLimit(1)
                                        }
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .strokeBorder(Theme.stroke, lineWidth: 1)
                                        )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }

                    HStack(spacing: 10) {
                        Button {
                            onSave(weight, reps, true, singleArm)
                            dismiss()
                        } label: {
                            Label("Riscaldamento", systemImage: "flame.fill")
                                .font(Theme.rounded(15, .semibold))
                                .frame(maxWidth: .infinity)
                                .frame(height: 52)
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .strokeBorder(Theme.stroke, lineWidth: 1)
                                )
                                .foregroundStyle(Theme.textSecondary)
                        }
                        .buttonStyle(.plain)
                        .disabled(weight <= 0 || reps < 1)

                        Button {
                            onSave(weight, reps, false, singleArm)
                            dismiss()
                        } label: {
                            Label(
                                isEditing ? "Salva modifica" : "Aggiungi serie",
                                systemImage: isEditing ? "checkmark.circle.fill" : "plus.circle.fill"
                            )
                            .font(Theme.rounded(16, .bold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(tint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .foregroundStyle(.black)
                        }
                        .buttonStyle(.plain)
                        .disabled(weight <= 0 || reps < 1)
                        .tourTarget("save")
                        .id("tour-save")
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            // il tutorial porta in vista il controllo del passo
            .onChange(of: TourState.shared.step) { scrollForTour(proxy) }
            .onAppear { scrollForTour(proxy) }
            }
            .tourMarks(.load, .save)
            .screenBackground(settings.accentColor)
            .navigationTitle(exercise.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Annulla") { dismiss() }
                }
            }
            .onAppear(perform: prefill)
        }
        .tourSheet(.save, when: !isEditing)
    }

    /// Peso e ripetizioni stanno in cima, il tasto che salva in fondo: sul telefono piccolo il passo del tutorial li porta in vista.
    private func scrollForTour(_ proxy: ScrollViewProxy) {
        guard !isEditing, TourState.shared.phase == .marks else { return }
        let step = TourState.shared.step
        guard step == .load || step == .save else { return }
        DispatchQueue.main.async {
            withAnimation(.smooth(duration: 0.35)) { proxy.scrollTo(step == .load ? "tour-load" : "tour-save", anchor: step == .load ? .top : .bottom) }
        }
    }

    private func prefill() {
        guard !didPrefill else { return }
        didPrefill = true
        if let first = allPreviousSets.first { openDays = [Calendar.current.startOfDay(for: first.createdAt)] }

        if case let .edit(set) = mode {
            weight = set.weight
            reps = set.reps
            isWarmup = set.isWarmup
            singleArm = set.singleArm
            return
        }

        var lastSet: WorkoutSet?
        if case let .add(previous) = mode { lastSet = previous }

        if let lastSet {
            weight = lastSet.weight
            reps = lastSet.reps
            isWarmup = false
        } else if let best = exercise.recordsByDateDesc.first {
            weight = best.weight
            reps = max(best.reps, 5)
        } else {
            weight = exercise.incrementStep * 4
            reps = 8
        }
    }

    private struct DayGroup: Identifiable {
        let day: Date
        let sets: [WorkoutSet]
        var id: Date { day }
    }

    /// Le serie recenti per giorno di calendario (ora locale), gli ultimi 5 giorni.
    private var recentDays: [DayGroup] {
        let calendar = Calendar.current
        let grouped = Dictionary(grouping: allPreviousSets) { calendar.startOfDay(for: $0.createdAt) }
        return grouped.keys.sorted(by: >).prefix(5).map { DayGroup(day: $0, sets: grouped[$0] ?? []) }
    }

    private func bestSet(_ sets: [WorkoutSet]) -> WorkoutSet {
        sets.dropFirst().reduce(sets[0]) { best, candidate in
            MaxRanking.beats(weight: candidate.weight, reps: candidate.reps, than: (best.weight, best.reps)) ? candidate : best
        }
    }

    private func dayBinding(_ day: Date) -> Binding<Bool> {
        Binding(
            get: { openDays.contains(day) },
            set: { if $0 { openDays.insert(day) } else { openDays.remove(day) } }
        )
    }

    private func recentSetButton(_ prev: WorkoutSet) -> some View {
        Button {
            weight = prev.weight
            reps = prev.reps
            isWarmup = false
            singleArm = prev.singleArm
            armMemo = nil
            Haptics.tap()
        } label: {
            HStack(spacing: 8) {
                HStack(spacing: 6) {
                    HStack(spacing: 4) {
                        Text(Fmt.setLine(weight: fieldKg(prev.weight), reps: prev.reps, unit: settings.unit, singleArm: prev.singleArm, group: exercise.muscleGroup))
                        if isRel || isSide {
                            Text("(tot. \(Fmt.weight(prev.weight, unit: settings.unit)))")
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                    }
                    .font(Theme.rounded(14, .bold))
                    .foregroundStyle(Theme.textPrimary)
                    if prev.isPersonalRecord { PRBadge() }
                }
                Spacer()
                if prev.reps > 1 {
                    Text("≈ \(Fmt.weight(prev.estimatedOneRepMax, unit: settings.unit))")
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            .padding(10)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var recentSetsDropdown: some View {
        GlassCard(padding: 16) {
            DisclosureGroup(isExpanded: $isRecentSetsExpanded) {
                if allPreviousSets.isEmpty {
                    Text("Nessuna serie precedente")
                        .font(Theme.rounded(13, .medium))
                        .foregroundStyle(Theme.textTertiary)
                        .padding(.top, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    VStack(spacing: 8) {
                        Text("Tocca una serie per usarla nel modulo.")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 4)

                        ForEach(recentDays) { group in
                            DisclosureGroup(isExpanded: dayBinding(group.day)) {
                                VStack(spacing: 8) {
                                    ForEach(group.sets) { prev in recentSetButton(prev) }
                                }
                                .padding(.top, 8)
                            } label: {
                                let best = bestSet(group.sets)
                                HStack(spacing: 8) {
                                    Text(Fmt.relativeDay(group.day))
                                        .font(Theme.rounded(13, .semibold))
                                        .foregroundStyle(Theme.textPrimary)
                                    Spacer()
                                    Text("\(group.sets.count) serie · \(Fmt.setLine(weight: fieldKg(best.weight), reps: best.reps, unit: settings.unit, singleArm: best.singleArm, group: exercise.muscleGroup))")
                                        .font(Theme.rounded(12, .medium))
                                        .foregroundStyle(Theme.textTertiary)
                                }
                            }
                            .tint(Theme.textSecondary)
                        }
                    }
                    .padding(.top, 8)
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(tint)
                    Text("Ultime serie su questo macchinario")
                        .font(Theme.rounded(14, .semibold))
                        .foregroundStyle(Theme.textPrimary)
                    Spacer()
                    if !allPreviousSets.isEmpty {
                        Text("\(allPreviousSets.count)")
                            .font(Theme.rounded(12, .bold))
                            .foregroundStyle(Theme.textTertiary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Theme.card, in: Capsule())
                    }
                }
            }
            .tint(Theme.textSecondary)
        }
    }
}
