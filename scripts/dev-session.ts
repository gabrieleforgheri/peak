// Sviluppo locale: crea un utente di prova con una sessione e stampa il valore del cookie `peak`.
// Alternativa a `/auth/dev` (che il server offre solo senza OIDC e solo da localhost): questo script scrive nel database.
// Uso: node scripts/dev-session.ts [cartellaDati] [sub]
import { openStore } from '../server/store.ts'

const store = openStore(process.argv[2] ?? 'data')
const user = store.upsertUser(process.argv[3] ?? 'dev', 'dev@example.com', 'dev', 'Dev')
console.log(store.createSession(user.id, 30))
store.close()
