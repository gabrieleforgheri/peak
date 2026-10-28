// Percorsi a hash (#/macchinari/UUID): il server serve solo "/", e il ritorno dal login non perde la schermata.

export const route = $state({ parts: [] as string[], dir: 'tab' as 'tab' | 'fwd' | 'back' })

// quanto «in fondo» è una pagina: le schede e il loro dettaglio (macchinari/UUID, allenamento/UUID/riepilogo…); le impostazioni stanno un livello sopra la scheda Me
const depth = (parts: string[]) => Math.max(1, parts.length) + (parts[0] === 'impostazioni' ? 1 : 0)

function parse() {
	const h = location.hash.replace(/^#\/?/, '')
	const parts = h ? h.split('/').map(decodeURIComponent) : []
	// i nomi di prima della 3.0 (segnalibri, schermata aggiunta alla Home)
	const renamed: Record<string, string> = { massimali: 'macchinari', allenamenti: 'allenamento', progressi: 'me', home: 'allenamento' }
	if (parts[0] && renamed[parts[0]]) parts[0] = renamed[parts[0]]
	// da dove arriva la pagina nuova: più in fondo di prima = avanti, meno = indietro (anche col tasto indietro del browser), stesso livello fra schede = cambio scheda
	const from = depth(route.parts), to = depth(parts)
	route.dir = to > from ? 'fwd' : to < from ? 'back' : to === 1 ? 'tab' : 'fwd'
	route.parts = parts
}

addEventListener('hashchange', parse)
parse()
route.dir = 'tab' // la prima pagina dell'avvio non arriva «da» nessuna parte

export function go(path: string, replace = false) {
	const url = `#/${path}`
	if (replace) location.replace(url)
	else location.hash = url
}

export const back = (fallback: string) => (history.length > 1 ? history.back() : go(fallback, true))
