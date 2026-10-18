import Foundation

/// La lingua dell'interfaccia: italiano (i testi del codice) o inglese (`en.lproj/Localizable.strings`, generato da web/src/lib/en.json).
/// "" = quella del telefono. I `Text("…")` si traducono da soli con la lingua dell'ambiente; per i testi passati come `String`
/// (titoli, righe di elenco) si usa `.tr`.
enum Lang {
    /// "it" o "en", dalle impostazioni; senza scelta, dal telefono.
    static var code: String {
        let saved = UserDefaults.standard.string(forKey: "settings.language") ?? ""
        if saved == "it" || saved == "en" { return saved }
        return Locale.preferredLanguages.first?.hasPrefix("it") == true ? "it" : "en"
    }

    static func code(for setting: String) -> String {
        setting == "it" || setting == "en" ? setting : (Locale.preferredLanguages.first?.hasPrefix("it") == true ? "it" : "en")
    }

    private static var bundles: [String: Bundle] = [:]

    static func tr(_ key: String) -> String {
        let c = code
        guard c == "en" else { return key }
        let bundle = bundles[c] ?? Bundle.main.path(forResource: c, ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
        bundles[c] = bundle
        return bundle.localizedString(forKey: key, value: key, table: nil)
    }
}

extension String {
    /// Il testo nella lingua scelta; se non c'è la traduzione resta com'è.
    var tr: String { Lang.tr(self) }
}
