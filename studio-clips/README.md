# studio-clips

## Overview
Sorgenti delle clip video della pagina yrb4g.com/studio/peak (repo yG). Sono composizioni [HyperFrames](https://hyperframes.heygen.com) in HTML (GSAP), una per clip e lingua, più il generatore `schermate.ts`. Non fanno parte dell'app né di `project.yml`. Le cartelle `renders/`, `shots/` e `.hyperframes/` sono in `.gitignore` e si rigenerano.

## Interface
- `schermate.ts`: da `shots/*.json` e `shots/*.png` (prodotti da `scripts/studio-shots.ts`) scrive `schermata-<nome>-<it|en>.html` (home, esercizio, progressi, schede, sessione, amici). Uso: `node studio-clips/schermate.ts`.
- `intro.html`, `sync.html`, `record.html`: clip scritte a mano; `record.html` prende la variabile `lang` (`it` o `en`, `--variables '{"lang":"en"}'`).
- `hyperframes.json` (configurazione), `assets/icon.png`.
- Orchestrate da `scripts/studio-page.sh`, vedi [scripts](../scripts/README.md).

## Invariants
- I file `schermata-*.html` sono generati: si cambia `schermate.ts` (o la coreografia in `scripts/studio-shots.ts`) e si rigenera, non si modificano a mano.
- Le clip girano in loop e finiscono come iniziano.
- Dimensioni fisse: schermata 390x844 CSS px, composizione al doppio; intro in 780x1688.
- Il render usa una versione fissata di HyperFrames (`0.8.166` in `studio-page.sh`).
- La pagina mostra sempre l'ultima versione dell'app: a ogni rilascio si rifà tutto.

## Side Effects
- `schermate.ts` legge `shots/` e scrive i file `schermata-*.html` in questa cartella.
- Il render (`npx hyperframes render`) scrive in `renders/`; i font e GSAP si caricano da CDN (Google Fonts, jsDelivr).
- Nessun deploy da qui: la copia verso yG la fa `studio-page.sh`.

## Verification
- Nessun test automatico. Controllo reale: rigenerare con `node scripts/studio-shots.ts && node studio-clips/schermate.ts` e visionare un render, ad es. `npx -y hyperframes@0.8.166 render -c intro.html -o renders/intro.mp4` (da lanciare a mano in questa cartella).
