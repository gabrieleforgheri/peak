import en from './en.json'

/* Italiano e inglese: quella scelta nelle impostazioni (per dispositivo), altrimenti la lingua del browser. Il testo
 * italiano è la chiave e le traduzioni stanno in en.json (come in yG Music). I segnaposto sono {0}, {1}…; i plurali sono
 * due chiavi ("1 serie" / "{0} serie"), scelte da tn(). Una parola italiana con due traduzioni prende un contesto dopo
 * "|", tolto in italiano. */

export type Lang = 'it' | 'en'
/** 'auto' = la lingua del browser. */
export type LangPref = 'auto' | Lang

const KEY = 'peak.lang'
/** Lo stesso valore in un cookie: lo leggono le pagine del server (login, uscita). */
const COOKIE = 'peak_lang'
/** E in una cache: il service worker non vede localStorage, e il testo dell'avviso di fine recupero lo scrive lui. */
const SW_CACHE = 'peak-lingua'

export const langPref: LangPref = (() => {
	try { const v = localStorage.getItem(KEY); return v === 'it' || v === 'en' ? v : 'auto' } catch { return 'auto' }
})()
export const lang: Lang = langPref !== 'auto' ? langPref : navigator.language?.toLowerCase().startsWith('it') ? 'it' : 'en'
document.documentElement.lang = lang

function share(pref: LangPref) {
	const secure = location.protocol === 'https:' ? '; Secure' : ''
	document.cookie = pref === 'auto' ? `${COOKIE}=; Path=/; Max-Age=0; SameSite=Lax${secure}` : `${COOKIE}=${pref}; Path=/; Max-Age=31536000; SameSite=Lax${secure}`
	return globalThis.caches?.open(SW_CACHE).then((c) => c.put('/lingua', new Response(pref === 'auto' ? '' : pref))).catch(() => {})
}
// a ogni avvio: il cookie scade dopo un anno, e la cache può essere stata svuotata
void share(langPref)

/** Cambia lingua e ricarica la pagina: i testi e i formati di numeri e date si leggono una volta, all'avvio. */
export async function setLangPref(pref: LangPref) {
	if (pref === langPref) return
	try { if (pref === 'auto') localStorage.removeItem(KEY); else localStorage.setItem(KEY, pref) } catch { /* modalità privata: resta com'è */ }
	await share(pref)
	location.reload()
}

const dict: Record<string, string> = lang === 'en' ? en : {}

export function t(key: string, ...args: (string | number)[]): string {
	return (dict[key] ?? key.split('|')[0]!).replace(/\{(\d)\}/g, (_, i: string) => String(args[Number(i)] ?? ''))
}

export const tn = (n: number, one: string, many: string): string => (n === 1 ? t(one) : t(many, n))

export const locale = lang === 'it' ? 'it-IT' : 'en-GB'
