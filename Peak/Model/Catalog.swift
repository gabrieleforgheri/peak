import Foundation

/// Una voce del catalogo degli esercizi (`common/catalog.json`, la stessa del server e del web).
/// L'`id` non cambia mai: è ciò che fa combaciare gli esercizi di persone diverse.
struct CatalogEntry: Codable, Identifiable, Hashable {
    let id: String
    let it: String
    let en: String
    let alias: [String]
    let group: String
    /// Incremento tipico del carico, in kg.
    let step: Double
    let glyph: String
    let notes: String
    /// I muscoli principali (id di `MuscleId`) e la vista forzata, "" = automatica. Assenti nelle copie vecchie del catalogo.
    let muscles: [String]
    let view: String

    var muscleIDs: [MuscleId] { muscles.compactMap { MuscleId(rawValue: $0) } }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        it = try c.decode(String.self, forKey: .it)
        en = try c.decode(String.self, forKey: .en)
        alias = try c.decode([String].self, forKey: .alias)
        group = try c.decode(String.self, forKey: .group)
        step = try c.decode(Double.self, forKey: .step)
        glyph = try c.decode(String.self, forKey: .glyph)
        notes = try c.decode(String.self, forKey: .notes)
        muscles = try c.decodeIfPresent([String].self, forKey: .muscles) ?? []
        view = try c.decodeIfPresent(String.self, forKey: .view) ?? ""
    }

    /// Il nome nella lingua dell'app, e l'altro (serve a riconoscere la voce).
    var name: String { Lang.isItalian ? it : en }
    var otherName: String { Lang.isItalian ? en : it }

    var muscleGroup: MuscleGroup { MuscleGroup(rawValue: group) ?? .petto }
    var machineGlyph: MachineGlyph { MachineGlyph(rawValue: glyph) ?? MachineGlyph.suggested(for: it, group: muscleGroup) }
}

/// Il catalogo: una copia sta nell'app (funziona anche offline e senza account), e se il server ne ha una più nuova
/// quella si salva e vince.
enum Catalog {

    /// Minuscole, senza accenti né punteggiatura, spazi singoli: "Leg Press 45°" → "leg press 45". Come `norm` in common/catalog.ts.
    static func norm(_ text: String) -> String {
        let folded = text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil).lowercased()
        var out = ""
        var pendingSpace = false
        for scalar in folded.unicodeScalars {
            if (scalar.value >= 97 && scalar.value <= 122) || (scalar.value >= 48 && scalar.value <= 57) {
                if pendingSpace, !out.isEmpty { out.append(" ") }
                pendingSpace = false
                out.unicodeScalars.append(scalar)
            } else {
                pendingSpace = true
            }
        }
        return out
    }

    // MARK: - Caricamento

    private static let etagKey = "catalog.etag"
    private static let buildKey = "catalog.build"

    private static var downloadedURL: URL? {
        try? FileManager.default
            .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("catalog.json")
    }

    private static var build: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "" }

    private static func decode(_ data: Data) -> [CatalogEntry]? {
        guard let list = try? JSONDecoder().decode([CatalogEntry].self, from: data), list.count >= 100 else { return nil }
        return list
    }

    /// Le voci, caricate una volta. Il file scaricato vale solo finché l'app è la stessa build: un'app nuova ha il suo catalogo.
    private(set) static var entries: [CatalogEntry] = load()
    private static var names: [String: [String]] = [:]

    private static func load() -> [CatalogEntry] {
        let defaults = UserDefaults.standard
        if defaults.string(forKey: buildKey) == build, let url = downloadedURL,
           let data = try? Data(contentsOf: url), let list = decode(data) {
            return list
        }
        guard let url = Bundle.main.url(forResource: "catalog", withExtension: "json"),
              let data = try? Data(contentsOf: url), let list = decode(data) else { return [] }
        return list
    }

    static func names(of entry: CatalogEntry) -> [String] {
        if let known = names[entry.id] { return known }
        let all = ([entry.it, entry.en] + entry.alias).map(norm)
        names[entry.id] = all
        return all
    }

    static func entry(id: String) -> CatalogEntry? { entries.first { $0.id == id } }

    // MARK: - Ricerca

    /// La voce che ha esattamente questo nome (italiano, inglese o sinonimo), se c'è.
    static func match(name: String) -> CatalogEntry? {
        let n = norm(name)
        guard !n.isEmpty else { return nil }
        return entries.first { norm($0.it) == n } ?? entries.first { names(of: $0).contains(n) }
    }

    /// Più parole in qualsiasi ordine: ognuna deve comparire in un nome o sinonimo. Prima chi inizia con la ricerca, poi chi
    /// ha una parola che inizia così, poi il resto; a parità vale l'ordine del catalogo.
    /// Con `group` si cerca solo lì, filtrando PRIMA del limite; ricerca vuota + gruppo = tutto il gruppo, senza limite.
    static func search(_ query: String, group: MuscleGroup? = nil, limit: Int = 40) -> [CatalogEntry] {
        let q = norm(query)
        let pool = group.map { g in entries.filter { $0.muscleGroup == g } } ?? entries
        guard !q.isEmpty else { return group == nil ? Array(pool.prefix(limit)) : pool }
        let words = q.split(separator: " ").map(String.init)
        var scored: [(entry: CatalogEntry, score: Int, index: Int)] = []
        for (index, entry) in pool.enumerated() {
            let hay = names(of: entry)
            guard words.allSatisfy({ w in hay.contains { $0.contains(w) } }) else { continue }
            let score = hay.contains { $0.hasPrefix(q) } ? 0
                : hay.contains { $0.split(separator: " ").contains { $0.hasPrefix(words[0]) } } ? 1 : 2
            scored.append((entry, score, index))
        }
        scored.sort { ($0.score, $0.index) < ($1.score, $1.index) }
        return scored.prefix(limit).map(\.entry)
    }

    // MARK: - Aggiornamento dal server

    /// Scarica `/catalogo.json` se è cambiato (ETag). Tace ogni errore: resta il catalogo che c'è.
    static func refresh() async {
        guard let url = downloadedURL else { return }
        var request = URLRequest(url: PeakAPI.base.appendingPathComponent("catalogo.json"))
        request.timeoutInterval = 20
        let defaults = UserDefaults.standard
        if defaults.string(forKey: buildKey) == build, let etag = defaults.string(forKey: etagKey) {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse, http.statusCode == 200,
              let list = decode(data), (try? data.write(to: url, options: .atomic)) != nil else { return }
        defaults.set(http.value(forHTTPHeaderField: "ETag"), forKey: etagKey)
        defaults.set(build, forKey: buildKey)
        await MainActor.run {
            entries = list
            names = [:]
        }
    }
}
