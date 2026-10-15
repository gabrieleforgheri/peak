import Foundation

/// Gli amici: tutto passa dal server (ogni percorso controlla l'amicizia e toglie i nascosti). Le risposte sono piccole
/// e si leggono a mano, come le altre; qui i tipi e le chiamate.

struct FriendPerson: Identifiable, Hashable {
    let id: Int
    let username: String
    let name: String
}

enum FriendState: String {
    case none = "nessuno", friends = "amici", sent = "inviata", received = "ricevuta"
}

struct FriendLists {
    var friends: [FriendPerson] = []
    var received: [FriendPerson] = []
    var sent: [FriendPerson] = []
}

struct LeagueRow: Identifiable {
    let id: Int
    let username: String
    let isMe: Bool
    let volume30: Double
    let sessions30: Int
    let streak: Int
    let last: Date?
}

struct SharedMax: Identifiable {
    var id: String { catalog + name }
    let catalog: String
    let name: String
    let oneRm: Double
    let weight: Double
    let reps: Int
    let date: Date
}

struct SharedRow {
    let catalog: String
    let name: String
    let sets: Int
    let repsMin: Int
    let repsMax: Int
}

struct SharedTemplate: Identifiable {
    let id: String
    let name: String
    let rows: [SharedRow]
}

struct FriendProfileData {
    let person: FriendPerson
    let maxes: [SharedMax]
    let templates: [SharedTemplate]
}

struct BoardRow: Identifiable {
    let id: Int
    let username: String
    let isMe: Bool
    let oneRm: Double
    let weight: Double
    let reps: Int
}

struct NewsEvent: Identifiable {
    var id: String { "\(friendID)-\(catalog)" }
    let friendID: Int
    let username: String
    let catalog: String
    let name: String
    let weight: Double
    let reps: Int
    let date: Date
    let passedYou: Bool
}

extension PeakAPI {

    private func ms(_ value: Any?) -> Date? { (value as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1000) } }
    private func person(_ o: [String: Any]) -> FriendPerson? {
        guard let id = (o["id"] as? NSNumber)?.intValue, let username = o["username"] as? String else { return nil }
        return FriendPerson(id: id, username: username, name: o["name"] as? String ?? "")
    }
    private func dict(_ data: Data) throws -> [String: Any] {
        guard let o = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw PeakAPIError.invalidAnswer }
        return o
    }
    private func jsonBody(_ o: [String: Any]) -> Data { (try? JSONSerialization.data(withJSONObject: o)) ?? Data() }

    func friends() async throws -> FriendLists {
        let o = try dict(await send("api/amici"))
        func list(_ key: String) -> [FriendPerson] { (o[key] as? [[String: Any]] ?? []).compactMap(person) }
        return FriendLists(friends: list("amici"), received: list("ricevute"), sent: list("inviate"))
    }

    func searchUsers(_ query: String) async throws -> [(FriendPerson, FriendState)] {
        let o = try dict(await send("api/amici/cerca", query: [URLQueryItem(name: "q", value: query)]))
        return (o["utenti"] as? [[String: Any]] ?? []).compactMap { u in
            person(u).map { ($0, FriendState(rawValue: u["stato"] as? String ?? "") ?? .none) }
        }
    }

    func requestFriend(_ id: Int) async throws -> FriendState {
        let o = try dict(await send("api/amici", method: "POST", body: jsonBody(["id": id])))
        return FriendState(rawValue: o["stato"] as? String ?? "") ?? .none
    }

    func acceptFriend(_ id: Int) async throws { _ = try await send("api/amici/\(id)/accetta", method: "POST", body: Data()) }
    func removeFriend(_ id: Int) async throws { _ = try await send("api/amici/\(id)", method: "DELETE") }

    /// Un invito monouso: il codice in chiaro sta solo in questa risposta.
    func createInvite() async throws -> String {
        let o = try dict(await send("api/inviti", method: "POST", body: Data()))
        guard let code = o["codice"] as? String else { throw PeakAPIError.invalidAnswer }
        return code
    }

    static func inviteLink(_ code: String) -> URL { base.appendingPathComponent("invito/\(code)") }

    func league() async throws -> [LeagueRow] {
        let o = try dict(await send("api/lega"))
        return (o["righe"] as? [[String: Any]] ?? []).compactMap { r in
            guard let id = (r["id"] as? NSNumber)?.intValue, let username = r["username"] as? String else { return nil }
            return LeagueRow(
                id: id, username: username, isMe: r["tu"] as? Bool ?? false,
                volume30: (r["volume30"] as? NSNumber)?.doubleValue ?? 0, sessions30: (r["sessions30"] as? NSNumber)?.intValue ?? 0,
                streak: (r["streak"] as? NSNumber)?.intValue ?? 0, last: ms(r["last"])
            )
        }
    }

    func board(catalog: String) async throws -> [BoardRow] {
        let o = try dict(await send("api/classifica/\(catalog)"))
        return (o["righe"] as? [[String: Any]] ?? []).compactMap { r in
            guard let id = (r["id"] as? NSNumber)?.intValue, let username = r["username"] as? String else { return nil }
            return BoardRow(
                id: id, username: username, isMe: r["tu"] as? Bool ?? false, oneRm: (r["oneRm"] as? NSNumber)?.doubleValue ?? 0,
                weight: (r["weight"] as? NSNumber)?.doubleValue ?? 0, reps: (r["reps"] as? NSNumber)?.intValue ?? 0
            )
        }
    }

    func news() async throws -> [NewsEvent] {
        let o = try dict(await send("api/novita"))
        return (o["eventi"] as? [[String: Any]] ?? []).compactMap { e in
            guard let id = (e["id"] as? NSNumber)?.intValue, let username = e["username"] as? String, let date = ms(e["date"]) else { return nil }
            return NewsEvent(
                friendID: id, username: username, catalog: e["catalog"] as? String ?? "", name: e["name"] as? String ?? "",
                weight: (e["weight"] as? NSNumber)?.doubleValue ?? 0, reps: (e["reps"] as? NSNumber)?.intValue ?? 0, date: date,
                passedYou: e["sorpasso"] as? Bool ?? false
            )
        }
    }

    func profile(_ id: Int) async throws -> FriendProfileData {
        let o = try dict(await send("api/amici/\(id)"))
        guard let p = person(o) else { throw PeakAPIError.invalidAnswer }
        let maxes = (o["maxes"] as? [[String: Any]] ?? []).compactMap { m -> SharedMax? in
            guard let date = ms(m["date"]) else { return nil }
            return SharedMax(
                catalog: m["catalog"] as? String ?? "", name: m["name"] as? String ?? "", oneRm: (m["oneRm"] as? NSNumber)?.doubleValue ?? 0,
                weight: (m["weight"] as? NSNumber)?.doubleValue ?? 0, reps: (m["reps"] as? NSNumber)?.intValue ?? 0, date: date
            )
        }
        let templates = (o["templates"] as? [[String: Any]] ?? []).map { t in
            SharedTemplate(
                id: t["uuid"] as? String ?? UUID().uuidString, name: t["name"] as? String ?? "",
                rows: (t["rows"] as? [[String: Any]] ?? []).map { r in
                    SharedRow(
                        catalog: r["catalog"] as? String ?? "", name: r["name"] as? String ?? "", sets: (r["sets"] as? NSNumber)?.intValue ?? 0,
                        repsMin: (r["repsMin"] as? NSNumber)?.intValue ?? 0, repsMax: (r["repsMax"] as? NSNumber)?.intValue ?? 0
                    )
                }
            )
        }
        return FriendProfileData(person: p, maxes: maxes, templates: templates)
    }
}
