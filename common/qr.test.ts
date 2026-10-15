import { test } from 'node:test'
import assert from 'node:assert/strict'
import { qrMatrix, qrSvg } from './qr.ts'

test('qr: dimensioni per versione e i tre riquadri di posizione', () => {
  assert.equal(qrMatrix('ciao').length, 21) // versione 1
  const link = 'https://peak.yrb4g.com/invito/' + 'A'.repeat(43)
  const m = qrMatrix(link)
  assert.equal(m.length, 37) // versione 5: 73 byte stanno in 84
  for (const [x, y] of [[0, 0], [m.length - 7, 0], [0, m.length - 7]]) {
    for (let i = 0; i < 7; i++) for (let j = 0; j < 7; j++) {
      const ring = Math.max(Math.abs(i - 3), Math.abs(j - 3))
      assert.equal(m[y + j][x + i], ring !== 2, `riquadro in ${x},${y}`)
    }
  }
  assert.equal(m[m.length - 8][8], true, 'modulo scuro fisso')
})

test('qr: stesso testo, stesso disegno; troppo lungo = errore; svg valido', () => {
  assert.deepEqual(qrMatrix('x'), qrMatrix('x'))
  assert.notDeepEqual(qrMatrix('x'), qrMatrix('y'))
  assert.throws(() => qrMatrix('a'.repeat(300)), RangeError)
  assert.match(qrSvg('x'), /^<svg [^>]*viewBox="0 0 29 29"/)
})
