// Le medaglie sulle schermate: nome, icona, spiegazione, e le medaglie come erano a una certa data (per «cosa hai guadagnato oggi»).

import { oneRepMax } from '@common/domain.ts'
import { medals, tierDates, type Medal, type MedalId } from '@common/medals.ts'
import { weeklyStreak, workoutVolume, type StatWorkout } from '@common/stats.ts'
import { V, W } from './fmt.ts'
import { t } from './i18n.ts'
import type { Model } from './types.ts'

export const MEDAL: Record<MedalId, { icon: string; title: string }> = {
	sessions: { icon: 'dumbbell', title: t('Costanza') }, records: { icon: 'trophy', title: t('Cacciatore di record') },
	streak: { icon: 'flame', title: t('In serie') }, volume: { icon: 'scale', title: t('Tonnellate') },
	strength: { icon: 'bolt', title: t('Forza') }, variety: { icon: 'compass', title: t('Esploratore') }, friends: { icon: 'users', title: t('Squadra') },
}
export const TIERS = [t('da conquistare'), t('Bronzo'), t('Argento'), t('Oro')]
export const fmtMedal = (id: MedalId, v: number): string => id === 'volume' ? V(v) : id === 'strength' ? W(v) : String(Math.round(v))
/** Quanto del livello dopo hai fatto, da 0 a 1 (valore / soglia): è anche quello che si legge accanto, «12 / 25». Oro preso = 1. */
export const medalFrac = (md: Medal): number => md.next === null ? 1 : Math.min(1, md.value / md.next)
/** Prima le più vicine al livello dopo, poi quelle d'oro (a parità resta l'ordine di sempre). */
export const medalOrder = (list: Medal[]): Medal[] => [...list.filter((md) => md.next !== null).sort((a, b) => medalFrac(b) - medalFrac(a)), ...list.filter((md) => md.next === null)]
/** Cosa misura ogni medaglia, in una riga. */
export const MEDAL_INFO: Record<MedalId, string> = {
	sessions: t('Ogni allenamento concluso conta.'), records: t('Ogni massimale battuto conta.'),
	streak: t('Settimane di seguito con almeno un allenamento.'), volume: t('Tutto il peso sollevato, sommato.'),
	strength: t('Il tuo miglior 1RM stimato, su un macchinario.'), variety: t('Quanti macchinari diversi hai usato.'),
	friends: t('Quanti amici hai collegato.'),
}

/**
 * Le medaglie con quello che c'era fino a un certo allenamento (`until`: la sua data; `inclusive`: conta anche lui).
 * Senza `until`, quelle di adesso. Gli amici non cambiano con un allenamento: si passano da fuori.
 */
export function medalsAt(m: Model, friends: number, until?: { ms: number; inclusive: boolean }): Medal[] {
	const cut = (ms: number) => !until || (until.inclusive ? ms <= until.ms : ms < until.ms)
	const done = m.workouts.filter((w) => !w.isActive && cut(w.dateMs))
	const ids = new Set(done.map((w) => w.uuid))
	const stat: StatWorkout[] = done.map((w) => ({ date: w.dateMs, sets: w.sets.map((s) => ({ exercise: s.exercise, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup, singleArm: s.singleArm })) }))
	let records = 0
	let bestOneRm = 0
	let machines = 0
	for (const e of m.exercises) {
		const rs = e.records.filter((r) => cut(r.date))
		const ss = e.sets.filter((s) => ids.has(s.workout) && !s.isWarmup && !s.singleArm)
		records += rs.length
		if (rs.length || e.sets.some((s) => ids.has(s.workout))) machines++
		for (const r of rs) bestOneRm = Math.max(bestOneRm, oneRepMax(r.weight, r.reps))
		for (const s of ss) bestOneRm = Math.max(bestOneRm, oneRepMax(s.weight, s.reps))
	}
	return medals({
		sessions: done.length, records, streak: weeklyStreak(stat, until?.ms), volumeKg: stat.reduce((v, w) => v + workoutVolume(w), 0),
		bestOneRm, machines, friends,
	})
}

const datesCache = new WeakMap<Model, Record<MedalId, (number | null)[]>>()
/**
 * Quando hai preso ogni livello (bronzo, argento, oro; null se non c'è o non si sa), ricavato dalla storia: gli istanti in cui
 * qualcosa è cambiato sono gli allenamenti e i massimali. Si calcola una volta per modello. Gli amici non hanno una data.
 */
export function medalDates(m: Model, friends: number): Record<MedalId, (number | null)[]> {
	let d = datesCache.get(m)
	if (!d) {
		const times = [...m.workouts.filter((w) => !w.isActive).map((w) => w.dateMs), ...m.exercises.flatMap((e) => e.records.map((r) => r.date))]
		d = tierDates(times, (ms) => medalsAt(m, friends, { ms, inclusive: true }), medalsAt(m, friends))
		d.friends = [null, null, null]
		datesCache.set(m, d)
	}
	return d
}
