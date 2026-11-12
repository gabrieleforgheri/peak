import SwiftUI

// MARK: - Testo secondario

extension View {
    /// Testo secondario piccolo: medium e `textTertiary`. Sostituisce `.font(Theme.Text.micro.weight(.medium)).foregroundStyle(Theme.textTertiary)`
    /// (anche con `caption` o `footnote` al posto di `micro`).
    func mutedCaption(_ font: Font = Theme.Text.micro) -> some View {
        self.font(font.weight(.medium)).foregroundStyle(Theme.textTertiary)
    }
}

// MARK: - Posizione in classifica

/// Il numero di posizione in una classifica: largo `width`, pesante, `tint` per chi si vuole evidenziare, terziario altrimenti.
/// Sostituisce `Text("\(i + 1)").font(footnote.heavy).foregroundStyle(...).frame(width: 22)`.
struct RankNumber: View {
    let rank: Int
    var tint: Color? = nil
    var alignment: Alignment = .center

    init(_ rank: Int, tint: Color? = nil, alignment: Alignment = .center) {
        self.rank = rank
        self.tint = tint
        self.alignment = alignment
    }

    var body: some View {
        Text(verbatim: "\(rank)")
            .font(Theme.Text.footnote.weight(.heavy))
            .foregroundStyle(tint ?? Theme.textTertiary)
            .frame(width: 22, alignment: alignment)
    }
}

#if DEBUG
#Preview("Testo secondario e posizione") {
    VStack(alignment: .leading, spacing: Theme.Space.s) {
        Text(verbatim: "Ultimo allenamento ieri").mutedCaption()
        Text(verbatim: "Nessun record negli ultimi giorni").mutedCaption(Theme.Text.footnote)
        HStack { RankNumber(1, tint: Theme.gold); Text(verbatim: "@marco").font(Theme.Text.footnote) }
        HStack { RankNumber(2); Text(verbatim: "@anna").font(Theme.Text.footnote) }
    }
    .padding(Theme.Space.l)
    .background(Theme.background(accent: Theme.positive))
}
#endif
