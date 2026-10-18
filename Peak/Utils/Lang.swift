import Foundation

/// La lingua dell'interfaccia: italiano (i testi del codice) o inglese (`en.lproj/Localizable.strings`, generato da web/src/lib/en.json).
/// "" = quella del telefono.
///
/// Come funziona: iOS sceglie la lingua dell'app all'avvio, da `AppleLanguages`. Cambiando la scelta si scrive quella chiave e la
/// lingua nuova vale **alla prossima apertura** (nessun ridisegno a caldo: era lento e instabile). I `Text("…")` si traducono da soli;
/// per i testi passati come `String` (titoli, righe di elenco) si usa `.tr`.
enum Lang {
    /// Quella con cui l'app è partita: `.tr` e i `Text` devono andare d'accordo fino alla chiusura.
    static let launched: String = {
        let saved = UserDefaults.standard.string(forKey: "settings.language") ?? ""
        return code(for: saved)
    }()

    static func code(for setting: String) -> String {
        if setting == "it" || setting == "en" { return setting }
        return Locale.preferredLanguages.first?.hasPrefix("it") == true ? "it" : "en"
    }

    /// Scrive la lingua per la prossima apertura: vuota = quella del telefono.
    static func apply(_ setting: String) {
        if setting == "it" || setting == "en" { UserDefaults.standard.set([setting], forKey: "AppleLanguages") }
        else { UserDefaults.standard.removeObject(forKey: "AppleLanguages") }
    }

    static var isItalian: Bool { launched == "it" }

    /// La lingua è già quella scelta, o serve riaprire l'app?
    static func needsRestart(_ setting: String) -> Bool { code(for: setting) != launched }

    /// Le traduzioni: la chiave è il testo italiano. Quelle con segnaposto (`%@`, `%lld`) valgono anche per i testi già composti
    /// ("3 esercizi" ↔ "%@ esercizi"), così basta un elenco per i testi fissi e per quelli con numeri e nomi dentro.
    private static let table: (exact: [String: String], patterns: [(regex: NSRegularExpression, value: String)]) = {
        guard launched == "en",
              let path = Bundle.main.path(forResource: "Localizable", ofType: "strings", inDirectory: nil, forLocalization: "en"),
              let dict = NSDictionary(contentsOfFile: path) as? [String: String] else { return ([:], []) }
        var patterns: [(NSRegularExpression, String)] = []
        for (key, value) in dict where key.contains("%@") || key.contains("%lld") {
            // senza almeno un po' di testo fisso il modello combacerebbe con qualsiasi cosa
            let fixed = key.replacingOccurrences(of: "%@", with: "").replacingOccurrences(of: "%lld", with: "").replacingOccurrences(of: "%lf", with: "")
            guard fixed.filter(\.isLetter).count >= 3 else { continue }
            var source = NSRegularExpression.escapedPattern(for: key)
            for token in ["%@", "%lld", "%lf"] { source = source.replacingOccurrences(of: NSRegularExpression.escapedPattern(for: token), with: "(.+?)") }
            source = source.replacingOccurrences(of: "%%", with: "%")
            if let regex = try? NSRegularExpression(pattern: "^" + source + "$", options: [.dotMatchesLineSeparators]) { patterns.append((regex, value)) }
        }
        // i più lunghi (più specifici) per primi
        patterns.sort { $0.0.pattern.count > $1.0.pattern.count }
        return (dict, patterns)
    }()

    /// Le risposte già calcolate (anche «nessuna traduzione»): lo stesso testo si ridisegna spesso, e provare tutti i modelli costa.
    private static var memo: [String: String] = [:]
    private static let lock = NSLock()

    static func tr(_ key: String) -> String {
        guard launched == "en", !key.isEmpty else { return key }
        lock.lock()
        if let cached = memo[key] { lock.unlock(); return cached }
        lock.unlock()
        let value = translate(key)
        lock.lock()
        if memo.count > 4000 { memo.removeAll(keepingCapacity: true) }
        memo[key] = value
        lock.unlock()
        return value
    }

    private static func translate(_ key: String) -> String {
        if let hit = table.exact[key] { return hit }
        let range = NSRange(key.startIndex..., in: key)
        for (regex, value) in table.patterns {
            guard let m = regex.firstMatch(in: key, range: range) else { continue }
            var out = value
            for i in 1..<m.numberOfRanges {
                guard let r = Range(m.range(at: i), in: key) else { continue }
                // le parti dentro (un nome, un numero) si traducono a loro volta se sono testo noto
                let raw = String(key[r])
                let part = raw == key ? raw : translate(raw)
                if let at = out.range(of: "%@") ?? out.range(of: "%lld") ?? out.range(of: "%lf") { out.replaceSubrange(at, with: part) }
            }
            return out.replacingOccurrences(of: "%%", with: "%")
        }
        return key
    }
}

extension String {
    /// Il testo nella lingua scelta; se non c'è la traduzione resta com'è.
    var tr: String { Lang.tr(self) }
}
