# scripts

## Overview
Strumenti di controllo per lo sviluppo del client web.

## Interface
- `check-i18n.mjs`: da lanciare dalla cartella `web` (`cd web && node scripts/check-i18n.mjs`: i percorsi `src/...` sono relativi alla cwd). Estrae le chiavi italiane passate a `t('...')` e `tn(n, 'uno', 'molti')` in `src/**/*.{svelte,ts}` e le confronta con `src/lib/en.json`. Con `--list` stampa le chiavi mancanti in JSON.

## Invariants
- Codice di uscita 1 se manca una traduzione; le voci inutilizzate sono solo segnalate.
- Riconosce solo chiavi letterali tra apici singoli: chiavi costruite dinamicamente non sono verificate.

## Side Effects
- Solo lettura di `src/`; scrive su stdout.

## Verification
- Run: `cd web && node scripts/check-i18n.mjs`
