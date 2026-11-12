import SwiftUI

extension Color {
    /// `Color(hex: 0x1A2B3C)` — comodo per tenere la palette leggibile.
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

/// Token visivi dell'app. L'interfaccia è solo scura (vedi `UIUserInterfaceStyle` in Info.plist),
/// quindi qui non servono varianti chiaro/scuro.
enum Theme {

    // Superfici
    static let bg = Color(hex: 0x0B0D14)
    static let bgRaised = Color(hex: 0x12151F)
    static let card = Color.white.opacity(0.055)
    static let cardStrong = Color.white.opacity(0.09)
    static let stroke = Color.white.opacity(0.10)
    static let strokeStrong = Color.white.opacity(0.18)

    // Testo
    static let textPrimary = Color.white.opacity(0.95)
    static let textSecondary = Color.white.opacity(0.58)
    static let textTertiary = Color.white.opacity(0.35)

    // Semantica
    static let positive = Color(hex: 0x5FD98A)
    static let negative = Color(hex: 0xFF6B6B)

    // I metalli delle medaglie
    static let bronze = Color(hex: 0xE0955A)
    static let silver = Color(hex: 0xDFE4EC)
    static let gold = Color(hex: 0xFFD45E)

    // Geometria
    static let radius: CGFloat = 20
    static let radiusSmall: CGFloat = 14
    static let padding: CGFloat = 16

    // MARK: Spaziatura, raggi, testo
    // Nuove viste usano questi, non valori grezzi; lo fa rispettare scripts/check_rules.mjs.
    // La provenienza dei gradini viene da un conteggio nelle viste (Peak/Features, PeakWatch, PeakWidgets).

    /// Spaziatura: `.padding`, `spacing:` degli stack, scarti. Griglia da 4 pt.
    /// Da dove migrare (valori vecchi, in ordine di frequenza): 8 -> s; 12 -> m; 10 -> s o m; 14 -> m o l; 6 -> xs o s;
    /// 16 -> l (è `Theme.padding`); 4 -> xs; 2 e 3 (filetti, spazi tra icona e testo) -> xs; 18, 20 -> l o xl; 24 -> xl; 28, 30 -> xl o xxl.
    /// Sopra i 32 pt (40, 52, 60, 84) non è spaziatura ma geometria di una vista: resta un valore locale.
    enum Space {
        static let xs: CGFloat = 4
        static let s: CGFloat = 8
        static let m: CGFloat = 12
        static let l: CGFloat = Theme.padding   // 16
        static let xl: CGFloat = 24
        static let xxl: CGFloat = 32
    }

    /// Raggi degli angoli (sempre con `style: .continuous`).
    /// Da dove migrare: 9, 10, 11 -> xs; 12, 13, 14 -> s (è `radiusSmall`); 16, 18, 20, 22 -> m (è `radius`); 24, 28 -> l.
    /// Le pillole sono `Capsule()`, non un raggio.
    enum Radius {
        static let xs: CGFloat = 10
        static let s: CGFloat = Theme.radiusSmall   // 14
        static let m: CGFloat = Theme.radius        // 20
        static let l: CGFloat = 28
    }

    /// Scala tipografica: ogni voce è uno stile di sistema (`Font.TextStyle`) in design `.rounded`, quindi segue Dynamic Type.
    /// La dimensione tra parentesi è quella a dimensione testo «Large» (l'impostazione predefinita), uguale ai valori vecchi.
    /// Il peso predefinito è semibold come `Theme.rounded`; per cambiarlo `.weight(.bold)`, per le cifre `.monospacedDigit()`.
    /// Da dove migrare (`Theme.rounded(N)` o `.system(size: N)`): 8, 9, 10, 11 -> micro; 12 -> caption; 13, 14 -> footnote;
    /// 15, 16 -> subhead; 17 -> body (o headline se è un titolo di riga); 18, 19, 20, 22, 24 -> title; 28 e più -> display.
    /// I numeri enormi (38, 42, 52, 96...) restano `Theme.display(size)`: sono grafica, non testo da ingrandire.
    enum Text {
        static let micro = Font.system(.caption2, design: .rounded).weight(.semibold)        // 11
        static let caption = Font.system(.caption, design: .rounded).weight(.semibold)       // 12
        static let footnote = Font.system(.footnote, design: .rounded).weight(.semibold)     // 13
        static let subhead = Font.system(.subheadline, design: .rounded).weight(.semibold)   // 15
        static let body = Font.system(.body, design: .rounded).weight(.medium)               // 17
        static let headline = Font.system(.headline, design: .rounded).weight(.bold)         // 17
        static let title = Font.system(.title3, design: .rounded).weight(.bold)              // 20
        static let display = Font.system(.largeTitle, design: .rounded).weight(.bold).monospacedDigit() // 34
    }

    /// Sfondo dell'app: nero bluastro con due aloni dell'accento in alto.
    static func background(accent: Color) -> some View {
        ZStack {
            bg
            RadialGradient(
                colors: [accent.opacity(0.22), .clear],
                center: .init(x: 0.15, y: -0.05),
                startRadius: 0,
                endRadius: 420
            )
            RadialGradient(
                colors: [Color(hex: 0x4A5BFF).opacity(0.16), .clear],
                center: .init(x: 0.95, y: 0.08),
                startRadius: 0,
                endRadius: 380
            )
        }
        .ignoresSafeArea()
    }

    /// Font dei numeri grandi: tondo, largo, tabellare così le cifre non ballano.
    static func display(_ size: CGFloat) -> Font {
        .system(size: size, weight: .bold, design: .rounded).monospacedDigit()
    }

    static func rounded(_ size: CGFloat, _ weight: Font.Weight = .semibold) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }
}
