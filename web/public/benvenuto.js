// Benvenuto di Peak: sei scene in ciclo (il piano è in brag-output-*/brag-plan.md). Solo italiano e inglese, come l'app.
const en = {
	tag: 'Your real max.', maxes: 'Maxes', bench: 'Bench press', cap2: 'The weight you actually lifted, on every machine.', tpl: 'Routines', ex3: '3 exercises', capT: 'Your routine, ready for every workout.',
	set: 'Set', new: 'NEW RECORD', cap3: 'A record? You see it right away.', prog: 'Progress', gain: 'Biggest gainers',
	cap4: 'Week after week.', cap5: 'On the web and on iPhone.<br />Always in sync.', login: 'Sign in with yG', note: 'Peak uses your yG account.'
}
if (!navigator.language?.toLowerCase().startsWith('it')) {
	document.documentElement.lang = 'en'
	for (const el of document.querySelectorAll('[data-t]')) el.innerHTML = en[el.dataset.t]
}

const scenes = [...document.querySelectorAll('.scene')]
const dots = [...document.querySelectorAll('#dots i')]
const count = document.getElementById('count')
const durations = [3200, 5500, 5000, 4500, 4500, 4500]

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

function show(n) {
	place(n)
	scenes.forEach((s, i) => s.classList.toggle('on', i === n))
	dots.forEach((d, i) => d.classList.toggle('on', i === n))
	if (n === 3) { // la serie batte il record: 180 → 190
		count.textContent = '180'
		setTimeout(() => {
			const t0 = performance.now()
			const tick = (t) => {
				const k = Math.min(1, (t - t0) / 1000)
				count.textContent = String(Math.round(180 + 10 * k))
				if (k < 1) requestAnimationFrame(tick)
			}
			requestAnimationFrame(tick)
		}, 1100)
	}
}

if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
	show(5) // senza movimento: l'ultima scena, ferma
} else {
	let n = 0
	const next = () => { show(n); setTimeout(next, durations[n]); n = (n + 1) % scenes.length }
	next()
}
