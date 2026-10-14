// Valori derivati per le schermate, nell'unità scelta (kg o lb): riscaldamento, prossimo carico, delta.
// Il calcolo sta in common/domain.ts; qui si portano i kg sulla griglia dell'unità e si ripassa ai kg alla fine.

import { displayStep, nextLoad, progressionFor, toDisplay, warmupPlan, round2, type Suggestion, type WarmupStep } from '@common/domain.ts'
import type { Ex, Model } from './types.ts'

/** Il passo del macchinario nell'unità scelta. */
export const stepOf = (ex: { incrementStep: number }, m: Model): number => displayStep(ex.incrementStep, m.settings.unit)

export function warmupFor(ex: Ex, m: Model): WarmupStep[] {
	return warmupPlan(ex.best ? round2(toDisplay(ex.best.weight, m.settings.unit)) : null, stepOf(ex, m))
}

/** Doppia progressione sull'ultima sessione conclusa; pesi nell'unità scelta. */
export function suggestionFor(ex: Ex, m: Model): Suggestion | null {
	if (!m.settings.progressionEnabled) return null
	const unit = m.settings.unit
	const range: [number, number] = [m.settings.repRangeMin, Math.max(m.settings.repRangeMin, m.settings.repRangeMax)]
	const sets = ex.sets.map((s) => {
		const wk = m.wkById.get(s.workout)!
		return { weight: round2(toDisplay(s.weight, unit)), reps: s.reps, isWarmup: s.isWarmup, workout: s.workout, workoutDate: wk.dateMs, workoutEnded: !wk.isActive }
	})
	return progressionFor(sets, stepOf(ex, m), range)
}
export { nextLoad }

/** Il miglior carico registrato prima di una certa data (per i badge di variazione). */
export const bestWeightBefore = (ex: Ex, ms: number): number | null => {
	let best: number | null = null
	for (const r of ex.records) if (r.date < ms && (best === null || r.weight > best)) best = r.weight
	return best
}

/** +% rispetto al miglior carico precedente alla data del massimale attuale, solo se è salito. */
export function deltaOf(ex: Ex): number | null {
	if (!ex.best) return null
	const prev = bestWeightBefore(ex, ex.best.date)
	return prev && ex.best.weight > prev ? ((ex.best.weight - prev) / prev) * 100 : null
}
