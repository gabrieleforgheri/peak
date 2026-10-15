// Documenti sincronizzati: tipi, validatori (confine di fiducia del server) e utilità di data/uuid.
// Solo sintassi cancellabile: gira sia nel server (Node, type stripping) sia nel client (Vite).

export const MUSCLE_GROUPS = ['petto', 'schiena', 'gambe', 'spalle', 'braccia', 'core'] as const
export type MuscleGroup = (typeof MUSCLE_GROUPS)[number]

export const GLYPHS = [
  'seatedPush', 'pecDeck', 'latPulldown', 'seatedRow', 'cableStation', 'cableCross', 'benchFlat',
  'benchIncline', 'barbell', 'dumbbell', 'plateLoaded', 'pullUpBar', 'legPress', 'legLever',
  'squatRack', 'smithMachine', 'hyperBench', 'calfPlatform', 'abBench', 'romanChair', 'bodyweight', 'machine',
] as const
export type GlyphName = (typeof GLYPHS)[number]

export const KINDS = ['setting', 'exercise', 'record', 'workout', 'set', 'bodyweight', 'template'] as const
export type Kind = (typeof KINDS)[number]

// ---- date e uuid -------------------------------------------------------------------------

/** Tronca ai secondi interi: stesso valore in TS e in Swift, e nei backup. */
export const floorSec = (ms: number): number => Math.floor(ms / 1000) * 1000

/** `YYYY-MM-DDTHH:MM:SSZ`, senza millisecondi (il decoder `.iso8601` di Swift li rifiuta). */
export const isoSec = (ms: number): string => new Date(floorSec(ms)).toISOString().replace('.000Z', 'Z')

const ISO_RE = /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?Z$/
const MIN_MS = Date.UTC(2000, 0, 1)
const MAX_MS = Date.UTC(2100, 0, 1)

/** Accetta con o senza millisecondi (backup vecchi), restituisce i ms interi o null. */
export function parseIso(value: unknown): number | null {
  if (typeof value !== 'string' || !ISO_RE.test(value)) return null
  const ms = Date.parse(value)
  return Number.isFinite(ms) && ms >= MIN_MS && ms <= MAX_MS ? floorSec(ms) : null
}

const UUID_RE = /^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$/

/** Maiuscolo, come li scrive Swift; null se non è un uuid. */
export function normUuid(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const up = value.toUpperCase()
  return UUID_RE.test(up) ? up : null
}

export const newUuid = (): string => crypto.randomUUID().toUpperCase()

// ---- tipi dei documenti --------------------------------------------------------------------

export interface ExerciseDoc {
  name: string; muscleGroup: MuscleGroup; notes: string; incrementStep: number; sortIndex: number
  isArchived: boolean; createdAt: string; glyph: '' | GlyphName; photo: string; photoOffset: number
  isUnilateral: boolean; restSeconds: number
  /** Id della voce del catalogo (vedi `catalog.json`); '' = esercizio personalizzato, fuori dalle classifiche. */
  catalog: string
  /** Nascosto agli amici. */
  hidden: boolean
}
export interface RecordDoc { exercise: string; date: string; weight: number; reps: number; note: string }
/** Una riga di scheda: sets/reps 0 = non indicato; weight in kg, null = si mostra l'ultimo usato. */
export interface TemplateItem { exercise: string; sets: number; repsMin: number; repsMax: number; weight: number | null }
export interface TemplateDoc { name: string; items: TemplateItem[]; sortIndex: number; createdAt: string; hidden: boolean }
/** `template`: la scheda da cui è partito (uuid o ''); `plan`: copia delle sue righe, poi modificata in sessione. */
export interface WorkoutDoc {
  date: string; endedAt: string | null; note: string; name: string; focus: string; template: string; plan: TemplateItem[]
}
export interface SetDoc {
  workout: string; exercise: string | null; weight: number; reps: number; rpe: number | null
  isWarmup: boolean; order: number; createdAt: string; noRecord: boolean
}
export interface BodyWeightDoc { date: string; weight: number; fromHealth: boolean }
export interface SettingDoc { value: unknown }

export interface DocDataMap {
  exercise: ExerciseDoc; record: RecordDoc; workout: WorkoutDoc; set: SetDoc
  bodyweight: BodyWeightDoc; setting: SettingDoc; template: TemplateDoc
}

// ---- impostazioni condivise ----------------------------------------------------------------

/** Le chiavi sincronizzate, con il loro valore predefinito. Il Watch, Salute ecc. restano per dispositivo. */
export const SETTING_DEFAULTS = {
  unit: 'kg', accent: 'lime',
  restTimerEnabled: true, restSeconds: 120,
  progressionEnabled: true, repRangeMin: 8, repRangeMax: 12,
  plateCalculatorEnabled: true, barWeight: 20, availablePlates: [25, 20, 15, 10, 5, 2.5, 1.25],
} as const
export type SettingKey = keyof typeof SETTING_DEFAULTS
/** Impostazioni tolte nella 3.0: i client vecchi le mandano ancora; il server le ignora senza segnalarle come errori. */
export const LEGACY_SETTINGS: readonly string[] = ['formula', 'templateSets', 'templateReps', 'templateWeight', 'lastExportDate']

export const ACCENTS = ['lime', 'ember', 'ice', 'violet', 'coral'] as const

const num = (v: unknown, min: number, max: number): number | null =>
  typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max ? v : null
const int = (v: unknown, min: number, max: number): number | null => {
  const n = num(v, min, max)
  return n !== null && Number.isInteger(n) ? n : null
}
const str = (v: unknown, max: number, min = 0): string | null =>
  typeof v === 'string' && v.length >= min && v.length <= max ? v : null

function cleanSetting(key: string, v: unknown): unknown {
  switch (key) {
    case 'unit': return v === 'kg' || v === 'lb' ? v : undefined
    case 'accent': return (ACCENTS as readonly unknown[]).includes(v) ? v : undefined
    case 'restTimerEnabled': case 'progressionEnabled': case 'plateCalculatorEnabled':
      return typeof v === 'boolean' ? v : undefined
    case 'restSeconds': return int(v, 15, 600) ?? undefined
    case 'repRangeMin': case 'repRangeMax': return int(v, 1, 30) ?? undefined
    case 'barWeight': return num(v, 0, 100) ?? undefined
    case 'availablePlates':
      return Array.isArray(v) && v.length <= 20 && v.every((p) => num(p, 0.01, 100) !== null) ? v : undefined
    default: return undefined
  }
}

// ---- validatori ---------------------------------------------------------------------------

export type Parsed<T> = { data: T } | { error: string }
const fail = (field: string): { error: string } => ({ error: field })

/** Ordine canonico dei gruppi in `focus`, vuoto = nessun filtro. */
export function cleanFocus(raw: unknown): string | null {
  if (typeof raw !== 'string' || raw.length > 64) return null
  const parts = raw.split(',').filter(Boolean)
  if (!parts.every((p) => (MUSCLE_GROUPS as readonly string[]).includes(p))) return null
  return MUSCLE_GROUPS.filter((g) => parts.includes(g)).join(',')
}

/** Forma di un id del catalogo (slug inglese). */
export const CATALOG_ID_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/

export const MAX_ITEMS = 60

/** Righe di scheda/piano: null se qualcosa non torna. */
export function cleanItems(raw: unknown): TemplateItem[] | null {
  if (!Array.isArray(raw) || raw.length > MAX_ITEMS) return null
  const out: TemplateItem[] = []
  for (const r of raw) {
    if (typeof r !== 'object' || r === null) return null
    const o = r as Record<string, unknown>
    const exercise = normUuid(o.exercise)
    const sets = int(o.sets ?? 0, 0, 20)
    const repsMin = int(o.repsMin ?? 0, 0, 100)
    const repsMax = int(o.repsMax ?? 0, 0, 100)
    const weight = o.weight === null || o.weight === undefined ? null : num(o.weight, 0, 2000)
    if (!exercise || sets === null || repsMin === null || repsMax === null || repsMin > repsMax) return null
    if (o.weight != null && weight === null) return null
    out.push({ exercise, sets, repsMin, repsMax, weight })
  }
  return out
}

/**
 * Valida e ripulisce un documento: tiene solo i campi noti, con tipo e intervallo giusti.
 * `uuid` è il nome della chiave per i setting, altrimenti un uuid (già normalizzato).
 */
export function parseDoc(kind: Kind, uuid: string, d: unknown): Parsed<DocDataMap[Kind]> {
  if (typeof d !== 'object' || d === null || Array.isArray(d)) return fail('data')
  const o = d as Record<string, unknown>
  switch (kind) {
    case 'setting': {
      const value = cleanSetting(uuid, o.value)
      return value === undefined ? fail('value') : { data: { value } }
    }
    case 'exercise': {
      const name = str(o.name, 80, 1)?.trim()
      if (!name) return fail('name')
      if (!(MUSCLE_GROUPS as readonly unknown[]).includes(o.muscleGroup)) return fail('muscleGroup')
      const notes = str(o.notes ?? '', 2000)
      const incrementStep = num(o.incrementStep, 0.01, 100)
      const sortIndex = int(o.sortIndex ?? 0, -1e6, 1e6)
      const createdAt = parseIso(o.createdAt)
      const glyph = o.glyph ?? ''
      if (glyph !== '' && !(GLYPHS as readonly unknown[]).includes(glyph)) return fail('glyph')
      const photo = o.photo ?? ''
      if (photo !== '' && !(typeof photo === 'string' && /^[0-9a-f]{64}$/.test(photo))) return fail('photo')
      const photoOffset = num(o.photoOffset ?? 0, -1, 1)
      const restSeconds = int(o.restSeconds ?? 0, 0, 3600)
      const catalog = o.catalog ?? ''
      if (catalog !== '' && !(typeof catalog === 'string' && catalog.length <= 80 && CATALOG_ID_RE.test(catalog))) return fail('catalog')
      if (notes === null) return fail('notes')
      if (incrementStep === null) return fail('incrementStep')
      if (sortIndex === null) return fail('sortIndex')
      if (createdAt === null) return fail('createdAt')
      if (photoOffset === null) return fail('photoOffset')
      if (restSeconds === null) return fail('restSeconds')
      return {
        data: {
          name, muscleGroup: o.muscleGroup as MuscleGroup, notes, incrementStep, sortIndex,
          isArchived: o.isArchived === true, createdAt: isoSec(createdAt), glyph: glyph as ExerciseDoc['glyph'],
          photo: photo as string, photoOffset, isUnilateral: o.isUnilateral === true, restSeconds,
          catalog: catalog as string, hidden: o.hidden === true,
        },
      }
    }
    case 'record': {
      const exercise = normUuid(o.exercise)
      const date = parseIso(o.date)
      const weight = num(o.weight, 0.01, 2000)
      const reps = int(o.reps, 1, 1000)
      const note = str(o.note ?? '', 500)
      if (!exercise) return fail('exercise')
      if (date === null) return fail('date')
      if (weight === null) return fail('weight')
      if (reps === null) return fail('reps')
      if (note === null) return fail('note')
      return { data: { exercise, date: isoSec(date), weight, reps, note } }
    }
    case 'workout': {
      const date = parseIso(o.date)
      const endedAt = o.endedAt === null || o.endedAt === undefined ? null : parseIso(o.endedAt)
      const note = str(o.note ?? '', 2000)
      const name = str(o.name ?? '', 80)
      const focus = cleanFocus(o.focus ?? '')
      const template = o.template == null || o.template === '' ? '' : normUuid(o.template)
      const plan = cleanItems(o.plan ?? [])
      if (date === null) return fail('date')
      if (o.endedAt != null && endedAt === null) return fail('endedAt')
      if (note === null) return fail('note')
      if (name === null) return fail('name')
      if (focus === null) return fail('focus')
      if (template === null) return fail('template')
      if (plan === null) return fail('plan')
      return { data: { date: isoSec(date), endedAt: endedAt === null ? null : isoSec(endedAt), note, name, focus, template, plan } }
    }
    case 'set': {
      const workout = normUuid(o.workout)
      const exercise = o.exercise === null || o.exercise === undefined ? null : normUuid(o.exercise)
      const weight = num(o.weight, 0, 2000)
      const reps = int(o.reps, 0, 1000)
      const rpe = o.rpe === null || o.rpe === undefined ? null : num(o.rpe, 1, 10)
      const order = int(o.order ?? 0, 0, 1e6)
      const createdAt = parseIso(o.createdAt)
      if (!workout) return fail('workout')
      if (o.exercise != null && exercise === null) return fail('exercise')
      if (weight === null) return fail('weight')
      if (reps === null) return fail('reps')
      if (o.rpe != null && rpe === null) return fail('rpe')
      if (order === null) return fail('order')
      if (createdAt === null) return fail('createdAt')
      return {
        data: {
          workout, exercise, weight, reps, rpe, isWarmup: o.isWarmup === true, order,
          createdAt: isoSec(createdAt), noRecord: o.noRecord === true,
        },
      }
    }
    case 'template': {
      const name = str(o.name, 80, 1)?.trim()
      const items = cleanItems(o.items ?? [])
      const sortIndex = int(o.sortIndex ?? 0, -1e6, 1e6)
      const createdAt = parseIso(o.createdAt)
      if (!name) return fail('name')
      if (items === null) return fail('items')
      if (sortIndex === null) return fail('sortIndex')
      if (createdAt === null) return fail('createdAt')
      return { data: { name, items, sortIndex, createdAt: isoSec(createdAt), hidden: o.hidden === true } }
    }
    case 'bodyweight': {
      const date = parseIso(o.date)
      const weight = num(o.weight, 20, 400)
      if (date === null) return fail('date')
      if (weight === null) return fail('weight')
      return { data: { date: isoSec(date), weight, fromHealth: o.fromHealth === true } }
    }
  }
}

/** Documenti salvati prima delle schede: i campi nuovi mancano, qui prendono il valore di partenza. */
export function upgradeDoc<T>(kind: Kind, data: T): T {
  if (!data) return data
  if (kind === 'workout') return { template: '', plan: [], ...data } as T
  if (kind === 'exercise') return { catalog: '', hidden: false, ...data } as T
  if (kind === 'template') return { hidden: false, ...data } as T
  return data
}

/** Campi aggiunti dopo la 2.1: un client vecchio non li manda, e il server non deve cancellarli. */
export const KEEP_IF_ABSENT: Partial<Record<Kind, readonly string[]>> = { exercise: ['catalog', 'hidden'], template: ['hidden'] }

/** Chiave unica di un documento, per mappe in memoria. */
export const docKey = (kind: Kind, uuid: string): string => `${kind}:${uuid}`

/** Un documento vivo, nella forma usata dalle funzioni pure (records, backup, stats). */
export interface LiveDoc<K extends Kind = Kind> { kind: K; uuid: string; data: DocDataMap[K] }

/** Raggruppa i documenti vivi per tipo. */
export function split(docs: Iterable<LiveDoc>) {
  const out = {
    exercises: [] as LiveDoc<'exercise'>[], records: [] as LiveDoc<'record'>[], workouts: [] as LiveDoc<'workout'>[],
    sets: [] as LiveDoc<'set'>[], bodyweights: [] as LiveDoc<'bodyweight'>[], settings: [] as LiveDoc<'setting'>[],
    templates: [] as LiveDoc<'template'>[],
  }
  for (const d of docs) {
    if (d.kind === 'exercise') out.exercises.push(d as LiveDoc<'exercise'>)
    else if (d.kind === 'record') out.records.push(d as LiveDoc<'record'>)
    else if (d.kind === 'workout') out.workouts.push(d as LiveDoc<'workout'>)
    else if (d.kind === 'set') out.sets.push(d as LiveDoc<'set'>)
    else if (d.kind === 'bodyweight') out.bodyweights.push(d as LiveDoc<'bodyweight'>)
    else if (d.kind === 'template') out.templates.push(d as LiveDoc<'template'>)
    else out.settings.push(d as LiveDoc<'setting'>)
  }
  return out
}
