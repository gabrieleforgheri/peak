import AuthenticationServices
import SwiftUI
import UniformTypeIdentifiers

/// Il collegamento a yG, passo dopo passo: login, poi (se serve) la scelta su cosa fare dei dati.
/// Sta dentro la pagina di benvenuto e dentro il foglio che si apre da banner e impostazioni.
struct ConnectFlowView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.webAuthenticationSession) private var webAuthentication

    var onFinish: () -> Void
    var onCancel: () -> Void

    @State private var flow = ConnectFlow()
    @State private var started = false
    @State private var showingImporter = false
    @State private var importError: String?

    var body: some View {
        VStack(spacing: 18) {
            Spacer(minLength: 0)
            content
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task {
            guard !started else { return }
            started = true
            await login()
        }
        .onChange(of: flow.stage) { _, stage in
            if stage == .done { onFinish() }
        }
        .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
            pickBackup(result)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch flow.stage {
        case .idle:
            message(symbol: "person.crop.circle.badge.questionmark", title: "Accesso annullato", text: "Il collegamento non è stato fatto. Puoi riprovare quando vuoi.")
            retryAndCancel

        case .working(let text):
            ProgressView().controlSize(.large).tint(settings.accentColor)
            Text(text)
                .font(Theme.rounded(15, .semibold))
                .foregroundStyle(Theme.textSecondary)

        case .emptyServer(let hasLocalData):
            message(
                symbol: "tray",
                title: "Il tuo account Peak è vuoto",
                text: hasLocalData
                    ? "Puoi portarci i dati di questo iPhone oppure partire da un backup."
                    : "Parti dal catalogo di macchinari o importa un backup."
            )
            if hasLocalData {
                choice("Carica i dati di questo iPhone", primary: true) { Task { await flow.uploadLocalData() } }
            } else {
                choice("Parti dal catalogo", primary: true) { Task { await flow.startFromCatalog() } }
            }
            choice("Importa un backup", primary: false) { showingImporter = true }
            if let importError { errorText(importError) }
            cancelButton

        case .conflict:
            message(
                symbol: "arrow.triangle.merge",
                title: "Ci sono dati qui e su Peak",
                text: "Prima di continuare una copia dei dati di questo iPhone resta nell'app File (Peak-prima-del-collegamento.json)."
            )
            choice("Unisci", primary: true, detail: "Aggiunge i dati di questo iPhone a quelli di Peak. I macchinari con lo stesso nome si uniscono.") {
                Task { await flow.merge() }
            }
            choice("Usa i dati del server", primary: false, detail: "Questo iPhone riparte dai dati di Peak.") {
                Task { await flow.useServerData() }
            }
            cancelButton

        case .failed(let text):
            message(symbol: "exclamationmark.triangle", title: "Collegamento non riuscito", text: text)
            retryAndCancel

        case .done:
            ProgressView().tint(settings.accentColor)
        }
    }

    private var retryAndCancel: some View {
        VStack(spacing: 10) {
            choice("Riprova", primary: true) { Task { await login() } }
            cancelButton
        }
    }

    private var cancelButton: some View {
        Button("Annulla") {
            flow.cancel()
            onCancel()
        }
        .font(Theme.rounded(14, .semibold))
        .foregroundStyle(Theme.textTertiary)
        .padding(.top, 4)
    }

    private func message(symbol: String, title: String, text: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: symbol)
                .font(.system(size: 38, weight: .light))
                .foregroundStyle(settings.accentColor)
            Text(title)
                .font(Theme.rounded(22, .bold))
                .foregroundStyle(Theme.textPrimary)
                .multilineTextAlignment(.center)
            Text(text)
                .font(Theme.rounded(14, .medium))
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func errorText(_ text: String) -> some View {
        Text(text)
            .font(Theme.rounded(12, .medium))
            .foregroundStyle(Theme.negative)
            .multilineTextAlignment(.center)
    }

    @ViewBuilder
    private func choice(_ title: String, primary: Bool, detail: String? = nil, action: @escaping () -> Void) -> some View {
        VStack(spacing: 6) {
            if primary {
                Button(title, action: action).buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
            } else {
                Button(title, action: action).buttonStyle(SecondaryButtonStyle())
            }
            if let detail {
                Text(detail)
                    .font(Theme.rounded(11, .medium))
                    .foregroundStyle(Theme.textTertiary)
                    .multilineTextAlignment(.center)
            }
        }
    }

    // MARK: - Azioni

    private func login() async {
        importError = nil
        await flow.begin { url in
            try await webAuthentication.authenticate(using: url, callbackURLScheme: PeakLogin.callbackScheme)
        }
    }

    private func pickBackup(_ result: Result<[URL], Error>) {
        importError = nil
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            do {
                let data = try Data(contentsOf: url)
                Task { await flow.importBackup(data) }
            } catch {
                importError = error.localizedDescription
            }
        case .failure(let error):
            importError = error.localizedDescription
        }
    }
}

/// Il foglio che si apre dal banner della lista e dalle impostazioni.
struct ConnectSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var settings

    var body: some View {
        ConnectFlowView(onFinish: { dismiss() }, onCancel: { dismiss() })
            .screenBackground(settings.accentColor)
            .interactiveDismissDisabled()
    }
}
