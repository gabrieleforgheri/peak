// Formattatori numerici (Utils/Formatters.swift). Le date relative ("Oggi", "Ieri") vivono nel client: servono le traduzioni.

import { toDisplay, type Unit } from './domain.ts'

const nf = (locale: string, min: number, max: number) => new Intl.NumberFormat(locale, { minimumFractionDigits: min, maximumFractionDigits: max })

/** Peso nell'unità scelta, fino a due decimali: un disco da 1,25 resta 1,25. */
export function weight(kg: number, unit: Unit, locale = 'it', symbol = true): string {
  const n = nf(locale, 0, 2).format(toDisplay(kg, unit))
  return symbol ? `${n} ${unit}` : n
}

/** Sopra la tonnellata passa a "12,4 t" (o "klb"). */
export function volume(kg: number, unit: Unit, locale = 'it'): string {
  const v = toDisplay(kg, unit)
  return v >= 1000 ? `${nf(locale, 0, 1).format(v / 1000)} ${unit === 'kg' ? 't' : 'klb'}` : `${nf(locale, 0, 0).format(v)} ${unit}`
}

/** "1:30". */
export function clock(seconds: number): string {
  const s = Math.max(0, Math.round(seconds))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** "45 min" o "1 h 20 min". */
export function duration(seconds: number): string {
  const minutes = Math.max(0, Math.round(seconds) / 60 | 0)
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`
}

/** "+4,2%". */
export const delta = (percent: number, locale = 'it'): string => `${percent > 0 ? '+' : ''}${nf(locale, 0, 1).format(percent)}%`

/** "80 kg × 5". */
export const setLine = (kg: number, reps: number, unit: Unit, locale = 'it'): string => `${weight(kg, unit, locale)} × ${reps}`

/** Riga da taccuino: serie consecutive con lo stesso peso raggruppate, ripetizioni unite da "·", gruppi separati da " · ". "71,5 kg 8·6 · 54 kg 8". */
export function notebookLine(sets: readonly { weight: number; reps: number }[], unit: Unit, locale = 'it'): string {
  const groups: { weight: number; reps: number[] }[] = []
  for (const s of sets) {
    const g = groups[groups.length - 1]
    if (g && g.weight === s.weight) g.reps.push(s.reps)
    else groups.push({ weight: s.weight, reps: [s.reps] })
  }
  return groups.map((g) => `${weight(g.weight, unit, locale)} ${g.reps.join('·')}`).join(' · ')
}

/** Raggruppa per mese di calendario (ora locale) mantenendo l'ordine in ingresso: con elementi dal più recente, i mesi vengono dal più recente. `ms` è la data del primo elemento. */
export function byMonth<T>(items: readonly T[], dateOf: (item: T) => number): { key: string; ms: number; items: T[] }[] {
  const groups = new Map<string, { key: string; ms: number; items: T[] }>()
  for (const item of items) {
    const ms = dateOf(item)
    const d = new Date(ms)
    const key = `${d.getFullYear()}-${d.getMonth() + 1}`
    const g = groups.get(key)
    if (g) g.items.push(item)
    else groups.set(key, { key, ms, items: [item] })
  }
  return [...groups.values()]
}
