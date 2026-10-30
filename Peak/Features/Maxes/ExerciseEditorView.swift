import SwiftUI
import SwiftData
import PhotosUI

/// Crea o modifica un macchinario. `exercise == nil` significa "nuovo".
struct ExerciseEditorView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    let exercise: Exercise?
    /// Chiamata dopo l'eliminazione, per far tornare indietro la vista di dettaglio.
    var onDeleted: (() -> Void)? = nil

    @Query(sort: \Exercise.sortIndex) private var allExercises: [Exercise]

    @State private var name = ""
    @State private var group: MuscleGroup = .petto
    @State private var step: Double = 2.5
    /// Incremento scritto a mano invece di uno dei valori preimpostati.
    @State private var notes = ""
    @State private var barWeight: Double = 0
    @State private var weightDisplay: String = "absolute"
    @State private var plateLoaded = false
    @State private var perSide = false
    @State private var isUnilateral = false
    @State private var restSeconds = 0
    @State private var isArchived = false
    @State private var isHidden = false
    @State private var showingDeleteConfirm = false
    @State private var didLoad = false
    @State private var photoData: Data?
    @State private var pickerItem: PhotosPickerItem?
    @State private var showingCamera = false
    /// Il pittogramma scelto a mano; finché è `nil` lo deduce il nome (e cambia con lui).
    @State private var pickedGlyph: MachineGlyph?
    /// Collegato: l'icona del suo attrezzo; personalizzato: quella che scegli, o nessuna.
    private var glyph: MachineGlyph? { Catalog.entry(id: catalogID)?.machineGlyph ?? pickedGlyph }
    @State private var photoOffset: Double = 0
    @State private var dragStart: Double = 0
    /// L'id della voce del catalogo; vuoto = personalizzato.
    @State private var catalogID = ""
    /// Creato a mano di proposito: non serve collegarlo al catalogo.
    @State private var isCustom = false
    /// I muscoli di un macchinario personalizzato, toccati sulla figura. Collegato, valgono quelli della voce.
    @State private var muscles: [MuscleId] = []
    @State private var figureSide: AnatomyView = .front
    /// Collega (o cambia il collegamento di) un macchinario che c'è già.
    @State private var linking = false
    /// Creazione passo per passo: il passo 3 (essenziale) è aperto; «Altro» è chiuso finché non lo apri.
    @State private var showingEssentials = false
    @State private var showMore = false
    @State private var tipDone = false
    private var linkedEntry: CatalogEntry? { catalogID.isEmpty ? nil : Catalog.entry(id: catalogID) }

    private var isNew: Bool { exercise == nil }

    var body: some View {
        if isNew { wizard } else { editor }
    }

    @ViewBuilder
    private var nameCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Nome")
                TextField("Es. Chest Press", text: $name)
                    .font(Theme.rounded(17, .semibold))
                    .textInputAutocapitalization(.words)
            }
        }
    }

    @ViewBuilder
    private var catalogCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Catalogo")
                HStack {
                    if catalogID.isEmpty {
                        Text(isCustom ? "Macchinario tuo: resta fuori dalle classifiche." : "Non collegato: resta fuori dalle classifiche.")
                            .font(Theme.rounded(13, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    } else {
                        Text(verbatim: linkedEntry?.name ?? catalogID)
                            .font(Theme.rounded(15, .semibold))
                    }
                    Spacer()
                    Button(catalogID.isEmpty ? "Collega" : "Cambia") { linking = true }
                        .font(Theme.rounded(14, .semibold))
                        .foregroundStyle(settings.accentColor)
                }
                if catalogID.isEmpty {
                    Toggle("È un macchinario mio", isOn: $isCustom)
                        .font(Theme.rounded(14, .semibold))
                        .tint(settings.accentColor)
                }
            }
        }
    }

    @ViewBuilder
    private var groupCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Gruppo muscolare")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 8)], spacing: 8) {
                    ForEach(MuscleGroup.allCases) { candidate in
                        Button {
                            group = candidate
                            Haptics.tap()
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: candidate.symbol)
                                    .font(.system(size: 11, weight: .semibold))
                                Text(candidate.title)
                                    .font(Theme.rounded(13, .semibold))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                group == candidate ? candidate.tint.opacity(0.22) : Theme.card,
                                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(group == candidate ? candidate.tint.opacity(0.6) : Theme.stroke, lineWidth: 1)
                            )
                            .foregroundStyle(group == candidate ? candidate.tint : Theme.textSecondary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var stepCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Incremento del carico")
                HStack(spacing: 14) {
                    Button { step = max(0.25, step - 0.25); Haptics.tap() } label: { Image(systemName: "minus").frame(width: 38, height: 38).background(Theme.card, in: Circle()) }
                        .buttonStyle(.plain)
                    // il numero si tocca e si scrive
                    HStack(spacing: 4) {
                        TextField("2,5", value: Binding(get: { step }, set: { if $0 >= 0.25, $0 <= 50 { step = $0 } }),
                                  format: .number.precision(.fractionLength(0...2)))
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .font(Theme.rounded(20, .heavy))
                            .frame(width: 70)
                        Text("kg").font(Theme.rounded(13, .semibold)).foregroundStyle(Theme.textTertiary)
                    }
                    Button { step = min(50, step + 0.25); Haptics.tap() } label: { Image(systemName: "plus").frame(width: 38, height: 38).background(Theme.card, in: Circle()) }
                        .buttonStyle(.plain)
                    Spacer()
                }
                .foregroundStyle(Theme.textPrimary)
                Text("È il salto minimo tra un carico e l'altro su questo macchinario: definisce i pulsanti + e −.")
                    .font(Theme.rounded(11, .medium))
                    .foregroundStyle(Theme.textTertiary)
            }
        }
    }

    @ViewBuilder
    private var advancedCards: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Peso della sbarra")
                Stepper(
                    value: Binding(
                        get: { settings.unit.fromKilograms(barWeight) },
                        set: { barWeight = max(0, settings.unit.toKilograms($0)) }
                    ),
                    in: 0...(settings.unit == .kg ? 60 : 135),
                    step: settings.unit == .kg ? 1 : 2.5
                ) {
                    Text(barWeight > 0
                         ? Fmt.weight(barWeight, unit: settings.unit)
                         : "Nessuna (0)")
                        .font(Theme.rounded(15, .semibold))
                }

                HStack(spacing: 8) {
                    ForEach(settings.unit == .kg ? [0.0, 10.0, 15.0, 20.0] : [0.0, 25.0, 35.0, 45.0], id: \.self) { p in
                        let kg = settings.unit.toKilograms(p)
                        let isOn = abs(barWeight - kg) < 0.1
                        stepChip(p == 0 ? "Nessuna (0)" : "\(Int(p)) \(settings.unit.symbol)", isOn: isOn) {
                            barWeight = kg
                        }
                    }
                }

                Text(barWeight > 0
                     ? "Inclusa nel calcolo del carico totale e dei dischi."
                     : "Nessuna sbarra: il carico inserito è solo dei dischi o del macchinario.")
                    .font(Theme.rounded(11, .medium))
                    .foregroundStyle(Theme.textTertiary)
            }
        }

        if barWeight <= 0 {
            GlassCard {
                Toggle(isOn: $plateLoaded) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Carico a dischi")
                            .font(Theme.rounded(15, .semibold))
                        Text("Calcolatore dischi senza sbarra (es. macchinari plate loaded).")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                }
                .tint(settings.accentColor)
            }
        }

        GlassCard {
            Toggle(isOn: $perSide) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Peso inserito per lato")
                        .font(Theme.rounded(15, .semibold))
                    Text("Inserisci il peso di un lato: il totale è il doppio (più la sbarra).")
                        .font(Theme.rounded(11, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            .tint(settings.accentColor)
        }

        if barWeight > 0, !perSide {
            GlassCard {
                VStack(alignment: .leading, spacing: 12) {
                    SectionHeader(title: "Visualizzazione del carico")
                    HStack(spacing: 8) {
                        stepChip("Assoluto (totale)", isOn: weightDisplay == "absolute") {
                            weightDisplay = "absolute"
                        }
                        stepChip("Relativo (dischi)", isOn: weightDisplay == "relative") {
                            weightDisplay = "relative"
                        }
                    }
                    Text(weightDisplay == "relative"
                         ? "Nelle serie inserisci e visualizzi solo i dischi. Per classifiche e massimali vale sempre il totale con la sbarra."
                         : "Mostra il carico totale sollevato, compreso il peso della sbarra.")
                        .font(Theme.rounded(11, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
        }

        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Esecuzione")
                HStack(spacing: 8) {
                    stepChip(group == .gambe ? "Entrambe le gambe" : "Entrambe le braccia", isOn: !isUnilateral) {
                        isUnilateral = false
                    }
                    stepChip(group == .gambe ? "Una gamba" : "Un braccio", isOn: isUnilateral) {
                        isUnilateral = true
                    }
                }
            }
        }

        if settings.restTimerEnabled {
            GlassCard {
                VStack(alignment: .leading, spacing: 12) {
                    SectionHeader(title: "Recupero")
                    Stepper(value: $restSeconds, in: 0...600, step: 15) {
                        Text(restSeconds == 0
                             ? "Come le impostazioni (\(Fmt.duration(seconds: settings.restSeconds)))"
                             : Fmt.duration(seconds: restSeconds))
                            .font(Theme.rounded(15, .semibold))
                    }
                    Text("Tempo di pausa dopo ogni serie su questo macchinario.")
                        .font(Theme.rounded(11, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
        }

        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Note")
                TextField("Es. sedile al foro 4, impugnatura larga", text: $notes, axis: .vertical)
                    .font(Theme.rounded(14, .regular))
                    .lineLimit(2...5)
            }
        }

        GlassCard {
            Toggle(isOn: $isHidden) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Nascondi agli amici")
                        .font(Theme.rounded(15, .semibold))
                    Text("Non compare nel tuo profilo né nelle classifiche.")
                        .font(Theme.rounded(11, .medium))
                        .foregroundStyle(Theme.textTertiary)
                }
            }
            .tint(settings.accentColor)
        }
    }

    @ViewBuilder
    private var archiveCard: some View {
            GlassCard {
                VStack(alignment: .leading, spacing: 12) {
                    Toggle(isOn: $isArchived) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Archivia")
                                .font(Theme.rounded(15, .semibold))
                            Text("Lo nasconde dalle liste senza perdere lo storico.")
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                    }
                    .tint(settings.accentColor)

                    Divider().overlay(Theme.stroke)

                    Button(role: .destructive) {
                        showingDeleteConfirm = true
                    } label: {
                        Label("Elimina macchinario", systemImage: "trash")
                            .font(Theme.rounded(15, .semibold))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
    }

    // MARK: - Creazione passo per passo

    /// Passi 1 e 2 sono il catalogo (gruppo, poi lista), il 3 l'essenziale. Il salvataggio è `save()`, lo stesso della modifica.
    private var wizard: some View {
        NavigationStack {
            CatalogPickerSheet(title: "Nuovo macchinario", onPick: { entry, picked in
                chosen(entry, group: picked)
                showingEssentials = true
            }, wizard: true)
            .navigationDestination(isPresented: $showingEssentials) { essentials }
        }
        .onAppear(perform: load)
    }

    private var tipsOn: Bool { !settings.machineTipsSeen && allExercises.isEmpty && !tipDone }

    private var essentials: some View {
        ScrollView {
            VStack(spacing: 14) {
                Text("Passo 3 di 3")
                    .font(Theme.rounded(12, .semibold))
                    .foregroundStyle(Theme.textTertiary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)

                nameCard

                if catalogID.isEmpty {
                    GlassCard {
                        VStack(alignment: .leading, spacing: 12) {
                            SectionHeader(title: "Muscoli")
                            musclesContent
                        }
                    }
                }

                VStack(spacing: 14) {
                    stepCard
                    DisclosureGroup(isExpanded: $showMore) {
                        VStack(spacing: 14) {
                            imageCard(showMuscles: !catalogID.isEmpty)
                            advancedCards
                        }
                        .padding(.top, 12)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Altro").font(Theme.rounded(15, .semibold))
                            Text("Sbarra, dischi, foto, recupero, note")
                                .font(Theme.rounded(11, .medium))
                                .foregroundStyle(Theme.textTertiary)
                        }
                    }
                    .tint(settings.accentColor)
                    .padding(.horizontal, 4)
                }
                .tourTarget("tip-step")
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .screenBackground(settings.accentColor)
        .navigationTitle("Nuovo macchinario")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { Button("Annulla") { dismiss() } }
        }
        .safeAreaInset(edge: .bottom) {
            Button { save() } label: { Text("Aggiungi") }
                .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
        }
        .machineTip("tip-step", show: tipsOn, title: "Incremento e Altro",
                    text: "L'incremento è il salto dei pulsanti + e −. Sotto «Altro» ci sono sbarra, dischi, foto e note: puoi lasciarli a dopo.",
                    last: true) { tipDone = true }
    }

    private var editor: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    nameCard
                    catalogCard
                    imageCard(showMuscles: true)
                    groupCard
                    stepCard
                    advancedCards

                    if !isNew { archiveCard }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .screenBackground(settings.accentColor)
            .navigationTitle(isNew ? "Nuovo macchinario" : "Modifica")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Annulla") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Salva") { save() }
                        .font(Theme.rounded(16, .bold))
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .confirmationDialog(
                "Eliminare il macchinario?",
                isPresented: $showingDeleteConfirm,
                titleVisibility: .visible
            ) {
                Button("Elimina tutto lo storico", role: .destructive) { deleteExercise() }
                Button("Annulla", role: .cancel) {}
            } message: {
                Text("Verranno persi anche i massimali registrati. Le serie già svolte restano negli allenamenti.")
            }
            .onAppear(perform: load)
            .sheet(isPresented: $linking) {
                CatalogPickerSheet(title: "Collega al catalogo", allowsCustom: false, onPick: { chosen($0, group: $1) })
            }
        }
    }


    /// Una voce scelta: nel macchinario nuovo precompila tutto (il nome si può cambiare subito con uno familiare).
    /// Se si torna indietro e si sceglie altro, quello che la scelta di prima aveva messo viene sostituito.
    private func chosen(_ entry: CatalogEntry?, group picked: MuscleGroup?) {
        let wasLinked = !catalogID.isEmpty
        catalogID = entry?.id ?? ""
        guard isNew else { return } // collegando uno che c'è già cambia solo il collegamento
        guard let entry else {
            isCustom = true
            if let picked { group = picked }
            if wasLinked { name = ""; notes = ""; step = 2.5; barWeight = 0; plateLoaded = false }
            return
        }
        isCustom = false
        muscles = []
        pickedGlyph = nil
        name = entry.name
        group = entry.muscleGroup
        step = entry.step
        notes = entry.notes
        barWeight = Plates.defaultBar(for: entry.machineGlyph, name: entry.name)
        plateLoaded = Plates.guessPlateLoaded(glyph: entry.machineGlyph, name: entry.name)
    }

    // MARK: - Foto e pittogramma

    /// `showMuscles`: la figura sta qui dentro, tranne che nella creazione di un personalizzato (lì sta già nel passo 3).
    private func imageCard(showMuscles: Bool) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                SectionHeader(title: "Immagine")

                if photoData != nil {
                    largePreview
                }

                HStack(spacing: 14) {
                    if photoData == nil { preview }

                    VStack(alignment: .leading, spacing: 8) {
                        if CameraPicker.isAvailable {
                            Button {
                                showingCamera = true
                            } label: {
                                Label("Scatta foto", systemImage: "camera.fill")
                                    .font(Theme.rounded(14, .semibold))
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(settings.accentColor)
                        }

                        PhotosPicker(selection: $pickerItem, matching: .images, photoLibrary: .shared()) {
                            Label("Scegli dal rullino", systemImage: "photo.on.rectangle")
                                .font(Theme.rounded(14, .semibold))
                                .foregroundStyle(settings.accentColor)
                        }

                        if photoData != nil {
                            Button {
                                withAnimation { photoData = nil }
                                photoOffset = 0
                                dragStart = 0
                                Haptics.tap()
                            } label: {
                                Label("Rimuovi foto", systemImage: "trash")
                                    .font(Theme.rounded(14, .semibold))
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(Theme.negative)
                        }
                    }

                    Spacer()
                }

                Text(photoData != nil ? "Trascina la foto per scegliere cosa si vede nel riquadro."
                     : catalogID.isEmpty ? "Senza foto si vede il pittogramma che scegli qui sotto."
                     : "Senza foto si vede il pittogramma dell'attrezzo del catalogo.")
                    .font(Theme.rounded(11, .medium))
                    .foregroundStyle(Theme.textTertiary)

                Divider().overlay(Theme.stroke)

                if showMuscles {
                    SectionHeader(title: "Muscoli")
                    musclesContent
                }

                SectionHeader(title: "Pittogramma", trailing: glyph?.title ?? "Nessuno")

                if catalogID.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Button {
                                pickedGlyph = nil
                                Haptics.tap()
                            } label: {
                                Text("Nessuno")
                                    .font(Theme.rounded(13, .bold))
                                    .foregroundStyle(glyph == nil ? group.tint : Theme.textTertiary)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(
                                        glyph == nil ? group.tint.opacity(0.18) : Theme.card,
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    )
                            }
                            .buttonStyle(.plain)
                            ForEach(MachineGlyph.allCases) { candidate in
                                Button {
                                    pickedGlyph = candidate
                                    Haptics.tap()
                                } label: {
                                    MachineGlyphView(
                                        glyph: candidate,
                                        tint: glyph == candidate ? group.tint : Theme.textTertiary,
                                        size: 26
                                    )
                                    .frame(width: 44, height: 44)
                                    .background(
                                        glyph == candidate ? group.tint.opacity(0.18) : Theme.card,
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .strokeBorder(glyph == candidate ? group.tint.opacity(0.5) : .clear, lineWidth: 1)
                                    )
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(candidate.title)
                            }
                        }
                        .padding(.horizontal, 2)
                    }
                    .scrollClipDisabled()
                }
            }
        }
        // A tutto schermo, non in una sheet: la fotocamera di UIKit disegna i suoi
        // comandi partendo dall'altezza piena dello schermo, e dentro un foglio
        // (per giunta annidato in un altro foglio) il tasto di scatto finisce
        // fuori dall'area visibile.
        .fullScreenCover(isPresented: $showingCamera) {
            CameraPicker { image in
                photoData = ImageStore.prepare(image)
                photoOffset = 0
                dragStart = 0
                Haptics.commit()
            }
            .ignoresSafeArea()
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    await MainActor.run {
                        photoData = ImageStore.prepare(image)
                        photoOffset = 0
                        dragStart = 0
                        Haptics.commit()
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var musclesContent: some View {
        if let entry = linkedEntry {
            Text("Sono quelli dell'attrezzo del catalogo.")
                .font(Theme.rounded(13, .medium))
                .foregroundStyle(Theme.textTertiary)
            MuscleFigureView(muscles: entry.muscleIDs, view: AnatomyView(rawValue: entry.view), zoom: false)
                .frame(maxWidth: 220)
        } else {
            Text("Tocca i muscoli che lavorano sulla figura.")
                .font(Theme.rounded(13, .medium))
                .foregroundStyle(Theme.textTertiary)
            MuscleFigureView(muscles: muscles, view: figureSide, zoom: false, onTap: toggleMuscle)
                .frame(maxWidth: 220)
            Button(figureSide == .front ? "Vedi di schiena" : "Vedi di fronte") {
                figureSide = figureSide == .front ? .back : .front
            }
            .buttonStyle(.bordered)
        }
    }

    /// Anteprima grande, trascinabile: è qui che si sceglie l'inquadratura,
    /// visto che il ritaglio di sistema della fotocamera è inservibile.
    @ViewBuilder
    private var largePreview: some View {
        if let photoData, let image = UIImage(data: photoData) {
            let side: CGFloat = 200
            let aspect = image.size.height > 0 ? image.size.width / image.size.height : 1
            let vertical = PhotoFraming.isVertical(imageAspect: aspect)
            let canMove = PhotoFraming.overflow(imageAspect: aspect, side: side) > 1

            VStack(spacing: 8) {
                FramedPhoto(image: image, offset: photoOffset, side: side)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .strokeBorder(Theme.stroke, lineWidth: 1)
                    )
                    // Ha la priorità sulla ScrollView: altrimenti un trascinamento
                    // verticale farebbe scorrere la pagina invece di spostare la foto.
                    .highPriorityGesture(
                        DragGesture(minimumDistance: 2)
                            .onChanged { value in
                                guard canMove else { return }
                                let points = vertical ? value.translation.height : value.translation.width
                                let delta = PhotoFraming.normalizedDelta(
                                    dragPoints: points,
                                    imageAspect: aspect,
                                    side: side
                                )
                                photoOffset = min(max(dragStart + Double(delta), -1), 1)
                            }
                            .onEnded { _ in dragStart = photoOffset }
                    )

                HStack(spacing: 10) {
                    if canMove {
                        Label(
                            vertical ? "Trascina in alto o in basso" : "Trascina a destra o a sinistra",
                            systemImage: "hand.draw"
                        )
                        .font(Theme.rounded(11, .medium))
                        .foregroundStyle(Theme.textTertiary)
                    } else {
                        Text("Foto già quadrata: si vede tutta.")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }

                    Spacer()

                    if photoOffset != 0 {
                        Button("Centra") {
                            withAnimation(.snappy) { photoOffset = 0 }
                            dragStart = 0
                            Haptics.tap()
                        }
                        .font(Theme.rounded(12, .semibold))
                        .foregroundStyle(settings.accentColor)
                    }
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder
    private var preview: some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        ZStack {
            if let photoData, let image = UIImage(data: photoData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                group.tint.opacity(0.14)
                if let glyph { MachineGlyphView(glyph: glyph, tint: group.tint, size: 44) }
            }
        }
        .frame(width: 78, height: 78)
        .clipShape(shape)
        .overlay(shape.strokeBorder(Theme.stroke, lineWidth: 1))
    }

    private func toggleMuscle(_ muscle: MuscleId) {
        if let i = muscles.firstIndex(of: muscle) { muscles.remove(at: i) } else { muscles.append(muscle) }
    }

    private func stepChip(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button {
            action()
            Haptics.tap()
        } label: {
            Text(title)
                .font(Theme.rounded(13, .bold))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(
                    isOn ? settings.accentColor.opacity(0.22) : Theme.card,
                    in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                )
                .foregroundStyle(isOn ? settings.accentColor : Theme.textSecondary)
        }
        .buttonStyle(.plain)
    }

    private func load() {
        guard !didLoad else { return }
        didLoad = true
        guard let exercise else {
            barWeight = 0
            weightDisplay = "absolute"
            plateLoaded = false
            perSide = false
            return
        }
        catalogID = exercise.catalogID
        isCustom = exercise.isCustom
        muscles = exercise.muscleIDs
        figureSide = AnatomyView(rawValue: exercise.viewRaw) ?? anatomyView(for: exercise.muscleIDs)
        name = exercise.displayName
        group = exercise.muscleGroup
        step = exercise.incrementStep
        notes = exercise.notes
        isUnilateral = exercise.isUnilateral
        restSeconds = exercise.restSeconds
        isArchived = exercise.isArchived
        isHidden = exercise.isHidden
        photoData = exercise.photo
        photoOffset = exercise.photoOffset
        dragStart = exercise.photoOffset
        pickedGlyph = MachineGlyph(rawValue: exercise.glyphRaw)
        barWeight = exercise.barWeight
        weightDisplay = exercise.weightDisplay
        plateLoaded = exercise.plateLoaded
        perSide = exercise.perSide
    }

    private func save() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if let exercise {
            exercise.name = trimmed
            exercise.muscleGroup = group
            exercise.incrementStep = step
            exercise.notes = notes
            exercise.isUnilateral = isUnilateral
            exercise.restSeconds = restSeconds
            exercise.isArchived = isArchived
            exercise.isHidden = isHidden
            exercise.photo = photoData
            exercise.photoOffset = photoOffset
            exercise.glyphRaw = pickedGlyph?.rawValue ?? ""
            exercise.catalogID = catalogID
            exercise.isCustom = catalogID.isEmpty && isCustom
            exercise.musclesRaw = muscles.map(\.rawValue).joined(separator: ",")
            exercise.viewRaw = figureSide.rawValue
            exercise.barWeight = barWeight
            exercise.weightDisplay = weightDisplay
            exercise.plateLoaded = plateLoaded
            exercise.perSide = perSide
        } else {
            let nextIndex = (allExercises.map(\.sortIndex).max() ?? 0) + 1
            let new = Exercise(
                name: trimmed,
                muscleGroup: group,
                incrementStep: step,
                notes: notes,
                sortIndex: nextIndex
            )
            context.insert(new)
            new.photo = photoData
            new.photoOffset = photoOffset
            new.glyphRaw = pickedGlyph?.rawValue ?? ""
            new.isUnilateral = isUnilateral
            new.restSeconds = restSeconds
            new.catalogID = catalogID
            new.isCustom = catalogID.isEmpty && isCustom
            new.musclesRaw = muscles.map(\.rawValue).joined(separator: ",")
            new.viewRaw = figureSide.rawValue
            new.isHidden = isHidden
            new.barWeight = barWeight
            new.weightDisplay = weightDisplay
            new.plateLoaded = plateLoaded
            new.perSide = perSide
        }
        try? context.save()
        if isNew { settings.machineTipsSeen = true } // ha creato un macchinario: i suggerimenti hanno fatto il loro
        Haptics.commit()
        dismiss()
    }

    private func deleteExercise() {
        guard let exercise else { return }
        Haptics.warning()
        dismiss()
        onDeleted?()

        // La cancellazione vera avviene dopo che il foglio e il dettaglio sono spariti:
        // se l'oggetto venisse rimosso mentre una vista lo sta ancora leggendo,
        // SwiftUI si ritroverebbe con un modello svuotato tra le mani.
        let context = self.context
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            context.delete(exercise)
            try? context.save()
        }
    }
}
