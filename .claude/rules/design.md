---
paths:
  - "Peak/Features/**"
  - "Peak/Design/**"
  - "PeakWatch/**"
  - "PeakWidgets/**"
  - "web/src/**"
---
Il design system è la fonte unica dell'aspetto. Una vista o un componente nuovo non decide valori: li prende da qui.

**Swift** (`Peak/Design/Theme.swift`)
- Colori: `Theme.*` (superfici, testo, `positive`/`negative`, metalli); l'accento viene da `settings.accentColor`.
- Spaziatura: `Theme.Space.xs/s/m/l/xl/xxl` (4, 8, 12, 16, 24, 32) per `.padding` e `spacing:`.
- Raggi: `Theme.Radius.xs/s/m/l` (10, 14, 20, 28) con `style: .continuous`; pillole con `Capsule()`.
- Testo: `Theme.Text.micro … display`. Sono stili di sistema, quindi seguono Dynamic Type: non usare `.font(.system(size:))`. Per i numeri enormi come grafica resta `Theme.display(size)`.
- Componenti esistenti in `Peak/Design/Components.swift` e negli altri file di `Peak/Design/` prima di crearne di nuovi. Un componente nuovo va in `Peak/Design/` e nella galleria `DesignGalleryView.swift` (solo `#if DEBUG`) con una `#Preview` deterministica.

**Web** (`web/src/app.css`)
- Variabili: `--s-*`, `--r*`, `--fs-*` e i colori. Nei componenti niente `#hex`, `rgb()`, `rgba()` a mano né `font-size` in px.
- Se manca il valore, si aggiunge alla scala (Swift e CSS insieme, stessi nomi e numeri), mai nella vista.

**Imposizione**
- `node scripts/check_rules.mjs` fallisce se un file aumenta i valori grezzi rispetto a `scripts/rules-baseline.json`; un file nuovo parte da zero.
- Migrando una vista, rilancia con `--update` per abbassare la baseline. La baseline non si alza mai (`--force` solo con motivo scritto nel commit).
- Mappe di migrazione (valore vecchio -> gradino): commenti sopra `Space`, `Radius` e `Text` in `Theme.swift`.
- Nessuna emoji: SF Symbols su iPhone, SVG sul web.
