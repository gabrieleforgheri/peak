import SwiftUI
import SwiftData

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.scenePhase) private var scenePhase

    private let nav = AppNav.shared

    var body: some View {
        ZStack {
            tabs
            // La prima pagina della 2.0, una volta sola.
            if !settings.didSeeWelcome {
                WelcomeView()
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: settings.didSeeWelcome)
        // la prima volta: prima l'obiettivo, poi il tutorial
        .fullScreenCover(isPresented: .constant(settings.didSeeWelcome && settings.weeklyGoal == 0)) {
            GoalPickerView(current: 0, suggested: 3) {}
        }
        .task {
            runAutoBackup()
            linkCatalogOnce()
            await SyncService.shared.syncNow()
            await Catalog.refresh()
        }
        .onChange(of: scenePhase) { _, phase in
            // Alla chiusura dell'app il backup automatico resta allineato (se non si è collegati a yG,
            // che ha già i dati): è la rete di sicurezza contro la disinstallazione.
            if phase == .background { runAutoBackup() }
            if phase == .active { Task { await SyncService.shared.syncNow() } }
        }
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
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(AppNav.Tab.home)

            WorkoutsListView()
                .tabItem { Label("Allenamento", systemImage: "figure.strengthtraining.traditional") }
                .tag(AppNav.Tab.workouts)

            ProgressDashboardView()
                .tabItem { Label("Progressi", systemImage: "chart.line.uptrend.xyaxis") }
                .tag(AppNav.Tab.progress)

            MaxesListView()
                .tabItem { Label { Text("Macchinari") } icon: { machinesIcon } }
                .tag(AppNav.Tab.machines)

            FriendsView()
                .tabItem { Label("Amici", systemImage: "person.2.fill") }
                .tag(AppNav.Tab.friends)
        }
        .tint(settings.accentColor)
        .overlayPreferenceValue(TourTargetKey.self) { anchors in
            GeometryReader { geo in
                if settings.didSeeWelcome, settings.weeklyGoal > 0, !settings.tutorialDone, !AppStore.isFallback {
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

    private func runAutoBackup() {
        // Con lo store di emergenza (in memoria) il backup sovrascriverebbe quello buono con un file vuoto.
        guard !AppStore.isFallback, !SyncService.shared.isConnected else { return }
        if let date = try? BackupService.writeAutoBackup(context: context) {
            settings.lastBackupDate = date
        }
    }
}
