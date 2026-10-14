import CryptoKit
import Foundation
import UIKit

/// Accesso con l'account yrb4g.com, per il server di Peak.
///
/// Il server apre il login di authentik (gruppo `svc-peak`), chiede conferma ("Collegare questo
/// iPhone?") e rimanda a `peak://login?code=`; il codice, con la prova PKCE, diventa un token per
/// questo dispositivo, revocabile da yrb4g.com/account/peak. È lo stesso schema di yG Music.
enum PeakLogin {

    static let callbackScheme = "peak"

    struct Start {
        let url: URL
        let verifier: String
    }

    static func start(deviceName: String = UIDevice.current.name) -> Start {
        let verifier = Data((0..<32).map { _ in UInt8.random(in: .min ... .max) }).base64URL
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64URL
        var components = URLComponents(url: PeakAPI.base.appendingPathComponent("auth/login"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "app", value: challenge),
            URLQueryItem(name: "nome", value: deviceName)
        ]
        return Start(url: components.url!, verifier: verifier)
    }

    /// Il codice dal ritorno `peak://login?code=…`.
    static func code(from callback: URL) -> String? {
        guard callback.scheme == callbackScheme, callback.host == "login" else { return nil }
        return URLComponents(url: callback, resolvingAgainstBaseURL: false)?
            .queryItems?.first { $0.name == "code" }?.value
    }

    /// Scambia il codice con il token del dispositivo.
    static func credentials(code: String, verifier: String) async throws -> PeakCredentials {
        var request = URLRequest(url: PeakAPI.base.appendingPathComponent("auth/app/token"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(["code": code, "verifier": verifier])
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let answer = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let token = answer["token"] as? String, let sub = answer["sub"] as? String else {
            throw PeakAPIError.invalidBackup("Il collegamento è scaduto: riprova.")
        }
        return PeakCredentials(token: token, sub: sub, name: answer["nome"] as? String ?? "")
    }
}

extension Data {
    var base64URL: String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
