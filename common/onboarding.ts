// I macchinari che il primo avvio propone per ogni gruppo: cinque comuni in palestra, prima macchine e cavi che pesi liberi.
// I primi tre partono già scelti. Sono id del catalogo (vedi catalog.json); common/onboarding.test.ts controlla che esistano.

import type { MuscleGroup } from './docs.ts'

export const SUGGESTED_MACHINES: Readonly<Record<MuscleGroup, readonly string[]>> = {
  petto: ['chest-press-machine', 'pec-deck', 'incline-chest-press-machine', 'cable-fly', 'bench-press-barbell'],
  schiena: ['lat-pulldown', 'seated-cable-row', 'row-machine', 'assisted-pull-up', 'back-extension-machine'],
  gambe: ['leg-press-45', 'leg-extension', 'seated-leg-curl', 'standing-calf-machine', 'hack-squat'],
  spalle: ['shoulder-press-machine', 'lateral-raise-machine', 'reverse-pec-deck', 'cable-lateral-raise', 'face-pull-rope'],
  braccia: ['curl-machine', 'triceps-pushdown', 'cable-curl', 'triceps-machine', 'preacher-curl-machine'],
  core: ['crunch-machine', 'cable-crunch', 'rotary-torso', 'roman-chair', 'hanging-leg-raise'],
}

/** Quanti, dall'inizio della lista, partono già scelti. */
export const SUGGESTED_PRESELECTED = 3
