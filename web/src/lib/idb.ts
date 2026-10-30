// IndexedDB minimale: un database per utente (peak-<sub>), così la coda di un altro account non si spinge mai.

import type { Kind } from '@common/docs.ts'

export interface Row { key: string; kind: Kind; uuid: string; data: unknown; m: number; deleted: boolean; dirty: boolean }
/** `failed`: il server l'ha rifiutata in modo definitivo (troppe foto, file non valido): non si riprova a ogni giro. */
export interface PhotoRow { sha: string; blob: Blob; uploaded: boolean; failed?: boolean }

let db: IDBDatabase | null = null

const wrap = <T>(req: IDBRequest<T>): Promise<T> => new Promise((ok, ko) => { req.onsuccess = () => ok(req.result); req.onerror = () => ko(req.error) })
const done = (tx: IDBTransaction): Promise<void> => new Promise((ok, ko) => { tx.oncomplete = () => ok(); tx.onerror = () => ko(tx.error); tx.onabort = () => ko(tx.error) })

export async function openDb(sub: string): Promise<void> {
	db?.close()
	db = await new Promise<IDBDatabase>((ok, ko) => {
		const req = indexedDB.open(`peak-${sub}`, 1)
		req.onupgradeneeded = () => {
			req.result.createObjectStore('docs', { keyPath: 'key' })
			req.result.createObjectStore('meta')
			req.result.createObjectStore('photos', { keyPath: 'sha' })
		}
		req.onsuccess = () => ok(req.result)
		req.onerror = () => ko(req.error)
	})
}

const tx = (stores: string | string[], mode: IDBTransactionMode = 'readonly') => db!.transaction(stores, mode)

export const allRows = (): Promise<Row[]> => wrap(tx('docs').objectStore('docs').getAll())

export async function writeRows(rows: Row[]): Promise<void> {
	const t = tx('docs', 'readwrite')
	for (const r of rows) t.objectStore('docs').put(r)
	await done(t)
}

export async function clearAll(): Promise<void> {
	const t = tx(['docs', 'meta'], 'readwrite')
	t.objectStore('docs').clear()
	t.objectStore('meta').clear()
	await done(t)
}

export const getMeta = <T>(key: string): Promise<T | undefined> => wrap(tx('meta').objectStore('meta').get(key))
export async function setMeta(key: string, value: unknown): Promise<void> {
	const t = tx('meta', 'readwrite')
	t.objectStore('meta').put(value, key)
	await done(t)
}

export const getPhoto = (sha: string): Promise<PhotoRow | undefined> => wrap(tx('photos').objectStore('photos').get(sha))
export const allPhotos = (): Promise<PhotoRow[]> => wrap(tx('photos').objectStore('photos').getAll())
export async function putPhoto(row: PhotoRow): Promise<void> {
	const t = tx('photos', 'readwrite')
	t.objectStore('photos').put(row)
	await done(t)
}
