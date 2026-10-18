// L'icona di una scheda quando non ne hai scelta una: la si legge dal nome ("Push", "Gambe", "Pull 1"…).
// Stesse parole e stesso ordine sull'iPhone (`TemplateIcon.swift`).

import type { TemplateIcon } from './docs.ts'

const RULES: [RegExp, TemplateIcon][] = [
  [/\b(leg|legs|gamb\w*|squat\w*|quad\w*|glut\w*|polpacc\w*|calf|calves|lower)\b/, 'muscle:gambe'],
  [/\b(pull|schien\w*|back|row|dors\w*|lat)\b/, 'muscle:schiena'],
  [/\b(push|petto|chest|pett\w*|bench|panca)\b/, 'muscle:petto'],
  [/\b(spall\w*|shoulder\w*|delt\w*)\b/, 'muscle:spalle'],
  [/\b(bracc\w*|arm|arms|bicip\w*|tricip\w*|curl)\b/, 'muscle:braccia'],
  [/\b(core|abs|addom\w*)\b/, 'muscle:core'],
  [/\b(cardio|run|corsa|hiit)\b/, 'heart'],
  [/\b(forza|strength|power)\b/, 'bolt'],
]

/** '' se il nome non dice niente: allora si mostra il manubrio. */
export function suggestTemplateIcon(name: string): '' | TemplateIcon {
  const n = name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
  return RULES.find(([re]) => re.test(n))?.[1] ?? ''
}
