import SwiftUI
import SwiftData

@main
struct PeakApp: App {
    @State private var settings = AppSettings()
    private let container = AppStore.sharedContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .id(settings.language)   // cambiando lingua tutto si ridisegna
                .environment(\.locale, Locale(identifier: Lang.code(for: settings.language)))
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
