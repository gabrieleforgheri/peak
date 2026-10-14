// Percorsi a hash (#/massimali/UUID): il server serve solo "/", e il ritorno dal login non perde la schermata.

export const route = $state({ parts: [] as string[] })

function parse() {
	const h = location.hash.replace(/^#\/?/, '')
	route.parts = h ? h.split('/').map(decodeURIComponent) : []
}

addEventListener('hashchange', parse)
parse()

export function go(path: string, replace = false) {
	const url = `#/${path}`
	if (replace) location.replace(url)
	else location.hash = url
}

export const back = (fallback: string) => (history.length > 1 ? history.back() : go(fallback, true))
