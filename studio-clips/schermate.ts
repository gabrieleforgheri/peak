// Le clip delle schermate per yrb4g.com/studio/peak: da studio-clips/shots/ (scripts/studio-shots.ts) scrive una
// composizione HyperFrames per schermata e lingua. Sotto c'è la pagina vera senza grafici; sopra, ritagliati dalla
// pagina piena, i pezzi che compaiono uno dopo l'altro (barre che crescono, righe che arrivano), una freccia sul
// numero che conta e, se serve, la pagina che scorre. Alla fine torna com'era all'inizio: la clip gira in loop.
// Uso: node studio-clips/schermate.ts   poi   npx hyperframes render -c schermata-<nome>-<lingua>.html …
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const DIR = new URL('.', import.meta.url).pathname
const W = 390, H = 844, S = 2 // pixel CSS della schermata, composizione al doppio
type Rect = [number, number, number, number]
type Beat = { kind: 'up' | 'right' | 'pop' | 'fade'; at: number; step?: number; dur?: number; rects: Rect[] }
type Data = {
  height: number; tab: Rect | null; beats: Beat[]
  scroll: { at: number; dur: number; y: number }[]; callouts: { at: number; rect: Rect | null }[]
}

for (const f of readdirSync(join(DIR, 'shots')).filter((f) => f.endsWith('.json'))) {
  const shot = f.replace('.json', '')
  const d: Data = JSON.parse(readFileSync(join(DIR, 'shots', f), 'utf8'))
  const maxY = Math.max(0, d.height - H)
  const scroll = d.scroll.map((s) => ({ ...s, y: Math.min(maxY, Math.max(0, s.y)) }))
  const yAt = (t: number) => scroll.filter((s) => s.at + s.dur <= t).at(-1)?.y ?? 0
  // Solo i pezzi in vista si aspettano il turno; quelli sotto la piega compaiono subito, tanto non si vedono.
  const pieces: { r: Rect; kind: Beat['kind']; at: number; dur: number }[] = []
  for (const b of d.beats) {
    let i = 0
    for (const r of b.rects) {
      const y = yAt(b.at)
      const inView = r[1] + r[3] > y && r[1] < y + H
      pieces.push({ r, kind: b.kind, at: inView ? b.at + (b.step ?? 0) * i++ : b.at, dur: inView ? (b.dur ?? 0.5) : 0.01 })
    }
  }
  const callouts = d.callouts.filter((c) => c.rect)
  const built = Math.max(...pieces.map((p) => p.at + p.dur), ...scroll.map((s) => s.at + s.dur), ...callouts.map((c) => c.at + 1))
  const back = Math.round((built + 1.8) * 10) / 10
  const dur = Math.round((back + 0.8) * 10) / 10
  const px = (v: number) => `${Math.round(v * S * 10) / 10}px`
  const img = (suffix: string) => `shots/${shot}${suffix}.png`

  const rg = pieces.map((p, i) => {
    const [x, y, w, h] = p.r
    return `<div class="rg" id="p${i}" style="left:${px(x)};top:${px(y)};width:${px(w)};height:${px(h)}"><img src="${img('-full')}" alt="" style="left:${px(-x)};top:${px(-y)}"></div>`
  }).join('\n          ')

  // La freccia arriva dal lato con più spazio e finisce sul bordo dell'anello.
  const svg = callouts.map((c, i) => {
    const [x, y, w, h] = c.rect!.map((v) => v * S)
    const pad = 10, cy = y + h / 2
    const fromRight = x + w / 2 < (W * S) / 2
    const ex = fromRight ? x + w + pad + 8 : x - pad - 8
    const sx = fromRight ? Math.min(ex + 120, W * S - 24) : Math.max(ex - 120, 24)
    const sy = cy - 90
    const dir = fromRight ? 1 : -1
    // Un elemento largo quanto lo schermo non lascia spazio di lato: solo l'anello.
    const freccia = Math.abs(sx - ex) >= 70
      ? `<path class="arrow" id="a${i}" d="M${sx} ${sy} Q${sx} ${cy} ${ex} ${cy}" pathLength="1"/>
            <path class="head" id="h${i}" d="M${ex + 16 * dir} ${cy - 12} L${ex} ${cy} L${ex + 16 * dir} ${cy + 12}"/>`
      : ''
    return `<g id="c${i}">
            <rect class="ring" x="${x - pad}" y="${y - pad}" width="${w + pad * 2}" height="${h + pad * 2}" rx="18" pathLength="1"/>
            ${freccia}
          </g>`
  }).join('\n          ')

  const tab = d.tab ? `<div id="tab" style="top:${px(d.tab[1])};height:${px(d.tab[3])}"><img src="${img('-tab')}" alt="" style="top:0"></div>` : ''

  const tweens = [
    ...pieces.map((p, i) => {
      const from = { up: '{ clipPath: "inset(100% 0% 0% 0%)" }', right: '{ clipPath: "inset(0% 100% 0% 0%)" }', pop: '{ opacity: 0, scale: 0.6 }', fade: '{ opacity: 0, y: 36 }' }[p.kind]
      const to = { up: '{ clipPath: "inset(0% 0% 0% 0%)"', right: '{ clipPath: "inset(0% 0% 0% 0%)"', pop: '{ opacity: 1, scale: 1', fade: '{ opacity: 1, y: 0' }[p.kind]
      const ease = p.kind === 'pop' ? 'back.out(1.8)' : 'power3.out'
      return `tl.fromTo("#p${i}", ${from}, ${to}, duration: ${p.dur}, ease: "${ease}" }, ${p.at.toFixed(2)});`
    }),
    ...callouts.map((c, i) => [
      `tl.fromTo("#c${i} .ring", { strokeDashoffset: 1, opacity: 1 }, { strokeDashoffset: 0, duration: 0.5, ease: "power2.out" }, ${c.at.toFixed(2)});`,
      ...(svg.includes(`id="a${i}"`) ? [
        `tl.fromTo("#a${i}", { strokeDashoffset: 1 }, { strokeDashoffset: 0, duration: 0.45, ease: "power2.inOut" }, ${(c.at + 0.25).toFixed(2)});`,
        `tl.fromTo("#h${i}", { opacity: 0 }, { opacity: 1, duration: 0.15 }, ${(c.at + 0.65).toFixed(2)});`,
      ] : []),
    ].join('\n      ')),
    ...scroll.map((s) => `tl.to("#page", { y: ${-s.y * S}, duration: ${s.dur}, ease: "power2.inOut" }, ${s.at});`),
    // ritorno all'inizio: i pezzi spariscono mentre la pagina torna in cima
    `tl.to(".rg, #callouts", { opacity: 0, duration: 0.6, ease: "power1.in" }, ${back});`,
    `tl.to("#page", { y: 0, duration: 0.8, ease: "power2.inOut" }, ${back});`,
  ].join('\n      ')

  writeFileSync(join(DIR, `schermata-${shot}.html`), `<!doctype html>
<!-- Generato da studio-clips/schermate.ts da shots/${shot}.json: non modificare a mano. -->
<html lang="${shot.endsWith('-en') ? 'en' : 'it'}">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=${W * S}, height=${H * S}" />
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.14.2/dist/gsap.min.js"></script>
    <style>
      * { margin: 0; padding: 0; box-sizing: border-box; }
      html, body { width: ${W * S}px; height: ${H * S}px; overflow: hidden; background: #0b0d14; }
      #root { position: relative; width: 100%; height: 100%; overflow: hidden; background: #0b0d14; }
      #page { position: absolute; left: 0; top: 0; width: ${W * S}px; height: ${px(d.height)}; }
      #page > img, .rg img, #tab img { position: absolute; left: 0; width: ${W * S}px; height: auto; display: block; }
      #page > img { top: 0; }
      .rg { position: absolute; overflow: hidden; }
      #callouts { position: absolute; left: 0; top: 0; width: ${W * S}px; height: ${px(d.height)}; overflow: visible; }
      #callouts .ring, #callouts .arrow { fill: none; stroke: rgb(184, 240, 77); stroke-width: 5; stroke-linecap: round; stroke-dasharray: 1; }
      #callouts .ring { filter: drop-shadow(0 0 12px rgba(184, 240, 77, 0.6)); }
      #callouts .head { fill: none; stroke: rgb(184, 240, 77); stroke-width: 5; stroke-linecap: round; stroke-linejoin: round; }
      #tab { position: absolute; left: 0; width: ${W * S}px; overflow: hidden; }
    </style>
  </head>
  <body>
    <div id="root" data-composition-id="schermata" data-start="0" data-duration="${dur}" data-width="${W * S}" data-height="${H * S}">
      <section class="clip" id="scene" data-start="0" data-duration="${dur}" style="position:absolute;inset:0">
        <div id="page">
          <img src="${img('-empty')}" alt="" />
          ${rg}
          <svg id="callouts" viewBox="0 0 ${W * S} ${d.height * S}" aria-hidden="true">
          ${svg}
          </svg>
        </div>
        ${tab}
      </section>
    </div>
    <script>
      const tl = gsap.timeline({ paused: true });
      ${tweens}
      window.__timelines["schermata"] = tl;
      tl.seek(0);
    </script>
  </body>
</html>
`)
  console.log(`schermata-${shot}.html  ${dur}s  ${pieces.length} pezzi`)
}
