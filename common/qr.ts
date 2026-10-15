// Codice QR senza dipendenze: modalità byte, correzione d'errore M, versioni 1–10 (fino a 213 byte).
// Segue l'algoritmo di riferimento di Nayuki (QR Code generator, licenza MIT) ridotto a ciò che serve per un link.

const ECC_PER_BLOCK = [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26] // livello M, per versione
const BLOCKS = [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5]
const MAX_VERSION = 10

const rawModules = (ver: number): number => {
  let n = (16 * ver + 128) * ver + 64
  if (ver >= 2) { const a = Math.floor(ver / 7) + 2; n -= (25 * a - 10) * a - 55; if (ver >= 7) n -= 36 }
  return n
}
const dataCodewords = (ver: number): number => Math.floor(rawModules(ver) / 8) - ECC_PER_BLOCK[ver] * BLOCKS[ver]

function mul(x: number, y: number): number {
  let z = 0
  for (let i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11d); z ^= ((y >>> i) & 1) * x }
  return z
}
function divisor(degree: number): number[] {
  const out = new Array<number>(degree).fill(0)
  out[degree - 1] = 1
  let root = 1
  for (let i = 0; i < degree; i++) {
    for (let j = 0; j < out.length; j++) { out[j] = mul(out[j], root); if (j + 1 < out.length) out[j] ^= out[j + 1] }
    root = mul(root, 2)
  }
  return out
}
function remainder(data: number[], div: number[]): number[] {
  const out = new Array<number>(div.length).fill(0)
  for (const b of data) {
    const factor = b ^ out.shift()!
    out.push(0)
    div.forEach((c, i) => { out[i] ^= mul(c, factor) })
  }
  return out
}

/** Le righe del QR: `true` = modulo scuro. Il testo è codificato in UTF-8. */
export function qrMatrix(text: string): boolean[][] {
  const bytes = [...new TextEncoder().encode(text)]
  let ver = 1
  while (ver <= MAX_VERSION && 4 + (ver <= 9 ? 8 : 16) + bytes.length * 8 > dataCodewords(ver) * 8) ver++
  if (ver > MAX_VERSION) throw new RangeError('testo troppo lungo per il QR')

  // dati: modo byte, lunghezza, byte, terminatore e riempimento
  const bits: number[] = []
  const put = (v: number, n: number) => { for (let i = n - 1; i >= 0; i--) bits.push((v >>> i) & 1) }
  put(4, 4); put(bytes.length, ver <= 9 ? 8 : 16); bytes.forEach((b) => put(b, 8))
  const cap = dataCodewords(ver) * 8
  put(0, Math.min(4, cap - bits.length))
  while (bits.length % 8) bits.push(0)
  for (let pad = 0xec; bits.length < cap; pad ^= 0xec ^ 0x11) put(pad, 8)
  const data: number[] = []
  for (let i = 0; i < bits.length; i += 8) data.push(parseInt(bits.slice(i, i + 8).join(''), 2))

  // blocchi con correzione d'errore, intercalati
  const nBlocks = BLOCKS[ver], eccLen = ECC_PER_BLOCK[ver]
  const raw = Math.floor(rawModules(ver) / 8)
  const nShort = nBlocks - (raw % nBlocks), shortLen = Math.floor(raw / nBlocks)
  const div = divisor(eccLen)
  const blocks: number[][] = []
  for (let i = 0, k = 0; i < nBlocks; i++) {
    const d = data.slice(k, k + shortLen - eccLen + (i < nShort ? 0 : 1))
    k += d.length
    const ecc = remainder(d, div)
    if (i < nShort) d.push(0)
    blocks.push(d.concat(ecc))
  }
  const words: number[] = []
  for (let i = 0; i < blocks[0].length; i++) blocks.forEach((b, j) => { if (i !== shortLen - eccLen || j >= nShort) words.push(b[i]) })

  // disegno
  const size = ver * 4 + 17
  const mod: boolean[][] = Array.from({ length: size }, () => new Array<boolean>(size).fill(false))
  const fn: boolean[][] = Array.from({ length: size }, () => new Array<boolean>(size).fill(false))
  const set = (x: number, y: number, dark: boolean) => { mod[y][x] = dark; fn[y][x] = true }
  for (let i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0) }
  const finder = (cx: number, cy: number) => {
    for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) {
      const x = cx + dx, y = cy + dy, d = Math.max(Math.abs(dx), Math.abs(dy))
      if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, d !== 2 && d !== 4)
    }
  }
  finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
  const nAlign = ver === 1 ? 0 : Math.floor(ver / 7) + 2
  const pos = [6]
  if (nAlign) { const step = Math.ceil((ver * 4 + 4) / (nAlign * 2 - 2)) * 2; for (let p = size - 7; pos.length < nAlign; p -= step) pos.splice(1, 0, p) }
  for (let i = 0; i < pos.length; i++) for (let j = 0; j < pos.length; j++) {
    if ((i === 0 && j === 0) || (i === 0 && j === pos.length - 1) || (i === pos.length - 1 && j === 0)) continue
    for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1)
  }
  const format = (mask: number) => {
    const d = (0 << 3) | mask // livello M = 0
    let rem = d
    for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537)
    const b = ((d << 10) | rem) ^ 0x5412
    const bit = (i: number) => ((b >>> i) & 1) !== 0
    for (let i = 0; i <= 5; i++) set(8, i, bit(i))
    set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
    for (let i = 9; i < 15; i++) set(14 - i, 8, bit(i))
    for (let i = 0; i < 8; i++) set(size - 1 - i, 8, bit(i))
    for (let i = 8; i < 15; i++) set(8, size - 15 + i, bit(i))
    set(8, size - 8, true)
  }
  format(0) // riserva i moduli del formato; si riscrive dopo la maschera
  if (ver >= 7) {
    let rem = ver
    for (let i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25)
    const b = (ver << 12) | rem
    for (let i = 0; i < 18; i++) { const a = size - 11 + (i % 3), c = Math.floor(i / 3), dark = ((b >>> i) & 1) !== 0; set(a, c, dark); set(c, a, dark) }
  }
  // codici, a zig-zag da destra
  for (let right = size - 1, i = 0; right >= 1; right -= 2) {
    if (right === 6) right = 5
    for (let vert = 0; vert < size; vert++) for (let j = 0; j < 2; j++) {
      const x = right - j, y = ((right + 1) & 2) === 0 ? size - 1 - vert : vert
      if (!fn[y][x] && i < words.length * 8) { mod[y][x] = ((words[i >>> 3] >>> (7 - (i & 7))) & 1) !== 0; i++ }
    }
  }
  const mask = (m: number) => {
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      const inv = [(x + y) % 2 === 0, y % 2 === 0, x % 3 === 0, (x + y) % 3 === 0, (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0,
        ((x * y) % 2) + ((x * y) % 3) === 0, (((x * y) % 2) + ((x * y) % 3)) % 2 === 0, ((((x + y) % 2) + ((x * y) % 3)) % 2) === 0][m]
      if (!fn[y][x] && inv) mod[y][x] = !mod[y][x]
    }
  }
  const penalty = (): number => {
    let p = 0
    const lines = [...mod, ...mod.map((_, x) => mod.map((row) => row[x]))]
    const A = [true, false, true, true, true, false, true, false, false, false, false], B = [...A].reverse()
    for (const l of lines) {
      for (let i = 0, run = 1; i < size; i++) { if (i > 0 && l[i] === l[i - 1]) { run++; if (run === 5) p += 3; else if (run > 5) p++ } else run = 1 }
      for (let i = 0; i + 11 <= size; i++) { if (A.every((v, k) => l[i + k] === v)) p += 40; if (B.every((v, k) => l[i + k] === v)) p += 40 }
    }
    for (let y = 0; y + 1 < size; y++) for (let x = 0; x + 1 < size; x++) if (mod[y][x] === mod[y][x + 1] && mod[y][x] === mod[y + 1][x] && mod[y][x] === mod[y + 1][x + 1]) p += 3
    const dark = mod.reduce((n, r) => n + r.filter(Boolean).length, 0)
    return p + Math.floor(Math.abs(dark * 20 - size * size * 10) / (size * size)) * 10 // ogni 5% di scarto dal 50% costa 10
  }
  let best = 0, bestScore = Infinity
  for (let m = 0; m < 8; m++) {
    mask(m); format(m)
    const s = penalty()
    if (s < bestScore) { best = m; bestScore = s }
    mask(m) // toglie la maschera
  }
  mask(best); format(best)
  return mod
}

/** Il QR come SVG a due colori (nero su bianco, con il bordo di quattro moduli che gli scanner vogliono). */
export function qrSvg(text: string, quiet = 4): string {
  const m = qrMatrix(text), n = m.length + quiet * 2
  let d = ''
  m.forEach((row, y) => row.forEach((dark, x) => { if (dark) d += `M${x + quiet} ${y + quiet}h1v1h-1z` }))
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${n} ${n}" shape-rendering="crispEdges"><rect width="${n}" height="${n}" fill="#fff"/><path d="${d}" fill="#000"/></svg>`
}
