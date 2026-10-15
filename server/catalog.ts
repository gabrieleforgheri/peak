// Il catalogo degli esercizi, letto una volta: serve alla migrazione, alla classifica e al file per i client.
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import type { CatalogEntry } from '../common/catalog.ts'

const file = readFileSync(new URL('../common/catalog.json', import.meta.url))

export const CATALOG: CatalogEntry[] = JSON.parse(file.toString('utf8'))
export const CATALOG_BODY: Buffer = file
/** Cambia solo se cambia il file: i client lo usano per sapere se riscaricare. */
export const CATALOG_ETAG = `"${createHash('sha256').update(file).digest('hex').slice(0, 16)}"`
