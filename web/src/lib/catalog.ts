import type { CatalogEntry } from '@common/catalog.ts'
import { defaultBarWeightFor, guessPlateLoaded } from '@common/domain.ts'
import { locale } from './i18n.ts'

let loaded: Promise<CatalogEntry[]> | null = null

/** Il catalogo, caricato solo la prima volta che serve (è un blocco a parte, in cache dal service worker). */
export const loadCatalog = (): Promise<CatalogEntry[]> => (loaded ??= import('@common/catalog.json').then((m) => m.default as CatalogEntry[]))

/** Il nome nella lingua dell'app. */
export const entryName = (e: CatalogEntry): string => (locale.startsWith('it') ? e.it : e.en)
/** L'altro nome, se serve a riconoscere la voce. */
export const entryOther = (e: CatalogEntry): string => (locale.startsWith('it') ? e.en : e.it)

/** I valori di un macchinario appena preso dal catalogo (kg): li usano l'aggiunta guidata, la copia di una scheda e il primo avvio. */
export function catalogDefaults(e: CatalogEntry) {
	const name = entryName(e)
	return { name, group: e.group, step: e.step, notes: e.notes, barWeight: defaultBarWeightFor(e.glyph, name), plateLoaded: guessPlateLoaded(e.glyph, name) }
}
