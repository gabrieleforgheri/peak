import SwiftUI
import SwiftData

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.scenePhase) private var scenePhase

    private let nav = AppNav.shared
    private let tour = TourState.shared

    /// Il primo avvio di un'app vuota (deciso una volta sola all'apertura) e se la sincronizzazione ha già detto la sua.
    @State private var decided = false
    @State private var onboarding = false
    @State private var synced = false
    /// Riepilogo di un allenamento chiuso dal Watch mentre la sessione non era a schermo.
    @State private var summaryWorkout: Workout?

    /// Il tutorial parte da solo (dopo il primo avvio, o da «Rivedi il tutorial») e resta finché non lo chiudi, anche se intanto le impostazioni cambiano.
    private var wantTutorial: Bool {
        synced && settings.didSeeWelcome && settings.weeklyGoal > 0 && (!settings.tutorialDone || settings.tourSeen < 2) && !AppStore.isFallback
    }

    var body: some View {
        ZStack {
            tabs
            // il primo avvio, a tutto schermo; chi ha già dei dati (un aggiornamento, un account ritrovato) non lo vede
            if onboarding {
                OnboardingView { settings.didSeeWelcome = true; onboarding = false }
                    .transition(.opacity)
                    .zIndex(1)
            } else if !decided && !settings.didSeeWelcome {
                Theme.background(accent: settings.accentColor).zIndex(1)
            }
            if tour.phase == .ideas {
                IdeasView(onNext: { tour.phase = .marks }, onSkip: { tour.skip(settings) })
                    .transition(.opacity)
                    .zIndex(1)
            }
            if tour.running { TourDriver() }
        }
        .animation(.easeInOut(duration: 0.25), value: onboarding)
        .animation(.easeInOut(duration: 0.25), value: tour.phase == .ideas)
        .onAppear(perform: decide)
        .onChange(of: wantTutorial, initial: true) { _, want in
            if want, !tour.running { tour.start() }
        }
        // la prima volta: prima l'obiettivo, poi il tutorial
        .fullScreenCover(isPresented: .constant(settings.didSeeWelcome && settings.weeklyGoal == 0)) {
            GoalPickerView(current: 0, suggested: 3) {}
        }
        .task {
            // senza rete (o con un server lento) il tutorial non aspetta più di qualche secondo
            Task { try? await Task.sleep(for: .seconds(4)); synced = true }
            runAutoBackup()
            linkCatalogOnce()
            Programs.adoptLoose(context: context, name: "La mia scheda")
            freezeGlyphsOnce()
            await SyncService.shared.syncNow()
            synced = true
            await Catalog.refresh()
        }
        .sheet(item: $summaryWorkout) { workout in
            WorkoutSummaryView(workout: workout) { summaryWorkout = nil }
        }
        .onChange(of: nav.pendingSummary, initial: true) { presentPendingSummary() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { presentPendingSummary() }
            // Alla chiusura dell'app il backup automatico resta allineato (se non si è collegati a yG,
            // che ha già i dati): è la rete di sicurezza contro la disinstallazione.
            if phase == .background { runAutoBackup() }
            if phase == .active { Task { await SyncService.shared.syncNow() } }
        }
    }

    /// Il Watch ha chiuso l'allenamento: se la sua sessione è a schermo è lei a congedarsi (template, riepilogo),
    /// altrimenti il riepilogo completo si apre qui, appena l'app è in primo piano.
    private func presentPendingSummary() {
        guard let id = nav.pendingSummary, nav.visibleSession != id, scenePhase == .active, summaryWorkout == nil else { return }
        nav.pendingSummary = nil
        var descriptor = FetchDescriptor<Workout>(predicate: #Predicate { $0.uuid == id })
        descriptor.fetchLimit = 1
        summaryWorkout = try? context.fetch(descriptor).first
    }

    /// Un'app vuota parte dal primo avvio; una con dei dati (o con lo store di emergenza) no.
    private func decide() {
        guard !decided else { return }
        decided = true
        guard !settings.didSeeWelcome, !AppStore.isFallback else { return }
        if Onboarding.hasData(context) { settings.didSeeWelcome = true } else { onboarding = true }
    }

    /// L'icona dei macchinari: il pittogramma di un macchinario a pacco pesi, lo stesso disegno che si vede nelle liste.
    private var machinesIcon: Image {
        let renderer = ImageRenderer(content: MachineGlyphView(glyph: .latPulldown, tint: .white, size: 26))
        renderer.scale = 3
        return Image(uiImage: renderer.uiImage ?? UIImage()).renderingMode(.template)
    }

    private var tabs: some View {
        @Bindable var nav = nav
        return TabView(selection: $nav.tab) {
            WorkoutsListView()
                .tabItem { Label("Allenamento", systemImage: "figure.strengthtraining.traditional") }
                .tag(AppNav.Tab.workouts)

            MaxesListView()
                .tabItem { Label { Text("Macchinari") } icon: { machinesIcon } }
                .tag(AppNav.Tab.machines)

            MeView()
                .tabItem { Label("Me", systemImage: "person.crop.circle.fill") }
                .tag(AppNav.Tab.me)

            FriendsView()
                .tabItem { Label("Amici", systemImage: "person.2.fill") }
                .tag(AppNav.Tab.friends)
        }
        .tint(settings.accentColor)
        .overlayPreferenceValue(TourTargetKey.self) { anchors in
            GeometryReader { geo in
                if tour.phase == .marks || tour.phase == .cheer, !AppStore.isFallback {
                    TourOverlay(rects: anchors.mapValues { geo[$0] }, size: geo.size)
                }
            }
            .ignoresSafeArea()
        }
    }

    /// Gli esercizi che c'erano prima del catalogo si collegano per nome, una volta sola; gli altri restano personalizzati.
    private func linkCatalogOnce() {
        guard !settings.didLinkCatalog, !AppStore.isFallback else { return }
        for exercise in (try? context.fetch(FetchDescriptor<Exercise>())) ?? [] where exercise.catalogID.isEmpty {
            exercise.catalogID = Catalog.match(name: exercise.name)?.id ?? ""
        }
        try? context.save()
        settings.didLinkCatalog = true
    }

    /// Prima un'icona vuota voleva dire «dedotta dal nome», ora vuol dire «nessuna»: i personalizzati che c'erano tengono,
    /// fissata una volta, l'icona che vedevano. Separato da `linkCatalogOnce`, che sui telefoni già in 3.0 è passato senza.
    /// Come `migrateCatalog` sul server.
    private func freezeGlyphsOnce() {
        guard !settings.didFreezeGlyphs, !AppStore.isFallback else { return }
        for exercise in (try? context.fetch(FetchDescriptor<Exercise>())) ?? [] where exercise.catalogID.isEmpty && !exercise.hasCustomGlyph {
            exercise.glyphRaw = MachineGlyph.suggested(for: exercise.name, group: exercise.muscleGroup).rawValue
        }
        try? context.save()
        settings.didFreezeGlyphs = true
    }

    private func runAutoBackup() {
        // Con lo store di emergenza (in memoria) il backup sovrascriverebbe quello buono con un file vuoto.
        guard !AppStore.isFallback, !SyncService.shared.isConnected else { return }
        if let date = try? BackupService.writeAutoBackup(context: context) {
            settings.lastBackupDate = date
        }
    }
}
