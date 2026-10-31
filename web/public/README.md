# public

## Overview
File statici copiati così come sono nella build. Contiene le icone e il manifest della PWA e la pagina di benvenuto (`benvenuto.html`, `benvenuto.css`, `benvenuto.mp4`) mostrata a chi non ha una sessione. L'unico sorgente è `benvenuto.js`.

## Interface
- `benvenuto.js`: script senza dipendenze della pagina di benvenuto (caricato da `benvenuto.html`). Sei scene in ciclo (durate in `durations`), con logo che si sposta tra `#slot` e `#dock`, contatore 180 -> 190 nella scena 4, navigazione a mano (swipe, tocco, puntini, frecce da tastiera). Con `prefers-reduced-motion` mostra l'ultima scena ferma.
- Testi: italiano nell'HTML, inglese nell'oggetto `en` applicato agli elementi `[data-t]` se la lingua del browser non è italiana.
- Asset: `manifest.webmanifest`, `icon-192.png`, `icon-512.png`, `apple-touch-icon.png`.

## Invariants
- Solo italiano e inglese, come l'app; non usa `t()`/`en.json` (la pagina è separata dal bundle Svelte).
- Ogni chiave `data-t` nell'HTML deve esistere in `en`.
- `icon-192.png` e `manifest.webmanifest` sono nella lista del guscio del service worker (`web/vite.config.ts`).

## Side Effects
- Legge gli elementi per id (`logo`, `slot`, `dock`, `count`, `stage`, `dots`) e ne modifica trasformazioni e classi; imposta `document.documentElement.lang`.
- Timer e `requestAnimationFrame` per il ciclo delle scene.

## Verification
- Nessun test automatico. La build la copia in `web/dist`: `npm run build` dalla radice.
