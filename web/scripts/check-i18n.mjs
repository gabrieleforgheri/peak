// Controlla che ogni testo passato a t()/tn() abbia la traduzione in src/lib/en.json, e che en.json non abbia voci orfane.
// Uso: node scripts/check-i18n.mjs  (esce con 1 se manca qualcosa)
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const walk = (d) => readdirSync(d).flatMap((n) => { const f = join(d, n); return statSync(f).isDirectory() ? walk(f) : [f] })
const files = walk('src').filter((f) => /\.(svelte|ts)$/.test(f))
const keys = new Set()
const str = String.raw`'((?:[^'\\\n]|\\.)*)'`
const unesc = (s) => s.replace(/\\(['\\])/g, '$1')
for (const f of files) {
	const src = readFileSync(f, 'utf8')
	for (const m of src.matchAll(new RegExp(String.raw`\bt\(\s*` + str, 'g'))) keys.add(unesc(m[1]))
	// tn(n, 'uno', 'molti'): le due chiavi
	for (const m of src.matchAll(new RegExp(String.raw`\btn\([^,]+,\s*` + str + String.raw`\s*,\s*` + str, 'g'))) { keys.add(unesc(m[1])); keys.add(unesc(m[2])) }
}
const en = JSON.parse(readFileSync('src/lib/en.json', 'utf8'))
const missing = [...keys].filter((k) => !(k in en)).sort()
const orphan = Object.keys(en).filter((k) => !keys.has(k)).sort()
if (process.argv.includes('--list')) console.log(JSON.stringify(missing, null, 1))
console.log(`${keys.size} testi, ${missing.length} senza traduzione, ${orphan.length} voci inutilizzate`)
for (const k of missing) console.log('  manca:', k)
for (const k of orphan) console.log('  inutile:', k)
process.exit(missing.length ? 1 : 0)
