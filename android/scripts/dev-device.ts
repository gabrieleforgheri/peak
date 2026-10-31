// Prova locale: crea un utente e un token di dispositivo (quello che l'app riceve dal login) nel database di un server di prova.
// Stampa una riga JSON {token, sub, nome}. Va lanciato PRIMA di avviare il server su quella cartella.
// Uso: node android/scripts/dev-device.ts <cartellaDati> [sub]
import { openStore } from '../../server/store.ts'

const store = openStore(process.argv[2] ?? 'data')
const user = store.upsertUser(process.argv[3] ?? 'dev', 'dev@example.com', 'dev', 'Dev')
console.log(JSON.stringify({ token: store.createDevice(user.id, 'Peak · Android di prova'), sub: user.sub, nome: user.name }))
store.close()
