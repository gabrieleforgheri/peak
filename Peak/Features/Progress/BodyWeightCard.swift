import SwiftUI
import SwiftData
import Charts

/// Peso corporeo: ultima pesata, andamento e inserimento rapido.
/// Sta nei Progressi perché è lì che si guarda l'andamento di tutto il resto.
struct BodyWeightCard: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings

    @Query(sort: \BodyWeightEntry.date, order: .reverse) private var entries: [BodyWeightEntry]

    @State private var showingEditor = false
    @State private var editing: BodyWeightEntry?
    @State private var importing = false
    @State private var importMessage: String?
    @State private var showingAll = false

    @State private var range: ChartRange = .all

    private var latest: BodyWeightEntry? { entries.first }

    /// Variazione rispetto alla pesata di circa un mese prima.
    private var monthDelta: Double? {
        guard let latest else { return nil }
        let cutoff = latest.date.addingTimeInterval(-30 * 86_400)
        guard let previous = entries.first(where: { $0.date <= cutoff }) else { return nil }
        return latest.weight - previous.weight
    }

    private var availableRanges: [ChartRange] {
        guard let earliest = entries.map(\.date).min() else { return [.all] }
        let spanDays = max(0, Date().timeIntervalSince(earliest) / 86_400)
        return ChartRange.available(forSpanDays: spanDays)
    }

    private var activeRange: ChartRange {
        availableRanges.contains(range) ? range : (availableRanges.first ?? .all)
    }

    /// Solo le pesate vere, una per passo: i giorni in cui non ti sei pesato non
    /// devono occupare spazio sull'asse.
    private var points: [(index: Int, entry: BodyWeightEntry)] {
        let cutoff = activeRange.days.map { Date().addingTimeInterval(-$0 * 86_400) }
        return entries
            .filter { cutoff == nil || $0.date >= cutoff! }
            .reversed()
            .enumerated()
            .map { ($0.offset, $0.element) }
    }

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                SectionHeader(title: "Peso corporeo", trailing: latest.map { Fmt.relativeDay($0.date) })

                if let latest {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(Fmt.weightValue(latest.weight, unit: settings.unit))
                            .font(Theme.display(38))
                            .foregroundStyle(Theme.textPrimary)
                        Text(settings.unit.symbol)
                            .font(Theme.rounded(15, .semibold))
                            .foregroundStyle(Theme.textSecondary)

                        if let monthDelta, abs(monthDelta) >= 0.05 {
                            let sign = monthDelta > 0 ? "+" : "−"
                            Text("\(sign)\(Fmt.weight(abs(monthDelta), unit: settings.unit)) in 30 gg")
                                .font(Theme.rounded(11, .semibold))
                                .foregroundStyle(Theme.textTertiary)
                        }
                    }
                } else {
                    Text("Nessuna pesata registrata")
                        .font(Theme.rounded(13, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }

                if entries.count >= 2 {
                    if availableRanges.count > 1 {
                        Picker("Intervallo", selection: $range) {
                            ForEach(availableRanges) { Text(LocalizedStringKey($0.title)).tag($0) }
                        }
                        .pickerStyle(.segmented)
                    }

                    if points.count >= 2 {
                        Chart(points, id: \.entry.persistentModelID) { point in
                            LineMark(
                                x: .value("Pesata", Double(point.index)),
                                y: .value("Peso", settings.unit.fromKilograms(point.entry.weight))
                            )
                            .foregroundStyle(settings.accentColor)
                            .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
                            .interpolationMethod(.monotone)

                            PointMark(
                                x: .value("Pesata", Double(point.index)),
                                y: .value("Peso", settings.unit.fromKilograms(point.entry.weight))
                            )
                            .foregroundStyle(point.entry.fromHealth ? settings.accentColor.opacity(0.5) : settings.accentColor)
                            .symbolSize(30)
                        }
                        .chartXScale(domain: -0.5...(Double(Swift.max(points.count - 1, 1)) + 0.5))
                        .chartYAxis {
                            AxisMarks(position: .leading) { value in
                                AxisGridLine().foregroundStyle(Theme.stroke)
                                AxisValueLabel {
                                    if let number = value.as(Double.self) {
                                        Text(number.formatted(.number.precision(.fractionLength(0))))
                                            .font(Theme.rounded(10, .medium))
                                            .foregroundStyle(Theme.textTertiary)
                                    }
                                }
                            }
                        }
                        .chartXAxis {
                            AxisMarks(values: Stats.tickIndices(count: points.count, max: 3).map(Double.init)) { value in
                                AxisValueLabel {
                                    if let index = value.as(Double.self).map({ Int($0.rounded()) }), points.indices.contains(index) {
                                        Text(Fmt.shortDate(points[index].entry.date))
                                            .font(Theme.rounded(10, .medium))
                                            .foregroundStyle(Theme.textTertiary)
                                    }
                                }
                            }
                        }
                        .frame(height: 120)
                        .drawIn()
                    } else {
                        Text("Dati non sufficienti per questo intervallo")
                            .font(Theme.rounded(12, .medium))
                            .foregroundStyle(Theme.textTertiary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .frame(height: 80)
                    }
                }

                HStack(spacing: 8) {
                    Button {
                        showingEditor = true
                    } label: {
                        Label("Aggiungi pesata", systemImage: "plus")
                            .font(Theme.rounded(13, .bold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(settings.accentColor.opacity(0.20), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                            .foregroundStyle(settings.accentColor)
                    }
                    .buttonStyle(.plain)

                    if settings.healthSyncEnabled {
                        Button {
                            Task { await importFromHealth() }
                        } label: {
                            Label(importing ? "Importo…" : "Da Salute", systemImage: "heart.fill")
                                .font(Theme.rounded(13, .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 9)
                                .background(Theme.card, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                                .foregroundStyle(Theme.textSecondary)
                        }
                        .buttonStyle(.plain)
                        .disabled(importing)
                    }
                }

                if let importMessage {
                    Text(importMessage)
                        .font(Theme.rounded(11, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }

                // Le ultime pesate: toccane una per correggerla o cancellarla.
                if !entries.isEmpty {
                    Divider().overlay(Theme.stroke)
                    ForEach(entries.prefix(5)) { entry in
                        weighRow(entry)
                    }
                    if entries.count > 5 {
                        Button {
                            withAnimation(.easeOut(duration: 0.2)) { showingAll.toggle() }
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 11, weight: .bold))
                                    .rotationEffect(.degrees(showingAll ? 90 : 0))
                                Text("Tutte le pesate")
                                    .font(Theme.rounded(13, .bold))
                                Spacer()
                                Text("\(entries.count)")
                                    .font(Theme.rounded(11, .semibold))
                            }
                            .foregroundStyle(Theme.textSecondary)
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if showingAll {
                            MonthStack(
                                items: Array(entries),
                                date: \.date,
                                count: { $0 == 1 ? "1 pesata" : "\($0) pesate" },
                                openFirst: false
                            ) { entry in
                                weighRow(entry)
                            }
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $showingEditor) {
            BodyWeightSheet(lastWeight: latest?.weight)
        }
        .sheet(item: $editing) { entry in
            BodyWeightSheet(lastWeight: entry.weight, entry: entry)
        }
    }

    /// Una pesata: toccala per correggerla o cancellarla.
    private func weighRow(_ entry: BodyWeightEntry) -> some View {
        Button {
            editing = entry
        } label: {
            HStack {
                Text(Fmt.date(entry.date))
                    .font(Theme.rounded(12, .medium))
                    .foregroundStyle(Theme.textTertiary)
                Spacer()
                Text(Fmt.weight(entry.weight, unit: settings.unit))
                    .font(Theme.rounded(13, .semibold))
                    .foregroundStyle(Theme.textPrimary)
                Image(systemName: "pencil")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Theme.textTertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// Prende l'ultima pesata da Salute, saltandola se è la stessa già registrata.
    private func importFromHealth() async {
        importing = true
        defer { importing = false }

        do {
            guard let sample = try await HealthService.latestBodyWeight() else {
                importMessage = "Salute non ha ancora nessuna pesata."
                return
            }
            if let latest, abs(latest.date.timeIntervalSince(sample.date)) < 60 {
                importMessage = "Già aggiornato."
                return
            }
            let entry = BodyWeightEntry(weight: sample.weight, date: sample.date, fromHealth: true)
            context.insert(entry)
            try context.save()
            importMessage = "Importata la pesata del \(Fmt.date(sample.date))."
            Haptics.success()
        } catch {
            importMessage = error.localizedDescription
        }
    }
}

/// Inserimento di una pesata.
struct BodyWeightSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    let lastWeight: Double?
    /// Se c'è, la sheet corregge (o cancella) questa pesata invece di crearne una.
    var entry: BodyWeightEntry?

    @State private var weight: Double = 75
    @State private var date = Date()
    @State private var didPrefill = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    GlassCard(padding: 18, tint: settings.accentColor) {
                        VStack(spacing: 18) {
                            SectionHeader(title: "Peso")
                            WeightStepper(
                                kilograms: $weight,
                                step: 0,
                                unit: settings.unit,
                                tint: settings.accentColor,
                                nativeStep: settings.unit.bodyWeightStep
                            )
                        }
                    }

                    GlassCard {
                        DatePicker("Data", selection: $date, in: ...Date(), displayedComponents: .date)
                            .font(Theme.rounded(14, .medium))
                    }

                    if settings.healthSyncEnabled, entry == nil {
                        Text("La pesata verrà scritta anche in Salute.")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }

                    if let entry {
                        Button(role: .destructive) {
                            context.delete(entry)
                            try? context.save()
                            Haptics.warning()
                            dismiss()
                        } label: {
                            Label("Cancella pesata", systemImage: "trash")
                                .font(Theme.rounded(14, .semibold))
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Theme.negative)
                        .padding(.top, 6)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .screenBackground(settings.accentColor)
            .navigationTitle(entry == nil ? "Pesata" : "Modifica pesata")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Annulla") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Salva") { save() }
                        .font(Theme.rounded(16, .bold))
                        .disabled(weight <= 0)
                }
            }
            .onAppear {
                guard !didPrefill else { return }
                didPrefill = true
                if let lastWeight { weight = lastWeight }
                if let entry { date = entry.date }
            }
        }
    }

    private func save() {
        if let entry {
            entry.weight = weight
            entry.date = date
            try? context.save()
            Haptics.commit()
            dismiss()
            return
        }
        let entry = BodyWeightEntry(weight: weight, date: date)
        context.insert(entry)
        try? context.save()

        if settings.healthSyncEnabled {
            let kilograms = weight
            let when = date
            Task { try? await HealthService.saveBodyWeight(kilograms, date: when) }
        }

        Haptics.commit()
        dismiss()
    }
}
