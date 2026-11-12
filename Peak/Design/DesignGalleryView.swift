#if DEBUG
import SwiftUI

/// Galleria del design system, solo per le preview: i token di `Theme` e i componenti di `Peak/Design/`
/// istanziati con dati finti. Non è collegata a nessuna schermata. Niente SwiftData, rete, timer o date correnti.
/// Fuori dalla galleria: `ExerciseThumbnail`, `ExerciseFigure`, `ExerciseCardBackground` (vogliono un `Exercise` di SwiftData),
/// `ConfettiBurst` (vuole `AppSettings` e parte da un timer), `CountText` e le entrate `appear`/`drawIn` (animano al primo sguardo).
struct DesignGalleryView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.xl) {
                GalleryColors()
                GalleryTypography()
                GallerySpacing()
                GalleryRadii()
                GalleryComponents()
            }
            .padding(Theme.Space.l)
        }
        .screenBackground(Theme.positive)
        .preferredColorScheme(.dark)
    }
}

// MARK: - Contenitore di sezione

private struct GallerySection<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.m) {
            Text(verbatim: title.uppercased())
                .font(Theme.Text.micro)
                .tracking(0.8)
                .foregroundStyle(Theme.textTertiary)
            content
        }
    }
}

// MARK: - Colori

private struct GalleryColors: View {
    private let surfaces: [(String, Color)] = [
        ("bg", Theme.bg), ("bgRaised", Theme.bgRaised), ("card", Theme.card), ("cardStrong", Theme.cardStrong),
        ("stroke", Theme.stroke), ("strokeStrong", Theme.strokeStrong)
    ]
    private let texts: [(String, Color)] = [
        ("textPrimary", Theme.textPrimary), ("textSecondary", Theme.textSecondary), ("textTertiary", Theme.textTertiary)
    ]
    private let semantic: [(String, Color)] = [
        ("positive", Theme.positive), ("negative", Theme.negative),
        ("bronze", Theme.bronze), ("silver", Theme.silver), ("gold", Theme.gold)
    ]
    private let columns = [GridItem(.adaptive(minimum: 96), spacing: Theme.Space.s)]

    var body: some View {
        GallerySection(title: "Colori") {
            swatches(surfaces)
            swatches(texts)
            swatches(semantic)
            swatches(MuscleGroup.allCases.map { ($0.title, $0.tint) })
        }
    }

    private func swatches(_ items: [(String, Color)]) -> some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: Theme.Space.s) {
            ForEach(items, id: \.0) { name, color in
                VStack(alignment: .leading, spacing: Theme.Space.xs) {
                    RoundedRectangle(cornerRadius: Theme.Radius.xs, style: .continuous)
                        .fill(color)
                        .frame(height: 40)
                        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.xs, style: .continuous).strokeBorder(Theme.stroke))
                    Text(verbatim: name)
                        .font(Theme.Text.micro)
                        .foregroundStyle(Theme.textSecondary)
                        .lineLimit(1)
                }
            }
        }
    }
}

// MARK: - Tipografia

private struct GalleryTypography: View {
    private let styles: [(String, String, Font)] = [
        ("micro 11", "Serie 4 di 5", Theme.Text.micro),
        ("caption 12", "Serie 4 di 5", Theme.Text.caption),
        ("footnote 13", "Ultimo allenamento ieri", Theme.Text.footnote),
        ("subhead 15", "Panca piana, 4 serie", Theme.Text.subhead),
        ("body 17", "Hai battuto il massimale", Theme.Text.body),
        ("headline 17", "Pressa orizzontale", Theme.Text.headline),
        ("title 20", "Riepilogo settimana", Theme.Text.title),
        ("display 34", "102,5", Theme.Text.display)
    ]

    var body: some View {
        GallerySection(title: "Tipografia (segue Dynamic Type)") {
            GlassCard {
                VStack(alignment: .leading, spacing: Theme.Space.m) {
                    ForEach(styles, id: \.0) { name, sample, font in
                        HStack(alignment: .firstTextBaseline) {
                            Text(verbatim: name)
                                .font(Theme.Text.micro)
                                .foregroundStyle(Theme.textTertiary)
                                .frame(width: 84, alignment: .leading)
                            Text(verbatim: sample)
                                .font(font)
                                .foregroundStyle(Theme.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Spaziatura

private struct GallerySpacing: View {
    private let steps: [(String, CGFloat)] = [
        ("xs", Theme.Space.xs), ("s", Theme.Space.s), ("m", Theme.Space.m),
        ("l", Theme.Space.l), ("xl", Theme.Space.xl), ("xxl", Theme.Space.xxl)
    ]

    var body: some View {
        GallerySection(title: "Spaziatura") {
            VStack(alignment: .leading, spacing: Theme.Space.s) {
                ForEach(steps, id: \.0) { name, value in
                    HStack(spacing: Theme.Space.m) {
                        Text(verbatim: "\(name) \(Int(value))")
                            .font(Theme.Text.caption)
                            .foregroundStyle(Theme.textSecondary)
                            .frame(width: 56, alignment: .leading)
                        Rectangle()
                            .fill(Theme.positive)
                            .frame(width: value, height: 16)
                    }
                }
            }
        }
    }
}

// MARK: - Raggi

private struct GalleryRadii: View {
    private let steps: [(String, CGFloat)] = [
        ("xs", Theme.Radius.xs), ("s", Theme.Radius.s), ("m", Theme.Radius.m), ("l", Theme.Radius.l)
    ]

    var body: some View {
        GallerySection(title: "Raggi") {
            HStack(spacing: Theme.Space.m) {
                ForEach(steps, id: \.0) { name, value in
                    VStack(spacing: Theme.Space.xs) {
                        RoundedRectangle(cornerRadius: value, style: .continuous)
                            .fill(Theme.cardStrong)
                            .overlay(RoundedRectangle(cornerRadius: value, style: .continuous).strokeBorder(Theme.strokeStrong))
                            .frame(height: 64)
                        Text(verbatim: "\(name) \(Int(value))")
                            .font(Theme.Text.caption)
                            .foregroundStyle(Theme.textSecondary)
                    }
                }
            }
        }
    }
}

// MARK: - Componenti

private struct GalleryComponents: View {
    var body: some View {
        GallerySection(title: "Chip, badge, tessere") {
            HStack(spacing: Theme.Space.s) {
                MuscleGroupChip(group: .petto)
                MuscleGroupChip(group: .gambe, compact: true)
                PRBadge()
                DeltaBadge(percent: 4.2)
                DeltaBadge(percent: -2.5)
            }
            HStack(spacing: Theme.Space.s) {
                StatTile(symbol: "scalemass.fill", title: "Volume", value: "12.480", caption: "kg questa settimana", tint: Theme.positive)
                StatTile(symbol: "flame.fill", title: "Serie", value: "5", tint: Theme.gold)
            }
        }

        GallerySection(title: "Pillole, testo secondario, posizione") {
            HStack(spacing: Theme.Space.s) {
                SelectablePill(isOn: true, tint: Theme.positive, action: {}) { Text(verbatim: "Volume 30 giorni") }
                SelectablePill(isOn: false, tint: Theme.positive, action: {}) { Text(verbatim: "Settimane di fila") }
            }
            Text(verbatim: "Ultimo allenamento ieri").mutedCaption()
            Text(verbatim: "Nessun record negli ultimi giorni").mutedCaption(Theme.Text.footnote)
            HStack(spacing: Theme.Space.m) {
                RankNumber(1, tint: Theme.gold)
                RankNumber(2)
                RankNumber(3, alignment: .leading)
            }
        }

        GallerySection(title: "Schede") {
            GlassCard {
                Text(verbatim: "GlassCard")
                    .font(Theme.Text.subhead)
                    .foregroundStyle(Theme.textPrimary)
            }
            GlassCard(tint: Theme.positive) {
                Text(verbatim: "GlassCard con tinta")
                    .font(Theme.Text.subhead)
                    .foregroundStyle(Theme.textPrimary)
            }
            WarningBanner(symbol: "exclamationmark.triangle.fill", title: "Backup scaduto",
                          message: "L'ultima copia risale a più di un mese fa.", actionTitle: "Esporta ora", action: {})
        }

        GallerySection(title: "Pulsanti") {
            Button {} label: { Text(verbatim: "Primario") }
                .buttonStyle(PrimaryButtonStyle(tint: Theme.positive))
            Button {} label: { Text(verbatim: "Primario non a tutta larghezza") }
                .buttonStyle(PrimaryButtonStyle(tint: Theme.gold, fullWidth: false))
            Button {} label: { Text(verbatim: "Secondario") }
                .buttonStyle(SecondaryButtonStyle())
            Button {} label: {
                GlassCard { Text(verbatim: "PressStyle su una card").font(Theme.Text.subhead).foregroundStyle(Theme.textPrimary) }
            }
            .buttonStyle(PressStyle())
            GalleryConfirm()
        }

        GallerySection(title: "Stepper") {
            GalleryWeightStepper()
            GalleryRepsStepper()
            GallerySideSegment()
        }

        GallerySection(title: "Intestazioni e mesi") {
            SectionHeader(title: "Allenamenti", trailing: "3 questo mese")
            GalleryMonths()
        }

        GallerySection(title: "Disegni") {
            HStack(spacing: Theme.Space.l) {
                MachineGlyphView(glyph: .legPress, tint: MuscleGroup.gambe.tint, size: 64)
                MuscleFigureView(muscles: [.chest, .deltoids])
                    .frame(width: 96, height: 96)
                MuscleFigureView(muscles: [.lowerBack, .trapezius], zoom: false)
                    .frame(width: 96, height: 96)
            }
            PlateBarView(load: Plates.load(total: 102.5, bar: 20, plates: Plates.catalog), unit: .kg, tint: Theme.positive)
            CelebrationToast(text: "Nuovo massimale!")
        }
    }
}

// MARK: - Componenti con stato

private struct GalleryConfirm: View {
    @State private var shown = false

    var body: some View {
        Button { shown = true } label: { Text(verbatim: "Apri il foglio di conferma") }
            .buttonStyle(SecondaryButtonStyle())
            .confirmSheet(isPresented: $shown, title: "Eliminare l'allenamento?", confirm: "Elimina",
                          cancel: "Annulla", tint: Theme.negative, action: {})
    }
}

private struct GalleryWeightStepper: View {
    @State private var kilograms = 82.5

    var body: some View {
        GlassCard { WeightStepper(kilograms: $kilograms, step: 2.5, unit: .kg, tint: Theme.positive) }
    }
}

private struct GalleryRepsStepper: View {
    @State private var reps = 8

    var body: some View {
        GlassCard { RepsStepper(reps: $reps, tint: Theme.positive) }
    }
}

private struct GallerySideSegment: View {
    @State private var singleArm = false

    var body: some View {
        HStack {
            SideSegment(singleArm: $singleArm, group: .petto)
            SideSegment(singleArm: .constant(true), group: .gambe)
        }
    }
}

private struct GalleryMonths: View {
    /// Date fisse (non `Date()`), a mezzogiorno UTC: la preview è sempre uguale.
    private let items: [(String, Date)] = [
        ("Petto e spalle", Date(timeIntervalSince1970: 1_791_201_600)),   // 2026-10-05
        ("Gambe", Date(timeIntervalSince1970: 1_790_164_800)),            // 2026-09-23
        ("Schiena", Date(timeIntervalSince1970: 1_788_868_800))           // 2026-09-08
    ]

    var body: some View {
        GlassCard {
            MonthStack(items: items, date: { $0.1 }, count: { "\($0) allenamenti" }, dividers: true) { item in
                Text(verbatim: item.0)
                    .font(Theme.Text.subhead)
                    .foregroundStyle(Theme.textPrimary)
                    .padding(.vertical, Theme.Space.xs)
            }
        }
    }
}

// MARK: - Preview

#Preview("Galleria") {
    DesignGalleryView()
}

#Preview("Chip e badge") {
    HStack(spacing: Theme.Space.s) {
        MuscleGroupChip(group: .braccia)
        PRBadge()
        DeltaBadge(percent: 6.0)
    }
    .padding(Theme.Space.l)
    .background(Theme.background(accent: Theme.positive))
}

#Preview("StatTile") {
    StatTile(symbol: "scalemass.fill", title: "Volume", value: "12.480", caption: "kg questa settimana", tint: Theme.positive)
        .padding(Theme.Space.l)
        .background(Theme.background(accent: Theme.positive))
}

#Preview("Pulsanti") {
    VStack(spacing: Theme.Space.m) {
        Button {} label: { Text(verbatim: "Primario") }.buttonStyle(PrimaryButtonStyle(tint: Theme.positive))
        Button {} label: { Text(verbatim: "Secondario") }.buttonStyle(SecondaryButtonStyle())
    }
    .padding(Theme.Space.l)
    .background(Theme.background(accent: Theme.positive))
}

#Preview("WarningBanner") {
    WarningBanner(symbol: "exclamationmark.triangle.fill", title: "Backup scaduto",
                  message: "L'ultima copia risale a più di un mese fa.", actionTitle: "Esporta ora", action: {})
        .padding(Theme.Space.l)
        .background(Theme.background(accent: Theme.positive))
}
#endif
