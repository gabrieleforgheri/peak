// Genera common/catalog.ts dal catalogo Swift. Una tantum: il Swift resta la fonte finché l'app iPhone non cambia.
import { readFileSync, writeFileSync } from 'node:fs'
const src = readFileSync('Massimali/Model/SeedCatalog.swift', 'utf8')
const re = /Entry\(name: "((?:[^"\\]|\\.)*)", group: \.(\w+), step: ([\d.]+)(?:,\s*notes: "((?:[^"\\]|\\.)*)")?\)/g
const rows = [...src.matchAll(re)].map(([, name, group, step, notes]) => ({ name, group, step: Number(step), notes: notes ?? '' }))
if (rows.length !== 98) throw new Error(`attese 98 voci, trovate ${rows.length}`)
const lines = rows.map((r) => '  ' + JSON.stringify(r)).join(',\n')
writeFileSync('common/catalog.ts', `// Generato da scripts/gen-catalog.mjs dal catalogo Swift (98 voci). Non modificare a mano.\nimport type { MuscleGroup } from './docs.ts'\n\nexport interface CatalogEntry { name: string; group: MuscleGroup; step: number; notes: string }\n\nexport const CATALOG: CatalogEntry[] = [\n${lines}\n]\n`)
console.log(rows.length)
