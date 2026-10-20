# Massimali

App iPhone per tenere traccia dei massimali su ogni macchinario della palestra: storico,
grafici dei progressi, riscaldamento consigliato e log degli allenamenti. SwiftUI + SwiftData, dati
tutti in locale, nessun account e nessuna dipendenza esterna.

## Requisiti

| Cosa | Perché | Come |
|---|---|---|
| **Xcode** (App Store, ~10 GB) | senza non si compila nulla | poi `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` e `sudo xcodebuild -license accept` |
| **XcodeGen** | genera `Massimali.xcodeproj` da `project.yml` | `brew install xcodegen` |
| **Apple ID** in Xcode | firma l'app | Xcode → Settings → Accounts → `+` |
| **Modalità sviluppatore** sull'iPhone | consente di installare app firmate da te | Impostazioni → Privacy e sicurezza → Modalità sviluppatore (riavvio) |

## Primo avvio

```bash
cd ~/Developer/massimali
xcodegen generate          # crea Massimali.xcodeproj
open Massimali.xcodeproj
```

In Xcode: target **Massimali** → *Signing & Capabilities* → *Team* = il tuo Apple ID
(Personal Team). Se il bundle id risultasse già usato da qualcun altro, cambialo in
`Signing & Capabilities` o in `project.yml` (`PRODUCT_BUNDLE_IDENTIFIER`) e rigenera.

Simulatore:

```bash
xcodebuild -project Massimali.xcodeproj -scheme Massimali \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

Test:

```bash
xcodebuild test -project Massimali.xcodeproj -scheme Massimali \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

## Sideload sull'iPhone

1. Collega l'iPhone al Mac e sbloccalo ("Autorizza questo computer").
2. In Xcode scegli l'iPhone come destinazione e premi **Run** (⌘R).
3. Al primo avvio l'iPhone rifiuta l'app: vai in **Impostazioni → Generali → VPN e
   gestione dispositivo**, tocca il tuo Apple ID e **Autorizza**.

### La scadenza dei 7 giorni

Con un Apple ID gratuito il certificato dura **7 giorni**: dopo, l'app non si apre più.
Per rinnovarla basta ricollegare l'iPhone e premere di nuovo Run in Xcode — **i dati
restano**, perché l'app viene reinstallata sopra sé stessa. Si perdono solo se
*disinstalli* l'app. Altri limiti dell'account gratuito: massimo 3 app firmate
contemporaneamente e niente capability iCloud.

## Backup — leggilo

Non c'è sincronizzazione iCloud (serve l'account sviluppatore a pagamento). Perciò:

- a ogni avvio e a fine allenamento l'app riscrive `Documents/backup-latest.json`;
- quella cartella è visibile in **File → Sul mio iPhone → Massimali**, da dove puoi
  copiare il backup su iCloud Drive;
- da **Impostazioni → Esporta backup** condividi lo stesso file dove vuoi;
- **Importa backup** offre due modalità: *unisci* (aggiunge solo ciò che manca,
  confronto per uuid) e *sostituisci* (cancella e riparte dal file).

Prima di disinstallare l'app, esporta.

## Struttura

```
project.yml                  configurazione XcodeGen (bundle id, target iOS 17, Info.plist)
Massimali/
  MassimaliApp.swift         @main, ModelContainer, iniezione delle preferenze
  Model/                     Exercise, MaxRecord, Workout, WorkoutSet, BodyWeightEntry,
                             MuscleGroup, OneRepMax (Epley), Warmup, SeedCatalog
  Store/                     AppStore (ModelContainer), BackupService + DTO,
                             RecordService, HealthService
  Design/                    Theme, GlassCard, Components (stepper, tessere, badge),
                             MachineGlyph (pittogrammi), ExerciseThumbnail
  Utils/                     AppSettings, Formatters, Haptics, Stats, ImageStore,
                             Provisioning (scadenza della firma)
  Features/
    Root/                    TabView e backup automatico
    Maxes/                   lista, dettaglio con grafico, sheet del massimale, editor
    Workouts/                sessione, aggiunta serie, selettore, riepilogo
    Progress/                volume settimanale, ripartizione, top movers
    Settings/                una vista per sottopagina: generale, allenamento, account yG,
                             Salute, backup, dati (archiviati, cancella tutto)
MassimaliWatch/              app Watch: contatore, Digital Crown, invio delle serie
WatchShared/                 messaggi iPhone↔Watch e RepCounter (compilati in entrambe)
MassimaliTests/              1RM, riscaldamento, rilevamento record, statistiche,
                             pittogrammi e foto, round-trip del backup
```

## Catalogo, riscaldamento e immagini

Il **catalogo** (`common/catalog.json`, 291 voci) è la fonte unica per web, server e iPhone: ogni voce è un
*esercizio* (attrezzo + movimento: «Panca piana bilanciere», «Leg press 45°») con un **id** slug inglese che non
cambia mai, nome italiano e inglese, sinonimi per la ricerca, gruppo, passo del carico, pittogramma e note. Un
macchinario nuovo si crea **cercando nel catalogo** (o come «personalizzato», senza id), poi gli si dà un nome
familiare: l'id resta, ed è ciò che permette le classifiche fra amici.

- **Non cambiare mai un id** né riusarlo; i nomi si possono correggere. I vecchi 98 nomi italiani restano come nome o
  sinonimo, così gli esercizi già in uso si collegano da soli (`matchByName`).
- **Collegamento degli esistenti**: il server (all'avvio, una volta per esercizio) e l'iPhone (una volta) mettono il
  campo `catalog` per nome; gli altri restano personalizzati e si collegano a mano da *Modifica → Catalogo*.
- **Dove sta**: il web lo carica a parte solo quando serve; il server lo serve da `/catalogo.json` (ETag); l'iPhone ne ha
  una copia nel bundle e scarica quella del server se è più nuova (`Catalog.refresh`).
- Un client più vecchio che non manda `catalog`/`hidden` non li cancella: il server tiene i valori che aveva.

Quello che la tua palestra non ha non serve archiviarlo: i macchinari partono vuoti e si aggiungono cercando.

Per ogni esercizio con un massimale registrato l'app propone il **riscaldamento**:
rampa 40% × 10 → 60% × 6 → 80% × 3 **sul carico vero** dell'esercizio. I pesi stanno
sulla griglia del macchinario: si parte dal massimale e si scende di N passi fino al
peso più vicino a quello calcolato, così anche un pacco sfalsato (5 → 8,75 → 12,5…)
dà solo pesi inseribili. Compare nel sottotitolo della lista, in una card nel dettaglio e come
pulsanti di compilazione rapida quando aggiungi una serie di riscaldamento.

Ogni macchinario ha un'**immagine**, con due livelli:

- un **pittogramma vettoriale**, uno per tipo di attrezzo (48: leg curl sdraiato,
  hip thrust, trap bar, panca Scott, cavo con corda…). Le forme stanno in
  `common/glyph.ts`; `node scripts/gen-glyphs.ts` ne scrive la copia Swift
  (`Design/MachineGlyphShapes.swift`, e un test controlla che sia aggiornata). Un
  macchinario collegato al catalogo mostra sempre l'attrezzo della sua voce; uno
  personalizzato non ha immagine finché non scegli un pittogramma o una foto (quelli
  creati prima del catalogo tengono, fissata una volta, l'icona dedotta dal nome);
- una **foto tua**, scattata o presa dal rullino dalla schermata di modifica, che
  sostituisce il pittogramma ovunque. Viene ridotta a 640 px e ricompressa in JPEG
  (`Utils/ImageStore.swift`) prima di finire nel database, e viaggia dentro i backup
  in base64 — è la ragione principale per cui il file di backup può crescere.

Nella stessa schermata si sceglie l'**esecuzione**: entrambe le braccia/gambe insieme
oppure un lato alla volta. Braccia o gambe si deducono dal gruppo muscolare.

Per un macchinario personalizzato, nella schermata di modifica c'è la striscia con
tutti i pittogrammi per sceglierlo a mano.

## Durante l'allenamento

Le serie si aggiungono, si **modificano** (tocco sulla riga) e si eliminano (menu
contestuale): correggendo una serie il massimale automatico viene ricalcolato istantaneamente
(sia al rialzo, mostrando il badge verde *Personal Record*, sia al ribasso, rimuovendo un record
non più valido). Ogni scheda esercizio in sessione mostra le **serie allenanti dell'ultimo
allenamento concluso** per avere un riferimento immediato dei carichi e delle ripetizioni
raggiunti la volta precedente. Inoltre, dal menu a tre puntini dell'esercizio è disponibile
l'azione **Vedi macchinario**, e durante l'inserimento o modifica di una serie un menu a comparsa
permette di consultare lo storico di tutte le serie registrate in precedenza. Il titolo
dell'esercizio è posizionato sempre in cima (sia in allenamento sia nell'editor delle schede)
per una leggibilità immediata. Ogni sessione ha il suo campo **note**, e il selettore degli
esercizi mette in cima quelli **usati di recente**.

Dopo ogni serie parte il **timer di recupero** (attivabile in *Impostazioni →
Allenamento*): durata predefinita lì, sovrascrivibile per macchinario nell'editor.
Si allunga o accorcia di 15 s, si salta, conta alla rovescia anche nella Live Activity
e, con il telefono in tasca, avvisa con una notifica locale.

Il **prossimo carico consigliato** segue la doppia progressione (`Model/Progression.swift`):
sull'ultima sessione conclusa conta il carico più pesante; se tutte le serie a quel
carico hanno raggiunto il massimo del range (8–12 di default, in *Impostazioni*) si sale
di un passo e si riparte dal minimo, altrimenti si resta sul carico con una ripetizione
in più. Compare nel dettaglio del macchinario e come scorciatoia quando aggiungi una serie.

Sugli esercizi con un bilanciere (anche panche, rack, hip thrust, EZ, trap bar) o *plate loaded* l'aggiunta della serie
mostra il **calcolatore dischi** (`Model/Plates.swift`, `Design/PlateBarView.swift`):
mezzo bilanciere con i dischi colorati che entrano a molla a ogni cambio di carico,
la lista per lato e quanto manca se i dischi della palestra non bastano. Peso del
bilanciere e dischi disponibili si scelgono in *Impostazioni → Allenamento*; le plate
loaded partono da zero.

## Foto dei macchinari

Si scatta senza il ritaglio di sistema di `UIImagePickerController`: quella schermata
rimbalza l'immagine al centro, non la lascia spostare e nasconde i comandi. La foto
arriva intera e **l'inquadratura si regola dopo**, trascinando l'anteprima grande
nella schermata di modifica. Lo spostamento è un solo numero da −1 a 1 sull'asse che
sborda dal quadrato (`Design/PhotoFraming.swift`), applicato ovunque compaia la
miniatura. Il gesto ha la priorità sulla `ScrollView`, altrimenti trascinando in
verticale scorrerebbe la pagina.

## App per Apple Watch

Il Watch mostra l'esercizio aperto nella sessione dell'iPhone (con l'obiettivo della
scheda): il carico si regola con la Digital Crown, le ripetizioni con − e +, *Registra*
manda la serie all'iPhone. Il conteggio automatico delle ripetizioni dal movimento del polso
è stato tolto nella 3.0.

- **Scelta dell'esercizio**: nella sessione si sceglie solo cosa stai facendo (dal
  selettore o toccando l'intestazione di una card, che diventa *in corso*), senza peso né
  ripetizioni. L'esercizio arriva subito sul Watch; *+ Serie* sul telefono resta per
  inserire a mano.
- **Peso dalla ghiera**: la Digital Crown si muove a passi del macchinario partendo dal
  peso proposto, quindi dà solo pesi inseribili (anche sui pacchi sfalsati).
- **Avvio**: l'icona del Watch sulla card apre l'app sull'orologio tramite HealthKit (la
  sessione di allenamento la tiene aperta a polso abbassato).
- Una serie arrivata dal Watch senza sessione aperta ne apre una nuova.

### Installazione: dal Mac, non da AltStore

Il sistema collega app iPhone e app Watch **solo se l'app iPhone installata contiene
l'app Watch** (cartella `Watch/`). Verificato sui dispositivi:

| App iPhone installata | App Watch | Risultato |
|---|---|---|
| senza `Watch/` | installata a parte | `watchAppInstalled=false`, niente collegamento |
| con `Watch/` | non installata | l'iPhone non la spinge da solo sull'orologio |
| con `Watch/`, poi Watch installato | ✓ | collegati: serie inviata e ricevuta |
| sostituita con una senza `Watch/` | già installata | il Watch perde il collegamento |

AltStore non può installare un'app con dentro il Watch: AltSign firma con `ldid`, che
riconosce come annidati solo `Frameworks/` e `PlugIns/`. L'eseguibile in `Watch/` viene
rifirmato come file qualsiasi, senza entitlement, e l'installazione fallisce (issue
altstoreio/AltStore #229 e #684). Quindi:

- **ipa di AltStore** (`build_ipa.sh`, CI): senza Watch, per chi non lo usa;
- **versione con il Watch**: `./scripts/install_device.sh`, con iPhone e Watch sbloccati e
  Modalità sviluppatore attiva su entrambi. Compila in una copia temporanea con gli stessi
  bundle id di AltStore (i dati restano), incorpora il Watch, installa prima l'iPhone e
  poi l'orologio. Con l'Apple ID gratuito dura 7 giorni: si rilancia lo script.
  Aggiornare Massimali da AltStore toglie il Watch: si torna a funzionare rilanciando lo script.

Per tenere i sensori accesi a polso abbassato il Watch apre una sessione di
allenamento HealthKit che **non viene mai salvata**: in Salute l'allenamento lo scrive
già l'iPhone.

## Peso corporeo e app Salute

Le pesate si registrano dalla scheda Progressi con selettore dell'orizzonte temporale dinamico.
La sincronizzazione con **Salute** (`Store/HealthService.swift`) funziona in due
direzioni: legge l'ultima pesata da Salute e vi registra gli allenamenti conclusi
come sessioni di *traditional strength training*.

La capability HealthKit **funziona anche con un Apple ID gratuito**: verificato, il
profilo di provisioning personale include `com.apple.developer.healthkit`. Il file
`Massimali/Massimali.entitlements` la dichiara.

L'invio a Salute riporta sempre l'esito: se il permesso di scrivere gli allenamenti
manca, lo dice invece di fallire in silenzio. Una sessione già conclusa — per esempio
chiusa prima di attivare la sincronizzazione — si rimanda con **Invia a Salute** dal
suo dettaglio. A HealthKit attraversano solo le date, mai i modelli SwiftData: non
sono sicuri da leggere fuori dal main actor.

## Non perdere i dati

Due avvisi automatici, perché con la firma gratuita i modi di perdere tutto sono due:

- **Scadenza della firma**: `Utils/Provisioning.swift` legge la data da
  `embedded.mobileprovision` dentro il bundle e, a due giorni dalla fine, mostra un
  banner nella schermata Massimali. Sul simulatore quel file non esiste e non
  compare nulla.
- **Backup fermo da troppo**: l'export passa da un foglio di condivisione che
  riporta l'esito, quindi l'app sa davvero quando hai salvato l'ultimo file. Dopo
  due settimane te lo ricorda.

## Come funzionano i massimali

Il massimale è **il carico vero che hai sollevato**, mai una stima. La regola di
confronto sta tutta in `Model/MaxRanking.swift`: vince prima il peso più alto, e a
parità di peso le ripetizioni in più. Quindi 85 × 3 batte 80 × 8, e 80 × 8 batte
80 × 5.

Il **1RM stimato** (Epley) resta come nota a margine accanto ai numeri veri: serve a
paragonare serie con ripetizioni diverse (e, nella 3.0, a ordinare le classifiche), non
decide mai un record. I record salvano **peso × ripetizioni grezzi** e la stima si
ricalcola al volo.

Durante un allenamento, ogni serie non di riscaldamento che batte il massimale
precedente crea da sola un record marcato *da allenamento*; cancellando quella serie il
record automatico sparisce con lei, mentre i massimali inseriti a mano non vengono mai
toccati.

## Come leggere i grafici

Tutti i grafici temporali hanno l'asse orizzontale a **passo fisso: un punto per
misurazione, non un giorno di calendario**. Saltare due settimane non disegna più una
lunga riga piatta, e il volume settimanale mostra solo le settimane in cui ti sei
davvero allenato. Le date restano stampate sotto alcune tacche
(`Stats.tickIndices`).

Su tutti i grafici di andamento (Dettaglio esercizio, Peso corporeo, Progressione 1RM)
è presente un **selettore dinamico dell'orizzonte temporale** (`1 mese`, `3 mesi`, `1 anno`,
`Tutto`): le opzioni disponibili si adattano automaticamente all'effettiva anzianità dello storico:
- con meno di 30 giorni di dati il selettore resta nascosto, mostrando direttamente tutto lo storico;
- tra 30 e 89 giorni sono disponibili `1 mese` e `Tutto`;
- tra 90 e 364 giorni sono disponibili `1 mese`, `3 mesi` e `Tutto`;
- da 365 giorni in su sono disponibili `3 mesi`, `1 anno` e `Tutto`.

Se in un intervallo selezionato i dati sono inferiori a 2 punti, la card rimane comunque visualizzata
mostrando l'avviso dedicato (*«Dati non sufficienti per questo intervallo»* / *«Not enough data for this range»*),
consentendo di tornare facilmente a un orizzonte più ampio.

I pesi sono **sempre salvati in chilogrammi** e mostrati con **due decimali**, così un
disco da 1,25 kg resta 1,25 e non diventa 1,3. L'impostazione kg/lb cambia solo come
vengono mostrati e inseriti.

## Se un domani passi all'account a pagamento

In `Store/ModelContainer+App.swift` c'è una sola riga da cambiare:
`cloudKitDatabase: .none` → `.automatic`, più la capability iCloud/CloudKit nel target.
Il modello è già compatibile (tutte le proprietà hanno un default, tutte le relazioni
sono opzionali). Da lì diventano possibili anche widget e Live Activity.
