// Le medaglie: sette traguardi, ognuno con tre livelli (bronzo, argento, oro). Si ricavano dai numeri che l'app ha già,
// niente da salvare: se cancelli un allenamento, la medaglia si ricalcola.

export type MedalId = 'sessions' | 'records' | 'streak' | 'volume' | 'strength' | 'variety' | 'friends'
export interface MedalInput {
  /** Allenamenti conclusi. */
  sessions: number
  /** Massimali battuti (manuali e da allenamento). */
  records: number
  /** Settimane di fila con almeno un allenamento. */
  streak: number
  /** Volume totale sollevato, in kg. */
  volumeKg: number
  /** Il miglior 1RM stimato fra tutti i macchinari, in kg. */
  bestOneRm: number
  /** Macchinari con almeno una serie o un massimale. */
  machines: number
  friends: number
}
export interface Medal {
  id: MedalId
  value: number
  /** Soglie di bronzo, argento, oro. */
  tiers: [number, number, number]
  /** 0 = ancora niente, 1 bronzo, 2 argento, 3 oro. */
  tier: 0 | 1 | 2 | 3
  /** Quanto manca al prossimo livello, da 0 a 1 (1 = oro già preso). */
  progress: number
  next: number | null
}

const DEFS: { id: MedalId; key: keyof MedalInput; tiers: [number, number, number] }[] = [
  { id: 'sessions', key: 'sessions', tiers: [5, 25, 100] },
  { id: 'records', key: 'records', tiers: [1, 10, 50] },
  { id: 'streak', key: 'streak', tiers: [2, 6, 12] },
  { id: 'volume', key: 'volumeKg', tiers: [1_000, 25_000, 250_000] },
  { id: 'strength', key: 'bestOneRm', tiers: [60, 100, 140] },
  { id: 'variety', key: 'machines', tiers: [5, 12, 25] },
  { id: 'friends', key: 'friends', tiers: [1, 3, 10] },
]

export function medals(input: MedalInput): Medal[] {
  return DEFS.map(({ id, key, tiers }) => {
    const value = input[key]
    const tier = (tiers.filter((t) => value >= t).length) as Medal['tier']
    const list: readonly number[] = tiers
    const next = tier < 3 ? list[tier]! : null
    const base = tier === 0 ? 0 : list[tier - 1]!
    const progress = next === null ? 1 : Math.max(0, Math.min(1, (value - base) / (next - base)))
    return { id, value, tiers, tier, progress, next }
  })
}

/** Quante medaglie (livelli) hai preso sul massimo possibile. */
export const medalTotals = (list: Medal[]): { earned: number; total: number } =>
  ({ earned: list.reduce((n, m) => n + m.tier, 0), total: list.length * 3 })
