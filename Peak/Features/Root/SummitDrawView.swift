import SwiftUI

// MARK: - Curve

/// Le curve del web (`cubic-bezier` del CSS) e i fotogrammi chiave, per portare le animazioni a tempo dei disegni del primo avvio.
enum Curve {
    typealias Bezier = (Double, Double, Double, Double)
    static let easeOut: Bezier = (0.23, 1, 0.32, 1)      // --ease-out
    static let spring: Bezier = (0.34, 1.56, 0.64, 1)    // la molla con un po' di rimbalzo
    static let ease: Bezier = (0.25, 0.1, 0.25, 1)       // `ease` del CSS
    static let linear: Bezier = (0, 0, 1, 1)

    static func bezier(_ c: Bezier, _ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        if c == linear { return x }
        func at(_ s: Double, _ a: Double, _ b: Double) -> Double { 3 * (1 - s) * (1 - s) * s * a + 3 * (1 - s) * s * s * b + s * s * s }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 {
            let m = (lo + hi) / 2
            if at(m, c.0, c.2) < x { lo = m } else { hi = m }
        }
        return at((lo + hi) / 2, c.1, c.3)
    }

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }

    /// Il valore alla posizione `p` (0...1) fra fotogrammi chiave `(posizione, valore)`; la curva vale in ogni tratto, come nel CSS.
    static func key(_ p: Double, _ frames: [(Double, Double)], _ curve: Bezier = ease) -> Double {
        guard let first = frames.first, let last = frames.last else { return 0 }
        if p <= first.0 { return first.1 }
        if p >= last.0 { return last.1 }
        for i in 1..<frames.count where p <= frames[i].0 {
            let a = frames[i - 1], b = frames[i]
            if b.0 == a.0 { return b.1 }
            return a.1 + (b.1 - a.1) * bezier(curve, (p - a.0) / (b.0 - a.0))
        }
        return last.1
    }
}

// MARK: - La montagna del benvenuto

/// Il grafico di un titolo che sale e si fa montagna, con la bandiera sulla vetta (circa 3 s, una volta). `still`: il fotogramma
/// finale, senza movimento (anche con «riduci movimento»). Stesso disegno e stessa scaletta di `SummitDraw.svelte`.
struct SummitDrawView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var still = false
    @State private var start = Date()

    private typealias P = CGPoint
    private static let W: CGFloat = 360, H: CGFloat = 260

    /// Una curva morbida per i punti (Catmull-Rom scritta come Bézier cubiche); nei punti `corners` resta uno spigolo netto.
    private static func smooth(_ pts: [P], corners: [Int] = []) -> (path: Path, segs: [[P]]) {
        func f(_ n: CGFloat) -> CGFloat { (n * 10).rounded() / 10 }
        var path = Path()
        var segs: [[P]] = []
        path.move(to: pts[0])
        for i in 0..<(pts.count - 1) {
            let a = pts[i], b = pts[i + 1]
            let p0 = i == 0 || corners.contains(i) ? a : pts[i - 1]
            let p3 = i + 2 >= pts.count || corners.contains(i + 1) ? b : pts[i + 2]
            let c1 = P(x: f(a.x + (b.x - p0.x) / 6), y: f(a.y + (b.y - p0.y) / 6))
            let c2 = P(x: f(b.x - (p3.x - a.x) / 6), y: f(b.y - (p3.y - a.y) / 6))
            segs.append([a, c1, c2, b])
            path.addCurve(to: b, control1: c1, control2: c2)
        }
        return (path, segs)
    }

    private static func at(_ s: [P], _ t: CGFloat) -> P {
        let u = 1 - t, w = [u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t]
        return P(x: zip(s, w).reduce(0) { $0 + $1.0.x * $1.1 }, y: zip(s, w).reduce(0) { $0 + $1.0.y * $1.1 })
    }

    /// Il punto della curva alla quota `y` (la quota sale o scende sempre lungo il tratto).
    private static func atY(_ s: [P], _ y: CGFloat) -> P {
        var lo: CGFloat = 0, hi: CGFloat = 1
        let up = s[3].y < s[0].y
        for _ in 0..<24 {
            let m = (lo + hi) / 2
            if (at(s, m).y < y) == up { hi = m } else { lo = m }
        }
        return at(s, (lo + hi) / 2)
    }

    // un solo monte, visto da vicino: la vetta vicina al centro (un po' a destra) a un quarto dall'alto, due fianchi lunghi fino ai bordi,
    // una spalla piatta per lato e nessun zigzag. Dietro, una sagoma più grande e più pallida
    private static let summit = P(x: 192, y: 70)
    private static let ridge = smooth([P(x: 0, y: 238), P(x: 62, y: 194), P(x: 112, y: 170), summit, P(x: 240, y: 128), P(x: 286, y: 148), P(x: 360, y: 228)], corners: [3])
    private static let range = smooth([P(x: 0, y: 216), P(x: 72, y: 158), P(x: 136, y: 104), P(x: 214, y: 134), P(x: 290, y: 160), P(x: 360, y: 196)])
    /// La neve: solo la punta, con il bordo di sotto a zig zag.
    private static let cap: Path = {
        let yb = summit.y + 26
        let l = atY(ridge.segs[2], yb), r = atY(ridge.segs[3], yb), w = r.x - l.x
        var p = Path()
        p.move(to: summit)
        p.addLine(to: P(x: l.x, y: yb))
        p.addLine(to: P(x: l.x + w * 0.3, y: yb - 3))
        p.addLine(to: P(x: l.x + w * 0.5, y: yb + 2))
        p.addLine(to: P(x: l.x + w * 0.72, y: yb - 3))
        p.addLine(to: P(x: r.x, y: yb))
        p.closeSubpath()
        return p
    }()

    private static func closed(_ p: Path) -> Path {
        var q = p
        q.addLine(to: P(x: W, y: H))
        q.addLine(to: P(x: 0, y: H))
        q.closeSubpath()
        return q
    }
    private static let ridgeFill = closed(ridge.path)
    private static let rangeFill = closed(range.path)

    var body: some View {
        let play = !still && !reduceMotion
        GeometryReader { geo in
            let k = geo.size.width / Self.W
            TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !play)) { ctx in
                art(play ? ctx.date.timeIntervalSince(start) : 99)
                    .frame(width: Self.W, height: Self.H)
                    .scaleEffect(k, anchor: .topLeading)
                    .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
            }
        }
        .aspectRatio(Self.W / Self.H, contentMode: .fit)
        .accessibilityHidden(true)
        .onAppear { start = Date() }
    }

    // MARK: - Il disegno al tempo `t` (secondi)

    private func art(_ t: Double) -> some View {
        let accent = settings.accentColor
        let kLine = Curve.clamp((t - 0.15) / 1.6)
        let p = kLine < 0.5 ? 4 * kLine * kLine * kLine : 1 - pow(-2 * kLine + 2, 3) / 2   // ease-in-out cubica
        let drawn = Self.ridge.path.trimmedPath(from: 0, to: max(0.0001, p))
        let headAt = drawn.currentPoint ?? Self.summit

        func fade(_ delay: Double, _ dur: Double) -> Double { Curve.bezier(Curve.easeOut, (t - delay) / dur) }
        let grow = fade(1.7, 0.9)
        let haloOp = 0.6 - 0.25 * fade(1.7, 0.8)
        let flagS = Curve.bezier(Curve.spring, (t - 2.35) / 0.56)
        // la bandiera ondeggia a regime (dal 2,9 s): su e giù, 1,8 s per verso, con ease-in-out
        let ph = max(0, t - 2.9) / 1.8
        let tri = ph.truncatingRemainder(dividingBy: 2)
        let w = Curve.bezier((0.42, 0, 0.58, 1), tri < 1 ? tri : 2 - tri)
        let angle = (-4 + 8 * w) * .pi / 180, sx = 1 - 0.1 * w

        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                Self.rangeFill
                    .fill(LinearGradient(colors: [accent.opacity(0.12), accent.opacity(0)], startPoint: UnitPoint(x: 0.5, y: 100 / Self.H), endPoint: UnitPoint(x: 0.5, y: 230 / Self.H)))
                    .blur(radius: 2.5)
                    .opacity(fade(1.85, 0.9))
                Self.ridgeFill
                    .fill(LinearGradient(colors: [accent.opacity(0.35), accent.opacity(0)], startPoint: UnitPoint(x: 0.5, y: 56 / Self.H), endPoint: .bottom))
                    .scaleEffect(x: 1, y: max(0.0001, grow), anchor: .bottom)
                    .opacity(grow)
                Self.cap.fill(Color.white.opacity(0.72)).opacity(fade(2.25, 0.45))
                drawn.stroke(accent, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                    .blur(radius: 4)
                    .opacity(haloOp)
                drawn.stroke(
                    LinearGradient(stops: [
                        .init(color: accent.opacity(0), location: 0), .init(color: accent.opacity(0.7), location: 0.35),
                        .init(color: accent, location: 0.65), .init(color: accent, location: 1)
                    ], startPoint: .leading, endPoint: .trailing),
                    style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round)
                )
                Circle().fill(Color.white).frame(width: 7, height: 7)
                    .shadow(color: accent, radius: 5)
                    .position(headAt)
                    .opacity(kLine > 0 && kLine < 1 ? 1 : 0)
            }
            .mask(LinearGradient(stops: [
                .init(color: .clear, location: 0), .init(color: .black, location: 0.08),
                .init(color: .black, location: 0.92), .init(color: .clear, location: 1)
            ], startPoint: .leading, endPoint: .trailing))

            Circle()
                .fill(RadialGradient(colors: [accent.opacity(0.26), accent.opacity(0)], center: .center, startRadius: 0, endRadius: 40))
                .frame(width: 80, height: 80)
                .position(x: Self.summit.x, y: Self.summit.y - 14)
                .opacity(fade(2.3, 0.8))

            ZStack(alignment: .topLeading) {
                Path { q in
                    q.move(to: P(x: Self.summit.x, y: Self.summit.y))
                    q.addLine(to: P(x: Self.summit.x, y: Self.summit.y - 24))
                }
                .stroke(accent, style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
                cloth(angle: angle, sx: sx)
                    .fill(accent)
                cloth(angle: angle, sx: sx)
                    .stroke(accent, style: StrokeStyle(lineWidth: 1, lineJoin: .round))
            }
            .scaleEffect(max(0.0001, flagS), anchor: UnitPoint(x: Self.summit.x / Self.W, y: Self.summit.y / Self.H))
        }
    }

    /// La stoffa: un triangolo che parte dall'asta, inclinato e stretto dall'ondeggiare (come `skewY` e `scaleX` del CSS, dal suo bordo sinistro).
    private func cloth(angle: Double, sx: Double) -> Path {
        let o = Self.summit
        func pt(_ x: CGFloat, _ y: CGFloat) -> P {
            let x1 = x * sx
            return P(x: o.x + x1, y: o.y + y + x1 * CGFloat(tan(angle)))
        }
        var p = Path()
        p.move(to: pt(0, -24))
        p.addLine(to: pt(16, -18.5))
        p.addLine(to: pt(0, -13))
        p.closeSubpath()
        return p
    }
}
