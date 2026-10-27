import Foundation
import Observation

/// Gli amici letti dal server, tenuti in memoria per le schermate (Allenamento, Amici, dettaglio di un macchinario). Niente di
/// tutto questo si salva sull'iPhone: senza account yG o senza rete restano vuoti e le schermate lo dicono.
@MainActor
@Observable
final class SocialStore {

    static let shared = SocialStore()

    private(set) var friends: FriendLists?
    private(set) var league: [LeagueRow]?
    private(set) var news: [NewsEvent]?
    /// L'ultima richiesta non è andata a buon fine (niente rete, o il server non risponde).
    private(set) var failed = false

    /// Serve un iPhone collegato a yG.
    var isAvailable: Bool { SyncService.shared.state == .connected }
    var friendCount: Int { friends?.friends.count ?? 0 }

    func refresh() async {
        guard isAvailable, let api = SyncService.shared.peakAPI else { return }
        do {
            async let f = api.friends()
            async let l = api.league()
            async let n = api.news()
            (friends, league, news) = try await (f, l, n)
            failed = false
        } catch {
            failed = true
        }
    }

    /// Dopo una richiesta, un'accettazione o una rimozione.
    func reload() async { await refresh() }
}
