import type { AnyRecord } from '@common/records.ts'
import type { ExerciseDoc, GlyphName, TemplateDoc, WorkoutDoc } from '@common/docs.ts'
import type { ACCENTS } from '@common/docs.ts'

export interface SetItem {
	uuid: string; workout: string; exercise: string | null; weight: number; reps: number; rpe: number | null
	isWarmup: boolean; order: number; createdAt: number; noRecord: boolean; isPR: boolean
}
export interface Ex extends ExerciseDoc {
	uuid: string
	glyphName: GlyphName
	/** Massimali manuali + automatici, dal più recente. */
	records: AnyRecord[]
	best: AnyRecord | null
	/** Serie degli allenamenti esistenti, dalla più recente. */
	sets: SetItem[]
}
export interface Wk extends WorkoutDoc {
	uuid: string
	dateMs: number
	endedMs: number | null
	isActive: boolean
	sets: SetItem[]
	volume: number
	workingSets: number
	prCount: number
}
export interface Tpl extends TemplateDoc { uuid: string }
export interface Bw { uuid: string; dateMs: number; weight: number; fromHealth: boolean }
export interface Settings {
	unit: 'kg' | 'lb'; formula: 'epley' | 'brzycki'; accent: (typeof ACCENTS)[number]
	restTimerEnabled: boolean; restSeconds: number; progressionEnabled: boolean; repRangeMin: number; repRangeMax: number
	plateCalculatorEnabled: boolean; barWeight: number; availablePlates: number[]; lastExportDate: string | null
	templateSets: boolean; templateReps: boolean; templateWeight: boolean
}
export interface Model {
	exercises: Ex[]
	exById: Map<string, Ex>
	workouts: Wk[]
	wkById: Map<string, Wk>
	templates: Tpl[]
	tplById: Map<string, Tpl>
	bodyweights: Bw[]
	settings: Settings
	hasData: boolean
}
