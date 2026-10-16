// Scrive Peak/Design/MachineGlyphShapes.swift dalle forme di common/glyph.ts: i disegni sono uno solo, per web e iPhone.
// Uso: node scripts/gen-glyphs.ts  (un test in common/ controlla che il file Swift sia aggiornato)
import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { GLYPHS } from '../common/docs.ts'
import { GLYPH_TITLES, SHAPES, type Shape } from '../common/glyph.ts'

export const SWIFT_FILE = fileURLToPath(new URL('../Peak/Design/MachineGlyphShapes.swift', import.meta.url))

const n = (v: number) => String(+v.toFixed(4))
const shape = (s: Shape) => `.${s[0] === 'rect' ? 'solidRect' : s[0]}(${s.slice(1).map((v) => n(v as number)).join(', ')})`

export function swiftSource(): string {
  return `// Generato da scripts/gen-glyphs.ts a partire da common/glyph.ts: non modificarlo a mano.

import CoreGraphics

/// Pittogrammi degli attrezzi, uno per tipo di attrezzo del catalogo, più quelli generici di prima.
enum MachineGlyph: String, Codable, CaseIterable, Identifiable {
${GLYPHS.map((g) => `    case ${g}`).join('\n')}

    var id: String { rawValue }

    var title: String {
        switch self {
${GLYPHS.map((g) => `        case .${g}: return ${JSON.stringify(GLYPH_TITLES[g])}`).join('\n')}
        }
    }

    var shapes: [GlyphShape] {
        switch self {
${GLYPHS.map((g) => `        case .${g}: return [\n${SHAPES[g].map((s) => `            ${shape(s)}`).join(',\n')}\n        ]`).join('\n')}
        }
    }
}
`
}

if (process.argv[1] === fileURLToPath(import.meta.url)) writeFileSync(SWIFT_FILE, swiftSource())
