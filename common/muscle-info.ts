// I muscoli che la figura colora. Gli id sono quelli dei pezzi del disegno (scripts/anatomy-src, MIT): mai rinominati né riusati,
// finiscono nel catalogo e nei documenti. Solo nomi e gruppi: il disegno sta in anatomy.ts, generato.

import type { MuscleGroup } from './docs.ts'

export const MUSCLES = [
  'chest', 'deltoids', 'trapezius', 'upperBack', 'lowerBack', 'abs', 'obliques', 'biceps', 'triceps', 'forearm',
  'quadriceps', 'adductors', 'tibialis', 'gluteal', 'hamstring', 'calves',
] as const
export type Muscle = (typeof MUSCLES)[number]
/** Vista della figura: di fronte o di schiena. */
export type View = 'front' | 'back'

export const MUSCLE_INFO: Record<Muscle, { group: MuscleGroup; it: string; en: string }> = {
  chest: { group: 'petto', it: 'Petto', en: 'Chest' },
  deltoids: { group: 'spalle', it: 'Deltoidi', en: 'Delts' },
  trapezius: { group: 'schiena', it: 'Trapezio', en: 'Traps' },
  upperBack: { group: 'schiena', it: 'Schiena alta', en: 'Upper back' },
  lowerBack: { group: 'schiena', it: 'Lombari', en: 'Lower back' },
  abs: { group: 'core', it: 'Addominali', en: 'Abs' },
  obliques: { group: 'core', it: 'Obliqui', en: 'Obliques' },
  biceps: { group: 'braccia', it: 'Bicipiti', en: 'Biceps' },
  triceps: { group: 'braccia', it: 'Tricipiti', en: 'Triceps' },
  forearm: { group: 'braccia', it: 'Avambracci', en: 'Forearms' },
  quadriceps: { group: 'gambe', it: 'Quadricipiti', en: 'Quads' },
  adductors: { group: 'gambe', it: 'Adduttori', en: 'Adductors' },
  tibialis: { group: 'gambe', it: 'Tibiale anteriore', en: 'Tibialis' },
  gluteal: { group: 'gambe', it: 'Glutei', en: 'Glutes' },
  hamstring: { group: 'gambe', it: 'Femorali', en: 'Hamstrings' },
  calves: { group: 'gambe', it: 'Polpacci', en: 'Calves' },
}

export const isMuscle = (value: unknown): value is Muscle =>
  typeof value === 'string' && (MUSCLES as readonly string[]).includes(value)
