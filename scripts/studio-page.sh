#!/bin/bash
# Rifà schermate e clip della pagina yrb4g.com/studio/peak e le copia nel repo yG.
# Si lancia a ogni versione di Peak: la pagina presenta sempre l'ultima. Poi in yG: npm test, commit, deploy,
# e se l'app ha funzioni nuove si ritoccano i testi in views/studio-peak.ejs (e la coreografia in studio-shots.ts).
# Serve: npm i --no-save playwright-core (una volta), ffmpeg, cwebp, Chrome. Ci mette qualche minuto.
# Uso: scripts/studio-page.sh [cartella del repo yG]
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-$HOME/Developer/yG}/public/peak-studio"
[ -d "$OUT" ] || { echo "manca $OUT" >&2; exit 1; }
hf() { npx -y hyperframes@0.8.166 render --crf 30 --quiet "$@" > /dev/null; }

rm -rf studio-clips/shots studio-clips/renders
node scripts/studio-shots.ts
node studio-clips/schermate.ts
cd studio-clips
for f in schermata-*.html; do c=${f#schermata-}; hf -c "$f" -o "renders/${c%.html}.mp4"; done
hf -c intro.html -o renders/intro.mp4
hf -c sync.html -o renders/sync.mp4
for l in it en; do hf -c record.html -o "renders/record-$l.mp4" --variables "{\"lang\":\"$l\"}"; done

# Poster pieno (movimento ridotto, autoplay rifiutato): la schermata vera, o la clip a costruzione finita.
for f in shots/*-??.png; do cwebp -quiet -q 82 "$f" -o "$OUT/$(basename "${f%.png}").webp"; done
for c in intro sync record-it record-en; do
  ffmpeg -loglevel error -y -ss 4.2 -i "renders/$c.mp4" -frames:v 1 "renders/$c-pieno.png"
  cwebp -quiet -q 80 "renders/$c-pieno.png" -o "$OUT/$c.webp"
done
# La clip e il suo primo fotogramma (poster mentre si scarica: niente stacco quando parte).
for v in renders/*.mp4; do
  b=$(basename "$v" .mp4)
  cp "$v" "$OUT/"
  ffmpeg -loglevel error -y -i "$v" -frames:v 1 "renders/$b-0.png"
  cwebp -quiet -q 80 "renders/$b-0.png" -o "$OUT/$b-0.webp"
done
echo "Fatto: $(ls "$OUT"/*.mp4 | wc -l | tr -d ' ') clip in $OUT. In yG: npm test, commit, deploy."
