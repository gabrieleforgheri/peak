import SwiftUI
import SwiftData

/// La scheda Amici: invito (link e QR), ricerca per username, richieste, la lega con il podio, la classifica per esercizio e le novità.
struct FriendsView: View {
    @Environment(AppSettings.self) private var settings
    @Query(sort: \Exercise.sortIndex) private var exercises: [Exercise]

    private let social = SocialStore.shared
    private let sync = SyncService.shared

    enum Metric: String, CaseIterable, Identifiable {
        case volume = "Volume 30 giorni", sessions = "Allenamenti 30 giorni", streak = "Settimane di fila"
        var id: String { rawValue }
    }

    @State private var metric: Metric = .volume
    @State private var showingInvite = false
    @State private var showingConnect = false
    @State private var query = ""
    @State private var found: [(FriendPerson, FriendState)] = []
    @State private var searched = ""
    @State private var pick = ""
    @State private var board: [BoardRow]?
    @State private var path: [FriendPerson] = []

    private var linkedMine: [Exercise] { exercises.filter { !$0.catalogID.isEmpty && !$0.isArchived && $0.bestRecord != nil } }
    private var current: String { pick.isEmpty ? (linkedMine.first?.catalogID ?? "") : pick }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if !social.isAvailable {
                        connectCard
                    } else {
                        inviteBlock
                        requests
                        if social.friendCount == 0 { emptyState } else { league; byExercise; news }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 28)
            }
            .screenBackground(settings.accentColor)
            .navigationTitle("Amici")
            .navigationDestination(for: FriendPerson.self) { FriendProfileView(person: $0) }
            .sheet(isPresented: $showingInvite) { InviteSheet() }
            .sheet(isPresented: $showingConnect) { ConnectSheet() }
        }
        .task { await social.refresh() }
        .task(id: current + String(social.friendCount)) {
            board = nil
            guard !current.isEmpty, social.friendCount > 0, let api = sync.peakAPI else { return }
            board = try? await api.board(catalog: current)
        }
    }

    // MARK: - Collegamento e inviti

    private var connectCard: some View {
        GlassCard {
            VStack(spacing: 12) {
                Image(systemName: "person.2.fill").font(.system(size: 40)).foregroundStyle(settings.accentColor)
                Text("Gli amici servono l'account yG").font(Theme.rounded(17, .bold))
                Text("Collega Peak a yG: potrai invitare gli amici, vedere i loro massimali e fare la classifica.")
                    .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
                Button { showingConnect = true } label: { Label("Collega a yG", systemImage: "person.crop.circle.badge.checkmark") }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var inviteBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button { showingInvite = true } label: { Label("Invita un amico", systemImage: "qrcode") }
                .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                .tourTarget("invite")
            Text("Il link vale una volta sola: chi lo apre diventa tuo amico subito.")
                .font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)

            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(Theme.textTertiary)
                TextField("Cerca un username", text: $query)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .onChange(of: query) { _, text in Task { await search(text) } }
            }
            .padding(12).glassSurface(radius: 14)

            if !searched.isEmpty, !query.isEmpty {
                if found.isEmpty {
                    Text("Nessun utente con questo username.").font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textTertiary)
                } else {
                    GlassCard(padding: 6) {
                        VStack(spacing: 0) {
                            ForEach(found, id: \.0.id) { person, state in
                                HStack {
                                    Text("@\(person.username)").font(Theme.rounded(15, .bold))
                                    Spacer()
                                    switch state {
                                    case .none: Button("Aggiungi") { Task { await ask(person) } }.font(Theme.rounded(13, .bold)).foregroundStyle(settings.accentColor)
                                    case .received: Button("Accetta") { Task { await ask(person) } }.font(Theme.rounded(13, .bold)).foregroundStyle(settings.accentColor)
                                    case .friends: Text("Amici").font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textTertiary)
                                    case .sent: Text("Richiesta inviata").font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textTertiary)
                                    }
                                }
                                .padding(10)
                            }
                        }
                    }
                }
            }
        }
    }

    private func search(_ text: String) async {
        let q = text.trimmingCharacters(in: .whitespaces)
        guard q.replacingOccurrences(of: "@", with: "").count >= 2 else { found = []; searched = ""; return }
        try? await Task.sleep(for: .milliseconds(300))
        guard q == query.trimmingCharacters(in: .whitespaces), let api = sync.peakAPI else { return }
        found = (try? await api.searchUsers(q)) ?? []
        searched = q
    }

    private func ask(_ person: FriendPerson) async {
        guard let api = sync.peakAPI, let state = try? await api.requestFriend(person.id) else { return }
        found = found.map { $0.0.id == person.id ? ($0.0, state) : $0 }
        Haptics.success()
        await social.reload()
    }

    @ViewBuilder
    private var requests: some View {
        if let received = social.friends?.received, !received.isEmpty {
            SectionHeader(title: "Richieste").padding(.top, 6)
            GlassCard(padding: 6) {
                VStack(spacing: 0) {
                    ForEach(received) { person in
                        HStack {
                            Text("@\(person.username)").font(Theme.rounded(15, .bold))
                            Spacer()
                            Button("Accetta") { Task { try? await sync.peakAPI?.acceptFriend(person.id); await social.reload() } }
                                .font(Theme.rounded(13, .bold)).foregroundStyle(settings.accentColor)
                            Button { Task { try? await sync.peakAPI?.removeFriend(person.id); await social.reload() } } label: { Image(systemName: "xmark") }
                                .foregroundStyle(Theme.textTertiary).padding(.leading, 8)
                        }
                        .padding(10)
                    }
                }
            }
        }
    }

    private var emptyState: some View {
        GlassCard {
            VStack(spacing: 10) {
                Image(systemName: "person.2.fill").font(.system(size: 38)).foregroundStyle(settings.accentColor)
                    .frame(width: 84, height: 84).background(Circle().fill(settings.accentColor.opacity(0.14)))
                Text("Allenarsi è più bello insieme").font(Theme.rounded(17, .bold))
                Text("Invita un amico con il link o il QR, oppure cerca il suo username. Vedrete i massimali e le schede l'uno dell'altro, e potrete fare la classifica.")
                    .font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: - La lega

    private func value(_ r: LeagueRow) -> Double {
        switch metric {
        case .volume: return r.volume30
        case .sessions: return Double(r.sessions30)
        case .streak: return Double(r.streak)
        }
    }

    private func text(_ r: LeagueRow) -> String {
        metric == .volume ? Fmt.volume(r.volume30, unit: settings.unit) : String(Int(value(r)))
    }

    @ViewBuilder
    private var league: some View {
        if let rows = social.league {
            let ranked = rows.sorted { (value($0), $0.isMe ? 1 : 0) > (value($1), $1.isMe ? 1 : 0) }
            let top = max(1, ranked.map(value).max() ?? 1)
            let mine = (ranked.firstIndex { $0.isMe } ?? -1) + 1

            SectionHeader(title: "La lega").padding(.top, 6)
            GlassCard {
                VStack(spacing: 14) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Metric.allCases) { m in
                                Button { metric = m } label: {
                                    Text(m.rawValue).font(Theme.rounded(13, .semibold))
                                        .foregroundStyle(metric == m ? Color.black.opacity(0.85) : Theme.textSecondary)
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(Capsule().fill(metric == m ? settings.accentColor : Theme.card))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    Podium(entries: ranked.prefix(3).map { PodiumEntry(id: $0.id, name: $0.isMe ? "Tu" : "@\($0.username)", initial: $0.username, value: text($0), isMe: $0.isMe) }) { id in
                        if let person = social.friends?.friends.first(where: { $0.id == id }) { path.append(person) }
                    }
                    if mine > 0 {
                        Text(mine == 1 ? "Sei in testa. Tienila stretta." : "Sei \(mine)° su \(ranked.count).")
                            .font(Theme.rounded(12, .medium)).foregroundStyle(Theme.textSecondary)
                    }
                }
            }
            GlassCard(padding: 6) {
                VStack(spacing: 0) {
                    ForEach(Array(ranked.enumerated()), id: \.element.id) { i, r in
                        Button {
                            if !r.isMe, let person = social.friends?.friends.first(where: { $0.id == r.id }) { path.append(person) }
                        } label: {
                            HStack(alignment: .top, spacing: 10) {
                                Text("\(i + 1)").font(Theme.rounded(14, .heavy)).foregroundStyle(r.isMe ? settings.accentColor : Theme.textTertiary).frame(width: 22)
                                VStack(alignment: .leading, spacing: 5) {
                                    HStack {
                                        Text(r.isMe ? "Tu" : "@\(r.username)").font(Theme.rounded(15, .bold))
                                        Spacer()
                                        Text(text(r)).font(Theme.rounded(15, .heavy))
                                    }
                                    ProgressBar(fraction: value(r) / top, tint: r.isMe ? settings.accentColor : Color.white.opacity(0.35))
                                    HStack(spacing: 3) {
                                        Image(systemName: "flame.fill").font(.system(size: 9))
                                        Text("\(r.streak) · \(r.last.map { Fmt.relativeDay($0) } ?? "Mai allenato")")
                                    }
                                    .font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary)
                                }
                            }
                            .padding(10)
                            .background(r.isMe ? settings.accentColor.opacity(0.08) : .clear, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Esercizio per esercizio

    @ViewBuilder
    private var byExercise: some View {
        if !linkedMine.isEmpty {
            SectionHeader(title: "Esercizio per esercizio").padding(.top, 6)
            GlassCard {
                VStack(alignment: .leading, spacing: 12) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(linkedMine) { e in
                                Button { pick = e.catalogID } label: {
                                    Text(verbatim: Catalog.entry(id: e.catalogID)?.name ?? e.name).font(Theme.rounded(13, .semibold))
                                        .foregroundStyle(current == e.catalogID ? Color.black.opacity(0.85) : Theme.textSecondary)
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(Capsule().fill(current == e.catalogID ? settings.accentColor : Theme.card))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    if let board {
                        if board.count < 2 {
                            Text("Nessun amico ha ancora questo esercizio.").font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textTertiary)
                        } else {
                            let top = max(1, board.map(\.oneRm).max() ?? 1)
                            ForEach(Array(board.enumerated()), id: \.element.id) { i, r in
                                VStack(alignment: .leading, spacing: 5) {
                                    HStack {
                                        Text("\(i + 1)").font(Theme.rounded(14, .heavy)).foregroundStyle(i == 0 ? Color(hex: 0xFFD45E) : Theme.textTertiary).frame(width: 22, alignment: .leading)
                                        Text(r.isMe ? "Tu" : "@\(r.username)").font(Theme.rounded(14, r.isMe ? .heavy : .semibold))
                                        Spacer()
                                        Text(Fmt.weight(r.oneRm, unit: settings.unit)).font(Theme.rounded(14, .heavy))
                                        Text(Fmt.setLine(weight: r.weight, reps: r.reps, unit: settings.unit)).font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
                                    }
                                    ProgressBar(fraction: r.oneRm / top, tint: r.isMe ? settings.accentColor : Color.white.opacity(0.35))
                                }
                            }
                            Text("1RM stimato dalle serie migliori.").font(Theme.rounded(10, .medium)).foregroundStyle(Theme.textTertiary)
                        }
                    } else {
                        ProgressView().tint(settings.accentColor).frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }

    // MARK: - Novità

    @ViewBuilder
    private var news: some View {
        SectionHeader(title: "Novità").padding(.top, 6)
        if let list = social.news, !list.isEmpty {
            GlassCard(padding: 6) {
                VStack(spacing: 0) {
                    ForEach(Array(list.prefix(6).enumerated()), id: \.offset) { i, e in
                        Button { if let p = social.friends?.friends.first(where: { $0.id == e.friendID }) { path.append(p) } } label: { NewsRow(event: e) }.buttonStyle(.plain)
                        if i < min(6, list.count) - 1 { Divider().overlay(Theme.stroke) }
                    }
                }
            }
        } else {
            Text("Nessun record dei tuoi amici negli ultimi giorni.").font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textTertiary).frame(maxWidth: .infinity)
        }
    }
}

/// Una barra orizzontale piena per la frazione data.
struct ProgressBar: View {
    let fraction: Double
    let tint: Color

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.cardStrong)
                Capsule().fill(tint).frame(width: max(6, geo.size.width * CGFloat(min(1, max(0, fraction)))))
            }
        }
        .frame(height: 10)
    }
}

// MARK: - Podio

struct PodiumEntry: Identifiable {
    let id: Int
    let name: String
    let initial: String
    let value: String
    let isMe: Bool
}

/// I primi tre sul podio: il primo al centro, il più alto.
struct Podium: View {
    @Environment(AppSettings.self) private var settings
    let entries: [PodiumEntry]
    var onPick: (Int) -> Void

    private var slots: [(entry: PodiumEntry, rank: Int)] {
        let order = [1, 0, 2]
        return order.compactMap { i in entries.indices.contains(i) ? (entries[i], i + 1) : nil }
    }

    private func colors(_ rank: Int) -> (hi: Color, lo: Color) {
        switch rank {
        case 1: return (Color(hex: 0xFFE69A), Color(hex: 0xC9921A))
        case 2: return (Color(hex: 0xF4F6FA), Color(hex: 0x8F98A8))
        default: return (Color(hex: 0xF1B27A), Color(hex: 0x94562A))
        }
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: 10) {
            ForEach(slots, id: \.entry.id) { slot in
                let c = colors(slot.rank)
                let height: CGFloat = slot.rank == 1 ? 112 : slot.rank == 2 ? 84 : 64
                Button { if !slot.entry.isMe { onPick(slot.entry.id) } } label: {
                    VStack(spacing: 6) {
                        Text(String(slot.entry.initial.prefix(1)).uppercased())
                            .font(Theme.rounded(20, .heavy)).foregroundStyle(Color(white: 0.1))
                            .frame(width: 48, height: 48)
                            .background(Circle().fill(RadialGradient(colors: [c.hi, c.lo], center: .init(x: 0.3, y: 0.25), startRadius: 0, endRadius: 34)))
                            .shadow(color: c.hi.opacity(0.4), radius: 10)
                        HStack(spacing: 3) {
                            if slot.entry.isMe { Image(systemName: "star.fill").font(.system(size: 10)) }
                            Text(slot.entry.name).lineLimit(1)
                        }
                        .font(Theme.rounded(14, .bold)).foregroundStyle(slot.entry.isMe ? settings.accentColor : Theme.textPrimary)
                        Text(slot.entry.value).font(Theme.rounded(13, .medium)).foregroundStyle(Theme.textSecondary).monospacedDigit()
                        Text("\(slot.rank)")
                            .font(Theme.rounded(24, .black)).foregroundStyle(Color.black.opacity(0.55))
                            .frame(maxWidth: .infinity, minHeight: height, alignment: .top).padding(.top, 8)
                            .background(UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 4, bottomTrailingRadius: 4, topTrailingRadius: 14).fill(LinearGradient(colors: [c.hi, c.lo], startPoint: .top, endPoint: .bottom)))
                    }
                    .frame(maxWidth: 120)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// Una riga di novità: chi, su cosa, quanto. Anche nella scheda Amici.
struct NewsRow: View {
    @Environment(AppSettings.self) private var settings
    let event: NewsEvent

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text("@\(event.username) · \(Catalog.entry(id: event.catalog)?.name ?? event.name)").font(Theme.rounded(14, .bold)).lineLimit(1)
                Text(Fmt.relativeDay(event.date)).font(Theme.rounded(11, .medium)).foregroundStyle(Theme.textTertiary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                Text(Fmt.setLine(weight: event.weight, reps: event.reps, unit: settings.unit)).font(Theme.rounded(14, .bold))
                if event.passedYou { Text("Ti ha superato").font(Theme.rounded(11, .semibold)).foregroundStyle(Theme.negative) }
            }
        }
        .padding(.horizontal, 8).padding(.vertical, 8)
    }
}
