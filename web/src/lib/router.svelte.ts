// Percorsi a hash (#/macchinari/UUID): il server serve solo "/", e il ritorno dal login non perde la schermata.

export const route = $state({ parts: [] as string[] })

function parse() {
	const h = location.hash.replace(/^#\/?/, '')
	const parts = h ? h.split('/').map(decodeURIComponent) : []
	// i nomi di prima della 3.0 (segnalibri, schermata aggiunta alla Home)
	const renamed: Record<string, string> = { massimali: 'macchinari', allenamenti: 'allenamento', progressi: 'me' }
	if (parts[0] && renamed[parts[0]]) parts[0] = renamed[parts[0]]
	route.parts = parts
}

addEventListener('hashchange', parse)
parse()

export function go(path: string, replace = false) {
	const url = `#/${path}`
	if (replace) location.replace(url)
	else location.hash = url
}

export const back = (fallback: string) => (history.length > 1 ? history.back() : go(fallback, true))
