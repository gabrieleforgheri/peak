import SwiftUI

/// Le quattro idee di Peak, a tutto schermo e nello stesso linguaggio del primo avvio: una piccola animazione che si ripete,
/// un titolo e una riga. Si scorre con «Avanti» o col dito. Come `Ideas.svelte`: con «riduci movimento» ogni disegno resta
/// il fotogramma finale.
struct IdeasView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var onNext: () -> Void
    var onSkip: () -> Void

    private let pages = 4
    @State private var page: Int? = 0
    private var at: Int { page ?? 0 }

    private var slides: [(title: String, text: String)] {
        [
            ("Sali ogni settimana", "Raggiungi l'obiettivo e allunga la serie di settimane."),
            ("Batti i tuoi record", "Ogni serie più forte diventa un record."),
            ("Medaglie a tre livelli", "Bronzo, argento, oro: sempre un traguardo più su."),
            ("Sfida gli amici", "Invitali con un link e confrontatevi sugli stessi macchinari.")
        ]
    }

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Capsule().fill(Color.white.opacity(0.12)).frame(height: 4)
                        .overlay(alignment: .leading) {
                            GeometryReader { g in
                                Capsule().fill(settings.accentColor)
                                    .frame(width: g.size.width * CGFloat(at + 1) / CGFloat(pages))
                                    .animation(reduceMotion ? nil : .timingCurve(0.23, 1, 0.32, 1, duration: 0.35), value: at)
                            }
                        }
                        .accessibilityElement()
                        .accessibilityLabel("Passo \(at + 1) di \(pages)")
                    Button("Salta") { onSkip() }
                        .font(Theme.rounded(15, .semibold))
                        .foregroundStyle(Theme.textSecondary)
                        .frame(minHeight: 44)
                        .padding(.horizontal, 12)
                        .padding(.trailing, -10)
                }
                .padding(.horizontal, 24)
                .frame(height: 56)

                ScrollView(.horizontal) {
                    HStack(spacing: 0) {
                        ForEach(0..<pages, id: \.self) { i in
                            slide(i, geo: geo)
                                .frame(width: geo.size.width)
                                .id(i)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.paging)
                .scrollPosition(id: $page)
                .scrollIndicators(.hidden)
                .scrollBounceBehavior(.basedOnSize)

                Button {
                    Haptics.tap()
                    if at == pages - 1 { onNext() }
                    else { withAnimation(reduceMotion ? nil : .smooth(duration: 0.35)) { page = at + 1 } }
                } label: {
                    Text(at == pages - 1 ? "Proviamo" : "Avanti").frame(minHeight: 24)
                }
                .buttonStyle(PrimaryButtonStyle(tint: settings.accentColor))
                .frame(maxWidth: 460)
                .padding(.horizontal, 24)
                .padding(.top, 12).padding(.bottom, 8)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .background(Theme.background(accent: settings.accentColor))
        .accessibilityAddTraits(.isModal)
    }

    private func slide(_ i: Int, geo: GeometryProxy) -> some View {
        let size = min(34, max(26, geo.size.width * 0.074))
        return VStack(spacing: 20) {
            IdeaArt(index: i, active: at == i)
                .frame(maxWidth: 480)
                .frame(maxHeight: min(geo.size.height * 0.46, 400))
            VStack(spacing: 8) {
                Text(slides[i].title)
                    .font(.system(size: size, weight: .black, design: .rounded))
                    .tracking(-0.03 * size)
                    .foregroundStyle(Theme.textPrimary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                // due righe sempre: il titolo non salta da una pagina all'altra
                Text(slides[i].text)
                    .font(Theme.rounded(16, .regular))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2, reservesSpace: true)
            }
            .frame(maxWidth: 460)
        }
        .padding(.horizontal, 24)
        .frame(maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - I disegni

/// Un disegno che si ripete ogni `T` secondi mentre la pagina è quella in vista e sfuma agli estremi, così il giro riparte pulito.
/// Tutto è una funzione del tempo `t`: le stesse scalette dei fotogrammi chiave di `Ideas.svelte`.
private struct IdeaArt: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let index: Int
    let active: Bool
    @State private var start = Date()

    private static let periods: [Double] = [7, 6, 9, 7]
    private var T: Double { Self.periods[index] }
    private var running: Bool { active && !reduceMotion }

    var body: some View {
        GeometryReader { g in
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !running)) { ctx in
                // fermo: il fotogramma finale (a 0,9 di giro tutto è a regime)
                let t = running ? ctx.date.timeIntervalSince(start) : 0.9 * T
                art(t)
                    .frame(width: 320, height: 240, alignment: .topLeading)
                    .scaleEffect(g.size.width / 320, anchor: .topLeading)
                    .frame(width: g.size.width, height: g.size.height, alignment: .topLeading)
            }
        }
        .aspectRatio(320.0 / 240.0, contentMode: .fit)
        .onChange(of: active) { _, on in if on { start = Date() } }
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func art(_ t: Double) -> some View {
        let p = (t.truncatingRemainder(dividingBy: T)) / T
        let fade = running ? Curve.key(p, [(0, 0), (0.04, 1), (0.93, 1), (1, 0)], Curve.linear) : 1
        Group {
            switch index {
            case 0: mountain(t, p)
            case 1: record(p)
            case 2: medals(t)
            default: friends(p)
            }
        }
        .opacity(fade)
    }

    // MARK: Pezzi comuni

    private enum Anchor { case start, middle, end }

    /// Un testo con la linea di base a `y` e il lato `anchor` a `x` (come il `<text>` dell'SVG).
    private func label(_ s: String, x: CGFloat, y: CGFloat, size: CGFloat, weight: Font.Weight = .semibold, color: Color, anchor: Anchor = .start) -> some View {
        Text(verbatim: s)
            .font(Theme.rounded(size, weight)).monospacedDigit()
            .foregroundStyle(color)
            .fixedSize()
            .alignmentGuide(.leading) { d in anchor == .start ? 0 : anchor == .end ? d.width : d.width / 2 }
            .alignmentGuide(.top) { d in d[.firstTextBaseline] }
            .offset(x: x, y: y)
    }

    private func poly(_ pts: [(CGFloat, CGFloat)]) -> Path {
        var p = Path()
        for (i, q) in pts.enumerated() {
            if i == 0 { p.move(to: CGPoint(x: q.0, y: q.1)) } else { p.addLine(to: CGPoint(x: q.0, y: q.1)) }
        }
        p.closeSubpath()
        return p
    }

    private var panel: Color { Color.white.opacity(0.06) }
    private var panelLine: Color { Color.white.opacity(0.12) }

    /// Un riquadro con angoli tondi in coordinate del disegno.
    private func box(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, r: CGFloat, fill: Color, stroke: Color? = nil) -> some View {
        RoundedRectangle(cornerRadius: r, style: .continuous)
            .fill(fill)
            .overlay { if let stroke { RoundedRectangle(cornerRadius: r, style: .continuous).strokeBorder(stroke, lineWidth: 1) } }
            .frame(width: w, height: h)
            .offset(x: x, y: y)
    }

    /// La bandiera/stoffa ondeggia a regime: `skewY` e `scaleX` dal bordo sinistro.
    private func wave(_ t: Double, period: Double, skew: Double, scaleX: Double) -> (angle: Double, sx: Double) {
        let ph = running ? t / period : 0.5
        let tri = ph.truncatingRemainder(dividingBy: 2)
        let w = Curve.bezier((0.42, 0, 0.58, 1), tri < 1 ? tri : 2 - tri)
        return ((-skew + 2 * skew * w) * .pi / 180, 1 - (1 - scaleX) * w)
    }

    // MARK: 1. La montagna

    private func mountain(_ t: Double, _ p: Double) -> some View {
        let accent = settings.accentColor
        func seg(_ a: Double, _ b: Double) -> Double { Curve.key(p, [(0, 0), (a, 0), (b, 1), (1, 1)], Curve.easeOut) }
        let flag = Curve.key(p, [(0, 0), (0.42, 0), (0.52, 1), (1, 1)], Curve.spring)
        let w = wave(t, period: 1.5, skew: 5, scaleX: 0.88)
        let n1 = Curve.key(p, [(0, 0), (0.54, 0), (0.58, 1), (1, 1)])
        let bump = Curve.key(p, [(0, 1), (0.54, 1), (0.6, 1.08), (0.68, 1), (1, 1)], Curve.easeOut)
        let cloth: Path = {
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                let x1 = x * w.sx
                return CGPoint(x: 240 + x1, y: 34 + y + x1 * CGFloat(tan(w.angle)))
            }
            var q = Path()
            q.move(to: pt(0, -24)); q.addLine(to: pt(22, -17)); q.addLine(to: pt(0, -10)); q.closeSubpath()
            return q
        }()
        return ZStack(alignment: .topLeading) {
            box(0, 0, 320, 176, r: 28, fill: accent.opacity(0.08), stroke: accent.opacity(0.3))
            ZStack(alignment: .topLeading) {
                poly([(0, 112), (36, 86), (74, 104), (118, 62), (160, 92), (206, 52), (252, 86), (290, 68), (320, 84), (320, 176), (0, 176)]).fill(Color.white.opacity(0.06))
                poly([(0, 148), (48, 112), (98, 130), (150, 80), (190, 108), (240, 34), (282, 90), (320, 72), (320, 176), (0, 176)])
                    .fill(LinearGradient(colors: [accent.opacity(0.5), accent.opacity(0.05)], startPoint: UnitPoint(x: 0.5, y: 34 / 176), endPoint: .bottom))
                poly([(240, 34), (223.8, 58), (231, 52), (240, 64), (248, 53), (258, 58)]).fill(Color.white.opacity(0.88))
                poly([(0, 176), (0, 160), (60, 150), (130, 164), (200, 148), (260, 160), (320, 150), (320, 176)]).fill(Color.black.opacity(0.38))
            }
            .frame(width: 320, height: 176)
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))

            ZStack(alignment: .topLeading) {
                Path { q in q.move(to: CGPoint(x: 240, y: 34)); q.addLine(to: CGPoint(x: 240, y: 10)) }
                    .stroke(accent, style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                cloth.fill(accent)
                cloth.stroke(accent, style: StrokeStyle(lineWidth: 1.4, lineJoin: .round))
            }
            .scaleEffect(max(0.0001, flag), anchor: UnitPoint(x: 240 / 320, y: 34 / 240))

            ForEach(0..<3, id: \.self) { i in
                let s = i == 0 ? seg(0.08, 0.16) : i == 1 ? seg(0.2, 0.28) : seg(0.32, 0.4)
                box(CGFloat(40 + i * 84), 192, 76, 10, r: 5, fill: Color.white.opacity(0.14))
                RoundedRectangle(cornerRadius: 5, style: .continuous).fill(accent)
                    .frame(width: 76, height: 10)
                    .shadow(color: i == 2 ? accent.opacity(0.7) : .clear, radius: 5)
                    .scaleEffect(x: max(0.0001, s), y: 1, anchor: .leading)
                    .offset(x: CGFloat(40 + i * 84), y: 192)
            }

            ZStack(alignment: .topLeading) {
                box(70, 212, 180, 26, r: 13, fill: Color.black.opacity(0.35), stroke: Color.white.opacity(0.12))
                Image(systemName: "flame.fill").font(.system(size: 15, weight: .semibold)).foregroundStyle(accent)
                    .frame(width: 20, height: 20).offset(x: 84, y: 215)
                label("3", x: 114, y: 230, size: 17, weight: .heavy, color: accent, anchor: .end).opacity(1 - n1)
                label("4", x: 114, y: 230, size: 17, weight: .heavy, color: accent, anchor: .end).opacity(n1)
                label("settimane di fila".tr, x: 130, y: 229.5, size: 12, color: Theme.textSecondary)
            }
            .scaleEffect(bump, anchor: UnitPoint(x: 160 / 320, y: 225 / 240))
        }
    }

    // MARK: 2. I record

    private func record(_ p: Double) -> some View {
        let accent = settings.accentColor
        let old = Curve.key(p, [(0, 1), (0.36, 1), (0.42, 0), (1, 0)])
        let new = Curve.key(p, [(0, 0), (0.36, 0), (0.44, 1), (1, 1)])
        let newY = Curve.key(p, [(0, 8), (0.36, 8), (0.44, 0), (1, 0)])
        let flash = running ? Curve.key(p, [(0, 0), (0.38, 0), (0.46, 1), (0.7, 0), (1, 0)]) : 0.6
        let pop = Curve.key(p, [(0, 0), (0.4, 0), (0.5, 1), (1, 1)], Curve.spring)
        let unit = settings.unit
        return ZStack(alignment: .topLeading) {
            box(10, 36, 300, 168, r: 26, fill: panel, stroke: panelLine)
            label("Chest Press", x: 30, y: 68, size: 13, weight: .bold, color: Theme.textSecondary)
            Rectangle().fill(Color.white.opacity(0.1)).frame(width: 260, height: 1).offset(x: 30, y: 112)
            label("1", x: 30, y: 100, size: 15, weight: .bold, color: Theme.textTertiary)
            label(Fmt.setLine(weight: 60, reps: 8, unit: unit), x: 58, y: 100, size: 22, weight: .heavy, color: Theme.textPrimary)
            box(18, 122, 284, 50, r: 16, fill: Theme.positive.opacity(0.22)).opacity(flash)
            label("2", x: 30, y: 154, size: 15, weight: .bold, color: Theme.textTertiary)
            label(Fmt.setLine(weight: 60, reps: 8, unit: unit), x: 58, y: 154, size: 22, weight: .heavy, color: Theme.textPrimary).opacity(old)
            label(Fmt.setLine(weight: 65, reps: 8, unit: unit), x: 58, y: 154, size: 22, weight: .heavy, color: Theme.textPrimary).opacity(new).offset(y: newY)

            ZStack {
                ForEach(0..<12, id: \.self) { k in
                    let a = Double(k) / 12 * .pi * 2 + 0.2, r = 34 + Double(k % 3) * 9
                    let dx = cos(a) * r, dy = sin(a) * r
                    Circle()
                        .fill([Theme.positive, accent, MuscleGroup.spalle.tint][k % 3])
                        .frame(width: k % 2 == 1 ? 5.2 : 7.2, height: k % 2 == 1 ? 5.2 : 7.2)
                        .scaleEffect(Curve.key(p, [(0, 0.3), (0.7, 0.3), (0.78, 1)], Curve.easeOut))
                        .offset(x: Curve.key(p, [(0, 0), (0.7, 0), (0.78, dx)], Curve.easeOut), y: Curve.key(p, [(0, 0), (0.7, 0), (0.78, dy)], Curve.easeOut))
                        .opacity(Curve.key(p, [(0, 0), (0.419, 0), (0.42, 1), (0.7, 1), (0.78, 0)], Curve.easeOut))
                }
                ZStack {
                    box(-24, -13, 48, 26, r: 13, fill: Theme.positive.opacity(0.18))
                    label("PR", x: 0, y: 5, size: 14, weight: .black, color: Theme.positive, anchor: .middle)
                }
                .scaleEffect(max(0.0001, pop))
            }
            .frame(width: 0, height: 0)
            .offset(x: 262, y: 147)
        }
    }

    // MARK: 3. Le medaglie

    private static let tiers: [(hi: Color, lo: Color, ink: Color, tint: Color)] = [
        (Color(hex: 0xF1B27A), Color(hex: 0xA35F2A), Color(hex: 0x3A1D08), Color(hex: 0xE0955A)),
        (Color(hex: 0xF4F6FA), Color(hex: 0x98A1B1), Color(hex: 0x232A36), Color(hex: 0xDFE4EC)),
        (Color(hex: 0xFFE69A), Color(hex: 0xD39A14), Color(hex: 0x3D2A00), Color(hex: 0xFFD45E))
    ]

    private func medals(_ t: Double) -> some View {
        let names = ["Bronzo".tr, "Argento".tr, "Oro".tr]
        return ZStack(alignment: .topLeading) {
            ForEach(0..<3, id: \.self) { i in
                let tr = Self.tiers[i]
                // ogni livello parte con il suo ritardo (un terzo di giro); prima del ritardo resta al primo fotogramma
                let local = t - Double(i) * T / 3
                let p = local < 0 ? 0 : (local / T).truncatingRemainder(dividingBy: 1)
                let op = Curve.key(p, [(0, 0), (0.04, 1), (0.3, 1), (0.34, 0), (1, 0)], Curve.spring)
                let sc = Curve.key(p, [(0, 0.7), (0.04, 1), (0.3, 1), (0.34, 0.92), (1, 0.92)], Curve.spring)
                let nameOp = Curve.key(p, [(0, 0), (0.03, 0), (0.06, 1), (0.3, 1), (0.34, 0), (1, 0)])
                let waveOp = Curve.key(p, [(0, 0), (0.02, 0), (0.04, 0.8), (0.14, 0), (1, 0)])
                let waveSc = Curve.key(p, [(0, 1), (0.02, 1), (0.14, 1.8), (1, 1.8)], Curve.easeOut)
                Circle().strokeBorder(tr.tint, lineWidth: 3).frame(width: 92, height: 92).scaleEffect(waveSc).opacity(waveOp)
                    .offset(x: 114, y: 70)
                medal(tr).scaleEffect(min(max(sc, 0), 1.2), anchor: UnitPoint(x: 0.5, y: 86 / 240)).opacity(min(1, op))
                label(names[i], x: 160, y: 214, size: 20, weight: .heavy, color: tr.tint, anchor: .middle).opacity(nameOp)
            }
        }
    }

    private func medal(_ tr: (hi: Color, lo: Color, ink: Color, tint: Color)) -> some View {
        ZStack(alignment: .topLeading) {
            poly([(124, 8), (150, 8), (176, 76), (150, 76)]).fill(tr.lo)
            poly([(196, 8), (170, 8), (144, 76), (170, 76)]).fill(tr.tint.opacity(0.85))
            Circle().fill(LinearGradient(colors: [tr.hi, tr.lo], startPoint: UnitPoint(x: 0.2, y: 0.1), endPoint: UnitPoint(x: 0.8, y: 0.95)))
                .frame(width: 96, height: 96).offset(x: 112, y: 68)
            Circle().strokeBorder(tr.ink.opacity(0.28), lineWidth: 2).frame(width: 76, height: 76).offset(x: 122, y: 78)
            Image(systemName: "star.fill").font(.system(size: 40, weight: .bold)).foregroundStyle(tr.ink.opacity(0.8))
                .frame(width: 42, height: 42).offset(x: 139, y: 95)
        }
        .shadow(color: .black.opacity(0.35), radius: 14, y: 8)
    }

    // MARK: 4. Gli amici

    private func friends(_ p: Double) -> some View {
        let accent = settings.accentColor
        let chip = Curve.key(p, [(0, 0), (0.02, 0), (0.1, 1), (1, 1)], Curve.spring)
        let bar = Curve.key(p, [(0, 0), (0.3, 0), (0.62, 1), (1, 1)], Curve.easeOut)
        let crown = Curve.key(p, [(0, 0), (0.64, 0), (0.74, 1), (1, 1)], Curve.spring)
        let crownY = Curve.key(p, [(0, -6), (0.64, -6), (0.74, 0), (1, 0)], Curve.spring)
        let board: [(w: CGFloat, c: Color, l: String)] = [(188, MuscleGroup.petto.tint, "A"), (146, MuscleGroup.schiena.tint, "M"), (104, MuscleGroup.gambe.tint, "G")]
        let starts = [(0.18, 0.28), (0.26, 0.36), (0.34, 0.44)]
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                box(86, 8, 148, 38, r: 19, fill: accent.opacity(0.16), stroke: accent.opacity(0.45))
                Image(systemName: "link").font(.system(size: 17, weight: .semibold)).foregroundStyle(accent)
                    .frame(width: 24, height: 24).offset(x: 102, y: 15)
                box(136, 24, 84, 7, r: 3.5, fill: Color.white.opacity(0.32))
            }
            .scaleEffect(max(0.0001, chip), anchor: UnitPoint(x: 160 / 320, y: 27 / 240))
            .opacity(min(1, chip))

            box(10, 62, 300, 168, r: 26, fill: panel, stroke: panelLine)
            label("Chest Press", x: 30, y: 92, size: 13, weight: .bold, color: Theme.textSecondary)
            ForEach(0..<3, id: \.self) { i in
                let b = board[i]
                let (a, z) = starts[i]
                let op = Curve.key(p, [(0, 0), (a, 0), (z, 1), (1, 1)], Curve.easeOut)
                let dx = Curve.key(p, [(0, -24), (a, -24), (z, 0), (1, 0)], Curve.easeOut)
                let y = CGFloat(i * 38)
                ZStack(alignment: .topLeading) {
                    Circle().fill(b.c).frame(width: 28, height: 28).offset(x: 28, y: 112 + y)
                    label(b.l, x: 42, y: 131 + y, size: 14, weight: .black, color: Color(hex: 0x0B0D14), anchor: .middle)
                    box(68, 120 + y, 222, 12, r: 6, fill: Color.white.opacity(0.08))
                    RoundedRectangle(cornerRadius: 6, style: .continuous).fill(b.c)
                        .frame(width: b.w, height: 12)
                        .scaleEffect(x: max(0.0001, bar), y: 1, anchor: .leading)
                        .offset(x: 68, y: 120 + y)
                }
                .opacity(op)
                .offset(x: dx)
            }
            Image(systemName: "crown.fill").font(.system(size: 14, weight: .semibold)).foregroundStyle(MuscleGroup.spalle.tint)
                .frame(width: 16, height: 16)
                .scaleEffect(max(0.0001, crown))
                .opacity(min(1, crown))
                .offset(x: 30, y: 98 + crownY)
        }
    }
}
