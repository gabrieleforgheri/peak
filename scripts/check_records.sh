#!/bin/bash
# Prova la regola dei record in Swift sugli stessi vettori della versione TypeScript.
# Niente simulatore, niente Xcode: solo swiftc, che compila in pochi secondi.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)/check_records
swiftc -O -o "$OUT" Peak/Model/MaxRanking.swift Peak/Model/RecordRules.swift scripts/check_records/main.swift
"$OUT" common/fixtures/records.json
