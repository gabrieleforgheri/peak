import SwiftUI

extension AnatomyView: Identifiable {
    var id: String { rawValue }
}

/// La figura a tutto schermo, già inquadrata sui muscoli che lavorano. Pizzico e trascinamento cambiano la zona
/// visibile (`box` di `MuscleFigureView`), così il disegno resta vettoriale e nitido; doppio tocco alterna corpo intero e inquadratura.
/// Stessa cosa di MuscleZoom.svelte sul web.
struct MuscleZoomView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let muscles: [MuscleId]
    @State private var side: AnatomyView
    @State private var size: CGSize = .zero
    @State private var cam = Cam(cx: 362, cy: 724, k: 1)
    // l'ultimo valore dei gesti: ogni passo si applica come differenza, così trascinamento e pizzico insieme non si pestano i piedi
    @State private var lastDrag: CGSize = .zero
    @State private var lastMagnify: CGFloat = 1

    private static let full = CGRect(x: 0, y: 0, width: 724, height: 1448)
    private static let maxZoom: Double = 6

    init(muscles: [MuscleId], side: AnatomyView) {
        self.muscles = muscles
        _side = State(initialValue: side)
    }

    /// La vista: centro nel disegno e punti per unità del disegno. La zona visibile ha sempre la forma dello schermo.
    struct Cam {
        var cx: Double, cy: Double, k: Double
    }

    private var kMin: Double { size.width > 0 ? min(size.width / Self.full.width, size.height / Self.full.height) : 1 }

    /// I muscoli di questo lato, per le targhette.
    private var mine: [MuscleId] {
        let here = Set(AnatomyCache.pieces(side).compactMap(\.muscle))
        return muscles.filter { here.contains($0) }
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Picker("", selection: $side) {
                    Text("Fronte".tr).tag(AnatomyView.front)
                    Text("Retro".tr).tag(AnatomyView.back)
                }
                .pickerStyle(.segmented)
                .frame(width: 200)

                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Theme.textPrimary)
                            .frame(width: 36, height: 36)
                            .background(Theme.cardStrong, in: Circle())
                    }
                    .accessibilityLabel("Chiudi".tr)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            GeometryReader { proxy in
                ZStack {
                    Color.clear
                    if size.width > 0 {
                        ZoomedFigure(muscles: muscles, side: side, size: size, cam: cam)
                    }
                }
                .frame(width: proxy.size.width, height: proxy.size.height)
                .clipped()
                .contentShape(Rectangle())
                .gesture(TapGesture(count: 2).onEnded { toggle() })
                .simultaneousGesture(
                    DragGesture(minimumDistance: 4)
                        .onChanged { v in
                            let d = CGSize(width: v.translation.width - lastDrag.width, height: v.translation.height - lastDrag.height)
                            lastDrag = v.translation
                            cam = clamped(Cam(cx: cam.cx - d.width / cam.k, cy: cam.cy - d.height / cam.k, k: cam.k))
                        }
                        .onEnded { _ in lastDrag = .zero }
                )
                .simultaneousGesture(
                    MagnifyGesture()
                        .onChanged { v in
                            let ratio = v.magnification / lastMagnify
                            lastMagnify = v.magnification
                            // il punto del disegno sotto le dita resta lì
                            let a = v.startLocation
                            let nk = min(max(cam.k * ratio, kMin), kMin * Self.maxZoom)
                            let wx = cam.cx + (a.x - size.width / 2) / cam.k, wy = cam.cy + (a.y - size.height / 2) / cam.k
                            cam = clamped(Cam(cx: wx - (a.x - size.width / 2) / nk, cy: wy - (a.y - size.height / 2) / nk, k: nk))
                        }
                        .onEnded { _ in lastMagnify = 1 }
                )
                .onChange(of: proxy.size, initial: true) { _, new in size = new }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(muscles.map(\.title).joined(separator: ", "))
            }
            // all'apertura, cambiando lato o dimensione: inquadra i muscoli di quel lato (senza, il corpo intero)
            .onChange(of: size, initial: true) { _, _ in cam = target(whole: false) }
            .onChange(of: side) { _, _ in cam = target(whole: false) }

            if !mine.isEmpty {
                MuscleChipGrid(muscles: mine)
                    .padding(16)
            }
        }
        .background(Theme.bg.ignoresSafeArea())
    }

    /// Zoom entro i limiti e centro che tiene la figura sullo schermo: se sta tutta, resta centrata.
    private func clamped(_ c: Cam) -> Cam {
        let k = min(max(c.k, kMin), kMin * Self.maxZoom)
        func lim(_ v: Double, _ view: Double, _ full: Double) -> Double {
            view >= full ? full / 2 : min(max(v, view / 2), full - view / 2)
        }
        return Cam(cx: lim(c.cx, size.width / k, Self.full.width), cy: lim(c.cy, size.height / k, Self.full.height), k: k)
    }

    /// Le due inquadrature: i muscoli che lavorano su questo lato (senza muscoli, il corpo intero) o tutto il corpo.
    private func target(whole: Bool) -> Cam {
        guard size.width > 0 else { return cam }
        let b = whole ? Self.full : AnatomyZoom.rect(for: muscles, view: side)
        return clamped(Cam(cx: b.midX, cy: b.midY, k: min(size.width / b.width, size.height / b.height)))
    }

    private func toggle() {
        let f = target(whole: false)
        let atFrame = abs(cam.k / f.k - 1) < 0.03 && abs(cam.cx - f.cx) < 8 && abs(cam.cy - f.cy) < 8
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.25)) {
            cam = atFrame ? target(whole: true) : f
        }
    }
}

/// La figura nella zona visibile data da `cam`; animabile, così il doppio tocco scorre invece di saltare.
private struct ZoomedFigure: View, Animatable {
    let muscles: [MuscleId]
    let side: AnatomyView
    let size: CGSize
    var cx: Double, cy: Double, k: Double

    init(muscles: [MuscleId], side: AnatomyView, size: CGSize, cam: MuscleZoomView.Cam) {
        self.muscles = muscles
        self.side = side
        self.size = size
        cx = cam.cx
        cy = cam.cy
        k = cam.k
    }

    var animatableData: AnimatablePair<Double, AnimatablePair<Double, Double>> {
        get { AnimatablePair(cx, AnimatablePair(cy, k)) }
        set { cx = newValue.first; cy = newValue.second.first; k = newValue.second.second }
    }

    var body: some View {
        let w = size.width / k, h = size.height / k
        MuscleFigureView(muscles: muscles, view: side, box: CGRect(x: cx - w / 2, y: cy - h / 2, width: w, height: h))
            .frame(width: size.width, height: size.height)
    }
}
