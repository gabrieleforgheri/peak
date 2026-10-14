// Foto dei macchinari: ridotta a 640 px sul lato lungo e ricompressa in JPEG (come Utils/ImageStore.swift),
// indirizzata dal suo sha256. Resta in IndexedDB finché non è sul server; poi si legge da lì anche offline.

import * as idb from './idb.ts'

const MAX_SIDE = 640
const urls = new Map<string, string>()

const hex = (buf: ArrayBuffer) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('')

/** Prepara una foto scelta o scattata: la salva in locale (da caricare) e restituisce lo sha256. */
export async function preparePhoto(file: Blob): Promise<string> {
	const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' })
	const scale = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height))
	const canvas = document.createElement('canvas')
	canvas.width = Math.max(1, Math.round(bmp.width * scale))
	canvas.height = Math.max(1, Math.round(bmp.height * scale))
	canvas.getContext('2d')!.drawImage(bmp, 0, 0, canvas.width, canvas.height)
	bmp.close()
	const blob = await new Promise<Blob>((ok, ko) => canvas.toBlob((b) => (b ? ok(b) : ko(new Error('jpeg'))), 'image/jpeg', 0.75))
	const sha = hex(await crypto.subtle.digest('SHA-256', await blob.arrayBuffer()))
	await idb.putPhoto({ sha, blob, uploaded: false })
	urls.set(sha, URL.createObjectURL(blob))
	return sha
}

/** L'indirizzo da mostrare: la copia locale se c'è, altrimenti il server (il service worker la tiene in cache). */
export async function photoUrl(sha: string): Promise<string> {
	const known = urls.get(sha)
	if (known) return known
	const row = await idb.getPhoto(sha)
	if (!row) return `/api/foto/${sha}`
	const url = URL.createObjectURL(row.blob)
	urls.set(sha, url)
	return url
}
