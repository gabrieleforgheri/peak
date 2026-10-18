import Foundation

/// La lingua dell'interfaccia: italiano (i testi del codice) o inglese (`en.lproj/Localizable.strings`, generato da web/src/lib/en.json).
/// "" = quella del telefono.
///
/// Come funziona: iOS sceglie la lingua dell'app all'avvio, da `AppleLanguages`. Cambiando la scelta si scrive quella chiave e la
/// lingua nuova vale **alla prossima apertura** (nessun ridisegno a caldo: era lento e instabile). I `Text("…")` si traducono da soli;
/// per i testi passati come `String` (titoli, righe di elenco) si usa `.tr`.
enum Lang {
    /// Quella con cui l'app è partita: `.tr` e i `Text` devono andare d'accordo fino alla chiusura.
    private static let launched: String = {
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

    /// La lingua è già quella scelta, o serve riaprire l'app?
    static func needsRestart(_ setting: String) -> Bool { code(for: setting) != launched }

    private static var bundle: Bundle = {
        launched == "en" ? (Bundle.main.path(forResource: "en", ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main) : .main
    }()

    static func tr(_ key: String) -> String {
        guard launched == "en" else { return key }
        return bundle.localizedString(forKey: key, value: key, table: nil)
    }
}

extension String {
    /// Il testo nella lingua scelta; se non c'è la traduzione resta com'è.
    var tr: String { Lang.tr(self) }
}
