import SwiftUI
import SwiftData

/// Impostazioni → Account yG: collegamento, sincronizzazione, scollegamento.
struct AccountSettingsView: View {
    @Environment(AppSettings.self) private var settings
    @State private var showingConnect = false
    @State private var showingDisconnectConfirm = false
    private let sync = SyncService.shared

    var body: some View {
        SettingsPage { accountCard }
            .sheet(isPresented: $showingConnect) { ConnectSheet() }
            .confirmationDialog(
                "Scollegare questo iPhone?",
                isPresented: $showingDisconnectConfirm,
                titleVisibility: .visible
            ) {
                Button("Scollega", role: .destructive) { sync.disconnect() }
                Button("Annulla", role: .cancel) {}
            } message: {
                Text("I dati restano su questo iPhone e su Peak, ma non si sincronizzano più. Per togliere l'accesso a questo iPhone usa anche yrb4g.com/account/peak.")
            }
    }

    var accountCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Account yG", trailing: accountStatus)

                switch sync.state {
                case .connected, .expired:
                    let expired = sync.state == .expired
                    Text(expired
                        ? "Il collegamento è scaduto: i dati restano qui ma non si sincronizzano."
                        : "Collegato come \(sync.credentials?.name.isEmpty == false ? sync.credentials!.name : "il tuo account yG"). Allenamenti e massimali sono anche su peak.yrb4g.com.")
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textSecondary)

                    if let lastSync = sync.lastSync {
                        Text("Ultima sincronizzazione \(Fmt.dateTime(lastSync))")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                    if sync.pendingCount > 0 {
                        Text("\(sync.pendingCount) modifiche in attesa di essere inviate")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                    if let error = sync.lastError {
                        Text(error)
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(.orange)
                    }

                    if expired {
                        Button { showingConnect = true } label: {
                            Label("Accedi di nuovo", systemImage: "person.crop.circle.badge.checkmark")
                        }
                        .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                    } else {
                        Button {
                            Task { await sync.syncNow() }
                        } label: {
                            Label(sync.isSyncing ? "Sincronizzo…" : "Sincronizza adesso", systemImage: "arrow.triangle.2.circlepath")
                        }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(sync.isSyncing)
                    }

                    Button(role: .destructive) {
                        showingDisconnectConfirm = true
                    } label: {
                        Label("Scollega questo iPhone", systemImage: "link.badge.plus")
                            .font(Theme.rounded(14, .semibold))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Theme.negative)

                default:
                    Text("Con l'account yG i tuoi dati si sincronizzano con il web e restano al sicuro sul server. Senza, Peak funziona solo su questo iPhone.")
                        .font(Theme.rounded(12, .medium))
                        .foregroundStyle(Theme.textSecondary)
                    Button { showingConnect = true } label: {
                        Label("Collega a yG", systemImage: "person.crop.circle.badge.checkmark")
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                }
            }
        }
    }

    var accountStatus: String {
        switch sync.state {
        case .connected: return "collegato"
        case .expired: return "scaduto"
        default: return "non collegato"
        }
    }
}
