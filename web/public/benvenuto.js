// Benvenuto di Peak: sei scene in ciclo (il piano è in brag-output-*/brag-plan.md). Solo italiano e inglese, come l'app.
const en = {
	tag: 'Your real max.', maxes: 'Maxes', bench: 'Bench press', cap2: 'The weight you actually lifted, on every machine.', tpl: 'Routines', ex3: '3 exercises', capT: 'Your routine, ready for every workout.',
	set: 'Set', new: 'NEW RECORD', cap3: 'A record? You see it right away.', prog: 'Progress', gain: 'Biggest gainers',
	cap4: 'Week after week.', cap5: 'On the web and on iPhone.<br />Always in sync.', login: 'Sign in with yG', note: 'Peak uses your yG account.'
}
// la lingua scelta nelle impostazioni dell'app (stessa chiave di src/lib/i18n.ts), altrimenti quella del browser
let chosen = null
try { chosen = localStorage.getItem('peak.lang') } catch { /* modalità privata */ }
if (chosen !== 'it' && (chosen === 'en' || !navigator.language?.toLowerCase().startsWith('it'))) {
	document.documentElement.lang = 'en'
	for (const el of document.querySelectorAll('[data-t]')) el.innerHTML = en[el.dataset.t]
}

const scenes = [...document.querySelectorAll('.scene')]
const dots = [...document.querySelectorAll('#dots i')]
const count = document.getElementById('count')
const durations = [2600, 4400, 4200, 4000, 4000, 3600]

const logo = document.getElementById('logo')
const rect = (id) => document.getElementById(id).getBoundingClientRect()
let at = 0
// grande sullo slot della prima scena, piccolo (40px) sul riquadro in alto a sinistra in tutte le altre
function place(n = at) {
	at = n
	const r = rect(n === 0 ? 'slot' : 'dock')
	logo.style.transform = `translate(${r.left}px, ${r.top}px) scale(${r.width / 96})`
	logo.classList.add('ready')
}
addEventListener('resize', () => place())

let counter
function show(n) {
	place(n)
	scenes.forEach((s, i) => s.classList.toggle('on', i === n))
	dots.forEach((d, i) => d.classList.toggle('on', i === n))
	clearTimeout(counter)
	count.textContent = '180'
	if (n === 3) { // la serie batte il record: 180 → 190
		counter = setTimeout(() => {
			const t0 = performance.now()
			const tick = (t) => {
				const k = Math.min(1, (t - t0) / 700)
				count.textContent = String(Math.round(180 + 10 * k))
				if (k < 1 && at === 3) requestAnimationFrame(tick)
			}
			requestAnimationFrame(tick)
		}, 700)
	}
}

// il giro automatico riparte da ogni scena, anche quando la scelta è a mano
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches
let timer
let current = 0
function go(n) {
	current = (n + scenes.length) % scenes.length
	show(current)
	clearTimeout(timer)
	if (!reduced) timer = setTimeout(() => go(current + 1), durations[current])
}
go(reduced ? scenes.length - 1 : 0) // senza movimento: l'ultima scena, ferma; si sfoglia a mano

// a mano: swipe o tocco sulla scena (il terzo a sinistra torna indietro), puntini, frecce
const stage = document.getElementById('stage')
let down
stage.addEventListener('pointerdown', (e) => { down = { x: e.clientX, y: e.clientY } })
stage.addEventListener('pointerup', (e) => {
	if (!down) return
	const dx = e.clientX - down.x, dy = e.clientY - down.y
	down = undefined
	if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) go(current + (dx < 0 ? 1 : -1))
	else if (Math.abs(dx) < 10 && Math.abs(dy) < 10) go(current + (e.clientX - stage.getBoundingClientRect().left < stage.clientWidth / 3 ? -1 : 1))
})
stage.addEventListener('pointercancel', () => { down = undefined })
dots.forEach((d, i) => d.addEventListener('click', () => go(i)))
addEventListener('keydown', (e) => {
	if (e.key === 'ArrowRight') go(current + 1)
	else if (e.key === 'ArrowLeft') go(current - 1)
})
