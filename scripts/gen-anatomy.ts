// Scrive common/anatomy.ts e Peak/Design/MuscleFigureShapes.swift dai contorni di react-muscle-highlighter (MIT, licenza in
// scripts/anatomy-src/LICENSE.txt). Legge i due file come testo: non li esegue mai.
// Uso: node scripts/gen-anatomy.ts   (un test in common/ controlla che i file siano aggiornati)
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { MUSCLE_INFO, MUSCLES, type Muscle } from '../common/muscle-info.ts'

const SRC = (name: string) => fileURLToPath(new URL(`./anatomy-src/${name}`, import.meta.url))
export const TS_FILE = fileURLToPath(new URL('../common/anatomy.ts', import.meta.url))
export const SWIFT_FILE = fileURLToPath(new URL('../Peak/Design/MuscleFigureShapes.swift', import.meta.url))

/** Pezzi del pacchetto che sono muscoli. Gli altri (testa, mani, piedi, ginocchia…) sono il corpo. */
export const SLUG_TO_MUSCLE: Record<string, Muscle> = {
  chest: 'chest', deltoids: 'deltoids', trapezius: 'trapezius', 'upper-back': 'upperBack', 'lower-back': 'lowerBack',
  abs: 'abs', obliques: 'obliques', biceps: 'biceps', triceps: 'triceps', forearm: 'forearm', quadriceps: 'quadriceps',
  adductors: 'adductors', tibialis: 'tibialis', gluteal: 'gluteal', hamstring: 'hamstring', calves: 'calves',
}

/** La vista di back è spostata di 724 in x nel pacchetto: la riporto a 0…724. */
const VIEW_OFFSET = { front: 0, back: 724 } as const
export type View = keyof typeof VIEW_OFFSET

export type Seg = ['M' | 'L', number, number] | ['C', number, number, number, number, number, number] | ['Z']

/** Un blocco `slug: "…"` con le sue stringhe di percorso. */
export function pieces(text: string): { slug: string; paths: string[] }[] {
  return text.split('slug: "').slice(1).map((block) => ({
    slug: block.slice(0, block.indexOf('"')),
    paths: [...block.matchAll(/"([Mm][^"]*)"/g)].map((m) => m[1]),
  }))
}

/** Un percorso SVG (M m L l H h V v C c Q q A a Z z) in comandi assoluti M, L, C, Z. */
export function absolute(d: string, dx = 0): Seg[] {
  let i = 0
  const skip = () => { while (i < d.length && /[\s,]/.test(d[i])) i++ }
  const num = (): number => {
    skip()
    const m = /^[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/.exec(d.slice(i))
    if (!m) throw new Error(`numero atteso a ${i}: ${d.slice(i, i + 12)}`)
    i += m[0].length
    return +m[0]
  }
  // i flag degli archi possono stare attaccati ("01"): si legge una cifra alla volta
  const flag = (): number => {
    skip()
    const c = d[i++]
    if (c !== '0' && c !== '1') throw new Error(`flag atteso a ${i}`)
    return +c
  }
  const out: Seg[] = []
  let cx = 0, cy = 0, sx = 0, sy = 0, cmd = ''
  while (true) {
    skip()
    if (i >= d.length) break
    if (/[A-Za-z]/.test(d[i])) cmd = d[i++]
    const rel = cmd === cmd.toLowerCase()
    const ox = rel ? cx : 0, oy = rel ? cy : 0
    switch (cmd.toUpperCase()) {
      case 'M': {
        cx = num() + ox; cy = num() + oy; sx = cx; sy = cy
        out.push(['M', cx + dx, cy])
        cmd = rel ? 'l' : 'L' // coppie successive dopo una M sono linee
        break
      }
      case 'L': {
        cx = num() + ox; cy = num() + oy
        out.push(['L', cx + dx, cy])
        break
      }
      case 'C': {
        const x1 = num() + ox, y1 = num() + oy, x2 = num() + ox, y2 = num() + oy
        cx = num() + ox; cy = num() + oy
        out.push(['C', x1 + dx, y1, x2 + dx, y2, cx + dx, cy])
        break
      }
      case 'Q': {
        const qx = num() + ox, qy = num() + oy
        const ex = num() + ox, ey = num() + oy
        // quadratica → cubica con gli stessi punti
        out.push(['C', cx + (2 / 3) * (qx - cx) + dx, cy + (2 / 3) * (qy - cy), ex + (2 / 3) * (qx - ex) + dx, ey + (2 / 3) * (qy - ey), ex + dx, ey])
        cx = ex; cy = ey
        break
      }
      case 'A': {
        const rx = num(), ry = num(), rot = num(), fa = flag(), fs = flag()
        const ex = num() + ox, ey = num() + oy
        out.push(...arcToCubics(cx, cy, rx, ry, rot, fa, fs, ex, ey).map((s) => shift(s, dx)))
        cx = ex; cy = ey
        break
      }
      case 'Z': {
        out.push(['Z'])
        cx = sx; cy = sy
        break
      }
      default:
        throw new Error(`comando non gestito: ${cmd}`)
    }
  }
  return out
}

const shift = (s: Seg, dx: number): Seg => {
  if (s[0] === 'Z') return s
  const r = [...s] as Seg
  for (let k = 1; k < r.length; k += 2) (r[k] as number) += dx
  return r
}

/** Arco SVG (F.6.5 della specifica) in curve cubiche. */
function arcToCubics(x1: number, y1: number, rx0: number, ry0: number, phiDeg: number, fa: number, fs: number, x2: number, y2: number): Seg[] {
  if (x1 === x2 && y1 === y2) return []
  let rx = Math.abs(rx0), ry = Math.abs(ry0)
  if (rx === 0 || ry === 0) return [['L', x2, y2]]
  const phi = (phiDeg * Math.PI) / 180
  const cos = Math.cos(phi), sin = Math.sin(phi)
  const dx2 = (x1 - x2) / 2, dy2 = (y1 - y2) / 2
  const x1p = cos * dx2 + sin * dy2, y1p = -sin * dx2 + cos * dy2
  const lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
  if (lam > 1) { rx *= Math.sqrt(lam); ry *= Math.sqrt(lam) }
  const num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
  const den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
  let co = Math.sqrt(Math.max(0, num / den))
  if (fa === fs) co = -co
  const cxp = (co * rx * y1p) / ry, cyp = (-co * ry * x1p) / rx
  const cx = cos * cxp - sin * cyp + (x1 + x2) / 2
  const cy = sin * cxp + cos * cyp + (y1 + y2) / 2
  const angle = (ux: number, uy: number, vx: number, vy: number) => {
    const a = Math.acos(Math.max(-1, Math.min(1, (ux * vx + uy * vy) / (Math.hypot(ux, uy) * Math.hypot(vx, vy)))))
    return ux * vy - uy * vx < 0 ? -a : a
  }
  const th1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
  let dth = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
  if (!fs && dth > 0) dth -= 2 * Math.PI
  else if (fs && dth < 0) dth += 2 * Math.PI
  const n = Math.max(1, Math.ceil(Math.abs(dth) / (Math.PI / 2) - 1e-9))
  const delta = dth / n
  const t = (4 / 3) * Math.tan(delta / 4)
  const at = (ux: number, uy: number): [number, number] => [cx + rx * ux * cos - ry * uy * sin, cy + rx * ux * sin + ry * uy * cos]
  const segs: Seg[] = []
  let a1 = th1
  for (let k = 0; k < n; k++) {
    const a2 = a1 + delta
    const c1 = Math.cos(a1), s1 = Math.sin(a1), c2 = Math.cos(a2), s2 = Math.sin(a2)
    const p1 = at(c1 - t * s1, s1 + t * c1)
    const p2 = at(c2 + t * s2, s2 - t * c2)
    const p3 = at(c2, s2)
    segs.push(['C', p1[0], p1[1], p2[0], p2[1], p3[0], p3[1]])
    a1 = a2
  }
  return segs
}

const r2 = (v: number) => +v.toFixed(2)

/** Il `d` assoluto di una lista di comandi, arrotondato a due decimali. */
export function toD(segs: Seg[]): string {
  return segs.map((s) => (s[0] === 'Z' ? 'Z' : `${s[0]}${s.slice(1).map((v) => r2(v as number)).join(' ')}`)).join('')
}

/** Il corpo di una vista: pezzi con il muscolo (o null) e i loro percorsi. */
export function viewPieces(view: View): { slug: string; muscle: Muscle | null; paths: string[] }[] {
  const text = readFileSync(SRC(view === 'front' ? 'bodyFront.js.txt' : 'bodyBack.js.txt'), 'utf8')
  return pieces(text).map((p) => ({
    slug: p.slug,
    muscle: SLUG_TO_MUSCLE[p.slug] ?? null,
    paths: p.paths.map((d) => toD(absolute(d, -VIEW_OFFSET[view]))),
  }))
}

/**
 * Il Swift legge una stringa di numeri per percorso: 0 = move x y, 1 = line x y, 2 = curve c1 c2 end (6 numeri), 3 = chiudi.
 */
export function opsOf(d: string): string {
  const ops: number[] = []
  for (const s of absolute(d)) {
    if (s[0] === 'M') ops.push(0, s[1], s[2])
    else if (s[0] === 'L') ops.push(1, s[1], s[2])
    else if (s[0] === 'C') ops.push(2, ...s.slice(1) as number[])
    else ops.push(3)
  }
  return ops.map(r2).join(' ')
}

/** Il rettangolo [x0, y0, x1, y1] che contiene un muscolo in una vista, dai numeri dei suoi percorsi (coppie x y). */
export function boxOf(paths: string[]): [number, number, number, number] {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity
  for (const d of paths) {
    const n = (d.match(/-?\d+(\.\d+)?/g) ?? []).map(Number)
    for (let i = 0; i + 1 < n.length; i += 2) {
      x0 = Math.min(x0, n[i]); x1 = Math.max(x1, n[i]); y0 = Math.min(y0, n[i + 1]); y1 = Math.max(y1, n[i + 1])
    }
  }
  return [r2(x0), r2(y0), r2(x1), r2(y1)]
}

/** Il rettangolo di ogni muscolo in ogni vista in cui compare. */
export function muscleBoxes(views: Record<View, ReturnType<typeof viewPieces>>): Record<View, Partial<Record<Muscle, [number, number, number, number]>>> {
  const out = { front: {}, back: {} } as Record<View, Partial<Record<Muscle, [number, number, number, number]>>>
  for (const v of ['front', 'back'] as const) {
    for (const m of MUSCLES) {
      const paths = views[v].filter((p) => p.muscle === m).flatMap((p) => p.paths)
      if (paths.length) out[v][m] = boxOf(paths)
    }
  }
  return out
}

export function tsSource(views: Record<View, ReturnType<typeof viewPieces>>): string {
  const boxes = muscleBoxes(views)
  const boxLines = (v: View) => MUSCLES.filter((m) => boxes[v][m]).map((m) => `    ${m}: [${boxes[v][m]!.join(', ')}],`).join('\n')
  const body = (v: View) => views[v].map((p) => `    { slug: ${JSON.stringify(p.slug)}, muscle: ${p.muscle ? JSON.stringify(p.muscle) : 'null'}, paths: [${p.paths.map((d) => JSON.stringify(d)).join(', ')}] },`).join('\n')
  return `// Generato da scripts/gen-anatomy.ts dai contorni di react-muscle-highlighter (MIT, vedi scripts/anatomy-src/LICENSE.txt):
// non modificarlo a mano. Le coordinate di back sono già riportate a 0…724.

export const ANATOMY_VIEWBOX = '0 0 724 1448'

export const ANATOMY = {
  front: [
${body('front')}
  ],
  back: [
${body('back')}
  ],
} as const

/** Il rettangolo [x0, y0, x1, y1] di ogni muscolo in ogni vista: per lo zoom sulla zona che lavora. */
export const MUSCLE_BOX = {
  front: {
${boxLines('front')}
  },
  back: {
${boxLines('back')}
  },
} as const
`
}

/** Le righe `"vista.muscolo": [x0, y0, x1, y1],` per il Swift. */
function swiftBoxes(boxes: ReturnType<typeof muscleBoxes>): string {
  return (['front', 'back'] as const).flatMap((v) => MUSCLES.filter((m) => boxes[v][m]).map((m) => `        "${v}.${m}": [${boxes[v][m]!.join(', ')}],`)).join('\n')
}

export function swiftSource(views: Record<View, ReturnType<typeof viewPieces>>): string {
  const boxes = muscleBoxes(views)
  const cases = MUSCLES.map((m) => `    case ${m}`).join('\n')
  const titles = MUSCLES.map((m) => `        case .${m}: return ${JSON.stringify(MUSCLE_INFO[m].it)}`).join('\n')
  const groups = MUSCLES.map((m) => `        case .${m}: return .${MUSCLE_INFO[m].group}`).join('\n')
  const piece = (p: ReturnType<typeof viewPieces>[number]) => {
    const muscle = p.muscle ? `.${p.muscle}` : 'nil'
    const ops = p.paths.map((d) => `"${opsOf(d)}"`).join(', ')
    return `        AnatomyPiece(muscle: ${muscle}, paths: [${ops}]),`
  }
  return `// Generato da scripts/gen-anatomy.ts dai contorni di react-muscle-highlighter (MIT, vedi scripts/anatomy-src/LICENSE.txt):
// non modificarlo a mano.

import SwiftUI

/// I muscoli che la figura colora. Il rawValue è l'id di common/muscle-info.ts.
enum MuscleId: String, CaseIterable, Codable {
${cases}

    var title: String {
        switch self {
${titles}
        }
    }

    /// Il gruppo muscolare: il colore del muscolo nella figura.
    var group: MuscleGroup {
        switch self {
${groups}
        }
    }
}

/// Un pezzo del corpo: il muscolo (nil = corpo, sempre grigio), e i suoi percorsi come stringhe di numeri.
struct AnatomyPiece {
    let muscle: MuscleId?
    let paths: [String]
}

enum AnatomyShapes {
    /// Stesse coordinate del web: 0…724 × 0…1448.
    static let size = CGSize(width: 724, height: 1448)

    static let front: [AnatomyPiece] = [
${views.front.map(piece).join('\n')}
    ]

    static let back: [AnatomyPiece] = [
${views.back.map(piece).join('\n')}
    ]

    /// Decodifica una stringa di numeri nel percorso: 0 = move, 1 = line, 2 = curve (6 numeri), 3 = chiudi.
    static func path(_ ops: String) -> Path {
        let v = ops.split(separator: " ").compactMap { Double($0) }
        var p = Path()
        var i = 0
        while i < v.count {
            switch Int(v[i]) {
            case 0:
                p.move(to: CGPoint(x: v[i + 1], y: v[i + 2])); i += 3
            case 1:
                p.addLine(to: CGPoint(x: v[i + 1], y: v[i + 2])); i += 3
            case 2:
                p.addCurve(to: CGPoint(x: v[i + 5], y: v[i + 6]), control1: CGPoint(x: v[i + 1], y: v[i + 2]), control2: CGPoint(x: v[i + 3], y: v[i + 4]))
                i += 7
            default:
                p.closeSubpath(); i += 1
            }
        }
        return p
    }
}

enum AnatomyView: String, CaseIterable {
    case front, back
}

/// Lo zoom sulla zona che lavora: il rettangolo dei muscoli attivi, con margine e una misura minima. Come zoomFor in common/muscles.ts.
enum AnatomyZoom {
    static let full: [Double] = [0, 0, 724, 1448]
    static let minSide: Double = 240

    /// [x0, y0, x1, y1] di ogni muscolo in ogni vista: la chiave è "vista.muscolo".
    static let boxes: [String: [Double]] = [
${swiftBoxes(boxes)}
    ]

    static func rect(for muscles: [MuscleId], view: AnatomyView) -> CGRect {
        let found = muscles.compactMap { boxes["\\(view.rawValue).\\($0.rawValue)"] }
        guard let first = found.first else { return CGRect(x: full[0], y: full[1], width: full[2] - full[0], height: full[3] - full[1]) }
        var x0 = first[0], y0 = first[1], x1 = first[2], y1 = first[3]
        for b in found.dropFirst() {
            x0 = min(x0, b[0]); y0 = min(y0, b[1]); x1 = max(x1, b[2]); y1 = max(y1, b[3])
        }
        let padX = max((x1 - x0) * 0.15, 30), padY = max((y1 - y0) * 0.15, 30)
        x0 -= padX; x1 += padX; y0 -= padY; y1 += padY
        let w = max(x1 - x0, minSide), h = max(y1 - y0, minSide)
        let cx = (x0 + x1) / 2, cy = (y0 + y1) / 2
        return CGRect(x: cx - w / 2, y: cy - h / 2, width: w, height: h)
    }
}
`
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const views = { front: viewPieces('front'), back: viewPieces('back') }
  writeFileSync(TS_FILE, tsSource(views))
  writeFileSync(SWIFT_FILE, swiftSource(views))
  console.log(`anatomia: ${views.front.length} pezzi davanti, ${views.back.length} di schiena`)
}
