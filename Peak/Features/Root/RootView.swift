import SwiftUI
import SwiftData

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings
    @Environment(\.scenePhase) private var scenePhase

    @State private var selection: Tab = .maxes

    enum Tab: Hashable {
        case maxes, workouts, progress, settings
    }

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
        .task {
            runAutoBackup()
            await SyncService.shared.syncNow()
        }
        .onChange(of: scenePhase) { _, phase in
            // Alla chiusura dell'app il backup automatico resta allineato (se non si è collegati a yG,
            // che ha già i dati): è la rete di sicurezza contro la disinstallazione.
            if phase == .background { runAutoBackup() }
            if phase == .active { Task { await SyncService.shared.syncNow() } }
        }
    }

    private var tabs: some View {
        TabView(selection: $selection) {
            MaxesListView()
                .tabItem { Label("Massimali", systemImage: "trophy.fill") }
                .tag(Tab.maxes)

            WorkoutsListView()
                .tabItem { Label("Allenamenti", systemImage: "figure.strengthtraining.traditional") }
                .tag(Tab.workouts)

            ProgressDashboardView()
                .tabItem { Label("Progressi", systemImage: "chart.line.uptrend.xyaxis") }
                .tag(Tab.progress)

            SettingsView()
                .tabItem { Label("Impostazioni", systemImage: "gearshape.fill") }
                .tag(Tab.settings)
        }
        .tint(settings.accentColor)
    }

    private func runAutoBackup() {
        // Con lo store di emergenza (in memoria) il backup sovrascriverebbe quello buono con un file vuoto.
        guard !AppStore.isFallback, !SyncService.shared.isConnected else { return }
        if let date = try? BackupService.writeAutoBackup(context: context) {
            settings.lastBackupDate = date
        }
    }
}
