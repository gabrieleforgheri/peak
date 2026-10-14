import SwiftUI
import SwiftData

@main
struct PeakApp: App {
    @State private var settings = AppSettings()
    private let container = AppStore.sharedContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(settings)
                .tint(settings.accentColor)
                .preferredColorScheme(.dark)
                .task {
                    SyncService.shared.configure(container: container, settings: settings)
                    WatchBridge.shared.activate(container: container, settings: settings)
                }
        }
        .modelContainer(container)
    }
}
