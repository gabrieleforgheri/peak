import type { CatalogEntry } from '@common/catalog.ts'
import { locale } from './i18n.ts'

let loaded: Promise<CatalogEntry[]> | null = null

/** Il catalogo, caricato solo la prima volta che serve (è un blocco a parte, in cache dal service worker). */
export const loadCatalog = (): Promise<CatalogEntry[]> => (loaded ??= import('@common/catalog.json').then((m) => m.default as CatalogEntry[]))

/** Il nome nella lingua dell'app. */
export const entryName = (e: CatalogEntry): string => (locale.startsWith('it') ? e.it : e.en)
/** L'altro nome, se serve a riconoscere la voce. */
export const entryOther = (e: CatalogEntry): string => (locale.startsWith('it') ? e.en : e.it)
