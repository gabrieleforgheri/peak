#!/bin/bash
# Compila Peak per Android: APK (da installare a mano) e AAB (per il Play Store), firmati.
#
# L'app è una Trusted Web Activity: apre peak.yrb4g.com dentro Chrome, a schermo intero.
# Il progetto in android/ l'ha generato Bubblewrap; colori e versione stanno in
# android/app/build.gradle (twaManifest) e android/twa-manifest.json.
# Per una versione nuova alza appVersionCode in android/app/build.gradle.
#
# Serve: brew install openjdk@17 && brew install --cask android-commandlinetools
#        sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
# La chiave sta in ~/.config/peak-android/ (peak.keystore + password), MAI nel repo:
# se si perde, l'app installata non si aggiorna più. La sua impronta SHA-256 è in
# web/public/.well-known/assetlinks.json: senza quel file online l'app mostra la barra
# dell'indirizzo.
#
# Uscita: build/android/peak.apk e build/android/peak.aab
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME=${JAVA_HOME_17:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
KEYS=$HOME/.config/peak-android
TOOLS=$ANDROID_HOME/build-tools/36.0.0
OUT=build/android
PASS=$(cat "$KEYS/password")

(cd android && ./gradlew --quiet assembleRelease bundleRelease)
mkdir -p "$OUT"

"$TOOLS/zipalign" -f -p 4 android/app/build/outputs/apk/release/app-release-unsigned.apk "$OUT/peak-unsigned.apk"
"$TOOLS/apksigner" sign --ks "$KEYS/peak.keystore" --ks-key-alias peak --ks-pass "pass:$PASS" \
  --out "$OUT/peak.apk" "$OUT/peak-unsigned.apk"
rm "$OUT/peak-unsigned.apk"

cp android/app/build/outputs/bundle/release/app-release.aab "$OUT/peak.aab"
"$JAVA_HOME/bin/jarsigner" -keystore "$KEYS/peak.keystore" -storepass "$PASS" "$OUT/peak.aab" peak >/dev/null

"$TOOLS/apksigner" verify "$OUT/peak.apk"
"$JAVA_HOME/bin/jarsigner" -verify "$OUT/peak.aab" >/dev/null
ls -lh "$OUT"
