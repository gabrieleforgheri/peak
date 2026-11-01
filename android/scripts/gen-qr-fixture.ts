// Genera android/app/src/test/resources/qr-vectors.json: i QR che scrive il vero common/qr.ts, modulo per modulo ("1" = scuro).
// La prova Android (QrTest) vuole lo stesso disegno da ui/friends/Qr.kt.
// Uso (dalla radice del repo): node android/scripts/gen-qr-fixture.ts
import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { qrMatrix } from '../../common/qr.ts'

const texts = [
  'ciao', 'x', 'https://peak.yrb4g.com/invito/' + 'A'.repeat(43), 'https://peak.yrb4g.com/invito/' + 'Zk3_-'.repeat(20),
  'Più forti insieme: èàù ñ 💪', 'q'.repeat(100), 'w'.repeat(170), 'e'.repeat(213), // versioni 1, 1, 5, 8, 3, 6, 9, 10
]
const out = texts.map((text) => ({ text, rows: qrMatrix(text).map((row) => row.map((dark) => (dark ? '1' : '0')).join('')) }))
writeFileSync(fileURLToPath(new URL('../app/src/test/resources/qr-vectors.json', import.meta.url)), JSON.stringify(out))
