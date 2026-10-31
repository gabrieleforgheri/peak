// Formattazione con l'unità scelta nelle impostazioni (lo stoccaggio resta in kg).

import * as f from '@common/format.ts'
import type { TemplateItem } from '@common/docs.ts'
import { toDisplay, toKg, round2 } from '@common/domain.ts'
import { getModel } from './store.svelte.ts'
import { locale, t } from './i18n.ts'

/** Quanto è lunga la barra rispetto al primo: 0...1, mai una divisione per zero. */
export const barFraction = (v: number, top: number): number => (top > 0 ? Math.min(1, Math.max(0, v / top)) : 0)
export const unit = () => getModel().settings.unit

/** "82,5 kg" */
export const W = (kg: number, symbol = true): string => f.weight(kg, unit(), locale, symbol)
/** "12,4 t" */
export const V = (kg: number): string => f.volume(kg, unit(), locale)
/** Il peso nell'unità scelta, due decimali: quello che si legge e si digita. */
export const toUnit = (kg: number): number => round2(toDisplay(kg, unit()))
export const fromUnit = (v: number): number => toKg(v, unit())
/** `group` è il gruppo muscolare dell'esercizio: decide se il segno è «1 br» o «1 gb». */
export const setLine = (kg: number, reps: number, singleArm = false, group?: string | null): string => f.setLine(kg, reps, unit(), locale, singleArm, group)
/** «Un braccio» / «1 br» / «2 br», «Una gamba» / «1 gb» / «2 gb» secondo il gruppo. */
export const sideText = (group: string | null | undefined, kind: f.SideKind): string => f.sideText(group, kind, locale)
export const hasSides = f.hasSides
/** "71,5 kg 8·6 · 54 kg 8": la riga del taccuino. */
export const notebookLine = (sets: { weight: number; reps: number; singleArm?: boolean }[], group?: string | null): string => f.notebookLine(sets, unit(), locale, group)
export const delta = (p: number): string => f.delta(p, locale)
/** "2/4 serie · 8–10 rip · 60 kg": solo i campi compilati; senza peso scritto, l'ultimo usato. */
export function targetLine(item: TemplateItem, done: number, lastKg: number | null): string {
	const parts: string[] = []
	if (item.sets) parts.push(t('{0}/{1} serie', done, item.sets))
	if (item.repsMax) parts.push(item.repsMin === item.repsMax ? t('{0} rip', item.repsMax) : t('{0}–{1} rip', item.repsMin, item.repsMax))
	if (item.weight !== null) parts.push(W(item.weight))
	else if (lastKg !== null) parts.push(t('ultimo {0}', W(lastKg)))
	return parts.join(' · ')
}
export const clock = f.clock
export const duration = f.duration

const dayStart = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
const intl = (o: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(locale, o)

export function relDay(ms: number): string {
	const days = Math.round((dayStart(new Date()) - dayStart(new Date(ms))) / 86_400_000)
	if (days === 0) return t('Oggi')
	if (days === 1) return t('Ieri')
	return intl({ weekday: 'short', day: 'numeric', month: 'short' }).format(ms)
}
export const monthYear = (ms: number): string => intl({ month: 'long', year: 'numeric' }).format(ms)
export const dateShort = (ms: number): string => intl({ day: 'numeric', month: 'short' }).format(ms)
export const dateLong = (ms: number): string => intl({ day: 'numeric', month: 'short', year: 'numeric' }).format(ms)
export const dateTime = (ms: number): string => intl({ day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(ms)
export const timeOnly = (ms: number): string => intl({ hour: '2-digit', minute: '2-digit' }).format(ms)

export const GROUP_NAMES: Record<string, string> = {
	get petto() { return t('Petto') }, get schiena() { return t('Schiena') }, get gambe() { return t('Gambe') },
	get spalle() { return t('Spalle') }, get braccia() { return t('Braccia') }, get core() { return t('Core') },
}

/** yyyy-mm-dd locale per <input type="date">. */
export const toDateInput = (ms: number): string => {
	const d = new Date(ms)
	return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
/** Data scelta + ora attuale (un massimale inserito oggi vale "adesso"). */
export const fromDateInput = (v: string): number => {
	const [y, m, d] = v.split('-').map(Number)
	if (!y || !m || !d) return Date.now() // campo vuoto o incompleto
	const now = new Date()
	const same = y === now.getFullYear() && m === now.getMonth() + 1 && d === now.getDate()
	// il server accetta dal 2000 a oggi: fuori da lì (digitato a mano) si riporta dentro
	return same ? now.getTime() : Math.min(now.getTime(), Math.max(Date.UTC(2000, 0, 1, 12), new Date(y, m - 1, d, 12).getTime()))
}

/** Nome breve del giorno della settimana, 0 = lunedì. */
export const weekdayName = (i: number): string => intl({ weekday: 'short' }).format(new Date(2024, 0, 1 + i))
