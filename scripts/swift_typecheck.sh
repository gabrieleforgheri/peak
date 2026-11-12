#!/bin/sh
# Typecheck di Peak/, Shared/ e WatchShared/ senza Xcode (circa 30 s), con il controllo di
# concorrenza completo. Fallisce se ci sono errori o se gli avvisi di concorrenza superano la
# baseline in scripts/concurrency-baseline. Con --update la abbassa (mai la alza).
# Non lancia test né simulatore.
cd "$(dirname "$0")/.." || exit 1
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
find Peak Shared WatchShared -name '*.swift' > "$tmp/files"
swiftc -typecheck -sdk "$(xcrun --sdk iphoneos --show-sdk-path)" -target arm64-apple-ios17.0 \
  -module-name Massimali -swift-version 5 -strict-concurrency=complete @"$tmp/files" 2> "$tmp/out"
errors=$(grep -c ': error:' "$tmp/out")
warnings=$(grep ': warning:' "$tmp/out" | sort -u | wc -l | tr -d ' ')
base=$(cat scripts/concurrency-baseline 2>/dev/null || echo 0)
if [ "$errors" -gt 0 ]; then grep ': error:' "$tmp/out" >&2; echo "FALLITO: $errors errori" >&2; exit 1; fi
if [ "$warnings" -gt "$base" ]; then
  grep ': warning:' "$tmp/out" | sort -u >&2
  echo "FALLITO: $warnings avvisi, la baseline e $base. Correggi il tipo (Sendable, isolamento), non silenziare." >&2; exit 1
fi
echo "ok: 0 errori, $warnings avvisi di concorrenza (baseline $base)"
if [ "$1" = "--update" ] && [ "$warnings" -lt "$base" ]; then echo "$warnings" > scripts/concurrency-baseline; echo "baseline abbassata a $warnings"; fi
