// Il catalogo degli esercizi: `catalog.json` è la fonte (id stabili, mai cambiati né riusati). Qui solo i tipi e la ricerca,
// così il client carica il JSON a parte e solo quando serve.
import type { GlyphName, MuscleGroup } from './docs.ts'
export { CATALOG_ID_RE } from './docs.ts'

export interface CatalogEntry {
  /** Slug inglese, per sempre lo stesso: è ciò che fa combaciare gli esercizi di persone diverse. */
  id: string
  it: string
  en: string
  alias: string[]
  group: MuscleGroup
  /** Incremento tipico del carico, in kg. */
  step: number
  glyph: GlyphName
  notes: string
}

/** Minuscole, senza accenti né punteggiatura, spazi singoli: "Leg Press 45°" → "leg press 45". */
export const norm = (s: string): string =>
  s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()

const names = (e: CatalogEntry): string[] => [e.it, e.en, ...e.alias].map(norm)

/** La voce che ha esattamente questo nome (italiano, inglese o sinonimo), se c'è. Serve a collegare gli esercizi che esistono già. */
export function matchByName(list: readonly CatalogEntry[], name: string): CatalogEntry | null {
  const n = norm(name)
  if (!n) return null
  return list.find((e) => norm(e.it) === n) ?? list.find((e) => names(e).includes(n)) ?? null
}

/**
 * Ricerca per più parole, in qualsiasi ordine: ognuna deve comparire in un nome o sinonimo. Prima le voci il cui nome inizia
 * con la ricerca, poi quelle con una parola che inizia così, poi il resto; a parità, l'ordine del catalogo.
 */
export function searchCatalog(list: readonly CatalogEntry[], query: string, limit = 40): CatalogEntry[] {
  const q = norm(query)
  if (!q) return list.slice(0, limit)
  const words = q.split(' ')
  const scored: { e: CatalogEntry; score: number; i: number }[] = []
  list.forEach((e, i) => {
    const hay = names(e)
    if (!words.every((w) => hay.some((h) => h.includes(w)))) return
    const best = hay.some((h) => h.startsWith(q)) ? 0 : hay.some((h) => h.split(' ').some((x) => x.startsWith(words[0]))) ? 1 : 2
    scored.push({ e, score: best, i })
  })
  return scored.sort((a, b) => a.score - b.score || a.i - b.i).slice(0, limit).map((x) => x.e)
}
