# components

## Overview
Componenti Svelte 5 riusabili dell'app Peak: fogli modali, selettori, grafici e figure. Non tengono stato proprio dell'app: leggono il modello da `../lib/store.svelte.ts` e scrivono tramite `../lib/actions.ts`, oppure ricevono tutto dalle proprietà (`$props()`) e avvisano con callback (`onclose`, `onstart`, ...).

## Interface
- Fogli: `Sheet.svelte` (base: `title`, `onclose`, `children`, `action`; blocca lo scroll del body, chiude con Esc), `AddSetSheet`, `BodyWeightSheet`, `EditMaxSheet`, `MuscleDetailSheet`.
- Editor: `ExerciseEditor`, `TemplateEditor`, `ProgramEditor`.
- Selettori: `CatalogPicker`, `ExercisePicker`, `FocusPicker`, `GoalPicker`, `IconPicker`, `Stepper`.
- Grafici e figure: `Heatmap`, `LineChart`, `WeekBars`, `Donut`, `Podium`, `MuscleFigure`, `PlateBar`, `SummitHero`, `BodyWeightCard`, `MonthGroups`, `Medal`, `CardBackground`.
- Icone e miniature: `Icon` (nome -> glifo), `Glyph`, `TemplateIcon`, `Thumb`, `FigThumb`; `solid-icons.ts` (set di icone); licenze in `Icon.LICENSE.txt` e `solid-icons.LICENSE.txt`.
- Altro: `Tour` (tutorial al primo avvio, ancorato agli attributi `data-tour`), `InstallBanner`, `FriendsList`.
- Le pagine che li montano sono in [../pages](../pages/README.md); logica e stato in [../lib](../lib/README.md).

## Invariants
- Ogni testo visibile passa da `t()`/`tn()` (`../lib/i18n.ts`) con chiave italiana e voce in `en.json`; `scripts/check-i18n.mjs` lo verifica.
- La logica di dominio (record, medaglie, anatomia, formati) viene da `@common/*`, non è riscritta qui.
- Le proprietà dei fogli sono lette una volta all'apertura (il warning `state_referenced_locally` è disattivato in `svelte.config.js`).
- Niente emoji nell'interfaccia: solo SVG (`Icon`, `Glyph`).

## Side Effects
- `Sheet` modifica `document.body.style.overflow` finché è aperto e lo ripristina alla chiusura.
- Gli editor e i fogli di inserimento scrivono dati chiamando le funzioni di `../lib/actions.ts` (quindi IndexedDB e coda di sincronizzazione).
- `Tour`, `Heatmap` e altri usano animazioni da `../lib/fx.ts` e suoni da `../lib/sound.ts`.

## Verification
- Run: `npm --prefix web run check`
- Run: `cd web && node scripts/check-i18n.mjs`
