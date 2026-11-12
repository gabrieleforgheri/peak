import Foundation
import MetricKit

/// Raccoglie i report di MetricKit (metriche giornaliere, crash, hang) e li salva in
/// `Documents/Diagnostics/`, visibile nell'app File («Sul mio iPhone > Massimali»).
/// Niente rete: i file restano sul telefono. Si tengono solo gli ultimi 20.
final class Diagnostics: NSObject, MXMetricManagerSubscriber, Sendable {

    static let shared = Diagnostics()
    private static let maxFiles = 20
    private let queue = DispatchQueue(label: "Diagnostics", qos: .utility)

    /// Da chiamare una volta all'avvio.
    func start() {
        MXMetricManager.shared.add(self)
    }

    func didReceive(_ payloads: [MXMetricPayload]) {
        let blobs = payloads.map { $0.jsonRepresentation() }
        queue.async { blobs.forEach { self.write($0, kind: "metriche") } }
    }

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        let blobs = payloads.map { $0.jsonRepresentation() }
        queue.async { blobs.forEach { self.write($0, kind: "diagnostici") } }
    }

    private func write(_ data: Data, kind: String) {
        do {
            let fm = FileManager.default
            let docs = try fm.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            let dir = docs.appendingPathComponent("Diagnostics", isDirectory: true)
            try fm.createDirectory(at: dir, withIntermediateDirectories: true)
            // i due punti dell'ISO8601 non piacciono a tutti i file system: li tolgo
            let stamp = ISO8601DateFormatter().string(from: Date()).replacingOccurrences(of: ":", with: "-")
            // il suffisso evita che due payload nello stesso secondo si sovrascrivano
            let name = "\(stamp)-\(kind)-\(UUID().uuidString.prefix(4)).json"
            try data.write(to: dir.appendingPathComponent(name), options: .atomic)
            try prune(dir)
        } catch {
            Log.app.error("Diagnostics: scrittura fallita: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// I nomi iniziano con la data ISO8601: l'ordine alfabetico è quello cronologico.
    private func prune(_ dir: URL) throws {
        let files = try FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
        for old in files.dropLast(Self.maxFiles) {
            try? FileManager.default.removeItem(at: old)
        }
    }
}
