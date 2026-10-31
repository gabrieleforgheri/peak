// Elenca i testi italiani nel Swift dell'iPhone/Watch/widget senza traduzione (Localizable.strings + web/src/lib/en.json).
// Uso, dalla radice del repo: node scripts/check-i18n-ios.mjs [cartella…]   (exit 1 se ne manca qualcuno)
// Regole di Lang.tr: chiave esatta, oppure modello con %@/%lld. Le parole italiane sono quelle di en.json/Localizable senza controparte inglese.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

// codice che non è interfaccia: parole chiave, campi dell'API, regex, nomi di file
const SKIP = ['Peak/Design/MachineGlyph.swift', 'Peak/Model/Plates.swift', 'Peak/Model/Programs.swift', 'Peak/Model/Catalog.swift', 'Peak/Model/Limbs.swift',
	'Peak/Store/PeakAPI.swift', 'Peak/Store/PeakSocial.swift', 'Peak/Store/PeakLogin.swift']

const walk = (d) => readdirSync(d).flatMap((n) => { const f = join(d, n); return statSync(f).isDirectory() ? walk(f) : [f] })
const unq = (s) => s.replace(/\\"/g, '"').replace(/\\n/g, '\n')
const dict = new Map()
for (const m of readFileSync('Peak/en.lproj/Localizable.strings', 'utf8').matchAll(/^"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)";/gm)) dict.set(unq(m[1]), m[2])
const en = JSON.parse(readFileSync('web/src/lib/en.json', 'utf8'))
for (const [k, v] of Object.entries(en)) { const key = k.replace(/\{\d\}/g, '%@'); if (!k.includes('|') && !dict.has(key)) dict.set(key, v) }

const words = (s) => s.toLowerCase().split(/[^a-zà-ÿ']+/).filter((w) => w.length >= 3)
const enW = new Set([...Object.values(en), ...dict.values()].flatMap(words))
const itOnly = new Set([...Object.keys(en), ...dict.keys()].flatMap((k) => words(k.split('|')[0])).filter((w) => !enW.has(w)))
for (const w of ['serie', 'poi', 'recupero', 'allenamento', 'corso', 'apri', 'salva', 'nessun', 'nessuna', 'già', 'più']) itOnly.add(w)
const pats = [...dict.keys()].filter((k) => /%@|%lld|%lf/.test(k)).map((k) => new RegExp('^' + k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/%@|%lld|%lf/g, '(.+?)').replace(/%%/g, '%') + '$', 's'))
const known = (s) => dict.has(s) || pats.some((r) => r.test(s))

// \(…) con anche stringhe e parentesi dentro: si sostituisce per prima, così le virgolette interne non spezzano il letterale
const STR = '"(?:[^"\\\\]|\\\\.)*"'
let paren = `\\((?:[^()"]|${STR})*\\)` // parentesi annidate fino a 3 livelli
for (let i = 0; i < 2; i++) paren = `\\((?:[^()"]|${STR}|${paren})*\\)`
const INTERP = new RegExp('\\\\' + paren, 'g')
const dirs = process.argv.slice(2).length ? process.argv.slice(2) : ['Peak', 'Shared'] // widget e Watch non usano Lang: hanno l'inglese nel codice
let n = 0
for (const f of dirs.flatMap(walk).filter((f) => f.endsWith('.swift') && !SKIP.includes(f))) {
	readFileSync(f, 'utf8').split('\n').forEach((l, i) => {
		const code = l.replace(INTERP, '\x01').replace(/\/\/.*$/, '')
		if (/^\s*(\/\/|\/\*|\*)/.test(l) || /print\(|Logger|forKey:|systemName:|named:|forResource|UserDefaults|\.font\(/.test(code)) return
		for (const m of code.matchAll(/"((?:[^"\\]|\\.)*)"/g)) {
			const raw = m[1]
			if (!words(raw.replace(/\x01/g, ' ')).some((w) => itOnly.has(w))) continue
			const key = raw.replace(/\x01/g, '%@').replace(/\\"/g, '"')
			// frammenti di sola interpolazione e punteggiatura («)», « per lato») non sono frasi
			if (!/[a-zà-ÿ]{3}/i.test(key.replace(/%@/g, ''))) continue
			if (known(raw.replace(/\x01/g, '7').replace(/\\"/g, '"')) || known(key) || known(key.replace(/%@/g, '%lld'))) continue
			n++; console.log(`${f}:${i + 1}: ${raw.replace(/\x01/g, '%@').slice(0, 110)}`)
		}
	})
}
console.log(n, 'testi italiani senza traduzione')
process.exit(n ? 1 : 0)
