// Fa rispettare le regole del repo in modo meccanico (CI su Linux e hook locale).
// Uso, da qualunque cartella: node scripts/check_rules.mjs [--quiet] [--update [--force]] [--version=warn|error] [--root <cartella>]
//   - controllo versione: web/package.json deve coincidere con MARKETING_VERSION di project.yml (per ora solo avviso, vedi --version)
//   - regole a ratchet: il numero di violazioni per file non può salire rispetto a scripts/rules-baseline.json; i file nuovi partono da 0
//   - --update riscrive la baseline solo verso il basso (la crea se manca); --force permette anche di alzarla
// Codice di uscita: 0 se tutto ok, 1 se una regola fallisce.
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'

const SWIFT = { dirs: ['Peak/Features', 'PeakWatch', 'PeakWidgets'], exts: ['.swift'] } // Peak/Design è fuori: lì si definiscono i valori
const WEB = { dirs: ['web/src'], exts: ['.svelte', '.css'], exclude: ['web/src/app.css'] } // app.css è fuori: lì si definiscono le variabili
const EMOJI = {
	dirs: ['Peak', 'PeakWatch', 'PeakWidgets', 'Shared', 'WatchShared', 'web/src', 'common'],
	exts: ['.swift', '.svelte', '.ts', '.css', '.json'],
}

// Il Swift si controlla senza le righe di commento; i commenti diventano spazi, così gli indici e i numeri di riga tornano.
const noSwiftComments = (t) => t.replace(/^[ \t]*\/\/.*$/gm, (m) => ' '.repeat(m.length))
const all = (re) => (t) => [...t.matchAll(re)].map((m) => m.index)

// Un colore esadecimale conta solo in un contesto di valore: dichiarazione CSS/oggetto JS (`proprietà: … #fff`)
// o attributo SVG di colore. Restano fuori href="#sezione", id, url(#id) e testo come "Serie #123".
const HEX = /(?<![\w&#/-])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})(?![\w-])/g
const hexColors = (t) =>
	[...t.matchAll(HEX)]
		.filter((m) => {
			const before = t.slice(t.lastIndexOf('\n', m.index) + 1, m.index)
			if (/(?:\b(?:href|id|for|name)\s*=\s*["'{]?|url\(\s*["']?)$/.test(before)) return false
			return /[\w-]+\s*:[^;]*$/.test(before) || /(?:fill|stroke|color|stop-color|flood-color)=(?:["'][^"']*|\{[^}]*)$/.test(before)
		})
		.map((m) => m.index)
const webColors = (t) => [...hexColors(t), ...all(/\brgba?\((?!\s*var\()/g)(t)].sort((a, b) => a - b)

const RULES = [
	{
		name: 'swift-font-size', ...SWIFT, prep: noSwiftComments, find: all(/\.font\(\s*\.system\(\s*size:/g),
		msg: 'Corpo del testo a mano: usa uno stile di testo di Theme (scala tipografica) al posto di .font(.system(size:).',
	},
	{
		name: 'swift-color-hex', ...SWIFT, prep: noSwiftComments, find: all(/\bColor\(\s*(?:\.\w+\s*,\s*)?(?:hex|red):/g),
		msg: 'Colore a mano: usa un colore di Theme al posto di Color(hex:) / Color(red:).',
	},
	{
		name: 'swift-padding-literal', ...SWIFT, prep: noSwiftComments, find: all(/\.padding\(\s*(?:(?:\.\w+|\[[^\]]*\])\s*,\s*)?-?\d/g),
		msg: 'Spaziatura a mano: usa Theme.Space al posto di .padding(<numero>).',
	},
	{
		name: 'swift-radius-literal', ...SWIFT, prep: noSwiftComments, find: all(/\bcornerRadius:\s*-?\d|\.cornerRadius\(\s*-?\d/g),
		msg: 'Raggio a mano: usa Theme.Radius al posto di cornerRadius con un numero.',
	},
	{
		name: 'swift-print', ...SWIFT, prep: noSwiftComments, find: all(/(?<![\w.])print\(/g),
		msg: 'print( nel codice: usa Log / Logger.',
	},
	{
		name: 'swift-legacy-api', ...SWIFT, prep: noSwiftComments,
		find: all(/\bNavigationView\b|\.foregroundColor\(|\bObservableObject\b|\bDispatchQueue\.main\b/g),
		msg: 'API superata: usa NavigationStack, .foregroundStyle, @Observable e il main actor / Task { @MainActor }.',
	},
	{
		name: 'web-color-literal', ...WEB, find: webColors,
		msg: 'Colore a mano (#hex, rgb(), rgba()): usa le variabili CSS di app.css (per la trasparenza color-mix o rgba(var(--x-rgb), a)).',
	},
	{
		name: 'web-font-px', ...WEB, find: all(/font-size\s*:\s*-?[\d.]+px/g),
		msg: 'font-size in px: usa un token di dimensione del testo definito in app.css (variabile CSS) al posto di px a mano.',
	},
	{
		name: 'emoji', ...EMOJI, find: all(/(?![©®™←-⇿])\p{Extended_Pictographic}/gu), // le frecce tipografiche (U+2190-21FF) sono testo, non emoji
		msg: 'Emoji (o simbolo pittografico) nel codice: niente emoji, usa un SVG sul web e un SF Symbol su iPhone.',
	},
]

const args = process.argv.slice(2)
const flag = (n) => args.includes(n)
const opt = (n) => { const i = args.findIndex((a) => a === n || a.startsWith(n + '=')); return i < 0 ? null : args[i].includes('=') ? args[i].split('=')[1] : args[i + 1] }
const root = opt('--root') ?? join(import.meta.dirname, '..')
const quiet = flag('--quiet')
const versionMode = opt('--version') ?? 'warn' // ponytail: avviso finché non si decide di allineare web e project.yml; poi --version=error o cambiare il default
const baselinePath = join(root, 'scripts', 'rules-baseline.json')

const walk = (d) => {
	if (!existsSync(d)) return []
	return readdirSync(d).flatMap((n) => {
		if (n === 'node_modules') return []
		const f = join(d, n)
		return statSync(f).isDirectory() ? walk(f) : [f]
	})
}
const rel = (f) => relative(root, f).split(sep).join('/')
const files = new Map() // percorso relativo -> testo, letto una volta sola
const read = (r) => { if (!files.has(r)) files.set(r, readFileSync(join(root, r), 'utf8')); return files.get(r) }

// conteggi: { regola: { file: { n, lines } } }
const current = {}
for (const rule of RULES) {
	const byFile = (current[rule.name] = {})
	const candidates = new Set(rule.dirs.flatMap((d) => walk(join(root, d)).map(rel)))
	for (const r of [...candidates].sort()) {
		if (!rule.exts.some((e) => r.endsWith(e)) || rule.exclude?.includes(r)) continue
		const text = read(r)
		const hits = rule.find(rule.prep ? rule.prep(text) : text)
		if (hits.length) byFile[r] = { n: hits.length, lines: hits.map((i) => text.slice(0, i).split('\n').length) }
	}
}

const sum = (o) => Object.values(o).reduce((a, b) => a + b, 0)
const toCounts = () => Object.fromEntries(Object.entries(current).map(([r, f]) => [r, Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v.n]))]))
const sorted = (o) => Object.fromEntries(Object.keys(o).sort().map((k) => [k, o[k]]))
const render = (c) => JSON.stringify(sorted(Object.fromEntries(Object.entries(c).filter(([, f]) => Object.keys(f).length).map(([r, f]) => [r, sorted(f)]))), null, 2) + '\n'

const out = (s) => console.log(s)
let failed = false

// ---- controllo versione
{
	const webPkg = join(root, 'web/package.json')
	const yml = join(root, 'project.yml')
	const norm = (v) => v.split('.').concat(['0', '0']).slice(0, 3).join('.') // 3.4 == 3.4.0
	if (!existsSync(webPkg) || !existsSync(yml)) {
		out('AVVISO version: web/package.json o project.yml non trovato, controllo saltato.')
	} else {
		const web = JSON.parse(readFileSync(webPkg, 'utf8')).version
		const ios = readFileSync(yml, 'utf8').match(/MARKETING_VERSION:\s*["']?([\d.]+)/)?.[1]
		if (!ios || !web) out('AVVISO version: versione non leggibile in web/package.json o project.yml.')
		else if (norm(web) !== norm(ios)) {
			const m = `version: web/package.json ${web} diverso da MARKETING_VERSION di project.yml ${ios}. Allinea le due versioni.`
			if (versionMode === 'error') { failed = true; out('ERRORE ' + m) } else out('AVVISO ' + m)
		} else if (!quiet) out(`version      ok (${web})`)
	}
}

// ---- baseline
const old = existsSync(baselinePath) ? JSON.parse(readFileSync(baselinePath, 'utf8')) : null
const counts = toCounts()

// aumenti rispetto alla baseline: [regola, file, n, baseline]
const rises = (base) => RULES.flatMap(({ name }) =>
	Object.entries(counts[name]).filter(([f, n]) => n > (base?.[name]?.[f] ?? 0)).map(([f, n]) => [name, f, n, base?.[name]?.[f] ?? 0]))

if (flag('--update')) {
	if (!old) {
		mkdirSync(dirname(baselinePath), { recursive: true })
		writeFileSync(baselinePath, render(counts))
		out('Baseline creata in scripts/rules-baseline.json con i conteggi attuali.')
	} else {
		const up = rises(old)
		if (up.length && !flag('--force')) {
			out('Baseline non aggiornata: rifiuto di alzare i conteggi (usa --force solo se è voluto).')
			for (const [r, f, n, b] of up) out(`  ${r}  ${f}: ${n} (baseline ${b})`)
			failed = true
		} else {
			const next = render(counts)
			if (next !== render(old)) writeFileSync(baselinePath, next)
			out(next === render(old) ? 'Baseline già allineata.' : 'Baseline aggiornata.')
		}
	}
}

const base = flag('--update') && !failed ? JSON.parse(readFileSync(baselinePath, 'utf8')) : old
if (!base) {
	out('Baseline assente: lancia con --update per crearla dai conteggi attuali.')
	failed = true
}

for (const rule of RULES) {
	const now = sum(counts[rule.name])
	const was = base ? sum(base[rule.name] ?? {}) : 0
	const bad = base ? rises(base).filter(([r]) => r === rule.name) : []
	if (bad.length) {
		failed = true
		out(`${rule.name.padEnd(22)} ${now} / ${was}  FALLITA`)
		out(`  ${rule.msg}`)
		for (const [, f, n, b] of bad) out(`  ${f}: ${n} (baseline ${b}) righe ${current[rule.name][f].lines.join(', ')}`)
	} else if (!quiet) {
		out(`${rule.name.padEnd(22)} ${now} / ${was}  ok${now < was ? '  (sotto la baseline: rilancia con --update)' : ''}`)
	}
}

process.exit(failed ? 1 : 0)
