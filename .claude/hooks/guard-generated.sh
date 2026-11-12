#!/bin/sh
# PreToolUse (Edit|Write|MultiEdit): nega la modifica a mano dei file generati.
# Si cambia la sorgente e si rilancia il generatore (vedi scripts/README.md).
f=$(jq -r '.tool_input.file_path // empty')
[ -z "$f" ] && exit 0
case "$f" in
  */common/anatomy.ts | */Peak/Design/MuscleFigureShapes.swift)
    src="scripts/gen-anatomy.ts (sorgenti in scripts/anatomy-src/)" ;;
  */Peak/Design/MachineGlyphShapes.swift)
    src="scripts/gen-glyphs.ts (sorgente common/glyph.ts)" ;;
  */studio-clips/schermata-*.html)
    src="studio-clips/schermate.ts e scripts/studio-shots.ts" ;;
  */web/dist/*)
    src="npm run build" ;;
  *) exit 0 ;;
esac
echo "File generato: non si modifica a mano. Cambia la sorgente e rilancia il generatore: $src." >&2
exit 2
