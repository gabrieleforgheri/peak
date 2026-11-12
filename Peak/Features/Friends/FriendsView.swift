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
                VStack(alignment: .leading, spacing: Theme.Space.l) {
                    if !social.isAvailable {
                        connectCard.appear(0)
                    } else {
                        inviteBlock.appear(0)
                        requests.appear(1)
                        if social.friendCount == 0 { emptyState.appear(2) } else { league.appear(2); byExercise.appear(3); news.appear(4) }
                    }
                }
                .padding(.horizontal, Theme.Space.l)
                .padding(.bottom, Theme.Space.xl)
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
            VStack(spacing: Theme.Space.m) {
                Image(systemName: "person.2.fill").font(Theme.display(40)).foregroundStyle(settings.accentColor)
                Text("Gli amici servono l'account yG").font(Theme.Text.headline)
                Text("Collega Peak a yG: potrai invitare gli amici, vedere i loro massimali e fare la classifica.")
                    .font(Theme.Text.footnote.weight(.medium)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
                Button { showingConnect = true } label: { Label("Collega a yG", systemImage: "person.crop.circle.badge.checkmark") }
                    .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var inviteBlock: some View {
        VStack(alignment: .leading, spacing: Theme.Space.s) {
            Button { showingInvite = true } label: { Label("Invita un amico", systemImage: "qrcode") }
                .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                .tourTarget("invite")
            Text("Il link vale una volta sola: chi lo apre diventa tuo amico subito.")
                .mutedCaption()

            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(Theme.textTertiary)
                TextField("Cerca un username", text: $query)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .onChange(of: query) { _, text in Task { await search(text) } }
            }
            .padding(Theme.Space.m).glassSurface(radius: Theme.Radius.s)

            if !searched.isEmpty, !query.isEmpty {
                if found.isEmpty {
                    Text("Nessun utente con questo username.").mutedCaption(Theme.Text.footnote)
                } else {
                    GlassCard(padding: Theme.Space.xs) {
                        VStack(spacing: 0) {
                            ForEach(found, id: \.0.id) { person, state in
                                HStack {
                                    Text("@\(person.username)").font(Theme.Text.subhead.weight(.bold))
                                    Spacer()
                                    switch state {
                                    case .none: Button("Aggiungi") { Task { await ask(person) } }.font(Theme.Text.footnote.weight(.bold)).foregroundStyle(settings.accentColor)
                                    case .received: Button("Accetta") { Task { await ask(person) } }.font(Theme.Text.footnote.weight(.bold)).foregroundStyle(settings.accentColor)
                                    case .friends: Text("Amici").mutedCaption(Theme.Text.caption)
                                    case .sent: Text("Richiesta inviata").mutedCaption(Theme.Text.caption)
                                    }
                                }
                                .padding(Theme.Space.m)
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
            SectionHeader(title: "Richieste").padding(.top, Theme.Space.s)
            GlassCard(padding: Theme.Space.xs) {
                VStack(spacing: 0) {
                    ForEach(received) { person in
                        HStack {
                            Text("@\(person.username)").font(Theme.Text.subhead.weight(.bold))
                            Spacer()
                            Button("Accetta") { Task { try? await sync.peakAPI?.acceptFriend(person.id); await social.reload() } }
                                .font(Theme.Text.footnote.weight(.bold)).foregroundStyle(settings.accentColor)
                            Button { Task { try? await sync.peakAPI?.removeFriend(person.id); await social.reload() } } label: { Image(systemName: "xmark") }
                                .foregroundStyle(Theme.textTertiary).padding(.leading, Theme.Space.s)
                        }
                        .padding(Theme.Space.m)
                    }
                }
            }
        }
    }

    private var emptyState: some View {
        GlassCard {
            VStack(spacing: Theme.Space.m) {
                Image(systemName: "person.2.fill").font(Theme.display(38)).foregroundStyle(settings.accentColor)
                    .frame(width: 84, height: 84).background(Circle().fill(settings.accentColor.opacity(0.14)))
                Text("Allenarsi è più bello insieme").font(Theme.Text.headline)
                Text("Invita un amico con il link o il QR, oppure cerca il suo username. Vedrete i massimali e le schede l'uno dell'altro, e potrete fare la classifica.")
                    .font(Theme.Text.footnote.weight(.medium)).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
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

            SectionHeader(title: "La lega").padding(.top, Theme.Space.s)
            GlassCard {
                VStack(spacing: Theme.Space.l) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: Theme.Space.s) {
                            ForEach(Metric.allCases) { m in
                                SelectablePill(isOn: metric == m, tint: settings.accentColor, action: { metric = m }) { Text(m.rawValue) }
                            }
                        }
                    }
                    Podium(entries: ranked.prefix(3).map { PodiumEntry(id: $0.id, name: $0.isMe ? "Tu" : "@\($0.username)", initial: $0.username, value: text($0), amount: value($0), isMe: $0.isMe) }) { id in
                        if let person = social.friends?.friends.first(where: { $0.id == id }) { path.append(person) }
                    }
                    if mine > 0 {
                        Text(mine == 1 ? "Sei in testa. Tienila stretta." : "Sei \(mine)° su \(ranked.count).")
                            .font(Theme.Text.caption.weight(.medium)).foregroundStyle(Theme.textSecondary)
                    }
                }
            }
            GlassCard(padding: Theme.Space.xs) {
                VStack(spacing: 0) {
                    ForEach(Array(ranked.enumerated()), id: \.element.id) { i, r in
                        Button {
                            if !r.isMe, let person = social.friends?.friends.first(where: { $0.id == r.id }) { path.append(person) }
                        } label: {
                            HStack(alignment: .top, spacing: Theme.Space.m) {
                                RankNumber(i + 1, tint: r.isMe ? settings.accentColor : nil)
                                VStack(alignment: .leading, spacing: Theme.Space.xs) {
                                    HStack {
                                        Text(r.isMe ? "Tu" : "@\(r.username)").font(Theme.Text.subhead.weight(.bold))
                                        Spacer()
                                        Text(text(r)).font(Theme.Text.subhead.weight(.heavy))
                                    }
                                    ProgressBar(fraction: Podium.barFraction(value(r), top: top), tint: r.isMe ? settings.accentColor : Color.white.opacity(0.35))
                                    HStack(spacing: Theme.Space.xs) {
                                        Image(systemName: "flame.fill").font(Theme.Text.micro)
                                        Text("\(r.streak) · \(r.last.map { Fmt.relativeDay($0) } ?? "Mai allenato")")
                                    }
                                    .mutedCaption()
                                }
                            }
                            .padding(Theme.Space.m)
                            .background(r.isMe ? settings.accentColor.opacity(0.08) : .clear, in: RoundedRectangle(cornerRadius: Theme.Radius.s, style: .continuous))
                        }
                        .buttonStyle(PressStyle())
                    }
                }
            }
        }
    }

    // MARK: - Esercizio per esercizio

    @ViewBuilder
    private var byExercise: some View {
        if !linkedMine.isEmpty {
            SectionHeader(title: "Esercizio per esercizio").padding(.top, Theme.Space.s)
            GlassCard {
                VStack(alignment: .leading, spacing: Theme.Space.m) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: Theme.Space.s) {
                            ForEach(linkedMine) { e in
                                SelectablePill(isOn: current == e.catalogID, tint: settings.accentColor, action: { pick = e.catalogID }) {
                                    Text(verbatim: Catalog.entry(id: e.catalogID)?.name ?? e.name)
                                }
                            }
                        }
                    }
                    if let board {
                        if board.count < 2 {
                            Text("Nessun amico ha ancora questo esercizio.").mutedCaption(Theme.Text.footnote)
                        } else {
                            let top = max(1, board.map(\.oneRm).max() ?? 1)
                            ForEach(Array(board.enumerated()), id: \.element.id) { i, r in
                                VStack(alignment: .leading, spacing: Theme.Space.xs) {
                                    HStack {
                                        RankNumber(i + 1, tint: i == 0 ? Theme.gold : nil, alignment: .leading)
                                        Text(r.isMe ? "Tu" : "@\(r.username)").font(Theme.Text.footnote.weight(r.isMe ? .heavy : .semibold))
                                        Spacer()
                                        Text(Fmt.weight(r.oneRm, unit: settings.unit)).font(Theme.Text.footnote.weight(.heavy))
                                        Text(Fmt.setLine(weight: r.weight, reps: r.reps, unit: settings.unit)).mutedCaption()
                                    }
                                    ProgressBar(fraction: Podium.barFraction(r.oneRm, top: top), tint: r.isMe ? settings.accentColor : Color.white.opacity(0.35))
                                }
                            }
                            Text("1RM stimato dalle serie migliori.").mutedCaption()
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
        SectionHeader(title: "Novità").padding(.top, Theme.Space.s)
        if let list = social.news, !list.isEmpty {
            GlassCard(padding: Theme.Space.xs) {
                VStack(spacing: 0) {
                    ForEach(Array(list.prefix(6).enumerated()), id: \.offset) { i, e in
                        Button { if let p = social.friends?.friends.first(where: { $0.id == e.friendID }) { path.append(p) } } label: { NewsRow(event: e) }.buttonStyle(PressStyle())
                        if i < min(6, list.count) - 1 { Divider().overlay(Theme.stroke) }
                    }
                }
            }
        } else {
            Text("Nessun record dei tuoi amici negli ultimi giorni.").mutedCaption(Theme.Text.footnote).frame(maxWidth: .infinity)
        }
    }
}

/// I colori dei gradini del podio (chiaro in alto, scuro in basso): sfumature delle medaglie che non hanno un token in `Theme`.
private enum PodiumPalette {
    static let gold = (hi: Color(hex: 0xFFE69A), lo: Color(hex: 0xC9921A))
    static let silver = (hi: Color(hex: 0xF4F6FA), lo: Color(hex: 0x8F98A8))
    static let bronze = (hi: Color(hex: 0xF1B27A), lo: Color(hex: 0x94562A))
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
                    .animation(Motion.out(Motion.enter), value: fraction) // cambiando metrica la barra scorre
                    .drawIn()
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
    /// Il valore numerico, per l'altezza della barra.
    let amount: Double
    let isMe: Bool
}

/// I primi tre sul podio: il primo al centro, il più alto.
struct Podium: View {
    @Environment(AppSettings.self) private var settings
    let entries: [PodiumEntry]
    var onPick: (Int) -> Void

    /// Quanto è lunga la barra rispetto al primo: 0...1, mai una divisione per zero.
    static func barFraction(_ v: Double, top: Double) -> Double { top > 0 ? min(1, max(0, v / top)) : 0 }

    private var slots: [(entry: PodiumEntry, rank: Int)] {
        let order = [1, 0, 2]
        return order.compactMap { i in entries.indices.contains(i) ? (entries[i], i + 1) : nil }
    }

    private func colors(_ rank: Int) -> (hi: Color, lo: Color) {
        switch rank {
        case 1: return PodiumPalette.gold
        case 2: return PodiumPalette.silver
        default: return PodiumPalette.bronze
        }
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: Theme.Space.s) {
            ForEach(slots, id: \.entry.id) { slot in
                let c = colors(slot.rank)
                // proporzionale al primo, con un minimo perché la barra contenga ancora il numero
                let height = max(28, 112 * CGFloat(Self.barFraction(slot.entry.amount, top: entries.first?.amount ?? 0)))
                Button { if !slot.entry.isMe { onPick(slot.entry.id) } } label: {
                    VStack(spacing: Theme.Space.xs) {
                        Text(String(slot.entry.initial.prefix(1)).uppercased())
                            .font(Theme.Text.title.weight(.heavy)).foregroundStyle(Color(white: 0.1))
                            .frame(width: 48, height: 48)
                            .background(Circle().fill(RadialGradient(colors: [c.hi, c.lo], center: .init(x: 0.3, y: 0.25), startRadius: 0, endRadius: 34)))
                            .shadow(color: c.hi.opacity(0.4), radius: 10)
                        HStack(spacing: Theme.Space.xs) {
                            if slot.entry.isMe { Image(systemName: "star.fill").font(Theme.Text.micro) }
                            Text(slot.entry.name).lineLimit(1)
                        }
                        .font(Theme.Text.footnote.weight(.bold)).foregroundStyle(slot.entry.isMe ? settings.accentColor : Theme.textPrimary)
                        Text(slot.entry.value).font(Theme.Text.footnote.weight(.medium)).foregroundStyle(Theme.textSecondary).monospacedDigit()
                        Text("\(slot.rank)")
                            .font(Theme.Text.title.weight(.black)).foregroundStyle(Color.black.opacity(0.55))
                            .frame(maxWidth: .infinity, minHeight: height, alignment: .top).padding(.top, Theme.Space.s)
                            .background(UnevenRoundedRectangle(topLeadingRadius: Theme.Radius.s, bottomLeadingRadius: 4, bottomTrailingRadius: 4, topTrailingRadius: Theme.Radius.s).fill(LinearGradient(colors: [c.hi, c.lo], startPoint: .top, endPoint: .bottom)))
                            .growUp(slot.rank) // il podio sale dal basso, un gradino dopo l'altro
                    }
                    .frame(maxWidth: 120)
                }
                .buttonStyle(PressStyle())
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
                Text("@\(event.username) · \(Catalog.entry(id: event.catalog)?.name ?? event.name)").font(Theme.Text.footnote.weight(.bold)).lineLimit(1)
                Text(Fmt.relativeDay(event.date)).mutedCaption()
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                Text(Fmt.setLine(weight: event.weight, reps: event.reps, unit: settings.unit)).font(Theme.Text.footnote.weight(.bold))
                if event.passedYou { Text("Ti ha superato").font(Theme.Text.micro).foregroundStyle(Theme.negative) }
            }
        }
        .padding(.horizontal, Theme.Space.s).padding(.vertical, Theme.Space.s)
    }
}
