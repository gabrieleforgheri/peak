// Gli amici: tutto passa dal server (ogni percorso controlla l'amicizia e toglie i nascosti). Qui solo chiamate e un po' di stato.

import { call } from './sync.ts'

export interface Person { id: number; username: string; name: string }
export interface Friends { amici: Person[]; ricevute: Person[]; inviate: Person[] }
export type FriendState = 'nessuno' | 'amici' | 'inviata' | 'ricevuta'
export interface Max { catalog: string; name: string; oneRm: number; weight: number; reps: number; date: number }
export interface Row { catalog: string; name: string; sets: number; repsMin: number; repsMax: number }
export interface SharedTemplate { uuid: string; name: string; rows: Row[] }
export interface Profile extends Person { maxes: Max[]; templates: SharedTemplate[] }
export interface BoardRow { id: number; username: string; tu: boolean; name: string; oneRm: number; weight: number; reps: number; date: number }
export interface LeagueRow { id: number; username: string; tu: boolean; volume30: number; sessions30: number; streak: number; last: number | null }
export interface NewsEvent extends Max { id: number; username: string; sorpasso: boolean }

export const F = $state({ friends: null as Friends | null, news: null as NewsEvent[] | null, offline: false })

async function json<T>(path: string, init?: RequestInit): Promise<T | null> {
	try {
		const res = await call(path, init)
		F.offline = false
		return res.ok ? ((await res.json()) as T) : null
	} catch { F.offline = true; return null }
}
const post = (body: unknown): RequestInit => ({ method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })

export async function loadFriends() { const r = await json<Friends>('/api/amici'); if (r) F.friends = r }
export async function loadNews() { const r = await json<{ eventi: NewsEvent[] }>('/api/novita'); if (r) F.news = r.eventi }
export const searchUsers = async (q: string) => (await json<{ utenti: (Person & { stato: FriendState })[] }>(`/api/amici/cerca?q=${encodeURIComponent(q)}`))?.utenti ?? []
export const requestFriend = async (id: number) => { const r = await json<{ stato: FriendState }>('/api/amici', post({ id })); await loadFriends(); return r?.stato ?? null }
export const acceptFriend = async (id: number) => { const r = await json(`/api/amici/${id}/accetta`, { method: 'POST' }); await loadFriends(); return !!r }
export const removeFriend = async (id: number) => { await json(`/api/amici/${id}`, { method: 'DELETE' }); await loadFriends() }
export const createInvite = () => json<{ codice: string; scade: number }>('/api/inviti', { method: 'POST' })
export const useInvite = (code: string) => json<{ amico: Person }>(`/api/inviti/${encodeURIComponent(code)}`, { method: 'POST' })
export const getLeague = async () => (await json<{ righe: LeagueRow[] }>('/api/lega'))?.righe ?? null
export const getProfile = (id: number) => json<Profile>(`/api/amici/${id}`)
export const getBoard = async (catalog: string) => (await json<{ righe: BoardRow[] }>(`/api/classifica/${encodeURIComponent(catalog)}`))?.righe ?? null

/** Il link da condividere: apre Peak (dopo il login) e vi fa amici. */
export const inviteLink = (code: string) => `${location.origin}/invito/${code}`
