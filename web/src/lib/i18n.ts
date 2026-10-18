import en from './en.json'

/* Italiano e inglese: quella scelta nelle impostazioni, altrimenti la lingua del browser. Il testo italiano è la chiave e le traduzioni stanno in
 * en.json (come in yG Music). I segnaposto sono {0}, {1}…; i plurali sono due chiavi ("1 serie" / "{0} serie"),
 * scelte da tn(). Una parola italiana con due traduzioni prende un contesto dopo "|", tolto in italiano. */

const KEY = 'peak-lang'
const saved = (() => { try { return localStorage.getItem(KEY) } catch { return null } })()
export const lang: 'it' | 'en' = saved === 'it' || saved === 'en' ? saved : navigator.language?.toLowerCase().startsWith('it') ? 'it' : 'en'

/** Ricorda la lingua su questo dispositivo (serve all'avvio, prima che arrivino le impostazioni) e ricarica: i testi sono calcolati all'avvio. */
export function applyLang(l: 'it' | 'en') {
	try { localStorage.setItem(KEY, l) } catch { /* senza archivio resta la lingua del browser */ }
	location.reload()
}
document.documentElement.lang = lang

const dict: Record<string, string> = lang === 'en' ? en : {}

export function t(key: string, ...args: (string | number)[]): string {
	return (dict[key] ?? key.split('|')[0]!).replace(/\{(\d)\}/g, (_, i: string) => String(args[Number(i)] ?? ''))
}

export const tn = (n: number, one: string, many: string): string => (n === 1 ? t(one) : t(many, n))

export const locale = lang === 'it' ? 'it-IT' : 'en-GB'
