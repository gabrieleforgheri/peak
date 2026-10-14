import Foundation

/// Date nel formato di Peak: ISO 8601 UTC a secondi interi (`2026-10-14T12:00:00Z`), le stesse
/// dei backup e dei documenti sincronizzati. Si tronca, non si arrotonda: lo stesso istante dà
/// lo stesso testo su iPhone, sul web e sul server.
enum PeakDates {

    private static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private static let fractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    static func string(_ date: Date) -> String {
        plain.string(from: Date(timeIntervalSince1970: date.timeIntervalSince1970.rounded(.down)))
    }

    /// Con o senza millisecondi; il risultato è sempre a secondi interi.
    static func date(_ text: String) -> Date? {
        guard let parsed = plain.date(from: text) ?? fractional.date(from: text) else { return nil }
        return Date(timeIntervalSince1970: parsed.timeIntervalSince1970.rounded(.down))
    }
}
