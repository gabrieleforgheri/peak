// Le medaglie sulle schermate: nome, icona, spiegazione, e le medaglie come erano a una certa data (per «cosa hai guadagnato oggi»).

import { oneRepMax } from '@common/domain.ts'
import { medals, type Medal, type MedalId } from '@common/medals.ts'
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
/** Sotto la medaglia: quanto manca, o il livello preso. */
export const medalSub = (md: Medal): string => md.next === null ? TIERS[3]! : md.tier === 0 ? `${fmtMedal(md.id, md.value)} / ${fmtMedal(md.id, md.next)}` : `${TIERS[md.tier]} · ${fmtMedal(md.id, md.value)}/${fmtMedal(md.id, md.next)}`
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
	const stat: StatWorkout[] = done.map((w) => ({ date: w.dateMs, sets: w.sets.map((s) => ({ exercise: s.exercise, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup })) }))
	let records = 0
	let bestOneRm = 0
	let machines = 0
	for (const e of m.exercises) {
		const rs = e.records.filter((r) => cut(r.date))
		const ss = e.sets.filter((s) => ids.has(s.workout) && !s.isWarmup)
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
