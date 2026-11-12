# Peak — regole per gli agenti

Panoramica, mappa delle cartelle e invarianti di dominio: `README.md` (ogni cartella ha il suo README). Qui solo ciò che non si ricava dal codice. Le regole senza eccezioni non sono in questo file ma in hook e script: se un controllo fallisce, correggi il codice, non il controllo.

## Prima di dire «fatto»

Mostra l'esito dei comandi, non «è tutto verde».

- `npm test` e `npm run typecheck` (radice: `common/`, `server/`, `scripts/`)
- `npm --prefix web run check` e `node web/scripts/check-i18n.mjs`
- `node scripts/check_rules.mjs` — regole meccaniche (versione, emoji, valori grezzi); il conteggio per file non può salire
- `scripts/check_records.sh` — regola dei record in Swift, senza Xcode
- `scripts/swift_typecheck.sh` — typecheck Swift senza Xcode (~40 s) con concorrenza completa; gli avvisi non possono superare `scripts/concurrency-baseline`
- MAI `xcodebuild test`, simulatore o installazione sull'iPhone senza un OK esplicito: il simulatore blocca il Mac, e sul telefono ci sono dati veri

## Regole che valgono sempre

- Nessuna emoji nell'interfaccia: SVG sul web, SF Symbols su iPhone.
- Pesi in kg nei dati; conversione solo nella vista.
- Una regola di dominio cambiata in `Peak/Model` va riportata in `common/` (e viceversa) **con una fixture JSON** in `common/fixtures/` letta da entrambe. Niente nuova copia di una regola senza fixture.
- Il formato di sync è in `common/docs.ts`; `parseDoc` è l'unico confine di fiducia.
- Ogni nuovo entitlement o chiave di privacy va anche in `docs/apps.json` (`appPermissions`).
- `web/package.json` e `MARKETING_VERSION` di `project.yml` hanno la stessa versione (lo controlla `check_rules`).
- Rilascio: prima il server (con `common/` e `web/dist`), poi i client.
- I file generati non si toccano (li blocca `.claude/hooks/guard-generated.sh`, attivo se registrato in `.claude/settings.json`): `common/anatomy.ts`, `Peak/Design/MuscleFigureShapes.swift`, `Peak/Design/MachineGlyphShapes.swift`, `studio-clips/schermata-*.html`, `web/dist/`.

## Interfaccia e design (dettagli: `.claude/rules/design.md`)

Nelle viste solo token: niente `.font(.system(size:)`, `Color(hex:)`, `padding`/raggi numerici, né hex/`rgba`/`px` nel CSS dei componenti web. Se manca il valore, si aggiunge alla scala in `Peak/Design/Theme.swift` e a `web/src/app.css`, non nella vista. Il ratchet di `check_rules` impedisce di peggiorare; quando migri una vista, abbassa la baseline con `--update`.

## Dati e Swift (dettagli: `.claude/rules/swift.md`)

- Un `@Model` cambiato = una nuova versione di schema (`Peak/Store/PeakSchema.swift`) con la sua migrazione, provata sull'iPhone con una copia del backup JSON.
- Il modulo si chiama `Massimali` e il bundle id non cambia: dipendono dai dati degli utenti e da AltStore.
- Niente `print`: usa `Log`. Niente `@unchecked Sendable` né `nonisolated(unsafe)` per far tacere il compilatore.
- Il Watch si accoppia solo se l'iPhone lo incorpora: lo fa solo `scripts/install_device.sh`.
- Script che scrivono fuori dal repo o installano (`install_device.sh`, `build_ipa.sh`, `studio-page.sh`): non lanciarli alla leggera.

## Come si lavora

- Il codice lo scrivono subagenti Sonnet; chi coordina rivede ogni diff, unisce e verifica. Nei worktree: per prima cosa `git merge --ff-only <ramo di partenza>`.
- Lo stato del lavoro in corso sta in `HANDOFF.md`.
- Se cambi codice coperto dal contratto di un README, aggiorna il README nello stesso commit: un contratto falso è contesto sbagliato per la sessione dopo.
- Commit piccoli, uno per intervento; mai push senza richiesta.
