import SwiftUI
import SwiftData

/// Le sottopagine delle impostazioni.
enum SettingsCategory: String, CaseIterable, Identifiable, Hashable {
    case general, training, account, health, watch, backup, data

    var id: String { rawValue }

    var title: String {
        switch self {
        case .general: return "Generale"
        case .training: return "Allenamento"
        case .account: return "Account yG"
        case .health: return "App Salute"
        case .watch: return "Apple Watch"
        case .backup: return "Backup"
        case .data: return "Dati"
        }
    }

    var symbol: String {
        switch self {
        case .general: return "slider.horizontal.3"
        case .training: return "dumbbell"
        case .account: return "icloud"
        case .health: return "heart"
        case .watch: return "applewatch"
        case .backup: return "externaldrive"
        case .data: return "archivebox"
        }
    }
}

/// Una sottopagina: scorre, ha lo sfondo e il titolo dell'app.
struct SettingsPage<Content: View>: View {
    @Environment(AppSettings.self) private var settings
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(spacing: 14) { content }
                .padding(.horizontal, 16)
                .padding(.bottom, 28)
        }
        .screenBackground(settings.accentColor)
    }
}

struct SettingsView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    categoryList
                    infoCard
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 28)
            }
            .screenBackground(settings.accentColor)
            .navigationTitle("Impostazioni")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Fine") { dismiss() } } }
            .onChange(of: AppNav.shared.closeSettings) { dismiss() }
            .navigationDestination(for: SettingsCategory.self) { category in
                page(category)
                    .navigationTitle(category.title)
                    .navigationBarTitleDisplayMode(.inline)
            }
        }
    }

    @ViewBuilder
    private func page(_ category: SettingsCategory) -> some View {
        switch category {
        case .general: GeneralSettingsView()
        case .training: TrainingSettingsView()
        case .account: AccountSettingsView()
        case .health: HealthSettingsView()
        case .watch: WatchSettingsView()
        case .backup: BackupSettingsView()
        case .data: DataSettingsView()
        }
    }

    private var categoryList: some View {
        GlassCard(padding: 6) {
            VStack(spacing: 0) {
                ForEach(SettingsCategory.allCases) { category in
                    NavigationLink(value: category) {
                        HStack(spacing: 12) {
                            Image(systemName: category.symbol)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(settings.accentColor)
                                .frame(width: 28)
                            Text(category.title)
                                .font(Theme.rounded(16, .semibold))
                                .foregroundStyle(Theme.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Theme.textTertiary)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 13)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if category != SettingsCategory.allCases.last {
                        Divider().overlay(Theme.stroke).padding(.leading, 52)
                    }
                }
            }
        }
    }

    private var infoCard: some View {
        Text("Peak \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")")
            .font(Theme.rounded(11, .medium))
            .foregroundStyle(Theme.textTertiary)
            .frame(maxWidth: .infinity)
    }
}
