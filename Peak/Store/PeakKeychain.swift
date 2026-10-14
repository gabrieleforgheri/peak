import Foundation
import Security

/// Il collegamento a Peak: il token di questo iPhone, chi è l'utente (`sub` di authentik) e il
/// nome da mostrare. Il token sta nel portachiavi di sistema, mai in `UserDefaults` né nei log;
/// si revoca da yrb4g.com/account/peak.
struct PeakCredentials: Codable, Equatable {
    var token: String
    var sub: String
    var name: String
}

enum PeakKeychain {

    private static let service = "com.gabrieleforghieri.peak.credentials"
    private static let account = "primary"

    private static var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }

    static func save(_ credentials: PeakCredentials) {
        guard let data = try? JSONEncoder().encode(credentials) else { return }
        SecItemDelete(query as CFDictionary)
        var insert = query
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(insert as CFDictionary, nil)
    }

    static func load() -> PeakCredentials? {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(request as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(PeakCredentials.self, from: data)
    }

    static func clear() {
        SecItemDelete(query as CFDictionary)
    }
}
