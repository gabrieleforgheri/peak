import SwiftUI
import CoreImage.CIFilterBuiltins

/// L'invito: un link monouso, mostrato come QR e condivisibile. Chi lo apre diventa amico subito.
struct InviteSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var settings

    @State private var link: URL?
    @State private var failed = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                Text("Fagli inquadrare il QR, oppure mandagli il link. Vale una volta sola e per sette giorni.")
                    .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)

                if let link, let image = Self.qr(link.absoluteString) {
                    Image(uiImage: image)
                        .interpolation(.none).resizable().scaledToFit()
                        .padding(14).background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .frame(maxWidth: 280)
                        .accessibilityLabel("Codice QR dell'invito")
                    ShareLink(item: link, message: Text("Aggiungimi su Peak:")) {
                        Label("Condividi", systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                    Button { UIPasteboard.general.url = link; Haptics.success() } label: { Label("Copia il link", systemImage: "doc.on.doc") }
                        .buttonStyle(SecondaryButtonStyle())
                } else if failed {
                    Text("Serve la connessione. Riprova.").font(Theme.rounded(14, .medium)).foregroundStyle(Theme.textTertiary)
                } else {
                    ProgressView().tint(settings.accentColor).padding(.top, 40)
                }
                Spacer()
            }
            .padding(20)
            .screenBackground(settings.accentColor)
            .navigationTitle("Invita un amico")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Chiudi") { dismiss() } } }
        }
        .task {
            guard let api = SyncService.shared.peakAPI else { failed = true; return }
            do { link = PeakAPI.inviteLink(try await api.createInvite()) } catch { failed = true }
        }
    }

    /// Il QR in nero su bianco, ingrandito senza sfocare.
    static func qr(_ text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)),
              let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
