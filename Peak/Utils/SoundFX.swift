import AVFoundation

/// Effetti sonori (file di `Peak/Sounds`, sintetizzati da `scripts/make-sounds.mjs`), tenuti in un punto solo come `Haptics`.
/// Sessione `.ambient`: si mischia alla musica che stai ascoltando senza fermarla e tace con il tasto silenzioso.
/// Si può chiamare da qualsiasi thread; se manca un file o l'audio non parte, non succede nulla.
/// L'interruttore (Impostazioni → Generale → Suoni) è di questo iPhone e acceso di base: lo legge da `UserDefaults`.
enum SoundFX {

    enum Effect: String {
        case set, record, finish, streak, goal, medal, remove
    }

    /// Stessa chiave di `AppSettings.soundsEnabled`.
    static let enabledKey = "settings.soundsEnabled"

    static func play(_ effect: Effect) {
        start(effect.rawValue)
    }

    /// Il giorno `index` (0-6) che si riempie sulla settimana: sette note di una pentatonica, in fila fanno una melodia che sale.
    static func playDay(_ index: Int) {
        start("day\(min(6, max(0, index)) + 1)")
    }

    // Tutto da qui in giù sta sul main thread.
    private static var data: [String: Data] = [:]
    private static var playing: [AVAudioPlayer] = []
    private static var sessionReady = false
    private static let reaper = Reaper()

    private static func start(_ name: String) {
        guard Thread.isMainThread else { DispatchQueue.main.async { start(name) }; return }
        guard UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true else { return }
        guard let bytes = load(name) else { return }
        let session = AVAudioSession.sharedInstance()
        if !sessionReady { sessionReady = (try? session.setCategory(.ambient, mode: .default, options: [])) != nil }
        try? session.setActive(true) // dopo una telefonata o Siri la sessione va riaccesa
        guard let player = try? AVAudioPlayer(data: bytes, fileTypeHint: AVFileType.m4a.rawValue) else { return }
        player.delegate = reaper
        player.prepareToPlay()
        guard player.play() else { return }
        playing.append(player) // forte finché suona, così più suoni si sovrappongono
        if playing.count > 16 { playing.removeFirst(playing.count - 16) } // rete di sicurezza se un delegato non arriva
    }

    private static func load(_ name: String) -> Data? {
        if let cached = data[name] { return cached }
        guard let url = Bundle.main.url(forResource: name, withExtension: "m4a"), let bytes = try? Data(contentsOf: url) else { return nil }
        data[name] = bytes
        return bytes
    }

    private final class Reaper: NSObject, AVAudioPlayerDelegate {
        func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) { reap(player) }
        func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) { reap(player) }
        private func reap(_ player: AVAudioPlayer) {
            DispatchQueue.main.async { SoundFX.playing.removeAll { $0 === player } }
        }
    }
}
