// La figura dei muscoli: il corpo di fronte o di schiena, con i muscoli che lavorano colorati per gruppo.
// Il disegno sta in anatomy.ts (generato da scripts/gen-anatomy.ts), lo stesso che usa l'iPhone.

import { ANATOMY, ANATOMY_VIEWBOX, MUSCLE_BOX } from './anatomy.ts'
import { MUSCLE_INFO, type Muscle, type View } from './muscle-info.ts'

export type { View }
export const VIEWS = ['front', 'back'] as const
export { ANATOMY_VIEWBOX }

/** Un rettangolo del disegno: [x0, y0, x1, y1]. */
export type Box = [number, number, number, number]

/** I pezzi di una vista: il muscolo (null = corpo) e i percorsi. */
export const pieces = (view: View) => ANATOMY[view]

/** Un muscolo si vede davanti, di schiena o in tutte e due (deltoidi, trapezio, avambracci, adduttori, polpacci…). */
export function muscleViews(m: Muscle): View[] {
  return VIEWS.filter((v) => ANATOMY[v].some((p) => p.muscle === m))
}

/**
 * La vista che mostra più dei muscoli dati; a parità, quella di fronte. Un macchinario o una scheda ha una sola figura,
 * e per gli stessi muscoli la scelta è sempre la stessa.
 */
export function viewFor(muscles: readonly Muscle[]): View {
  const count = (v: View) => muscles.filter((m) => muscleViews(m).includes(v)).length
  return count('front') >= count('back') ? 'front' : 'back'
}

/**
 * La figura da mostrare: la vista forzata se c'è, altrimenti quella automatica. I muscoli di una voce del catalogo
 * o di un macchinario personalizzato, mai dei due insieme.
 */
export function figureFor(muscles: readonly Muscle[], view: '' | View): { view: View; active: readonly Muscle[] } {
  return { view: view || viewFor(muscles), active: muscles }
}

const FULL: Box = [0, 0, 724, 1448]
const PAD = 0.15
const PAD_MIN = 30
const MIN_SIDE = 240

/**
 * Lo zoom sulla zona che lavora, come nelle foto dei macchinari: il rettangolo dei muscoli attivi in quella vista, con un
 * margine e una misura minima. Se nessun muscolo attivo compare in quella vista, si mostra tutto il corpo.
 * Stessa regola di `AnatomyZoom` nel Swift generato.
 */
export function zoomFor(active: readonly Muscle[], view: View): Box {
  const boxes = MUSCLE_BOX[view] as Partial<Record<Muscle, Box>>
  const found = active.map((m) => boxes[m]).filter((b): b is Box => !!b)
  if (!found.length) return FULL
  let [x0, y0, x1, y1] = found[0]
  for (const b of found.slice(1)) {
    x0 = Math.min(x0, b[0]); y0 = Math.min(y0, b[1]); x1 = Math.max(x1, b[2]); y1 = Math.max(y1, b[3])
  }
  const padX = Math.max((x1 - x0) * PAD, PAD_MIN), padY = Math.max((y1 - y0) * PAD, PAD_MIN)
  x0 -= padX; x1 += padX; y0 -= padY; y1 += padY
  const w = Math.max(x1 - x0, MIN_SIDE), h = Math.max(y1 - y0, MIN_SIDE)
  const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2
  return [cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2]
}

/** Il gruppo di un muscolo, per il colore: ogni muscolo ha il colore del suo gruppo. */
export const groupOf = (m: Muscle) => MUSCLE_INFO[m].group
