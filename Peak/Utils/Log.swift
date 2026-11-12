import Foundation
import os

/// Log di sistema (visibili in Console.app e nei sysdiagnose). Nessun invio in rete.
/// I messaggi con dati dell'utente (nomi di documento, di esercizi) vanno interpolati con
/// `privacy: .private`: fuori dal debugger restano oscurati.
enum Log {
    private static let subsystem = Bundle.main.bundleIdentifier ?? "com.gabrieleforghieri.massimali"

    static let sync = Logger(subsystem: subsystem, category: "sync")
    static let watch = Logger(subsystem: subsystem, category: "watch")
    static let store = Logger(subsystem: subsystem, category: "store")
    static let app = Logger(subsystem: subsystem, category: "app")
}
