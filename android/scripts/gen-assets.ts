// Genera i file Android che derivano da dati condivisi con web e iPhone, così non si ricopiano a mano:
//   - domain/MachineGlyph.kt         dai disegni di common/glyph.ts (come scripts/gen-glyphs.ts per Swift)
//   - domain/MuscleId.kt             dai muscoli di common/muscle-info.ts
//   - design/AnatomyShapes.kt        dai contorni di common/anatomy.ts
//   - res/values/strings.xml, res/values-en/strings.xml   da Peak/en.lproj/Localizable.strings + web/src/lib/en.json
//   - res/raw/fx_*.m4a               da Peak/Sounds/
// Uso (dalla radice del repo): node android/scripts/gen-assets.ts
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { GLYPHS } from '../../common/docs.ts'
import { GLYPH_TITLES, SHAPES, type Shape } from '../../common/glyph.ts'
import { ANATOMY, MUSCLE_BOX } from '../../common/anatomy.ts'
import { MUSCLES, MUSCLE_INFO } from '../../common/muscle-info.ts'

const root = (p: string) => fileURLToPath(new URL(`../../${p}`, import.meta.url))
const kt = (p: string) => root(`android/app/src/main/java/com/gabrieleforghieri/peak/${p}`)
const res = (p: string) => root(`android/app/src/main/res/${p}`)
const HEADER = '// Generato da android/scripts/gen-assets.ts: non modificarlo a mano.\n'
const q = (s: string) => JSON.stringify(s).replace(/\$/g, '\\$')

// ---- pittogrammi --------------------------------------------------------------------------------

const num = (v: number) => `${+v.toFixed(4)}f`
const shape = (s: Shape) => `${{ line: 'Line', bar: 'Bar', rect: 'SolidRect', dot: 'Dot', ring: 'Ring' }[s[0]]}(${s.slice(1).map((v) => num(v as number)).join(', ')})`

function glyphs(): string {
  return `${HEADER}
package com.gabrieleforghieri.peak.domain

/** Pittogrammi degli attrezzi: uno per tipo di attrezzo del catalogo, più quelli generici di prima. */
enum class MachineGlyph(val raw: String, val title: String, val shapes: List<GlyphShape>) {
${GLYPHS.map((g) => `    ${g}(${q(g)}, ${q(GLYPH_TITLES[g])}, listOf(\n${SHAPES[g].map((s) => `        ${shape(s)}`).join(',\n')}\n    ))`).join(',\n')};

    companion object {
        fun of(raw: String?): MachineGlyph? = entries.firstOrNull { it.raw == raw }
    }
}
`
}

// ---- figura dei muscoli --------------------------------------------------------------------------

function muscleIds(): string {
  return `${HEADER}
package com.gabrieleforghieri.peak.domain

/** I muscoli che la figura colora. Il raw è l'id di common/muscle-info.ts: mai rinominato, finisce nei documenti. */
enum class MuscleId(val title: String, val titleEn: String, val group: MuscleGroup) {
${MUSCLES.map((m) => `    ${m}(${q(MUSCLE_INFO[m].it)}, ${q(MUSCLE_INFO[m].en)}, MuscleGroup.${MUSCLE_INFO[m].group})`).join(',\n')};

    companion object {
        fun of(raw: String?): MuscleId? = entries.firstOrNull { it.name == raw }
    }
}
`
}

function anatomy(): string {
  const pieces = (view: 'front' | 'back') =>
    ANATOMY[view].map((p) => `        AnatomyPiece(${p.muscle ? `MuscleId.${p.muscle}` : 'null'}, listOf(\n${p.paths.map((d) => `            ${q(d)}`).join(',\n')}\n        ))`).join(',\n')
  const boxes = (['front', 'back'] as const).flatMap((v) =>
    Object.entries(MUSCLE_BOX[v]).map(([m, b]) => `        ${q(`${v}.${m}`)} to doubleArrayOf(${(b as number[]).join(', ')})`)).join(',\n')
  return `${HEADER}
package com.gabrieleforghieri.peak.design

import com.gabrieleforghieri.peak.domain.MuscleId

/** Un pezzo del corpo: il muscolo (null = corpo, sempre grigio) e i suoi contorni come percorsi SVG assoluti. */
class AnatomyPiece(val muscle: MuscleId?, val paths: List<String>)

object AnatomyShapes {
    /** Stesse coordinate del web: 0…724 × 0…1448. */
    const val WIDTH = 724f
    const val HEIGHT = 1448f

    val front: List<AnatomyPiece> = listOf(
${pieces('front')}
    )

    val back: List<AnatomyPiece> = listOf(
${pieces('back')}
    )

    /** [x0, y0, x1, y1] di ogni muscolo in ogni vista: la chiave è "vista.muscolo". */
    val boxes: Map<String, DoubleArray> = mapOf(
${boxes}
    )
}
`
}

// ---- stringhe -----------------------------------------------------------------------------------

/** FNV-1a a 32 bit sui byte UTF-8: lo stesso numero in Kotlin (Strings.slug). */
function fnv(text: string): string {
  let h = 0x811c9dc5
  for (const b of Buffer.from(text, 'utf8')) h = Math.imul(h ^ b, 0x01000193) >>> 0
  return h.toString(16).padStart(8, '0')
}

/** Il nome della risorsa per un testo italiano: t_<parole>_<hash>. Identico a `Strings.slug` in Kotlin. */
export function slug(text: string): string {
  const words = text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 40).replace(/_+$/, '')
  return `t_${words}_${fnv(text)}`
}

/** `%@` `%lld` `%lf` → `%1$s` `%2$d`…; un `%` che non apre un segnaposto → `%%`; `{0}` del web → `%1$s`. */
function android(text: string): string {
  let n = 0
  let out = text.replace(/\{(\d)\}/g, (_, i) => `%${+i + 1}$s`)
  out = out.replace(/%%|%@|%lld|%lf|%d|%s|%/g, (m) => {
    if (m === '%%') return '%%'
    if (m === '%') return '%%'
    n++
    return m === '%lld' || m === '%d' ? `%${n}$d` : m === '%lf' ? `%${n}$f` : `%${n}$s`
  })
  // le virgolette tipografiche restano; quelle dritte e l'apice si proteggono per aapt2
  out = out.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\\/g, '\\\\')
  out = out.replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/\n/g, '\\n')
  return /^[@?]/.test(out) ? `\\${out}` : out
}

/** Legge un .strings: "chiave" = "valore"; con gli escape \" \\ \n. */
function parseStrings(text: string): Map<string, string> {
  const out = new Map<string, string>()
  const un = (s: string) => s.replace(/\\(["\\n])/g, (_, c) => (c === 'n' ? '\n' : c))
  for (const m of text.matchAll(/^"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)";\s*$/gm)) out.set(un(m[1]), un(m[2]))
  return out
}

/** Testi che esistono solo su Android (il guscio dell'app): italiano → inglese. */
const ANDROID_ONLY: Record<string, string> = {
  'In arrivo': 'Coming soon',
  'Questa scheda è in costruzione.': 'This tab is under construction.',
}

function strings(): { it: string; en: string; count: number } {
  const ios = parseStrings(readFileSync(root('Peak/en.lproj/Localizable.strings'), 'utf8'))
  const web = JSON.parse(readFileSync(root('web/src/lib/en.json'), 'utf8')) as Record<string, string>
  // come Lang.swift: Localizable.strings vince su en.json; i testi con «|» (plurali del web) restano fuori
  const table = new Map<string, string>()
  for (const [k, v] of Object.entries(web)) if (!k.includes('|')) table.set(k.replace(/\{\d\}/g, '%@'), v)
  for (const [k, v] of ios) table.set(k, v)
  for (const [k, v] of Object.entries(ANDROID_ONLY)) table.set(k, v)
  // i testi nuovi delle schermate, uno per area: android/texts/<area>.json, {"italiano": "english"} (vedi AREE.md)
  const dir = root('android/texts')
  if (existsSync(dir)) {
    for (const f of readdirSync(dir).filter((n) => n.endsWith('.json')).sort()) {
      for (const [k, v] of Object.entries(JSON.parse(readFileSync(`${dir}/${f}`, 'utf8')) as Record<string, string>)) table.set(k, v)
    }
  }
  // i nomi dei gruppi e delle tessere, usati dal codice con .tr
  const names = new Map<string, string>()
  for (const [k, v] of table) {
    const name = slug(k)
    if (names.has(name) && names.get(name) !== k) throw new Error(`nome di risorsa doppio: ${name}`)
    names.set(name, k)
  }
  const sorted = [...names].sort(([a], [b]) => (a < b ? -1 : 1))
  const file = (value: (k: string) => string) =>
    `<?xml version="1.0" encoding="utf-8"?>\n<!-- Generato da android/scripts/gen-assets.ts: non modificarlo a mano. -->\n<resources>\n    <string name="app_name">Peak</string>\n${sorted
      .map(([name, k]) => `    <string name="${name}">${android(value(k))}</string>`).join('\n')}\n</resources>\n`
  return { it: file((k) => k), en: file((k) => table.get(k)!), count: sorted.length }
}

// ---- scrittura ----------------------------------------------------------------------------------

function main() {
  mkdirSync(kt('design'), { recursive: true })
  mkdirSync(kt('domain'), { recursive: true })
  writeFileSync(kt('domain/MachineGlyph.kt'), glyphs())
  writeFileSync(kt('domain/MuscleId.kt'), muscleIds())
  writeFileSync(kt('design/AnatomyShapes.kt'), anatomy())

  const s = strings()
  mkdirSync(res('values-en'), { recursive: true })
  writeFileSync(res('values/strings.xml'), s.it)
  writeFileSync(res('values-en/strings.xml'), s.en)

  mkdirSync(res('raw'), { recursive: true })
  for (const f of readdirSync(root('Peak/Sounds')).filter((n) => n.endsWith('.m4a'))) copyFileSync(root(`Peak/Sounds/${f}`), res(`raw/fx_${f}`))
  console.log(`${GLYPHS.length} pittogrammi, ${MUSCLES.length} muscoli, ${s.count} testi`)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main()
