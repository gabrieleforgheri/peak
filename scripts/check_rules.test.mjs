// Prove di scripts/check_rules.mjs su una miniatura di repo in una cartella temporanea. Si lancia con: node --test scripts/
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import test from 'node:test'

const SCRIPT = join(import.meta.dirname, 'check_rules.mjs')

// File che violano (una regola a testa) e file che rispettano, nelle cartelle che le regole guardano.
const FILES = {
	'web/package.json': '{ "version": "1.0.0" }',
	'project.yml': 'settings:\n  MARKETING_VERSION: "1.0"\n',
	'Peak/Features/Bad.swift': [
		'Text("a").font(.system(size: 14))',
		'Color(hex: 0xFFFFFF)',
		'Text("b").padding(16).padding(.horizontal, 12)',
		'RoundedRectangle(cornerRadius: 10)',
		'print("x")',
		'NavigationView {}',
		'// print("commento") non conta',
	].join('\n'),
	'Peak/Features/Good.swift': 'Text("a").font(Theme.body).padding(Theme.Space.m).padding(.horizontal)\n',
	'Peak/Design/Theme.swift': 'let a = Color(hex: 0x000000); let b = 8; Text("").padding(8)\n', // Design è fuori ambito
	'web/src/app.css': ':root { --c: #112233; font-size: 16px; }',
	'web/src/Bad.svelte': '<style>.a { color: #fff; border: 1px solid #00000080; background: rgba(0, 0, 0, .5); font-size: 14px; }</style>',
	'web/src/Good.svelte': '<a href="#abc">x</a><svg><use href="#def"/></svg><i style="fill: url(#abc)"></i><style>.a { color: var(--text); background: rgba(var(--accent-rgb), .2); font-size: 1rem; }</style>',
	'web/src/lib/emoji.ts': 'export const s = "\u{1F600} ciao © ™"',
	'common/ok.ts': 'export const s = "niente"',
}

async function repo(extra = {}) {
	const root = await mkdtemp(join(tmpdir(), 'rules-'))
	for (const [p, c] of Object.entries({ ...FILES, ...extra })) {
		mkdirSync(dirname(join(root, p)), { recursive: true })
		writeFileSync(join(root, p), c)
	}
	return root
}
const run = (root, ...a) => {
	const r = spawnSync(process.execPath, [SCRIPT, '--root', root, ...a], { encoding: 'utf8' })
	return { code: r.status, out: r.stdout + r.stderr }
}
const baseline = (root) => JSON.parse(readFileSync(join(root, 'scripts/rules-baseline.json'), 'utf8'))
const setBaseline = (root, b) => { mkdirSync(join(root, 'scripts'), { recursive: true }); writeFileSync(join(root, 'scripts/rules-baseline.json'), JSON.stringify(b)) }

test('--update crea la baseline con i conteggi giusti per ogni famiglia', async () => {
	const root = await repo()
	const r = run(root, '--update')
	assert.equal(r.code, 0, r.out)
	const b = baseline(root)
	assert.deepEqual(b['swift-font-size'], { 'Peak/Features/Bad.swift': 1 })
	assert.deepEqual(b['swift-color-hex'], { 'Peak/Features/Bad.swift': 1 })
	assert.deepEqual(b['swift-padding-literal'], { 'Peak/Features/Bad.swift': 2 })
	assert.deepEqual(b['swift-radius-literal'], { 'Peak/Features/Bad.swift': 1 })
	assert.deepEqual(b['swift-print'], { 'Peak/Features/Bad.swift': 1 }) // la riga di commento non conta
	assert.deepEqual(b['swift-legacy-api'], { 'Peak/Features/Bad.swift': 1 })
	assert.deepEqual(b['web-color-literal'], { 'web/src/Bad.svelte': 3 }) // gli href e url(#id) di Good non contano
	assert.deepEqual(b['web-font-px'], { 'web/src/Bad.svelte': 1 })
	assert.deepEqual(b.emoji, { 'web/src/lib/emoji.ts': 1 }) // (c) e tm non sono emoji
	assert.equal(run(root).code, 0)
})

test('un conteggio sopra la baseline fallisce e dice cosa usare', async () => {
	const root = await repo()
	run(root, '--update')
	const b = baseline(root)
	b['swift-padding-literal']['Peak/Features/Bad.swift'] = 1
	setBaseline(root, b)
	const r = run(root)
	assert.equal(r.code, 1)
	assert.match(r.out, /swift-padding-literal.*FALLITA/)
	assert.match(r.out, /Theme\.Space/)
})

test('un file nuovo con violazioni fallisce, uno pulito no', async () => {
	const root = await repo()
	run(root, '--update')
	writeFileSync(join(root, 'Peak/Features/Clean.swift'), 'Text("").padding(Theme.Space.s)\n')
	assert.equal(run(root).code, 0)
	writeFileSync(join(root, 'Peak/Features/New.swift'), 'Text("").padding(4)\n')
	const r = run(root)
	assert.equal(r.code, 1)
	assert.match(r.out, /New\.swift: 1 \(baseline 0\)/)
})

test('--update non alza la baseline, la abbassa; --force la alza', async () => {
	const root = await repo()
	run(root, '--update')
	const before = readFileSync(join(root, 'scripts/rules-baseline.json'), 'utf8')
	writeFileSync(join(root, 'Peak/Features/New.swift'), 'Text("").padding(4)\n')
	assert.equal(run(root, '--update').code, 1)
	assert.equal(readFileSync(join(root, 'scripts/rules-baseline.json'), 'utf8'), before)
	assert.equal(run(root, '--update', '--force').code, 0)
	assert.equal(baseline(root)['swift-padding-literal']['Peak/Features/New.swift'], 1)
	writeFileSync(join(root, 'Peak/Features/New.swift'), 'Text("")\n')
	assert.match(run(root).out, /rilancia con --update/)
	assert.equal(run(root, '--update').code, 0)
	assert.equal(baseline(root)['swift-padding-literal']['Peak/Features/New.swift'], undefined)
})

test('il controllo versione segnala la differenza (errore di default, avviso con --version=warn)', async () => {
	const root = await repo({ 'web/package.json': '{ "version": "2.0.0" }' })
	run(root, '--update')
	const err = run(root)
	assert.equal(err.code, 1)
	assert.match(err.out, /ERRORE version: .*2\.0\.0.*1\.0/)
	const warn = run(root, '--version=warn')
	assert.equal(warn.code, 0)
	assert.match(warn.out, /AVVISO version/)
	// 1.0 e 1.0.0 sono la stessa versione: nessun avviso
	const same = run(await repo(), '--update')
	assert.doesNotMatch(same.out, /AVVISO/)
})
