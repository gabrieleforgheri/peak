#!/usr/bin/env bash
# Prova di integrazione della fase 2: l'app nell'emulatore contro il VERO server di Peak in locale (mai la produzione).
#   - spinge dati dell'app e controlla che il server li veda;
#   - li riprende (un "altro dispositivo" scrive sul server);
#   - prova un conflitto (due modifiche allo stesso documento), nei due versi;
#   - ripristina un backup del server nell'app e manda a quello del server un backup dell'app;
#   - apre la Custom Tab del login (URL giusto) e gestisce peak://login?code=.
# Il login vero con yG non si automatizza: qui il token è quello che crea android/scripts/dev-device.ts.
# Serve un emulatore avviato (AVD peak_test) e `node` 24. Uso (dalla radice del repo): android/scripts/sync-e2e.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
ADB="$ANDROID_HOME/platform-tools/adb"
PORT="${PORT:-3416}"
BASE="http://127.0.0.1:$PORT"
PKG=com.gabrieleforghieri.peak
DATA="$(mktemp -d)"
SHOTS="${SHOTS:-$ROOT/android/docs}"
SERVER_PID=""

cleanup() { [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true; rm -rf "$DATA"; }
trap cleanup EXIT

step() { printf '\n== %s\n' "$*"; }
fail() { printf 'ERRORE: %s\n' "$*" >&2; exit 1; }
js() { node -e "$1"; }   # piccoli controlli sul JSON

[ "$("$ADB" get-state 2>/dev/null)" = "device" ] || fail "nessun emulatore: avvialo (emulator -avd peak_test) e riprova"

step "server locale su $BASE (dati in $DATA)"
CRED="$(node "$ROOT/android/scripts/dev-device.ts" "$DATA")"
TOKEN="$(printf '%s' "$CRED" | js "console.log(JSON.parse(require('fs').readFileSync(0,'utf8')).token)")"
SUB="$(printf '%s' "$CRED" | js "console.log(JSON.parse(require('fs').readFileSync(0,'utf8')).sub)")"
curl -fs "$BASE/api/health" >/dev/null 2>&1 && fail "la porta $PORT è già occupata da un altro server"
(cd "$ROOT" && PORT=$PORT DATA_DIR="$DATA" exec node server/index.ts > "$DATA/server.log" 2>&1) &
SERVER_PID=$!
for _ in $(seq 1 50); do curl -fs "$BASE/api/health" >/dev/null 2>&1 && break; sleep 0.2; done
curl -fs "$BASE/api/health" >/dev/null || fail "il server non parte: $(cat "$DATA/server.log")"

api() { curl -fsS -H "Authorization: Bearer $TOKEN" "$@"; }
assert() { [ "$2" = "$3" ] || fail "$1: atteso «$3», trovato «$2»"; }

step "build di debug che parla con 10.0.2.2:$PORT e installazione"
(cd "$ROOT/android" && ./gradlew assembleDebug -PpeakBaseUrl="http://10.0.2.2:$PORT" --console=plain -q)
APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" uninstall $PKG >/dev/null 2>&1 || true
"$ADB" install -r "$APK" >/dev/null
# l'app in primo piano e già avviata: un processo avviato solo da un broadcast viene congelato prima di finire (avvio lento nell'emulatore)
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
sleep 10
"$ADB" logcat -c

# -f 32 = FLAG_INCLUDE_STOPPED_PACKAGES: l'app appena installata è «ferma» e senza non riceve il comando
cmd() { "$ADB" shell am broadcast -f 32 -n $PKG/.DebugCommands --es cmd "$@" >/dev/null; }
# aspetta una riga nel log con quel testo (i comandi sono asincroni) e la stampa
await() {
  for _ in $(seq 1 60); do
    LINE="$("$ADB" logcat -d -s PeakDebug:I | grep -F "$1" | tail -1 || true)"
    [ -n "$LINE" ] && { echo "   ${LINE#*PeakDebug: }"; return 0; }
    sleep 0.5
  done
  fail "nel log non compare «$1»: $("$ADB" logcat -d -s PeakDebug:I AndroidRuntime:E | tail -8)"
}
run() { "$ADB" logcat -c; cmd "$@"; await "$EXPECT"; }

step "1. dati nell'app, collegamento, scelta «carica i dati di questo telefono»"
EXPECT="OK seed"; run seed
EXPECT="OK connect"; run connect --es token "$TOKEN" --es sub "$SUB" --es nome "Prova"
[ -n "$("$ADB" logcat -d -s PeakDebug | grep 'stage=EmptyServer(hasLocalData=true)')" ] || fail "doveva chiedere cosa fare (server vuoto, telefono con dati)"
EXPECT="OK choice"; run choice --es which upload
[ -n "$("$ADB" logcat -d -s PeakDebug | grep 'stage=com.gabrieleforghieri.peak.sync.ConnectFlow\$Stage\$Done')" ] || fail "il collegamento non si è chiuso"

step "2. il server vede i dati dell'app"
api "$BASE/api/sync?dopo=0" > "$DATA/pull1.json"
js "
const d=JSON.parse(require('fs').readFileSync('$DATA/pull1.json','utf8')).docs.filter(x=>!x.deleted);
const by=k=>d.filter(x=>x.kind===k);
const ex=by('exercise')[0];
console.log('   documenti:',Object.fromEntries(['setting','exercise','record','workout','set','bodyweight'].map(k=>[k,by(k).length])));
if(ex.uuid!=='0000AAAA-0000-4000-8000-000000000001'||ex.data.name!=='Leg Press')throw new Error('macchinario');
if(by('workout').length!==1||by('set').length!==2||by('bodyweight').length!==1)throw new Error('allenamento/serie/pesata');
if(by('record').length!==0)throw new Error('i massimali da allenamento non devono viaggiare');
"
api "$BASE/api/backup" > "$DATA/server-backup.json"
js "const b=JSON.parse(require('fs').readFileSync('$DATA/server-backup.json','utf8'));
if(b.exercises[0].records.filter(r=>r.isEstimated).length<1)throw new Error('il server doveva derivare i record');
console.log('   backup del server: ',b.exercises.length,'macchinari,',b.workouts[0].sets.length,'serie, record automatici derivati dal server')"

step "3. un altro dispositivo scrive sul server, l'app lo riprende"
NOW="$(js 'console.log(Date.now())')"
NEWEX="0000BBBB-0000-4000-8000-000000000001"
api -X POST -H 'content-type: application/json' "$BASE/api/sync" -d "{\"docs\":[{\"kind\":\"exercise\",\"uuid\":\"$NEWEX\",\"m\":$NOW,\"deleted\":false,\"data\":{\"name\":\"Dal web\",\"muscleGroup\":\"schiena\",\"notes\":\"\",\"incrementStep\":2.5,\"sortIndex\":5,\"isArchived\":false,\"createdAt\":\"2026-10-01T10:00:00Z\"}}]}" > /dev/null
EXPECT="OK sync"; run sync
EXPECT="DUMP"; run dump
case "$LINE" in *"Dal web"*) ;; *) fail "l'app non ha preso «Dal web»: $LINE";; esac

step "4. conflitto A: il server ha la modifica più recente, quella dell'app perde"
FUTURE=$((NOW + 600000))
EXPECT="OK rename"; run rename --es uuid 0000AAAA-0000-4000-8000-000000000001 --es nome "'Leg Press app'"
# prima che l'app si sincronizzi, un altro dispositivo scrive lo stesso documento con una m più alta
api -X POST -H 'content-type: application/json' "$BASE/api/sync" -d "{\"docs\":[{\"kind\":\"exercise\",\"uuid\":\"0000AAAA-0000-4000-8000-000000000001\",\"m\":$FUTURE,\"deleted\":false,\"data\":{\"name\":\"Leg Press server\",\"muscleGroup\":\"gambe\",\"notes\":\"\",\"incrementStep\":10,\"sortIndex\":0,\"isArchived\":false,\"createdAt\":\"2026-09-01T10:00:00Z\"}}]}" > /dev/null
EXPECT="OK sync"; run sync
EXPECT="DUMP"; run dump
case "$LINE" in *"Leg Press server"*) ;; *) fail "doveva vincere il server: $LINE";; esac
assert "nome sul server" "$(api "$BASE/api/sync?dopo=0" | js "console.log(JSON.parse(require('fs').readFileSync(0,'utf8')).docs.find(d=>d.uuid==='0000AAAA-0000-4000-8000-000000000001'&&d.kind==='exercise').data.name)")" "Leg Press server"

step "5. conflitto B: l'app modifica dopo, la modifica dell'app vince sul server"
# la m del server (FUTURE, +10 minuti) è maggiore dell'orologio dell'app: l'app la supera perché ogni modifica ha m > la precedente
EXPECT="OK rename"; run rename --es uuid 0000AAAA-0000-4000-8000-000000000001 --es nome "'Leg Press app dopo'"
EXPECT="OK sync"; run sync
assert "nome sul server" "$(api "$BASE/api/sync?dopo=0" | js "console.log(JSON.parse(require('fs').readFileSync(0,'utf8')).docs.find(d=>d.uuid==='0000AAAA-0000-4000-8000-000000000001'&&d.kind==='exercise').data.name)")" "Leg Press app dopo"

step "6. cancellazione sul server: l'app la applica"
api -X POST -H 'content-type: application/json' "$BASE/api/sync" -d "{\"docs\":[{\"kind\":\"exercise\",\"uuid\":\"$NEWEX\",\"m\":$((FUTURE + 1000)),\"deleted\":true,\"data\":null}]}" > /dev/null
EXPECT="OK sync"; run sync
EXPECT="DUMP"; run dump
case "$LINE" in *"Dal web"*) fail "«Dal web» doveva sparire: $LINE";; esac

step "7. backup: l'app riprende quello del server; il server accetta quello dell'app"
"$ADB" shell "run-as $PKG sh -c 'cat > files/server-backup.json'" < "$DATA/server-backup.json"
EXPECT="OK restore"; run restore --es file server-backup.json --es mode merge
EXPECT="OK export"; run export --es file app-backup.json
"$ADB" shell "run-as $PKG cat files/app-backup.json" > "$DATA/app-backup.json"
HTTP="$(curl -s -o "$DATA/import.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" -H 'content-type: application/json' --data-binary @"$DATA/app-backup.json" "$BASE/api/importa?modo=unisci")"
assert "importazione del backup dell'app sul server" "$HTTP" "200"
echo "   il server ha accettato il backup dell'app: $(cat "$DATA/import.json")"

# il pulsante «Collega l'account yG» (la scheda Me): in fondo, si trova dal dump dell'interfaccia
tap_text() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" pull /sdcard/ui.xml "$DATA/ui.xml" >/dev/null 2>&1
  python3 - "$DATA/ui.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8').read()
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if sys.argv[2] in m.group(1):
        x = (int(m.group(2)) + int(m.group(4))) // 2; y = (int(m.group(3)) + int(m.group(5))) // 2
        print(x, y); sys.exit(0)
sys.exit(1)
PY
}

if [ -n "${SCREENSHOT:-}" ]; then
  step "schermata provvisoria della scheda Me, collegata → $SCREENSHOT"
  "$ADB" shell cmd locale set-app-locales $PKG --locales it-IT >/dev/null 2>&1 || true # l'emulatore è in inglese: lo screenshot in italiano
  "$ADB" shell am force-stop $PKG; "$ADB" shell am start -n $PKG/.MainActivity >/dev/null; sleep 6
  XY="$(tap_text 'Me' || true)"; [ -n "$XY" ] && "$ADB" shell input tap $XY; sleep 2
  "$ADB" exec-out screencap -p > "$SCREENSHOT"
fi

step "8. login: la Custom Tab si apre con l'URL giusto, il ritorno peak://login?code= è gestito"
EXPECT="OK disconnect"; run disconnect
"$ADB" shell am force-stop $PKG
"$ADB" logcat -c
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
sleep 6
XY="$(tap_text 'Me' || true)"; [ -n "$XY" ] && "$ADB" shell input tap $XY; sleep 1.5
XY="$(tap_text "Collega l'account yG")" || fail "non trovo il pulsante «Collega l'account yG»"
"$ADB" logcat -c
"$ADB" shell input tap $XY
for _ in $(seq 1 20); do URL="$("$ADB" logcat -d -s PeakLogin:I | grep -o 'http://10.0.2.2:[^ ]*' | tail -1 || true)"; [ -n "$URL" ] && break; sleep 0.5; done
[ -n "$URL" ] || fail "la Custom Tab non è stata aperta"
sleep 2
"$ADB" logcat -d | grep -E "START u0|Displayed" | grep -q "com.android.chrome" || fail "il browser (Custom Tab) non è partito"
echo "   URL aperto: $URL"
case "$URL" in "http://10.0.2.2:$PORT/auth/login?app="*"&nome="*) ;; *) fail "URL inatteso";; esac
CHALLENGE="$(printf '%s' "$URL" | sed -E 's/.*app=([A-Za-z0-9_-]+)&.*/\1/')"
[ "${#CHALLENGE}" = 43 ] || fail "la sfida PKCE doveva avere 43 caratteri: $CHALLENGE"
# il ritorno dal browser: l'intent filter porta l'indirizzo all'app, che scambia il codice sul server (che lo rifiuta: non l'ha emesso lui)
"$ADB" logcat -c
"$ADB" shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "peak://login?code=CODICE-DI-PROVA" >/dev/null
sleep 3
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" pull /sdcard/ui.xml "$DATA/ui.xml" >/dev/null 2>&1
"$ADB" logcat -d -s PeakLogin:I | grep -q "verifier PKCE trovato" || fail "il ritorno non ha trovato il verifier PKCE salvato"
grep -q "Il collegamento è scaduto" "$DATA/ui.xml" || fail "l'app doveva mostrare «Il collegamento è scaduto»: il codice di prova non lo ha emesso il server"
echo "   peak://login?code=… ricevuto dall'app, scambiato su /auth/app/token e rifiutato dal server: ok"
"$ADB" logcat -d | grep -q "FATAL EXCEPTION" && fail "crash nel log" || true

printf '\nTUTTO OK\n'
