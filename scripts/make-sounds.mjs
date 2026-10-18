#!/usr/bin/env node
// Sintetizza i suoni di Peak (originali, nessun campione) e li converte in .m4a (AAC 128k mono) per web e iPhone:
// web/src/sounds/<nome>.m4a e Peak/Sounds/<nome>.m4a. Serve macOS (afconvert). Uso: node scripts/make-sounds.mjs
// Timbro "ricompensa da gioco": marimba/campanelli a parziali con attacco 2-5 ms e decadimento esponenziale, un filo di
// pluck, coda leggera da delay con feedback. Il rumore ha il seme fisso: rilanciarlo dà gli stessi campioni (i .m4a cambiano solo per la data nel contenitore).
import { mkdtempSync, writeFileSync, readFileSync, mkdirSync, copyFileSync, statSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const SR = 44100
const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const outDirs = [join(root, 'web/src/sounds'), join(root, 'Peak/Sounds')]
const CEIL_DB = -1.5 // tetto del picco prima di AAC, che può sforare di mezzo dB in decodifica
const BIG = 2.5 // i suoni "grandi" (record, fine, serie, obiettivo, medaglia) sono 2,5 dB sopra i giorni; set e remove 7 dB sotto

/** 'C#6' -> Hz */
const hz = (name) => {
	const m = /^([A-G])(#|b)?(\d)$/.exec(name)
	if (!m) throw new Error(`nota? ${name}`)
	const midi = 12 * (Number(m[3]) + 1) + { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 }[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0)
	return 440 * 2 ** ((midi - 69) / 12)
}

const mulberry32 = (a) => () => { a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296 }

/** Un suono in costruzione: dur secondi, campioni in x. Ogni voce gira fino alla fine: a chiuderla ci pensa la dissolvenza finale. */
const song = (dur) => ({ dur, n: Math.round(dur * SR), x: new Float64Array(Math.round(dur * SR)) })
const put = (S, t, sig, g = 1) => { const o = Math.round(t * SR); for (let i = 0; i < sig.length && o + i < S.n; i++) S.x[o + i] += sig[i] * g }

/** Somma di parziali [rapporto, ampiezza, decadimento relativo]; attacco a coseno rialzato (parte da zero), poi exp(-t/tau). */
function partials(f, parts, len, tau, attack = 0.003) {
	const n = Math.round(len * SR), out = new Float64Array(n), na = Math.round(attack * SR)
	for (const [r, a, d] of parts) {
		if (f * r > SR * 0.45) continue // niente aliasing
		const w = (2 * Math.PI * f * r) / SR, k = 1 / (tau * d * SR)
		for (let i = 0; i < n; i++) out[i] += a * Math.sin(w * i) * Math.exp(-i * k)
	}
	for (let i = 0; i < na && i < n; i++) out[i] *= 0.5 - 0.5 * Math.cos((Math.PI * i) / na)
	return out
}

/** Rumore passa-banda (filtro a variabili di stato TPT): fc(u) e env(u) con u in 0..1 sulla durata. */
function bandNoise(len, fc, env, rnd, k = 0.5) {
	const n = Math.round(len * SR), out = new Float64Array(n)
	let c1 = 0, c2 = 0
	for (let i = 0; i < n; i++) {
		const u = i / n, g = Math.tan((Math.PI * fc(u)) / SR), a1 = 1 / (1 + g * (g + k)), a2 = g * a1, a3 = g * a2
		const v3 = (rnd() * 2 - 1) - c2, v1 = a1 * c1 + a2 * v3, v2 = c2 + a2 * c1 + a3 * v3
		c1 = 2 * v1 - c1; c2 = 2 * v2 - c2
		out[i] = v1 * env(u)
	}
	return out
}

// Voci. Il guadagno è relativo: il livello finale lo decide la normalizzazione.
const bell = (S, t, n, g = 1, tau = 0.2) => put(S, t, partials(hz(n), [[1, 1, 1], [2, 0.42, 0.5], [3, 0.18, 0.3], [4.2, 0.09, 0.2]], S.dur - t, tau, 0.0025), g)
/** Campanello puro e cortissimo, per le scintille. */
const spark = (S, t, n, g = 0.1, tau = 0.05) => put(S, t, partials(hz(n), [[1, 1, 1], [2, 0.2, 0.5]], S.dur - t, tau, 0.002), g)
/** Accordo caldo: fondamentale, ottava e quinta morbide, più una gemella scordata di poco che fa respirare la nota. */
const chime = (S, t, n, g = 1, tau = 0.3) => put(S, t, partials(hz(n), [[1, 1, 1], [1.0018, 0.5, 1], [2, 0.25, 0.6], [3, 0.07, 0.4]], S.dur - t, tau, 0.004), g)
/** Campana metallica: parziali inarmoniche e una gemella scordata che batte (lo shimmer). */
const metal = (S, t, n, g = 1, tau = 0.4) => put(S, t, partials(hz(n), [[1, 1, 1], [1.004, 0.7, 1], [2, 0.3, 0.6], [2.76, 0.14, 0.4], [4.07, 0.06, 0.3]], S.dur - t, tau, 0.003), g)
/** Marimba: fondamentale, quarta e decima parziale (accordatura delle barre) e un colpetto di rumore filtrato. */
function marimba(S, t, n, g = 1, tau = 0.075) {
	const f = hz(n)
	put(S, t, partials(f, [[1, 1, 1], [4, 0.3, 0.35], [10, 0.06, 0.12]], S.dur - t, tau, 0.002), g)
	put(S, t, bandNoise(0.03, () => f * 3, (u) => Math.min(1, u * 40) * Math.exp(-u * 30), mulberry32(Math.round(f))), g * 0.12)
}
/** Pop: sinusoide con la seconda armonica e un piccolo scivolo di intonazione all'inizio. */
function blip(S, t, n, g = 1, tau = 0.04) {
	const f = hz(n), len = S.dur - t, m = Math.round(len * SR), out = new Float64Array(m), na = Math.round(0.0025 * SR)
	let ph = 0
	for (let i = 0; i < m; i++) {
		const s = i / SR
		ph += (2 * Math.PI * f * (1 + 0.05 * Math.exp(-s / 0.008))) / SR
		out[i] = (Math.sin(ph) + 0.3 * Math.sin(2 * ph)) * Math.exp(-s / tau) * (i < na ? 0.5 - 0.5 * Math.cos((Math.PI * i) / na) : 1)
	}
	put(S, t, out, g)
}

/** Delay con feedback e passa-basso nel ciclo: la coda morbida. taps = [secondi, guadagno]; wet scala tutti i guadagni. */
function tail(S, taps, wet = 1, lpHz = 3500) {
	const x = S.x, y = new Float64Array(S.n), a = Math.exp((-2 * Math.PI * lpHz) / SR)
	const tp = taps.map(([d, g]) => [Math.round(d * SR), g * wet]), lp = tp.map(() => 0)
	for (let i = 0; i < S.n; i++) {
		let s = x[i]
		tp.forEach(([d, g], j) => { if (i >= d) { lp[j] = (1 - a) * y[i - d] + a * lp[j]; s += g * lp[j] } })
		y[i] = s
	}
	S.x = y
}
const TAPS = [[0.061, 0.26], [0.097, 0.2], [0.143, 0.14]] // somma 0,6: stabile
const TAPS_SHORT = [[0.043, 0.3], [0.071, 0.2]]

/** Dissolvenza di chiusura a coseno: l'ultimo campione è zero. */
function fadeOut(S, sec) {
	const m = Math.round(sec * SR)
	for (let i = 0; i < m; i++) S.x[S.n - 1 - i] *= 0.5 - 0.5 * Math.cos((Math.PI * i) / m)
	S.x[S.n - 1] = 0
}

// ---- i suoni ----
const DAYS = ['C5', 'D5', 'E5', 'G5', 'A5', 'C6', 'D6'] // pentatonica maggiore: i giorni in fila fanno una melodia che sale
const defs = {
	// salvata una serie: due pop piccoli e morbidi
	set: { trim: -7, build() { const S = song(0.25); blip(S, 0, 'G5', 1); blip(S, 0.055, 'C6', 0.9); fadeOut(S, 0.05); return S } },
	// record: il "ding-ding" luminoso di una risposta giusta (E6 -> A6) con una scintilla
	record: { trim: BIG, build() {
		const S = song(0.8)
		bell(S, 0, 'E6', 1, 0.22); bell(S, 0.09, 'A6', 1.05, 0.26)
		spark(S, 0.17, 'A7', 0.11); spark(S, 0.215, 'C#8', 0.09); spark(S, 0.26, 'E8', 0.07)
		tail(S, TAPS, 1); fadeOut(S, 0.08); return S
	} },
	// fine allenamento: arpeggio C E G C che atterra su un accordo caldo di Do maggiore, con un velo di campanelli
	finish: { trim: BIG, build() {
		const S = song(1.6)
		;['C5', 'E5', 'G5', 'C6'].forEach((n, i) => bell(S, i * 0.08, n, i === 3 ? 1.05 : 1, 0.16))
		chime(S, 0.32, 'C4', 0.35)
		;['C5', 'E5', 'G5', 'C6'].forEach((n) => chime(S, 0.32, n, 0.5))
		bell(S, 0.32, 'G6', 0.3, 0.45); bell(S, 0.34, 'C7', 0.2, 0.4)
		tail(S, TAPS, 1); fadeOut(S, 0.12); return S
	} },
	// serie settimanale +1: soffio d'aria che sale e si scioglie in un tocco caldo (D6 + A6)
	streak: { trim: BIG, build() {
		const S = song(1.2), len = 0.3, rnd = mulberry32(7)
		const sm = (u) => { const v = Math.min(1, Math.max(0, (u - 0.85) / 0.15)); return v * v * (3 - 2 * v) } // chiusura dolce negli ultimi 15%
		const w = bandNoise(len, (u) => 500 * (4500 / 500) ** u, (u) => u * u * (1 - sm(u)), rnd, 0.45)
		const pk = w.reduce((m, v) => Math.max(m, Math.abs(v)), 0)
		put(S, 0, w, 0.32 / pk)
		chime(S, 0.26, 'D6', 1, 0.32); chime(S, 0.26, 'A6', 0.8, 0.32); bell(S, 0.28, 'D7', 0.12, 0.2)
		tail(S, TAPS, 0.8); fadeOut(S, 0.1); return S
	} },
	// obiettivo raggiunto: arpeggio trionfale G B D G, accordo di Sol e un trillo di scintille
	goal: { trim: BIG, build() {
		const S = song(1.4)
		;['G5', 'B5', 'D6', 'G6'].forEach((n, i) => bell(S, i * 0.07, n, i === 3 ? 1.05 : 1, 0.16))
		chime(S, 0.28, 'G4', 0.3)
		;['G5', 'B5', 'D6'].forEach((n) => chime(S, 0.28, n, 0.5)); bell(S, 0.28, 'G6', 0.4, 0.4)
		;['G7', 'B7', 'G7', 'B7', 'G7', 'B7'].forEach((n, i) => spark(S, 0.36 + i * 0.04, n, 0.16 - i * 0.02, 0.05))
		tail(S, TAPS, 1); fadeOut(S, 0.1); return S
	} },
	// livello di medaglia: grappolo metallico A6 C#7 E7 che brilla e svanisce piano
	medal: { trim: BIG, build() {
		const S = song(1.2)
		;['A6', 'C#7', 'E7'].forEach((n, i) => metal(S, i * 0.045, n, [1, 0.9, 0.85][i], 0.4))
		chime(S, 0, 'A5', 0.2, 0.35)
		tail(S, TAPS, 1.2); fadeOut(S, 0.1); return S
	} },
	// serie tolta: due pop discendenti, piano
	remove: { trim: -7, build() { const S = song(0.25); blip(S, 0, 'E5', 1); blip(S, 0.06, 'B4', 0.85); fadeOut(S, 0.05); return S } }
}
DAYS.forEach((n, i) => { defs[`day${i + 1}`] = { build() { const S = song(0.3); marimba(S, 0, n); tail(S, TAPS_SHORT, 0.5); fadeOut(S, 0.05); return S } } })

// ---- misure e normalizzazione ----
const peakOf = (x) => x.reduce((m, v) => Math.max(m, Math.abs(v)), 0)
const db = (v) => 20 * Math.log10(Math.max(v, 1e-9))
/** Sonorità: massimo dell'RMS su finestre di 250 ms (passo 10 ms). */
function loud(x) {
	const w = Math.min(x.length, Math.round(0.25 * SR)), hop = Math.round(0.01 * SR)
	let best = 0
	for (let s = 0; s + w <= x.length; s += hop) { let e = 0; for (let i = s; i < s + w; i++) e += x[i] * x[i]; best = Math.max(best, e / w) }
	return Math.sqrt(best)
}

function wav(x) {
	const b = Buffer.alloc(44 + x.length * 2)
	b.write('RIFF', 0); b.writeUInt32LE(36 + x.length * 2, 4); b.write('WAVEfmt ', 8); b.writeUInt32LE(16, 16)
	b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34)
	b.write('data', 36); b.writeUInt32LE(x.length * 2, 40)
	x.forEach((v, i) => b.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(v * 32767))), 44 + i * 2))
	return b
}
function readWav(file) {
	const b = readFileSync(file)
	for (let p = 12; p + 8 <= b.length;) {
		const id = b.toString('ascii', p, p + 4), size = b.readUInt32LE(p + 4)
		if (id === 'data') return Float64Array.from({ length: Math.floor(size / 2) }, (_, i) => b.readInt16LE(p + 8 + i * 2) / 32768)
		p += 8 + size + (size & 1)
	}
	throw new Error('wav senza data')
}

const built = Object.entries(defs).map(([name, d]) => ({ name, trim: d.trim ?? 0, S: d.build() }))
for (const { name, S } of built) if (!S.x.every(Number.isFinite)) throw new Error(`${name}: valori non finiti`)
// sonorità comune: la più alta a cui nessun suono supera il tetto (con il suo scarto in dB)
const ceil = 10 ** (CEIL_DB / 20)
const target = Math.min(...built.map(({ S, trim }) => (ceil * loud(S.x)) / peakOf(S.x) / 10 ** (trim / 20)))
const tmp = mkdtempSync(join(tmpdir(), 'peak-sounds-'))
for (const d of outDirs) mkdirSync(d, { recursive: true })

console.log('nome     durata   picco     RMS250   fine/picco  media    picco dopo AAC  byte')
for (const { name, trim, S } of built) {
	const g = (target * 10 ** (trim / 20)) / loud(S.x)
	const x = S.x.map((v) => v * g)
	const wavFile = join(tmp, `${name}.wav`), m4a = join(tmp, `${name}.m4a`), back = join(tmp, `${name}-dec.wav`)
	writeFileSync(wavFile, wav(x))
	execFileSync('afconvert', ['-f', 'm4af', '-d', 'aac', '-b', '128000', wavFile, m4a])
	for (const d of outDirs) copyFileSync(m4a, join(d, `${name}.m4a`))
	execFileSync('afconvert', ['-f', 'WAVE', '-d', 'LEI16', m4a, back]) // controllo: si decodifica e non sfora
	const dec = readWav(back), mean = x.reduce((s, v) => s + v, 0) / x.length
	const tailLvl = peakOf(x.subarray(Math.round(x.length - 0.06 * SR), Math.round(x.length - 0.05 * SR)))
	console.log(`${name.padEnd(8)} ${(x.length / SR).toFixed(2)} s  ${db(peakOf(x)).toFixed(1).padStart(6)} dB ${db(loud(x)).toFixed(1).padStart(6)} dB ${db(tailLvl / peakOf(x)).toFixed(0).padStart(6)} dB  ${(mean * 1e5).toFixed(1).padStart(5)}e-5  ${db(peakOf(dec)).toFixed(1).padStart(6)} dB   ${statSync(m4a).size}`)
	if (peakOf(dec) >= 0.98) throw new Error(`${name}: sfora dopo AAC`)
	if (Math.abs(x[0]) > 1e-4 || Math.abs(x[x.length - 1]) > 1e-4) throw new Error(`${name}: non parte o non finisce da zero`)
}
rmSync(tmp, { recursive: true, force: true })
