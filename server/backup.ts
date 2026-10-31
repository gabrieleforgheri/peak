// Backup JSON v1 (formato dell'app iPhone: Store/BackupDTO.swift) ⇄ documenti sincronizzati.
// Compatibilità con l'app: uuid maiuscoli, date ISO a secondi interi, chiavi opzionali omesse se assenti.
// I record automatici e i flag PR non si importano: si ricalcolano (common/records.ts).

import { createHash } from 'node:crypto'
import {
  CATALOG_ID_RE, cleanFocus, isoSec, MAX_ITEMS, normUuid, parseDoc, parseIso, split, MUSCLE_GROUPS,
  type DocDataMap, type ExerciseDoc, type Kind, type LiveDoc, type MuscleGroup, type TemplateItem, isTemplateIcon, cleanMuscles } from '../common/docs.ts'
import { deriveRecords, type RecManual, type RecSet } from '../common/records.ts'
import { EPSILON, LIMBS, type Limbs } from '../common/domain.ts'
import { matchByName } from '../common/catalog.ts'
import { isGlyph, suggestedGlyph } from '../common/glyph.ts'
import { CATALOG } from './catalog.ts'

export const BACKUP_VERSION = 1
const MAX_PHOTO = 1_000_000

export interface RecordDTO { uuid: string; date: string; weight: number; reps: number; isEstimated: boolean; note: string; singleArm?: boolean }
export interface SetDTO {
  uuid: string; exerciseUUID?: string; weight: number; reps: number; rpe?: number; isWarmup: boolean
  order: number; createdAt: string; isPersonalRecord: boolean; excludedFromRecords?: boolean
  /** Un braccio solo; assente nei backup vecchi = due braccia. */
  singleArm?: boolean
}
export interface ExerciseDTO {
  uuid: string; name: string; muscleGroup: string; notes: string; incrementStep: number; sortIndex: number
  isArchived: boolean; createdAt: string; glyph?: string; photo?: string; photoOffset?: number
  isUnilateral?: boolean; restSeconds?: number; catalogID?: string; isHidden?: boolean; isCustom?: boolean
  /** La figura dei muscoli: solo per i macchinari personalizzati; assenti nei backup vecchi. */
  muscles?: string[]; view?: string; barWeight?: number; weightDisplay?: string; plateLoaded?: boolean; perSide?: boolean; limbs?: string; records: RecordDTO[]
}
/** Riga di scheda; `weight` assente = si mostra l'ultimo usato. */
export interface ItemDTO { exerciseUUID: string; sets: number; repsMin: number; repsMax: number; weight?: number }
export interface TemplateDTO { uuid: string; name: string; sortIndex: number; createdAt: string; isHidden?: boolean; icon?: string; programID?: string; weekday?: number; items: ItemDTO[] }
export interface ProgramDTO { uuid: string; name: string; sortIndex: number; createdAt: string; useDays?: boolean }
export interface WorkoutDTO {
  uuid: string; date: string; endedAt?: string; note: string; focus: string; name: string; sets: SetDTO[]
  template?: string; plan?: ItemDTO[]
}
export interface BodyWeightDTO { uuid: string; date: string; weight: number; fromHealth: boolean }
export interface BackupFile {
  version: number; exportedAt: string; exercises: ExerciseDTO[]; workouts: WorkoutDTO[]; bodyWeights?: BodyWeightDTO[]
  templates?: TemplateDTO[]; programs?: ProgramDTO[]
}

// ---- esportazione ---------------------------------------------------------------------------------------

const cmp = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0)

const toItemDTOs = (items: TemplateItem[], liveEx: Set<string>): ItemDTO[] =>
  items.filter((i) => liveEx.has(i.exercise)).map((i) => {
    const out: ItemDTO = { exerciseUUID: i.exercise, sets: i.sets, repsMin: i.repsMin, repsMax: i.repsMax }
    if (i.weight !== null) out.weight = i.weight
    return out
  })

/** `photo(sha)` restituisce i byte del JPEG, o null se non c'è. */
export function exportBackup(docs: Iterable<LiveDoc>, photo: (sha: string) => Uint8Array | null, now = Date.now()): BackupFile {
  const d = split(docs)
  const liveEx = new Set(d.exercises.map((e) => e.uuid))
  const liveWk = new Set(d.workouts.map((w) => w.uuid))
  const manual: RecManual[] = d.records.filter((r) => liveEx.has(r.data.exercise)).map((r) => ({
    uuid: r.uuid, exercise: r.data.exercise, date: Date.parse(r.data.date), weight: r.data.weight, reps: r.data.reps, singleArm: r.data.singleArm === true,
  }))
  const sets: RecSet[] = d.sets.filter((s) => liveWk.has(s.data.workout)).map((s) => ({
    uuid: s.uuid, exercise: s.data.exercise && liveEx.has(s.data.exercise) ? s.data.exercise : null, workout: s.data.workout,
    weight: s.data.weight, reps: s.data.reps, isWarmup: s.data.isWarmup, order: s.data.order,
    createdAt: Date.parse(s.data.createdAt), noRecord: s.data.noRecord, singleArm: s.data.singleArm === true,
  }))
  const { prSets, auto } = deriveRecords(manual, sets, liveWk)

  const exercises: ExerciseDTO[] = [...d.exercises]
    .sort((a, b) => a.data.sortIndex - b.data.sortIndex || cmp(a.data.createdAt, b.data.createdAt) || cmp(a.uuid, b.uuid))
    .map(({ uuid, data: e }) => {
      const records: RecordDTO[] = [
        ...d.records.filter((r) => r.data.exercise === uuid).map((r) => ({
          uuid: r.uuid, date: r.data.date, weight: r.data.weight, reps: r.data.reps, isEstimated: false, note: r.data.note,
          ...(r.data.singleArm ? { singleArm: true } : {}),
        })),
        ...auto.filter((a) => a.exercise === uuid).map((a) => ({
          uuid: a.uuid, date: isoSec(a.date), weight: a.weight, reps: a.reps, isEstimated: true, note: 'Da allenamento',
          ...(a.singleArm ? { singleArm: true } : {}),
        })),
      ].sort((a, b) => cmp(a.date, b.date) || cmp(a.uuid, b.uuid))
      const dto: ExerciseDTO = {
        uuid, name: e.name, muscleGroup: e.muscleGroup, notes: e.notes, incrementStep: e.incrementStep,
        sortIndex: e.sortIndex, isArchived: e.isArchived, createdAt: e.createdAt, records,
      }
      if (e.glyph) dto.glyph = e.glyph
      const bytes = e.photo ? photo(e.photo) : null
      if (bytes) dto.photo = Buffer.from(bytes).toString('base64')
      if (e.photoOffset !== 0) dto.photoOffset = e.photoOffset
      if (e.isUnilateral) dto.isUnilateral = true
      if (e.restSeconds) dto.restSeconds = e.restSeconds
      if (e.catalog) dto.catalogID = e.catalog
      if (e.hidden) dto.isHidden = true
      if (e.custom) dto.isCustom = true
      if (e.muscles.length) dto.muscles = e.muscles
      if (e.view) dto.view = e.view
      if (e.barWeight > 0) dto.barWeight = e.barWeight
      if (e.weightDisplay === 'relative') dto.weightDisplay = e.weightDisplay
      if (e.plateLoaded) dto.plateLoaded = true
      if (e.perSide) dto.perSide = true
      if (e.limbs) dto.limbs = e.limbs
      return dto
    })

  const workouts: WorkoutDTO[] = [...d.workouts]
    .sort((a, b) => cmp(a.data.date, b.data.date) || cmp(a.uuid, b.uuid))
    .map(({ uuid, data: w }) => {
      const dto: WorkoutDTO = {
        uuid, date: w.date, note: w.note, focus: w.focus, name: w.name,
        sets: d.sets.filter((s) => s.data.workout === uuid)
          .sort((a, b) => a.data.order - b.data.order || cmp(a.uuid, b.uuid))
          .map(({ uuid: su, data: s }) => {
            const out: SetDTO = {
              uuid: su, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup, order: s.order,
              createdAt: s.createdAt, isPersonalRecord: prSets.has(su),
            }
            if (s.exercise && liveEx.has(s.exercise)) out.exerciseUUID = s.exercise
            if (s.rpe !== null) out.rpe = s.rpe
            if (s.noRecord) out.excludedFromRecords = true
            if (s.singleArm) out.singleArm = true
            return out
          }),
      }
      if (w.endedAt) dto.endedAt = w.endedAt
      if (w.template) dto.template = w.template
      if (w.plan.length) dto.plan = toItemDTOs(w.plan, liveEx)
      return dto
    })

  const bodyWeights: BodyWeightDTO[] = [...d.bodyweights]
    .sort((a, b) => cmp(a.data.date, b.data.date) || cmp(a.uuid, b.uuid))
    .map(({ uuid, data: b }) => ({ uuid, date: b.date, weight: b.weight, fromHealth: b.fromHealth }))

  const templates: TemplateDTO[] = [...d.templates]
    .sort((a, b) => a.data.sortIndex - b.data.sortIndex || cmp(a.data.createdAt, b.data.createdAt) || cmp(a.uuid, b.uuid))
    .map(({ uuid, data: t }) => ({ uuid, name: t.name, sortIndex: t.sortIndex, createdAt: t.createdAt, ...(t.hidden ? { isHidden: true } : {}), ...(t.icon ? { icon: t.icon } : {}), ...(t.program ? { programID: t.program } : {}), ...(t.weekday >= 0 ? { weekday: t.weekday } : {}), items: toItemDTOs(t.items, liveEx) }))

  const programs: ProgramDTO[] = [...d.programs]
    .sort((a, b) => a.data.sortIndex - b.data.sortIndex || cmp(a.data.createdAt, b.data.createdAt) || cmp(a.uuid, b.uuid))
    .map(({ uuid, data: p }) => ({ uuid, name: p.name, sortIndex: p.sortIndex, createdAt: p.createdAt, ...(p.useDays ? { useDays: true } : {}) }))

  return { version: BACKUP_VERSION, exportedAt: isoSec(now), exercises, workouts, bodyWeights, templates, ...(programs.length ? { programs } : {}) }
}

/** JSON a chiavi ordinate e indentato, come lo scrive l'app. */
export function serializeBackup(file: BackupFile): string {
  const sort = (v: unknown): unknown =>
    Array.isArray(v) ? v.map(sort)
      : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).sort(([a], [b]) => cmp(a, b)).map(([k, x]) => [k, sort(x)]))
        : v
  return JSON.stringify(sort(file), null, 2)
}

// ---- lettura ----------------------------------------------------------------------------------------------

export type ParseResult = { file: BackupFile } | { error: 'invalido' } | { error: 'versione'; version: number }

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v)

/** Lettura tollerante: i campi opzionali dei backup vecchi mancano, quelli essenziali no. */
export function parseBackup(raw: unknown): ParseResult {
  if (!isObj(raw) || typeof raw.version !== 'number') return { error: 'invalido' }
  if (raw.version > BACKUP_VERSION) return { error: 'versione', version: raw.version }
  if (!Array.isArray(raw.exercises) || !Array.isArray(raw.workouts)) return { error: 'invalido' }
  if (raw.bodyWeights !== undefined && !Array.isArray(raw.bodyWeights)) return { error: 'invalido' }
  if (raw.templates !== undefined && !Array.isArray(raw.templates)) return { error: 'invalido' }
  const bad = { error: 'invalido' } as const
  const exercises: ExerciseDTO[] = []
  for (const e of raw.exercises) {
    if (!isObj(e)) return bad
    const uuid = normUuid(e.uuid)
    const createdAt = parseIso(e.createdAt)
    if (!uuid || typeof e.name !== 'string' || createdAt === null || !Array.isArray(e.records)) return bad
    const records: RecordDTO[] = []
    for (const r of e.records) {
      if (!isObj(r)) return bad
      const ru = normUuid(r.uuid)
      const date = parseIso(r.date)
      if (!ru || date === null || typeof r.weight !== 'number' || typeof r.reps !== 'number') return bad
      records.push({ uuid: ru, date: isoSec(date), weight: r.weight, reps: r.reps, isEstimated: r.isEstimated === true, note: typeof r.note === 'string' ? r.note : '', singleArm: r.singleArm === true ? true : undefined })
    }
    exercises.push({
      uuid, name: e.name, muscleGroup: typeof e.muscleGroup === 'string' ? e.muscleGroup : 'petto',
      notes: typeof e.notes === 'string' ? e.notes : '', incrementStep: typeof e.incrementStep === 'number' ? e.incrementStep : 2.5,
      sortIndex: typeof e.sortIndex === 'number' ? e.sortIndex : 0, isArchived: e.isArchived === true, createdAt: isoSec(createdAt),
      glyph: typeof e.glyph === 'string' ? e.glyph : undefined, photo: typeof e.photo === 'string' ? e.photo : undefined,
      photoOffset: typeof e.photoOffset === 'number' ? e.photoOffset : undefined, isUnilateral: e.isUnilateral === true ? true : undefined,
      restSeconds: typeof e.restSeconds === 'number' ? e.restSeconds : undefined, records,
      catalogID: typeof e.catalogID === 'string' && CATALOG_ID_RE.test(e.catalogID) ? e.catalogID : undefined, isHidden: e.isHidden === true ? true : undefined, isCustom: e.isCustom === true ? true : undefined,
      muscles: Array.isArray(e.muscles) ? e.muscles.map(String) : undefined, view: typeof e.view === 'string' ? e.view : undefined,
      barWeight: typeof e.barWeight === 'number' && e.barWeight >= 0 && e.barWeight <= 200 ? e.barWeight : undefined,
      weightDisplay: typeof e.weightDisplay === 'string' ? e.weightDisplay : undefined,
      plateLoaded: e.plateLoaded === true ? true : undefined, perSide: e.perSide === true ? true : undefined,
      limbs: typeof e.limbs === 'string' ? e.limbs : undefined,
    })
  }
  /** Righe di scheda del file: tollerante, una riga rotta si scarta (non invalida tutto il backup). */
  const readItems = (v: unknown): ItemDTO[] | null => {
    if (v === undefined) return []
    if (!Array.isArray(v)) return null
    const out: ItemDTO[] = []
    for (const i of v) {
      if (!isObj(i)) return null
      const exerciseUUID = normUuid(i.exerciseUUID)
      if (!exerciseUUID) continue
      const n = (x: unknown) => (typeof x === 'number' ? x : 0)
      out.push({ exerciseUUID, sets: n(i.sets), repsMin: n(i.repsMin), repsMax: n(i.repsMax), weight: typeof i.weight === 'number' ? i.weight : undefined })
    }
    return out
  }
  const workouts: WorkoutDTO[] = []
  for (const w of raw.workouts) {
    if (!isObj(w)) return bad
    const uuid = normUuid(w.uuid)
    const date = parseIso(w.date)
    if (!uuid || date === null || !Array.isArray(w.sets)) return bad
    const endedAt = w.endedAt == null ? null : parseIso(w.endedAt)
    if (w.endedAt != null && endedAt === null) return bad
    const sets: SetDTO[] = []
    for (const s of w.sets) {
      if (!isObj(s)) return bad
      const su = normUuid(s.uuid)
      const createdAt = parseIso(s.createdAt)
      if (!su || createdAt === null || typeof s.weight !== 'number' || typeof s.reps !== 'number') return bad
      sets.push({
        uuid: su, exerciseUUID: normUuid(s.exerciseUUID) ?? undefined, weight: s.weight, reps: s.reps,
        rpe: typeof s.rpe === 'number' ? s.rpe : undefined, isWarmup: s.isWarmup === true,
        order: typeof s.order === 'number' ? s.order : 0, createdAt: isoSec(createdAt), isPersonalRecord: false,
        excludedFromRecords: s.excludedFromRecords === true ? true : undefined,
        singleArm: s.singleArm === true ? true : undefined,
      })
    }
    const plan = readItems(w.plan)
    if (plan === null) return bad
    workouts.push({
      uuid, date: isoSec(date), endedAt: endedAt === null ? undefined : isoSec(endedAt), note: typeof w.note === 'string' ? w.note : '',
      focus: typeof w.focus === 'string' ? w.focus : '', name: typeof w.name === 'string' ? w.name : '', sets,
      template: normUuid(w.template) ?? undefined, plan: plan.length ? plan : undefined,
    })
  }
  const bodyWeights: BodyWeightDTO[] = []
  for (const b of (raw.bodyWeights as unknown[] | undefined) ?? []) {
    if (!isObj(b)) return bad
    const uuid = normUuid(b.uuid)
    const date = parseIso(b.date)
    if (!uuid || date === null || typeof b.weight !== 'number') return bad
    bodyWeights.push({ uuid, date: isoSec(date), weight: b.weight, fromHealth: b.fromHealth === true })
  }
  const templates: TemplateDTO[] = []
  for (const t of (raw.templates as unknown[] | undefined) ?? []) {
    if (!isObj(t)) return bad
    const uuid = normUuid(t.uuid)
    const createdAt = parseIso(t.createdAt)
    const items = readItems(t.items)
    if (!uuid || typeof t.name !== 'string' || createdAt === null || items === null) return bad
    templates.push({ uuid, name: t.name, sortIndex: typeof t.sortIndex === 'number' ? t.sortIndex : 0, createdAt: isoSec(createdAt), isHidden: t.isHidden === true ? true : undefined, icon: typeof t.icon === 'string' ? t.icon : undefined, programID: typeof t.programID === 'string' ? t.programID : undefined, weekday: typeof t.weekday === 'number' ? t.weekday : undefined, items })
  }
  const programs: ProgramDTO[] = []
  for (const p of (raw.programs as unknown[] | undefined) ?? []) {
    if (!isObj(p)) return bad
    const uuid = normUuid(p.uuid)
    const createdAt = parseIso(p.createdAt)
    if (!uuid || typeof p.name !== 'string' || createdAt === null) return bad
    programs.push({ uuid, name: p.name, sortIndex: typeof p.sortIndex === 'number' ? p.sortIndex : 0, createdAt: isoSec(createdAt), useDays: p.useDays === true ? true : undefined })
  }
  const exportedAt = parseIso(raw.exportedAt)
  return { file: { version: raw.version, exportedAt: isoSec(exportedAt ?? 0), exercises, workouts, bodyWeights, templates, programs } }
}

// ---- importazione -------------------------------------------------------------------------------------------

export interface ImportPlan {
  put: { kind: Kind; uuid: string; data: DocDataMap[Kind] }[]
  del: { kind: Kind; uuid: string }[]
  /** Foto nuove da salvare, per sha256. */
  photos: Map<string, Uint8Array>
  conteggi: { esercizi: number; allenamenti: number; serie: number; record: number; pesate: number; schede: number }
  /** Record automatici senza una serie che li spieghi: scartati. */
  orfani: number
  /** Documenti che non hanno passato la validazione. */
  scartati: number
}

const normName = (s: string) => s.trim().toLowerCase()
/** Porta nei limiti del validatore: un file con un valore fuori scala perde la precisione, non il documento. */
const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v))
const clampInt = (v: number, min: number, max: number) => clamp(Math.round(v), min, max)
const cut = (v: string, max: number) => v.slice(0, max)

function decodePhoto(b64: string | undefined): { sha: string; bytes: Uint8Array } | null {
  if (!b64) return null
  const bytes = Buffer.from(b64, 'base64')
  if (bytes.length < 4 || bytes.length > MAX_PHOTO || bytes[0] !== 0xff || bytes[1] !== 0xd8) return null
  return { sha: createHash('sha256').update(bytes).digest('hex'), bytes }
}

/**
 * `live`: i documenti vivi dell'utente. `unisci`: l'esercizio con uuid noto non si tocca (si aggiungono i
 * massimali mancanti), uno con lo stesso nome ma uuid diverso si unisce a quello esistente, un allenamento
 * già presente si salta. `sostituisci`: tutto ciò che non è un'impostazione sparisce e riparte dal file.
 */
export function planImport(file: BackupFile, live: Iterable<LiveDoc>, mode: 'unisci' | 'sostituisci'): ImportPlan {
  const l = split(live)
  const plan: ImportPlan = {
    put: [], del: [], photos: new Map(), conteggi: { esercizi: 0, allenamenti: 0, serie: 0, record: 0, pesate: 0, schede: 0 }, orfani: 0, scartati: 0,
  }
  const putKeys = new Set<string>()
  const add = (kind: Kind, uuid: string, data: unknown, count?: keyof ImportPlan['conteggi']) => {
    const parsed = parseDoc(kind, uuid, data)
    if ('error' in parsed) { plan.scartati++; return false }
    const key = `${kind}:${uuid}`
    if (putKeys.has(key)) return false
    putKeys.add(key)
    plan.put.push({ kind, uuid, data: parsed.data })
    if (count) plan.conteggi[count]++
    return true
  }
  const merge = mode === 'unisci'

  const liveEx = new Map(l.exercises.map((e) => [e.uuid, e]))
  const liveByName = new Map<string, LiveDoc<'exercise'>>()
  for (const e of [...l.exercises].sort((a, b) => a.data.sortIndex - b.data.sortIndex)) {
    if (!liveByName.has(normName(e.data.name))) liveByName.set(normName(e.data.name), e)
  }
  const liveRecs = new Map<string, LiveDoc<'record'>[]>()
  for (const r of l.records) (liveRecs.get(r.data.exercise) ?? liveRecs.set(r.data.exercise, []).get(r.data.exercise)!).push(r)
  const liveRecIds = new Set(l.records.map((r) => r.uuid))
  const liveWk = new Set(l.workouts.map((w) => w.uuid))
  const liveBw = new Set(l.bodyweights.map((b) => b.uuid))
  const liveTpl = new Set(l.templates.map((t) => t.uuid))
  const liveProg = new Set(l.programs.map((p) => p.uuid))
  const fileProg = new Set((file.programs ?? []).map((p) => p.uuid))

  // file → esistente (o sé stesso)
  const exMap = new Map<string, string>()
  const fileSets = new Map<string, SetDTO[]>()
  for (const w of file.workouts) for (const s of w.sets) if (s.exerciseUUID) (fileSets.get(s.exerciseUUID) ?? fileSets.set(s.exerciseUUID, []).get(s.exerciseUUID)!).push(s)

  for (const ex of file.exercises) {
    const group: MuscleGroup = (MUSCLE_GROUPS as readonly string[]).includes(ex.muscleGroup) ? (ex.muscleGroup as MuscleGroup) : 'petto'
    const photo = decodePhoto(ex.photo)
    // un backup vecchio non ha l'id: lo si ricava dal nome; se resta personalizzato tiene l'icona che vedeva
    // (un esercizio fatto a mano di proposito non si ricollega al catalogo per nome)
    const catalog = ex.isCustom ? '' : (ex.catalogID ?? matchByName(CATALOG, ex.name)?.id ?? '')
    const doc = (): ExerciseDoc => ({
      name: cut(ex.name.trim(), 80) || 'Senza nome', muscleGroup: group, notes: cut(ex.notes, 2000),
      incrementStep: clamp(ex.incrementStep, 0.01, 100), sortIndex: clampInt(ex.sortIndex, -1e6, 1e6),
      isArchived: ex.isArchived, createdAt: ex.createdAt,
      glyph: isGlyph(ex.glyph) ? ex.glyph : ex.catalogID === undefined && !catalog ? suggestedGlyph(ex.name, group) : '',
      photo: photo?.sha ?? '', photoOffset: Math.min(1, Math.max(-1, ex.photoOffset ?? 0)),
      isUnilateral: ex.isUnilateral === true, restSeconds: clampInt(ex.restSeconds ?? 0, 0, 3600),
      catalog, hidden: ex.isHidden === true, custom: ex.isCustom === true,
      muscles: cleanMuscles(ex.muscles ?? []) ?? [], view: ex.view === 'front' || ex.view === 'back' ? ex.view : '',
      barWeight: ex.barWeight ?? 0, weightDisplay: ex.weightDisplay === 'relative' ? 'relative' : 'absolute',
      plateLoaded: ex.plateLoaded === true, perSide: ex.perSide === true,
      ...((LIMBS as readonly unknown[]).includes(ex.limbs) ? { limbs: ex.limbs as Limbs } : {}),
    })
    let target = ex.uuid
    let isNew = true
    if (merge && liveEx.has(ex.uuid)) { isNew = false }
    else if (merge && liveByName.has(normName(ex.name))) {
      const existing = liveByName.get(normName(ex.name))!
      target = existing.uuid
      isNew = false
      if (photo && !existing.data.photo) {
        add('exercise', target, { ...existing.data, photo: photo.sha })
        plan.photos.set(photo.sha, photo.bytes)
      }
    }
    exMap.set(ex.uuid, target)
    if (isNew && add('exercise', ex.uuid, doc(), 'esercizi') && photo) plan.photos.set(photo.sha, photo.bytes)

    // massimali: solo quelli manuali; gli automatici si ricalcolano dalle serie
    const sets = fileSets.get(ex.uuid) ?? []
    for (const r of ex.records) {
      if (r.isEstimated) {
        const spiegato = sets.some((s) => !s.isWarmup && s.reps === r.reps && Math.abs(s.weight - r.weight) < EPSILON && s.createdAt === r.date)
        if (!spiegato) plan.orfani++
        continue
      }
      if (merge && liveRecIds.has(r.uuid)) continue
      if (merge && !isNew) {
        const dup = [...(liveRecs.get(target) ?? [])].some((x) =>
          x.data.date === r.date && x.data.reps === r.reps && Math.abs(x.data.weight - r.weight) < EPSILON && (x.data.singleArm === true) === (r.singleArm === true))
        if (dup) continue
      }
      add('record', r.uuid, { exercise: target, date: r.date, weight: clamp(r.weight, 0.01, 2000), reps: clampInt(r.reps, 1, 1000), note: cut(r.note, 500), singleArm: r.singleArm === true }, 'record')
    }
  }

  /** Righe del file con l'esercizio riportato a quello vero (unione per nome); quelle senza esercizio spariscono. */
  const mapItems = (items: ItemDTO[] | undefined) =>
    (items ?? []).flatMap((i) => {
      const ex = exMap.get(i.exerciseUUID) ?? (liveEx.has(i.exerciseUUID) ? i.exerciseUUID : null)
      if (!ex) return []
      const repsMin = clampInt(i.repsMin, 0, 100)
      return [{ exercise: ex, sets: clampInt(i.sets, 0, 20), repsMin, repsMax: Math.max(repsMin, clampInt(i.repsMax, 0, 100)), weight: i.weight == null ? null : clamp(i.weight, 0, 2000) }]
    }).slice(0, MAX_ITEMS)

  for (const w of file.workouts) {
    if (merge && liveWk.has(w.uuid)) continue
    const workoutDoc = {
      date: w.date, endedAt: w.endedAt ?? null, note: cut(w.note, 2000), name: cut(w.name, 80), focus: cleanFocus(w.focus) ?? '',
      template: w.template ?? '', plan: mapItems(w.plan),
    }
    if (!add('workout', w.uuid, workoutDoc, 'allenamenti')) continue
    for (const s of w.sets) {
      const ex = s.exerciseUUID ? (exMap.get(s.exerciseUUID) ?? (liveEx.has(s.exerciseUUID) ? s.exerciseUUID : null)) : null
      add('set', s.uuid, {
        workout: w.uuid, exercise: ex, weight: clamp(s.weight, 0, 2000), reps: clampInt(s.reps, 0, 1000),
        rpe: s.rpe !== undefined && s.rpe >= 1 && s.rpe <= 10 ? s.rpe : null, isWarmup: s.isWarmup,
        order: clampInt(s.order, 0, 1e6), createdAt: s.createdAt, noRecord: s.excludedFromRecords === true, singleArm: s.singleArm === true,
      }, 'serie')
    }
  }
  for (const b of file.bodyWeights ?? []) {
    if (merge && liveBw.has(b.uuid)) continue
    add('bodyweight', b.uuid, { date: b.date, weight: clamp(b.weight, 20, 400), fromHealth: b.fromHealth }, 'pesate')
  }

  for (const p of file.programs ?? []) {
    if (merge && liveProg.has(p.uuid)) continue
    add('program', p.uuid, { name: cut(p.name.trim(), 80) || 'Senza nome', sortIndex: clampInt(p.sortIndex, -1e6, 1e6), createdAt: p.createdAt, useDays: p.useDays === true })
  }
  for (const t of file.templates ?? []) {
    if (merge && liveTpl.has(t.uuid)) continue
    // la scheda generale deve esistere (nel file o già qui), altrimenti il giorno resta senza
    const program = t.programID && (fileProg.has(t.programID) || liveProg.has(t.programID)) ? t.programID : ''
    const weekday = Number.isInteger(t.weekday) && t.weekday! >= 0 && t.weekday! <= 6 ? t.weekday! : -1
    add('template', t.uuid, { name: cut(t.name.trim(), 80) || 'Senza nome', items: mapItems(t.items), sortIndex: clampInt(t.sortIndex, -1e6, 1e6), createdAt: t.createdAt, hidden: t.isHidden === true, icon: isTemplateIcon(t.icon) ? t.icon : '', program, weekday }, 'schede')
  }

  if (!merge) {
    for (const d of [...l.exercises, ...l.records, ...l.workouts, ...l.sets, ...l.bodyweights, ...l.templates, ...l.programs]) {
      if (!putKeys.has(`${d.kind}:${d.uuid}`)) plan.del.push({ kind: d.kind, uuid: d.uuid })
    }
  }
  return plan
}
