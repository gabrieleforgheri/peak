// Scrive in common/catalog.json i campi `muscles` (i muscoli principali) e `view` (vista forzata, '' = automatica)
// dalla tabella scripts/catalog-muscles.txt. Ogni voce del catalogo deve avere la sua riga, e ogni riga una voce.
// Uso: node scripts/gen-catalog-muscles.ts   (un test in common/ controlla che il catalogo sia aggiornato)
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { isMuscle, type Muscle } from '../common/muscle-info.ts'

export const CATALOG_FILE = fileURLToPath(new URL('../common/catalog.json', import.meta.url))
export const TABLE_FILE = fileURLToPath(new URL('./catalog-muscles.txt', import.meta.url))

export interface MuscleRow { id: string; muscles: Muscle[]; view: '' | 'front' | 'back' }

/** Legge la tabella: una riga per voce, `id: muscolo muscolo view=back`. Lancia un errore alla prima riga sbagliata. */
export function parseTable(text: string): MuscleRow[] {
  return text.split('\n').map((raw) => raw.trim()).filter((line) => line && !line.startsWith('#')).map((line, n) => {
    const colon = line.indexOf(':')
    if (colon < 0) throw new Error(`riga ${n + 1} senza ':' → ${line}`)
    const id = line.slice(0, colon).trim()
    const tokens = line.slice(colon + 1).trim().split(/\s+/)
    const views = tokens.filter((t) => t.startsWith('view='))
    const muscles = tokens.filter((t) => !t.startsWith('view='))
    if (views.length > 1) throw new Error(`${id}: più di una vista`)
    const view = views.length ? views[0].slice(5) : ''
    if (view !== '' && view !== 'front' && view !== 'back') throw new Error(`${id}: vista sconosciuta ${view}`)
    const bad = muscles.filter((m) => !isMuscle(m))
    if (bad.length) throw new Error(`${id}: muscoli sconosciuti ${bad.join(', ')}`)
    if (!muscles.length) throw new Error(`${id}: nessun muscolo`)
    return { id, muscles: muscles as Muscle[], view }
  })
}

/** La tabella contro il catalogo: stesse voci, niente doppioni. */
export function check(rows: MuscleRow[], entryIds: string[]): string[] {
  const problems: string[] = []
  const seen = new Set<string>()
  for (const r of rows) {
    if (seen.has(r.id)) problems.push(`doppio: ${r.id}`)
    seen.add(r.id)
  }
  for (const id of entryIds) if (!seen.has(id)) problems.push(`manca nella tabella: ${id}`)
  const known = new Set(entryIds)
  for (const r of rows) if (!known.has(r.id)) problems.push(`non è nel catalogo: ${r.id}`)
  return problems
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const entries = JSON.parse(readFileSync(CATALOG_FILE, 'utf8')) as Record<string, unknown>[]
  const rows = parseTable(readFileSync(TABLE_FILE, 'utf8'))
  const problems = check(rows, entries.map((e) => e.id as string))
  if (problems.length) {
    console.error(problems.join('\n'))
    process.exit(1)
  }
  const byId = new Map(rows.map((r) => [r.id, r]))
  const out = entries.map((e) => {
    const r = byId.get(e.id as string)!
    const { muscles: _old, view: _oldView, ...rest } = e
    return { ...rest, muscles: r.muscles, view: r.view }
  })
  writeFileSync(CATALOG_FILE, `[\n${out.map((e) => JSON.stringify(e)).join(',\n')}\n]\n`)
  console.log(`catalogo: ${out.length} voci con muscoli`)
}
