// Pittogrammi dei macchinari: forme in coordinate 0…1 (y verso il basso), come in Design/MachineGlyph.swift.
// Cinque primitive: line (sottile), bar (spessa), rect (pieno, angoli morbidi), dot (pieno), ring (vuoto).

import { GLYPHS, type GlyphName, type MuscleGroup } from './docs.ts'

export type Shape =
  | ['line' | 'bar', number, number, number, number]
  | ['rect', number, number, number, number]
  | ['dot' | 'ring', number, number, number]

const line = (x1: number, y1: number, x2: number, y2: number): Shape => ['line', x1, y1, x2, y2]
const bar = (x1: number, y1: number, x2: number, y2: number): Shape => ['bar', x1, y1, x2, y2]
const rect = (x: number, y: number, w: number, h: number): Shape => ['rect', x, y, w, h]
const dot = (x: number, y: number, r: number): Shape => ['dot', x, y, r]
const ring = (x: number, y: number, r: number): Shape => ['ring', x, y, r]

/** Pacco pesi: rettangolo pieno con le fessure fra una piastra e l'altra. */
const stack = (x: number, y: number, w: number, h: number): Shape[] => [
  rect(x, y, w, h), ...[1, 2, 3].map((i) => line(x, y + (h * i) / 4, x + w, y + (h * i) / 4)),
]
const base = line(0.08, 0.88, 0.92, 0.88)
const loadedBar = (y: number, r = 0.085): Shape[] => [bar(0.12, y, 0.88, y), dot(0.17, y, r), dot(0.83, y, r)]

export const SHAPES: Record<GlyphName, Shape[]> = {
  seatedPush: [
    ...stack(0.06, 0.26, 0.14, 0.54), line(0.28, 0.14, 0.28, 0.88), rect(0.37, 0.30, 0.075, 0.32),
    rect(0.37, 0.62, 0.26, 0.07), line(0.46, 0.30, 0.76, 0.30), line(0.46, 0.44, 0.76, 0.44),
    dot(0.80, 0.30, 0.055), dot(0.80, 0.44, 0.055), base,
  ],
  pecDeck: [
    ...stack(0.06, 0.30, 0.13, 0.50), line(0.30, 0.16, 0.30, 0.88), rect(0.38, 0.60, 0.24, 0.07),
    line(0.44, 0.24, 0.74, 0.34), line(0.44, 0.52, 0.74, 0.42), dot(0.78, 0.34, 0.05), dot(0.78, 0.42, 0.05), base,
  ],
  latPulldown: [
    ...stack(0.06, 0.32, 0.12, 0.48), line(0.24, 0.12, 0.24, 0.88), line(0.24, 0.13, 0.68, 0.13),
    ring(0.64, 0.19, 0.05), line(0.64, 0.24, 0.64, 0.44), bar(0.38, 0.46, 0.92, 0.46),
    rect(0.46, 0.70, 0.26, 0.07), base,
  ],
  seatedRow: [
    ...stack(0.06, 0.30, 0.12, 0.50), line(0.24, 0.16, 0.24, 0.88), line(0.24, 0.52, 0.64, 0.52),
    dot(0.68, 0.52, 0.055), rect(0.36, 0.64, 0.26, 0.07), bar(0.84, 0.52, 0.84, 0.80), base,
  ],
  cableStation: [
    rect(0.14, 0.12, 0.10, 0.76), ring(0.19, 0.22, 0.045), line(0.19, 0.265, 0.19, 0.38),
    line(0.19, 0.38, 0.62, 0.46), dot(0.66, 0.47, 0.055), base,
  ],
  cableCross: [
    rect(0.07, 0.14, 0.09, 0.74), rect(0.84, 0.14, 0.09, 0.74), ring(0.115, 0.24, 0.04), ring(0.885, 0.24, 0.04),
    line(0.115, 0.28, 0.50, 0.54), line(0.885, 0.28, 0.50, 0.54), dot(0.50, 0.57, 0.055), base,
  ],
  benchFlat: [
    ...loadedBar(0.32), rect(0.24, 0.54, 0.52, 0.09), line(0.32, 0.63, 0.32, 0.86), line(0.68, 0.63, 0.68, 0.86),
  ],
  benchIncline: [
    ...loadedBar(0.26, 0.075), bar(0.26, 0.74, 0.66, 0.46), line(0.30, 0.74, 0.30, 0.87),
    line(0.63, 0.50, 0.63, 0.87), rect(0.20, 0.72, 0.16, 0.07),
  ],
  barbell: [
    bar(0.08, 0.50, 0.92, 0.50), dot(0.20, 0.50, 0.145), dot(0.80, 0.50, 0.145), dot(0.34, 0.50, 0.085), dot(0.66, 0.50, 0.085),
  ],
  dumbbell: [bar(0.33, 0.50, 0.67, 0.50), rect(0.14, 0.33, 0.14, 0.34), rect(0.72, 0.33, 0.14, 0.34)],
  plateLoaded: [
    line(0.22, 0.14, 0.22, 0.88), rect(0.28, 0.58, 0.24, 0.07), rect(0.28, 0.30, 0.07, 0.28),
    line(0.38, 0.40, 0.64, 0.40), ring(0.76, 0.50, 0.155), ring(0.76, 0.50, 0.05), base,
  ],
  pullUpBar: [
    line(0.14, 0.18, 0.86, 0.18), line(0.16, 0.18, 0.16, 0.88), line(0.84, 0.18, 0.84, 0.88),
    dot(0.38, 0.18, 0.05), dot(0.62, 0.18, 0.05), line(0.38, 0.22, 0.50, 0.40), line(0.62, 0.22, 0.50, 0.40),
    dot(0.50, 0.46, 0.075), line(0.50, 0.53, 0.50, 0.74),
  ],
  legPress: [
    bar(0.20, 0.78, 0.72, 0.34), bar(0.64, 0.20, 0.86, 0.44), rect(0.10, 0.62, 0.22, 0.08), ring(0.48, 0.58, 0.09), base,
  ],
  legLever: [
    rect(0.20, 0.50, 0.30, 0.08), rect(0.16, 0.24, 0.08, 0.26), line(0.50, 0.58, 0.78, 0.42),
    dot(0.83, 0.39, 0.075), line(0.34, 0.58, 0.34, 0.88), base,
  ],
  squatRack: [
    ...loadedBar(0.30, 0.09), line(0.22, 0.14, 0.22, 0.88), line(0.78, 0.14, 0.78, 0.88),
    line(0.22, 0.38, 0.30, 0.33), line(0.78, 0.38, 0.70, 0.33), base,
  ],
  smithMachine: [
    line(0.26, 0.10, 0.26, 0.88), line(0.74, 0.10, 0.74, 0.88), rect(0.22, 0.40, 0.08, 0.09), rect(0.70, 0.40, 0.08, 0.09),
    bar(0.10, 0.445, 0.90, 0.445), dot(0.15, 0.445, 0.08), dot(0.85, 0.445, 0.08), base,
  ],
  hyperBench: [
    bar(0.24, 0.68, 0.62, 0.32), line(0.44, 0.52, 0.44, 0.86), dot(0.72, 0.68, 0.06), dot(0.82, 0.68, 0.06),
    line(0.66, 0.68, 0.66, 0.86), base,
  ],
  calfPlatform: [
    ...stack(0.74, 0.36, 0.12, 0.34), rect(0.18, 0.68, 0.50, 0.09), line(0.28, 0.18, 0.28, 0.68), rect(0.22, 0.22, 0.30, 0.08), base,
  ],
  abBench: [
    bar(0.20, 0.72, 0.68, 0.42), line(0.28, 0.70, 0.28, 0.86), line(0.62, 0.46, 0.62, 0.86),
    dot(0.76, 0.38, 0.06), dot(0.76, 0.52, 0.06), base,
  ],
  romanChair: [
    line(0.28, 0.12, 0.28, 0.88), rect(0.28, 0.24, 0.07, 0.26), rect(0.34, 0.34, 0.26, 0.07), dot(0.50, 0.20, 0.075),
    line(0.50, 0.44, 0.50, 0.62), line(0.50, 0.62, 0.68, 0.60), base,
  ],
  bodyweight: [
    dot(0.50, 0.22, 0.095), line(0.50, 0.32, 0.50, 0.58), line(0.28, 0.42, 0.72, 0.42),
    line(0.50, 0.58, 0.34, 0.80), line(0.50, 0.58, 0.66, 0.80),
  ],
  machine: [
    ...stack(0.10, 0.26, 0.16, 0.56), line(0.34, 0.14, 0.34, 0.88), rect(0.42, 0.56, 0.28, 0.08), rect(0.42, 0.30, 0.07, 0.26), base,
  ],
  // ---- un attrezzo per voce del catalogo ----
  legCurlLying: [
    rect(0.10, 0.46, 0.56, 0.08), line(0.20, 0.54, 0.20, 0.88), line(0.56, 0.54, 0.56, 0.88),
    line(0.66, 0.50, 0.82, 0.30), dot(0.84, 0.27, 0.07), base,
  ],
  legCurlSeated: [
    rect(0.16, 0.24, 0.08, 0.30), rect(0.20, 0.54, 0.30, 0.08), rect(0.30, 0.40, 0.20, 0.07),
    line(0.50, 0.58, 0.80, 0.50), dot(0.82, 0.58, 0.07), line(0.34, 0.62, 0.34, 0.88), base,
  ],
  hackSquat: [
    line(0.16, 0.88, 0.58, 0.12), bar(0.38, 0.66, 0.58, 0.32), dot(0.64, 0.30, 0.06), bar(0.36, 0.80, 0.68, 0.74), base,
  ],
  squatMachine: [
    line(0.18, 0.36, 0.18, 0.88), bar(0.18, 0.36, 0.70, 0.28), dot(0.46, 0.39, 0.055), ring(0.78, 0.27, 0.11),
    rect(0.30, 0.80, 0.42, 0.07), base,
  ],
  hipThrust: [
    rect(0.06, 0.42, 0.26, 0.10), line(0.12, 0.52, 0.12, 0.88), line(0.26, 0.52, 0.26, 0.88),
    ring(0.62, 0.64, 0.22), dot(0.62, 0.64, 0.06), base,
  ],
  gluteMachine: [
    ...stack(0.06, 0.30, 0.12, 0.50), line(0.26, 0.14, 0.26, 0.88), rect(0.32, 0.30, 0.22, 0.07),
    line(0.42, 0.58, 0.78, 0.42), bar(0.72, 0.32, 0.86, 0.54), base,
  ],
  hipAdductor: [
    rect(0.40, 0.16, 0.20, 0.32), rect(0.28, 0.50, 0.44, 0.08), bar(0.42, 0.60, 0.18, 0.78), bar(0.58, 0.60, 0.82, 0.78),
    line(0.50, 0.58, 0.50, 0.88), base,
  ],
  calfSeated: [
    rect(0.10, 0.56, 0.28, 0.08), line(0.24, 0.64, 0.24, 0.88), rect(0.36, 0.42, 0.22, 0.07),
    line(0.47, 0.44, 0.76, 0.38), ring(0.82, 0.37, 0.09), rect(0.50, 0.78, 0.16, 0.07), base,
  ],
  ghd: [
    line(0.10, 0.74, 0.88, 0.74), line(0.18, 0.74, 0.18, 0.88), line(0.80, 0.74, 0.80, 0.88),
    bar(0.20, 0.36, 0.20, 0.60), line(0.20, 0.60, 0.20, 0.74), dot(0.29, 0.42, 0.05), dot(0.29, 0.56, 0.05),
    rect(0.48, 0.46, 0.32, 0.16), line(0.64, 0.62, 0.64, 0.74), base,
  ],
  kettlebell: [ring(0.50, 0.30, 0.15), dot(0.50, 0.60, 0.25)],
  trapBar: [
    bar(0.30, 0.50, 0.40, 0.32), bar(0.40, 0.32, 0.60, 0.32), bar(0.60, 0.32, 0.70, 0.50),
    bar(0.70, 0.50, 0.60, 0.68), bar(0.60, 0.68, 0.40, 0.68), bar(0.40, 0.68, 0.30, 0.50),
    bar(0.06, 0.50, 0.30, 0.50), bar(0.70, 0.50, 0.94, 0.50), rect(0.12, 0.32, 0.08, 0.36), rect(0.80, 0.32, 0.08, 0.36),
    line(0.42, 0.44, 0.42, 0.56), line(0.58, 0.44, 0.58, 0.56),
  ],
  ezBar: [
    bar(0.06, 0.50, 0.30, 0.50), bar(0.30, 0.50, 0.37, 0.40), bar(0.37, 0.40, 0.44, 0.58), bar(0.44, 0.58, 0.50, 0.48),
    bar(0.50, 0.48, 0.56, 0.58), bar(0.56, 0.58, 0.63, 0.40), bar(0.63, 0.40, 0.70, 0.50), bar(0.70, 0.50, 0.94, 0.50),
    rect(0.10, 0.32, 0.08, 0.36), rect(0.82, 0.32, 0.08, 0.36),
  ],
  landmine: [rect(0.06, 0.80, 0.16, 0.08), bar(0.14, 0.82, 0.74, 0.30), dot(0.78, 0.27, 0.13), base],
  preacherBench: [
    rect(0.10, 0.58, 0.24, 0.08), line(0.22, 0.66, 0.22, 0.88), bar(0.40, 0.32, 0.66, 0.54), line(0.52, 0.44, 0.52, 0.88),
    dot(0.75, 0.60, 0.06), base,
  ],
  dipStation: [
    line(0.24, 0.32, 0.24, 0.88), line(0.76, 0.32, 0.76, 0.88), bar(0.12, 0.32, 0.38, 0.32), bar(0.62, 0.32, 0.88, 0.32), base,
  ],
  assistMachine: [
    ...stack(0.06, 0.34, 0.12, 0.46), line(0.26, 0.10, 0.26, 0.88), line(0.26, 0.12, 0.76, 0.12), dot(0.76, 0.12, 0.05),
    bar(0.50, 0.40, 0.78, 0.40), rect(0.44, 0.62, 0.28, 0.07), line(0.26, 0.66, 0.44, 0.66), base,
  ],
  rowMachine: [
    ...stack(0.06, 0.30, 0.12, 0.50), line(0.24, 0.16, 0.24, 0.88), line(0.24, 0.40, 0.40, 0.40), dot(0.43, 0.40, 0.055),
    rect(0.54, 0.28, 0.07, 0.30), rect(0.54, 0.62, 0.30, 0.07), line(0.68, 0.69, 0.68, 0.88), base,
  ],
  cableRope: [
    rect(0.10, 0.12, 0.10, 0.76), line(0.15, 0.14, 0.60, 0.14), ring(0.60, 0.20, 0.05), line(0.60, 0.25, 0.60, 0.46),
    line(0.60, 0.46, 0.50, 0.68), line(0.60, 0.46, 0.70, 0.68), dot(0.49, 0.71, 0.05), dot(0.71, 0.71, 0.05), base,
  ],
  cablePushdown: [
    rect(0.10, 0.12, 0.10, 0.76), line(0.15, 0.14, 0.60, 0.14), ring(0.60, 0.20, 0.05), line(0.60, 0.25, 0.60, 0.52),
    bar(0.44, 0.54, 0.76, 0.54), base,
  ],
  sled: [
    bar(0.10, 0.82, 0.74, 0.82), line(0.74, 0.82, 0.88, 0.70), bar(0.24, 0.80, 0.14, 0.20),
    line(0.58, 0.82, 0.58, 0.34), rect(0.40, 0.60, 0.36, 0.09), rect(0.40, 0.46, 0.36, 0.09),
  ],
  dumbbellBench: [
    rect(0.20, 0.58, 0.60, 0.09), line(0.30, 0.67, 0.30, 0.88), line(0.70, 0.67, 0.70, 0.88),
    bar(0.40, 0.32, 0.60, 0.32), rect(0.29, 0.21, 0.10, 0.22), rect(0.61, 0.21, 0.10, 0.22),
  ],
  inclineDumbbell: [
    bar(0.24, 0.76, 0.60, 0.48), line(0.28, 0.76, 0.28, 0.88), line(0.56, 0.52, 0.56, 0.88), rect(0.16, 0.74, 0.16, 0.07),
    bar(0.62, 0.22, 0.80, 0.22), rect(0.53, 0.12, 0.09, 0.20), rect(0.80, 0.12, 0.09, 0.20),
  ],
  abMachine: [
    ...stack(0.06, 0.30, 0.12, 0.50), line(0.26, 0.14, 0.26, 0.88), rect(0.36, 0.62, 0.26, 0.07), rect(0.36, 0.32, 0.07, 0.30),
    line(0.40, 0.22, 0.70, 0.30), line(0.70, 0.30, 0.70, 0.44), dot(0.70, 0.47, 0.06), base,
  ],
  deltMachine: [
    rect(0.42, 0.20, 0.16, 0.34), rect(0.32, 0.56, 0.36, 0.08), line(0.50, 0.64, 0.50, 0.88),
    bar(0.40, 0.36, 0.14, 0.26), bar(0.60, 0.36, 0.86, 0.26), dot(0.12, 0.25, 0.06), dot(0.88, 0.25, 0.06), base,
  ],
  floorMat: [
    rect(0.06, 0.78, 0.88, 0.07), dot(0.20, 0.44, 0.075), line(0.28, 0.48, 0.84, 0.70), line(0.30, 0.52, 0.30, 0.76),
  ],
  plate: [ring(0.50, 0.50, 0.36), ring(0.50, 0.50, 0.20), dot(0.50, 0.50, 0.07)],
}

export const GLYPH_TITLES: Record<GlyphName, string> = {
  seatedPush: 'Spinta da seduto', pecDeck: 'Butterfly', latPulldown: 'Lat machine', seatedRow: 'Rematore / pulley',
  cableStation: 'Cavi', cableCross: 'Croci ai cavi', benchFlat: 'Panca piana', benchIncline: 'Panca inclinata',
  barbell: 'Bilanciere', dumbbell: 'Manubri', plateLoaded: 'Plate loaded', pullUpBar: 'Sbarra / parallele',
  legPress: 'Pressa', legLever: 'Leva per gambe', squatRack: 'Rack', smithMachine: 'Multipower',
  hyperBench: 'Panca iperestensioni', calfPlatform: 'Pedana calf', abBench: 'Panca addominali',
  romanChair: 'Sedia romana', bodyweight: 'Corpo libero', machine: 'Macchina generica',
  legCurlLying: 'Leg curl sdraiato', legCurlSeated: 'Leg curl seduto', hackSquat: 'Hack squat', squatMachine: 'Squat a leva',
  hipThrust: 'Hip thrust', gluteMachine: 'Glute machine', hipAdductor: 'Adduttori / abduttori', calfSeated: 'Calf seduto',
  ghd: 'Glute ham', kettlebell: 'Kettlebell', trapBar: 'Trap bar', ezBar: 'Bilanciere EZ', landmine: 'Landmine',
  preacherBench: 'Panca Scott', dipStation: 'Parallele', assistMachine: 'Trazioni assistite', rowMachine: 'Rematore machine',
  cableRope: 'Cavo con corda', cablePushdown: 'Cavo alto con barra', sled: 'Slitta', dumbbellBench: 'Panca e manubri',
  inclineDumbbell: 'Panca inclinata e manubri', abMachine: 'Macchina addominali', deltMachine: 'Alzate laterali machine',
  floorMat: 'A terra', plate: 'Disco',
}

// ---- riconoscimento dal nome ---------------------------------------------------------------------------

/** Valutate **in ordine**: le voci più specifiche prima ("Rematore Bilanciere" prima di "Rematore"). */
const KEYWORDS: readonly (readonly [string, GlyphName])[] = [
  // Gambe
  ['leg press', 'legPress'], ['pressa', 'legPress'], ['hack squat', 'legPress'],
  ['leg extension', 'legLever'], ['leg curl', 'legLever'],
  ['calf', 'calfPlatform'],
  ['adductor', 'machine'], ['abductor', 'machine'], ['glute machine', 'machine'],
  ['multipower', 'smithMachine'],
  ['squat', 'squatRack'], ['hip thrust', 'barbell'], ['step up', 'dumbbell'],
  ['affondi', 'dumbbell'], ['bulgarian', 'dumbbell'],
  // Schiena
  ['lat machine', 'latPulldown'],
  ['pulley', 'seatedRow'],
  ['rematore bilanciere', 'barbell'], ['rematore con manubrio', 'dumbbell'],
  ['rematore ai cavi', 'cableStation'], ['rematore t-bar', 'barbell'],
  ['rematore', 'seatedRow'],
  ['trazioni', 'pullUpBar'], ['dip', 'pullUpBar'],
  ['stacco', 'barbell'], ['good morning', 'barbell'],
  ['iperestensioni', 'hyperBench'],
  ['pullover machine', 'machine'],
  // Petto e spalle
  ['plate loaded', 'plateLoaded'],
  ['pectoral', 'pecDeck'], ['butterfly', 'pecDeck'],
  ['croci ai cavi', 'cableCross'],
  ['panca piana', 'benchFlat'], ['panca stretta', 'benchFlat'],
  ['panca inclinata', 'benchIncline'], ['panca declinata', 'benchIncline'],
  ['panca scott', 'benchIncline'],
  ['panca addominali', 'abBench'],
  ['chest press', 'seatedPush'], ['shoulder press', 'seatedPush'],
  ['lento avanti', 'barbell'],
  // Core
  ['sedia romana', 'romanChair'], ['leg raise', 'romanChair'],
  ['crunch machine', 'machine'], ['rotary', 'machine'],
  ['plank', 'bodyweight'], ['piegamenti', 'bodyweight'],
  ['russian', 'bodyweight'], ['ab wheel', 'bodyweight'],
  ['crunch a terra', 'bodyweight'],
  // Generici, per ultimi
  ['cavi', 'cableStation'], ['cable', 'cableStation'],
  ['pushdown', 'cableStation'], ['face pull', 'cableStation'],
  ['kickback', 'cableStation'],
  ['machine', 'machine'],
  ['manubri', 'dumbbell'], ['manubrio', 'dumbbell'],
  ['bilanciere', 'barbell'], ['sbarra', 'pullUpBar'],
  ['alzate', 'dumbbell'], ['scrollate', 'dumbbell'], ['curl', 'barbell'],
  ['press', 'seatedPush'],
]

const GROUP_DEFAULT: Record<MuscleGroup, GlyphName> = {
  petto: 'seatedPush', spalle: 'seatedPush', schiena: 'seatedRow', gambe: 'legLever', braccia: 'dumbbell', core: 'bodyweight',
}

/**
 * Pittogramma dedotto dal nome; se non riconosce nulla, il default del gruppo. Non si mostra più: serve al calcolatore dischi
 * e a fissare, una volta, l'icona dei macchinari personalizzati di prima del catalogo. Le parole chiave restano quelle di
 * allora apposta, così quei macchinari tengono l'icona che avevano.
 */
export function suggestedGlyph(name: string, group: MuscleGroup): GlyphName {
  const needle = name.toLowerCase()
  for (const [key, glyph] of KEYWORDS) if (needle.includes(key)) return glyph
  return GROUP_DEFAULT[group]
}

export const isGlyph = (g: string | undefined): g is GlyphName => (GLYPHS as readonly string[]).includes(g ?? '')

/**
 * L'icona da mostrare: quella dell'attrezzo del catalogo; per un macchinario personalizzato quella scelta a mano,
 * altrimenti nessuna. `catalogGlyph` è il pittogramma della voce `e.catalog` (undefined se il catalogo non è ancora caricato).
 */
export const glyphOf = (e: { glyph: string; catalog: string }, catalogGlyph: string | undefined): GlyphName | null =>
  e.catalog && isGlyph(catalogGlyph) ? catalogGlyph : isGlyph(e.glyph) ? e.glyph : null

// ---- SVG ------------------------------------------------------------------------------------------------

const f = (n: number) => +n.toFixed(4)

/** Markup interno per un `<svg viewBox="0 0 1 1" color="…">`: usa `currentColor`. */
export function glyphSvg(glyph: GlyphName): string {
  return SHAPES[glyph]
    .map((s) => {
      switch (s[0]) {
        case 'line': return `<line x1="${s[1]}" y1="${s[2]}" x2="${s[3]}" y2="${s[4]}" stroke="currentColor" stroke-width="0.055" stroke-linecap="round"/>`
        case 'bar': return `<line x1="${s[1]}" y1="${s[2]}" x2="${s[3]}" y2="${s[4]}" stroke="currentColor" stroke-width="0.1" stroke-linecap="round"/>`
        case 'rect': { const r = f(Math.min(s[3], s[4]) * 0.35); return `<rect x="${s[1]}" y="${s[2]}" width="${s[3]}" height="${s[4]}" rx="${r}" fill="currentColor"/>` }
        case 'dot': return `<circle cx="${s[1]}" cy="${s[2]}" r="${s[3]}" fill="currentColor"/>`
        case 'ring': return `<circle cx="${s[1]}" cy="${s[2]}" r="${s[3]}" fill="none" stroke="currentColor" stroke-width="0.055"/>`
      }
    })
    .join('')
}
