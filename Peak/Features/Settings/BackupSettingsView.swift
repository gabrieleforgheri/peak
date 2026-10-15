import SwiftUI
import SwiftData

import UniformTypeIdentifiers

/// Impostazioni → Backup: esporta, importa.
struct BackupSettingsView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSettings.self) private var settings

    // Un solo stato opzionale invece di URL + Bool separati: con .sheet(isPresented:)
    // impostare i due insieme può far presentare lo sheet prima che il secondo stato
    // sia visibile al closure, aprendolo vuoto. .sheet(item:) lega la presentazione
    // direttamente al valore, eliminando la race.
    private struct ShareFile: Identifiable {
        let id = UUID()
        let url: URL
    }
    @State private var shareFile: ShareFile?
    @State private var showingImporter = false
    @State private var pendingImport: Data?
    @State private var showingImportChoice = false
    @State private var alert: AlertPayload?
    @State private var isImporting = false
    private let sync = SyncService.shared

    private struct AlertPayload: Identifiable {
        let id = UUID()
        let title: String
        let message: String
    }

    var body: some View {
        SettingsPage { backupCard }
            .sheet(item: $shareFile) { file in
                ShareSheet(items: [file.url]) { completed in
                    if completed { Haptics.success() }
                }
            }
            .fileImporter(
                isPresented: $showingImporter,
                allowedContentTypes: [.json],
                allowsMultipleSelection: false
            ) { result in
                handlePickedFile(result)
            }
            .confirmationDialog(
                "Come importare il backup?",
                isPresented: $showingImportChoice,
                titleVisibility: .visible
            ) {
                Button("Unisci ai dati attuali") { performImport(mode: .merge) }
                Button("Sostituisci tutto", role: .destructive) { performImport(mode: .replace) }
                Button("Annulla", role: .cancel) { pendingImport = nil }
            } message: {
                Text("«Unisci» aggiunge solo ciò che manca. «Sostituisci» cancella i dati attuali e riparte dal file.")
            }
            .alert(
                alert?.title ?? "",
                isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })
            ) {
                Button("Ok", role: .cancel) {}
            } message: {
                Text(alert?.message ?? "")
            }
    }

    var backupCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Backup")

                Text(sync.isConnected
                    ? "I dati sono anche su Peak, sul server. Un file ogni tanto, su iCloud Drive, resta comunque una copia in più."
                    : "I dati vivono solo su questo iPhone. Esporta un file ogni tanto e tienilo su iCloud Drive: è l'unico modo per non perderli se l'app viene disinstallata.")
                    .font(Theme.rounded(12, .medium))
                    .foregroundStyle(Theme.textSecondary)

                Button {
                    exportBackup()
                } label: {
                    Label("Esporta backup", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))

                Button {
                    showingImporter = true
                } label: {
                    Label("Importa backup", systemImage: "square.and.arrow.down")
                }
                .buttonStyle(SecondaryButtonStyle())

                Divider().overlay(Theme.stroke)

                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Backup automatico")
                            .font(Theme.rounded(13, .semibold))
                            .foregroundStyle(Theme.textPrimary)
                        Text(settings.lastBackupDate.map { "Aggiornato \(Fmt.dateTime($0))" } ?? "Non ancora eseguito")
                            .font(Theme.rounded(11, .medium))
                            .foregroundStyle(Theme.textTertiary)
                    }
                    Spacer()
                    Image(systemName: "folder")
                        .foregroundStyle(Theme.textTertiary)
                }

                Text("Lo trovi in File › Sul mio iPhone › Peak › \(BackupService.autoBackupFilename)")
                    .font(Theme.rounded(11, .medium))
                    .foregroundStyle(Theme.textTertiary)
            }
        }
    }

    private func exportBackup() {
        do {
            shareFile = ShareFile(url: try BackupService.makeShareFile(context: context))
        } catch {
            alert = AlertPayload(title: "Esportazione non riuscita", message: error.localizedDescription)
        }
    }

    private func handlePickedFile(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            do {
                pendingImport = try Data(contentsOf: url)
                showingImportChoice = true
            } catch {
                alert = AlertPayload(title: "Import non riuscito", message: error.localizedDescription)
            }
        case .failure(let error):
            alert = AlertPayload(title: "Import non riuscito", message: error.localizedDescription)
        }
    }

    private func performImport(mode: RestoreMode) {
        guard let data = pendingImport else { return }
        pendingImport = nil
        if sync.state == .connected {
            importThroughServer(data, replace: mode == .replace)
            return
        }
        do {
            let file = try BackupService.restore(from: data, context: context, mode: mode)
            Haptics.success()
            alert = AlertPayload(
                title: "Backup importato",
                message: "\(file.exercises.count) macchinari e \(file.workouts.count) allenamenti dal \(Fmt.date(file.exportedAt))."
            )
        } catch {
            Haptics.warning()
            alert = AlertPayload(title: "Import non riuscito", message: error.localizedDescription)
        }
    }

    /// Collegato: il backup passa dal server (che unisce i macchinari con lo stesso nome) e poi l'iPhone si riallinea.
    private func importThroughServer(_ data: Data, replace: Bool) {
        guard !isImporting else { return }
        isImporting = true
        Task {
            defer { isImporting = false }
            do {
                try sync.writePreConnectionBackup()
                try await sync.uploadBackup(data, replace: replace, first: false)
                try await sync.replaceLocalWithServer()
                Haptics.success()
                alert = AlertPayload(title: "Backup importato", message: "I dati sono su Peak e su questo iPhone.")
            } catch {
                Haptics.warning()
                alert = AlertPayload(title: "Import non riuscito", message: error.localizedDescription)
            }
        }
    }
}
