# Peak contro le best practice dello sviluppo mobile e assistito da AI (2026-11-12)

Analisi, non modifiche: nessun file di codice è stato toccato. Ambito scelto: iPhone + Watch + Widget, web PWA + server + `common/`, processo trasversale. Android escluso (solo dove il processo lo riguarda). Ogni criticità ha due colonne: **Ideale** (pratica standard di un team professionale) e **Realistico** (con i tuoi vincoli: sviluppatore unico, account Apple gratuito, AltStore, niente test sul Mac).

## Come leggere le fonti

Ho fatto circa 40 ricerche e letto per intero 3 fonti primarie. La qualità è disomogenea e lo dico per non farti fidare dove non si può:

| Livello | Fonti | Uso |
|---|---|---|
| Primario, letto | [Claude Code best practices](https://code.claude.com/docs/en/best-practices), [Martin Fowler: Context Engineering for Coding Agents](https://martinfowler.com/articles/exploring-gen-ai/context-engineering-coding-agents.html), [OWASP MASVS](https://mas.owasp.org/MASVS/) (solo l'elenco dei gruppi di controlli) | Regole citate come tali |
| Primario, non recuperato | Apple HIG accessibilità, guida Apple all'adozione di Swift 6, DORA AI Capabilities Model: la pagina è tornata senza testo | Il contenuto arriva da fonti secondarie o dalla mia conoscenza: indicato caso per caso |
| Secondario | Blog di sviluppatori, guide di fornitori (Sentry, Ditto, ecc.), pagine di "skill" per agenti | Solo dove più fonti concordano |
| Non verificato | Percentuali di studi citate da blog (GitClear, Veracode, METR) | Riportate come ordine di grandezza, mai come fatto |

Le [fonti](#fonti) sono in fondo.

## Cosa dicono le fonti, in sintesi

**Costruire un'app mobile (generale)**
1. Architettura a strati con la persistenza fuori dalle viste; per SwiftUI moderno `@Observable` + SwiftData + `NavigationStack`, concordato da tutte le fonti pratiche ([riassunto](https://tessl.io/registry/dpearson2699/swift-ios-skills/3.6.1/files/skills/swiftui-patterns/references/architecture-patterns.md)).
2. Un design system (token + componenti + regole) è la base della coerenza; i token sono lo strato condiviso tra design e codice, e vanno applicati da strumenti, non dalla memoria di chi scrive ([Buffer](https://buffer.com/resources/popcorn-to-go/), [CM Telematics](https://www.cmtelematics.com/engineering/leveraging-design-tokens-for-scalable-and-consistent-ui-design/)).
3. Accessibilità: Dynamic Type con stili di testo, VoiceOver con etichette, contrasto, bersagli da 44 pt, Reduce Motion, colore mai da solo ([HIG](https://developer.apple.com/design/human-interface-guidelines/accessibility)). Numeri precisi dalla mia conoscenza, non dalla pagina.
4. Offline-first: LWW è semplice ma perde dati in silenzio e l'orologio del dispositivo è una base debole; per dati ad alto valore tenere entrambe le versioni o fondere per campo ([Ditto](https://www.ditto.com/blog/how-to-build-robust-offline-first-apps-a-technical-guide-to-conflict-resolution-with-crdts-and-ditto), [LWW trap](https://omr.it.com/blog/epoch-based-conflict-resolution-offline-first-apps/)).
5. Test: Swift Testing per unità, XCTest per UI e snapshot; gli snapshot sono l'unico modo pratico di verificare le viste SwiftUI ([confronto](https://theswiftk.it.com/blog/swift-testing-vs-xctest-migration-guide-2026), [SnapshotPreviews](https://www.github.com/EmergeTools/SnapshotPreviews-iOS)).
6. CI/CD: build + test a ogni push, firma automatizzata, rilascio a tappe ([guida](https://reintech.io/blog/continuous-integration-deployment-ios-apps)). Kill switch e versione minima nella **prima** release, perché non ritirano le versioni precedenti ([Livefront](https://medium.com/livefront/dont-live-with-regret-build-a-kill-switch-into-your-mobile-app-11b800c72ad4), [Firebase](https://firebase.google.com/docs/remote-config/use-cases/minimum-version)).
7. Swift 6: prima `complete` come avvisi in modo Swift 5, poi modo Swift 6; evitare `@unchecked Sendable` e `nonisolated(unsafe)` ([Apple](https://developer.apple.com/documentation/swift/adoptingswift6), contenuto qui da fonte secondaria).
8. SwiftData: usare `VersionedSchema` fin dall'inizio; un rename è visto come cancellazione più creazione ([Mert Bulan](https://mertbulan.com/never-use-swiftdata-without-versionedschema/), [Donny Wals](https://www.donnywals.com/a-deep-dive-into-swiftdata-migrations/)).
9. Sicurezza: MASVS ha 8 gruppi (STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, RESILIENCE, PRIVACY); OAuth nativo = codice + PKCE nel browser di sistema, token nel Keychain ([Curity](https://curity.io/resources/learn/oauth-for-mobile-apps-best-practices/)); API: l'autorizzazione per oggetto è il primo rischio OWASP 2023.
10. Privacy: manifest di privacy con motivi per le API sensibili obbligatorio per App Store ([Apple news](https://developer.apple.com/news/?id=3d8a9yyh)); dati di salute: informativa, minimizzazione, niente uso pubblicitario ([HealthKit](https://developer.apple.com/documentation/healthkit/protecting-user-privacy.md)).
11. Osservabilità: crash simbolicati, tasso di sessioni senza crash per versione, MetricKit come base senza SDK ([guida MetricKit](https://medium.com/@rajanTheSilentCompiler/a-practical-guide-to-apples-metrickit-stop-guessing-start-measuring-your-ios-app-s-health-5639db388e9c)).

**Sviluppo assistito da AI**
1. **Dare all'agente una verifica che risponde pass/fail** è la regola più forte: "Claude si ferma quando il lavoro sembra finito"; senza un controllo eseguibile sei tu la suite di test ([Claude Code](https://code.claude.com/docs/en/best-practices)). Gli agenti Swift sono bravi a scrivere e deboli a finire; vanno meglio con build, target di test e traguardo chiaro ([Jbrackin](https://dev.to/jbrackin/coding-agents-are-good-at-writing-swift-theyre-bad-at-finishing-it-md3)).
2. Le regole che **non ammettono eccezioni** vanno in hook o configurazione, non in `CLAUDE.md`, che è una linea guida che l'agente può ignorare ([Claude Code](https://code.claude.com/docs/en/best-practices)). Un hook `Stop` può rifiutare di chiudere finché il typecheck non passa.
3. File di istruzioni corto (meno di 200 righe), solo ciò che l'agente non ricava dal codice: comandi di verifica, trappole, decisioni non scritte. Istruzioni contraddittorie o duplicate producono errori che sembrano difetti dell'agente ([Fowler](https://martinfowler.com/articles/exploring-gen-ai/context-engineering-coding-agents.html)).
4. Review da una sessione che non ha scritto il codice (subagente fresco), con il comando "salta lo stile": un revisore che cerca problemi ne trova sempre, e ottieni strati inutili ([Claude Code](https://code.claude.com/docs/en/best-practices)).
5. Spec scritta prima, test come guardia: gli agenti possono cancellare i test invece di correggere il codice, quindi la guardia deve essere meccanica ([SDD](https://www.tpximpact.com/knowledge-hub/blogs/tech/spec-driven-development-ai-engineering/), [OpenReplay](https://blog.openreplay.com/tdd-agents-writing-code/)).
6. **L'AI amplifica ciò che c'è già** (DORA 2025: ~5000 intervistati): accelera dove la base è sana ed espone le debolezze a valle; capacità citate: versioning/rollback frequente, piccoli lotti, piattaforma interna di qualità, dati sani per l'AI ([DORA](https://research.google/pubs/dora-2025-state-of-ai-assisted-software-development-report/)). Sette capacità, solo sei confermate dalle fonti che ho trovato.
7. Cautela sui numeri: duplicazione e *churn* in crescita (GitClear), vulnerabilità nel 45% circa dei compiti (Veracode), sviluppatori esperti più lenti del 19% pur sentendosi più veloci (METR, 16 persone, 2025). Tutti da blog che riassumono, non letti all'origine.
8. Per UI coerente con agenti: token come unici valori ammessi, **lint che vieta hex e pixel grezzi**, file di design compatto richiamato dalle istruzioni, CI che fallisce; gli agenti eseguono i linter in ciclo e correggono da soli ([Builder.io](https://www.builder.io/blog/how-to-make-ai-agents-follow-your-design-system)).

---

## Cosa Peak fa già bene (non toccare)

- Fixture condivise per le regole dei record (`common/fixtures/records.json`) verificate sia in Swift sia in TypeScript: è esattamente la pratica che le fonti raccomandano contro la deriva tra implementazioni.
- `parseDoc` come unico confine di fiducia; limite al corpo delle richieste; intestazioni di sicurezza (CSP, HSTS); `timingSafeEqual`; token nel Keychain; OIDC con PKCE.
- Moderno nel linguaggio: 0 `ObservableObject`, 0 `NavigationView`, 0 `foregroundColor`, 12 `@Observable`, 28 usi di Reduce Motion.
- Protezione del dato locale: `isFallback` blocca backup e sync con lo store d'emergenza, più `backup-latest.json`.
- `typecheck` pulito fuori dai test (gli errori sono nei file di test), `svelte-check` a zero errori.
- Documentazione per cartella e un rapporto di deriva fatto a mano: la disciplina c'è.
- I due problemi gravi del [rapporto di deriva del 25/10](2026-10-25_deriva-contratti.md) risultano **risolti**: `/auth/dev` ora richiede `!cfg && local` (`server/auth.ts:171`) e `writeAutoBackup` ha la guardia `isFallback` (`BackupService.swift:199`). Il rapporto, non tracciato, è in parte superato.

---

## Criticità, in ordine di gravità

| # | Criticità | Gravità |
|---|---|---|
| 1 | Nessuna verifica automatica prima di integrare | Alta |
| 2 | Design system incompleto e non imposto (il tuo esempio) | Alta |
| 3 | Dominio implementato più volte, parità solo per convenzione | Alta |
| 4 | SwiftData senza schema versionato | Alta |
| 5 | Flusso AI senza regole imposte (hook), documentazione che invecchia | Media |
| 6 | Swift 5 senza controllo di concorrenza | Media |
| 7 | Nessuna osservabilità | Media |
| 8 | Accessibilità: Dynamic Type assente | Media |
| 9 | Privacy e conformità non verificate | Media (Bassa finché è solo AltStore) |
| 10 | Localizzazione fatta in casa | Bassa |
| 11 | Server: limitazione di frequenza non trovata, `node:sqlite` | Da confermare |
| 12 | Viste enormi che scrivono nel database | Bassa |

### 1. Nessuna verifica automatica prima di integrare — Alta

**Fonti.** La regola più forte per l'AI è dare un controllo pass/fail; DORA dice che l'AI espone le debolezze a valle.
**In Peak.**
- L'unico workflow (`.github/workflows/altstore-release.yml`) parte solo al tag `v*` e compila: non esegue `npm test`, `typecheck`, `svelte-check` né `check_records.sh`.
- Il web ha 0 test. Le 20.385 righe Swift hanno 1.279 righe di test, tutte sul modello: viste, store, `SyncService` (522 righe) e Watch non ne hanno.
- I test Swift si lanciano a mano e solo su iPhone fisico.
- `typecheck` non è pulito: gli errori noti (il README ne dichiara 4) restano nei test del server e si sono abituati a essere ignorati.
- 270 commit in circa tre settimane, quasi tutti su `main`, 46 rami, nessun hook di commit.

**Ideale.** CI a ogni push e PR: lint, typecheck, test TS, `svelte-check`, build; test Swift e snapshot delle viste su simulatore; blocco del merge se rosso.
**Realistico.** Un secondo workflow su `push`/`pull_request` su runner Linux (gratuiti, nessun Xcode): `npm test`, `npm run typecheck`, `npm --prefix web run check`, `check-i18n`. `check_records.sh` gira senza Xcode, quindi anche lì. Azzerare i 4 errori TS per avere una base verde. I test Swift restano a mano; costo dei runner macOS da verificare prima di metterli.

### 2. Design system incompleto e non imposto — Alta

**Fonti.** Token come unico valore ammesso, lint che vieta valori grezzi, galleria dei componenti, agenti che correggono guidati dal linter.
**In Peak.** `Peak/Design/Theme.swift` ha una base buona (superfici, testo, semantica, tre misure di geometria) e `Components.swift` ha componenti veri (stepper, chip, stili di bottone). Ma:

| Misura | Valore | Lettura |
|---|---|---|
| `.font(.system(size:` fissi (Peak + Watch + Widget) | 74 | Non esiste una scala tipografica |
| Font con stile semantico (`.body`, `.headline`…) in `Peak/` | 0 | Dynamic Type non agisce (vedi 8) |
| `.padding(<numero>)` nelle Features | 210 | Non esiste una scala di spaziatura |
| Raggi hardcoded nelle Features | 72 | Esistono solo `radius`, `radiusSmall` |
| `Color(hex:)` nelle Features | 18 | Colori fuori da `Theme` (medaglie, podio) |
| `#Preview` nell'intera app | 1 (`PlateBarView`) | Nessuna galleria, nessuno snapshot |
| Web: colori/`rgba` hardcoded nei `.svelte` | 133 righe (Ideas 23, SummitHero 14, PlateBar 13…) | `app.css` ha ~60 variabili ma non sono imposte |
| Web: `font-size` in px | 63 | Idem |

Due insiemi di token (Swift e CSS) allineati a mano, senza fonte unica; ci sono già generatori nel repo (`common/anatomy.ts`) per questo tipo di lavoro.
**Ideale.** Fonte unica dei token (JSON) che genera `Theme` Swift e `app.css`; scale (tipografia, spaziatura, raggi, ombre) con nomi; componenti in galleria con `#Preview` e snapshot; lint che vieta valori grezzi; `DESIGN.md` compatto richiamato dalle istruzioni dell'agente.
**Realistico.** In quest'ordine, ognuno utile da solo: (a) scale in `Theme` e in `app.css` con i valori che già usi di più, (b) regole regex personalizzate in SwiftLint per vietare `.font(.system(size:`, `Color(hex:` e numeri nel `padding` fuori da `Design/` (con `--baseline` per non bloccare il passato), uno script `grep` equivalente per il web, (c) una vista "galleria" in Impostazioni di sviluppo con i componenti, (d) migrare le viste a ondate. Gli snapshot automatici dopo.

### 3. Dominio implementato più volte, parità per convenzione — Alta

**Fonti.** Fixture neutre e test di contratto sono la difesa standard; la duplicazione tra confini è accettabile solo se verificata.
**In Peak.** "La logica di dominio esiste due volte" (Swift e TS) e le regole sono allineate per convenzione tranne i record. Medaglie, riscaldamento, dischi, progressione, statistiche, hash di sync hanno gemelle non verificate tra loro. Android è una terza copia (la sync è stata ritradotta in Kotlin e `HANDOFF.md` annota "Codice scritto da una sessione Sonnet, da rivedere"; elenca già cinque differenze trovate tra iPhone, web e server). La documentazione conferma che il problema è reale: il rapporto di deriva ha trovato falso il contratto di quasi ogni cartella.
**Ideale.** Ogni regola con fixture JSON condivise, eseguite da tutte le implementazioni in CI.
**Realistico.** Estendere il meccanismo di `records.json` alle regole con più rischio di divergenza: sync (hash e `m`), medaglie, progressione 1RM, dischi. Prima di scrivere la quarta implementazione di una regola, scrivere la fixture. È la criticità che costa di più nel tempo con tre piattaforme.

### 4. SwiftData senza schema versionato — Alta

**Fonti.** `VersionedSchema` + `SchemaMigrationPlan` fin dall'inizio; un rename è una cancellazione; la migrazione va provata su dispositivo con dati veri.
**In Peak.** `Peak/Store/ModelContainer+App.swift` usa `Schema([...])` senza versioni. Se lo store non si apre, l'app lavora in memoria (con `try!` come ultima riga) e protegge backup e sync: scelta prudente, ma il dato "sparisce" dalla vista finché non importi un backup. Il modulo si chiama ancora `Massimali` proprio per non rischiare una migrazione (`project.yml`): il vincolo è già diventato una scelta di architettura.
**Ideale.** Schema v1 congelato oggi come `VersionedSchema`, piano di migrazione, test di migrazione con un file di dati reali.
**Realistico.** Definire ora lo schema attuale come v1 senza cambiare il modello (rischio: le fonti avvertono di errori "Duplicate version checksums" se si versiona male, quindi prova su iPhone con una copia del backup prima di rilasciare). Poi ogni cambio di modello passa da lì. Il backup JSON resta la rete.

### 5. Flusso AI senza regole imposte, documentazione che invecchia — Media

**Fonti.** Hook per le regole senza eccezioni; file corto; istruzioni contraddittorie fanno danni; review fresca.
**In Peak.**
- Nessun `CLAUDE.md`/`AGENTS.md` nel repo (c'è solo il `README.md` con "System Verification", che fa da sostituto decente, e la tua memoria personale). Nessun hook: `.claude/settings.local.json` consente solo `npm test`.
- Le regole che non ammettono eccezioni sono in prosa: "ogni nuova chiave di privacy va anche in `docs/apps.json`", "versione web = `MARKETING_VERSION`", "niente emoji", "i file generati non si modificano a mano", "prima il server, poi i client". Nulla le fa rispettare.
- Contratti dei README falsi in molti punti (rapporto del 25/10); 20 file non tracciati, quasi tutti README. Un contratto falso è contesto sbagliato per ogni sessione successiva.
- Il lavoro di sessioni Sonnet in parallelo (fase 3 Android, sei aree) produce codice "da rivedere": la review è il collo di bottiglia, non la scrittura.

**Ideale.** `CLAUDE.md` corto (comandi, trappole, invarianti); hook `Stop` con typecheck/test; hook `PreToolUse` che nega modifiche a file generati; `/code-review` con subagente fresco prima del merge; controllo di deriva dei README automatico.
**Realistico.** Un `CLAUDE.md` di meno di 60 righe preso dagli "Global Invariants" del README. Tre controlli meccanici invece di prose: (a) script che fallisce se `web/package.json` ≠ `MARKETING_VERSION`, (b) script che fallisce se in `Peak/Features` compare un'emoji, (c) hook che nega l'edit a `common/anatomy.ts` e `studio-clips/schermata-*.html`. Commit o scarto dei README non tracciati.

### 6. Swift 5, nessun controllo di concorrenza — Media

**Fonti.** Strict concurrency `complete` in modo Swift 5, poi Swift 6; stati globali in attori.
**In Peak.** `SWIFT_VERSION: "5.0"`; 41 `@MainActor`, 2 `Sendable`, 7 `DispatchQueue`, un cache con `NSLock` a mano in `Lang.swift`. Watch, HealthKit, WatchConnectivity e ActivityKit sono i luoghi tipici delle gare sui dati.
**Ideale.** Swift 6 target per target.
**Realistico.** Solo `SWIFT_STRICT_CONCURRENCY = complete` come avvisi, un target alla volta, senza cambiare il modo del linguaggio. Anche utile all'agente: gli avvisi sono feedback gratuito.

### 7. Nessuna osservabilità — Media

**Fonti.** Crash simbolicati, tasso senza crash per versione, MetricKit senza SDK.
**In Peak.** 0 occorrenze di `MetricKit`, `OSLog`, `Logger`; 2 `print`. Per un'app distribuita con AltStore non ti arriva niente (i rapporti di Xcode Organizer vengono dalle build di App Store/TestFlight: da verificare nel tuo caso). Il server ha log, il client no.
**Ideale.** Crash reporting con simboli + metriche per versione + log strutturato.
**Realistico.** `MXMetricManagerSubscriber` che scrive i diagnostici in un file esportabile da Impostazioni (come il backup) e `Logger` al posto di `print`. Nessun fornitore esterno.

### 8. Accessibilità: Dynamic Type assente — Media

**In Peak.** 0 stili di testo semantici, 0 `ScaledMetric`/`dynamicTypeSize`, 74 dimensioni fisse: il testo non segue le impostazioni di sistema. Punti positivi: 33 `accessibilityLabel`, 16 nascosti per decorazione, 28 usi di Reduce Motion, 66 `aria-label` sul web. Non ho misurato quanti bottoni solo-icona restano senza etichetta (195 `Button` in totale): da fare con una scansione mirata.
**Ideale.** Tutto il testo con stili semantici, prova a AX5, contrasto verificato.
**Realistico.** Si risolve insieme alla scala tipografica del punto 2: ogni voce della scala usa `Font.TextStyle` con corpo relativo. Costo marginale se fatto lì.

### 9. Privacy e conformità non verificate — Media (Bassa finché è solo AltStore)

**In Peak.** Nessun `PrivacyInfo.xcprivacy`. L'app usa `UserDefaults` (API con motivo obbligatorio) e HealthKit, sincronizza con un server tuo e ha una funzione amici. `docs/apps.json` documenta i permessi per AltStore. Per l'App Store il manifest, l'informativa per HealthKit (come revocare e cancellare) e la cancellazione dell'account sono richieste (fonti secondarie); per GDPR i dati sanitari sono categoria speciale e il consenso va separato dal resto ([thenextweb/noyb](https://thenextweb.com/news/fitbit-google-illegal-data-sharing)). Non so se esistono un'informativa pubblica e un percorso di cancellazione lato server: vedi domande.
**Realistico.** Aggiungere il manifest quando c'è un motivo concreto (App Store o altri utenti); intanto verificare le due domande aperte.

### 10. Localizzazione fatta in casa — Bassa

**In Peak.** `Lang.swift` traduce a runtime con tabella esatta più modelli regex sulla chiave italiana, con cache a 4000 voci; 137 `Text("…")` letterali; `en.json` condiviso col web. Le fonti indicano gli String Catalogs come standard e un test che trova stringhe letterali. Il tuo schema ha un vantaggio (un solo `en.json` per iPhone e web) e un rischio (una traduzione approssimata per regex, nessuna segnalazione di chiavi mancanti lato Swift).
**Realistico.** Tenerlo; aggiungere un controllo come `check-i18n.mjs` ma per Swift. Migrare agli String Catalogs solo se aggiungi altre lingue.

### 11. Server — Da confermare

Non ho trovato limitazione di frequenza (`rate limit`, 429) nel codice del server. Potrebbe stare su Cloudflare (la memoria registra una regola WAF) o sul proxy: non posso saperlo da qui. OWASP la mette tra i primi rischi delle API ("consumo illimitato di risorse"). Inoltre `node:sqlite` è giudicato non stabile da alcune fonti e stabile da altre, e il backup del file in WAL può essere incoerente se copiato a caldo: il backup notturno usa `peak-backup.db` e andrebbe verificato che usi l'API di backup di SQLite e non una copia del file.

### 12. Viste enormi che scrivono nel database — Bassa

`WorkoutSessionView.swift` 994 righe, `ExerciseEditorView.swift` 872. Il rapporto di deriva conferma che diverse viste scrivono SwiftData direttamente (contro il README). Costo reale: più contesto per ogni sessione dell'agente e logica non testabile fuori dalla vista. Si tocca quando si tocca il file, non prima.

---

## Cosa farei per primo (solo ordine, nessuna azione intrapresa)

1. CI minima su push (punto 1) e azzeramento degli errori `typecheck`: un'ora, e rende utile ogni altra cosa.
2. Scale di token + regole di lint contro i valori grezzi (punto 2): impedisce che il problema cresca mentre migri.
3. `CLAUDE.md` corto e tre controlli meccanici (punto 5).
4. Schema v1 versionato (punto 4) prima del prossimo cambio di modello.
5. Fixture condivise per sync e medaglie (punto 3) prima che Android sia finito.

## Domande aperte (non ho assunto)

1. Esiste un'informativa privacy pubblica e un percorso per cancellare i dati di un utente sul server? Ci sono utenti oltre a te?
2. Il rate limiting c'è su Cloudflare/NPM o non c'è?
3. Pensi all'App Store o resti su AltStore? Cambia la gravità del punto 9 e la disponibilità dei rapporti di crash.
4. Il repository è privato? Cambia il costo dei runner macOS per i test Swift in CI.
5. Il backup notturno `peak-backup.db` usa l'API di backup di SQLite o copia il file?

## Fonti

Primarie: [Claude Code best practices](https://code.claude.com/docs/en/best-practices) · [Fowler, Context Engineering](https://martinfowler.com/articles/exploring-gen-ai/context-engineering-coding-agents.html) · [OWASP MASVS](https://mas.owasp.org/MASVS/) · [Apple HIG accessibilità](https://developer.apple.com/design/human-interface-guidelines/accessibility) · [Apple, Swift 6](https://developer.apple.com/documentation/swift/adoptingswift6) · [Apple, privacy manifest](https://developer.apple.com/news/?id=3d8a9yyh) · [Apple, HealthKit privacy](https://developer.apple.com/documentation/healthkit/protecting-user-privacy.md) · [Firebase, versione minima](https://firebase.google.com/docs/remote-config/use-cases/minimum-version) · [DORA 2025](https://research.google/pubs/dora-2025-state-of-ai-assisted-software-development-report/) · [DORA AI Capabilities Model](https://research.google/pubs/introducing-the-dora-ai-capabilities-model-7-keys-to-succeeding-in-ai-assisted-software-development/)

Secondarie: [Builder.io, agenti e design system](https://www.builder.io/blog/how-to-make-ai-agents-follow-your-design-system) · [Buffer, design system mobile](https://buffer.com/resources/popcorn-to-go/) · [CM Telematics, token](https://www.cmtelematics.com/engineering/leveraging-design-tokens-for-scalable-and-consistent-ui-design/) · [Ditto, conflitti offline](https://www.ditto.com/blog/how-to-build-robust-offline-first-apps-a-technical-guide-to-conflict-resolution-with-crdts-and-ditto) · [LWW trap](https://omr.it.com/blog/epoch-based-conflict-resolution-offline-first-apps/) · [Mert Bulan, VersionedSchema](https://mertbulan.com/never-use-swiftdata-without-versionedschema/) · [Donny Wals, migrazioni](https://www.donnywals.com/a-deep-dive-into-swiftdata-migrations/) · [SnapshotPreviews](https://www.github.com/EmergeTools/SnapshotPreviews-iOS) · [Curity, OAuth mobile](https://curity.io/resources/learn/oauth-for-mobile-apps-best-practices/) · [Livefront, kill switch](https://medium.com/livefront/dont-live-with-regret-build-a-kill-switch-into-your-mobile-app-11b800c72ad4) · [MetricKit](https://medium.com/@rajanTheSilentCompiler/a-practical-guide-to-apples-metrickit-stop-guessing-start-measuring-your-ios-app-s-health-5639db388e9c) · [Jbrackin, agenti e Swift](https://dev.to/jbrackin/coding-agents-are-good-at-writing-swift-theyre-bad-at-finishing-it-md3) · [SDD](https://www.tpximpact.com/knowledge-hub/blogs/tech/spec-driven-development-ai-engineering/) · [OpenReplay, TDD e agenti](https://blog.openreplay.com/tdd-agents-writing-code/) · [Swift Testing vs XCTest](https://theswiftk.it.com/blog/swift-testing-vs-xctest-migration-guide-2026) · [Architettura SwiftUI](https://tessl.io/registry/dpearson2699/swift-ios-skills/3.6.1/files/skills/swiftui-patterns/references/architecture-patterns.md) · [CI iOS](https://reintech.io/blog/continuous-integration-deployment-ios-apps) · [noyb/Fitbit](https://thenextweb.com/news/fitbit-google-illegal-data-sharing)

Non verificati: cifre di GitClear, Veracode e METR (da blog che le riassumono, non dagli originali).

## Come ho misurato Peak

Conteggi con `grep` sul codice al 12/11/2026 (`Peak/`, `PeakWatch/`, `PeakWidgets/`, `web/src/`, `server/`, `common/`): un `grep` conta righe, non elementi, e non distingue il codice morto; `npm run typecheck` e `npm --prefix web run check` eseguiti. Nessun test Swift lanciato (regola della memoria: non sul Mac). Dove una fonte primaria non era leggibile l'ho scritto.
