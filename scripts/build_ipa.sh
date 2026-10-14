#!/bin/bash
# Builds an unsigned .ipa for AltStore distribution. AltServer re-signs the app
# on install with the user's own Apple ID, so no signing certificate is needed here.
set -euo pipefail
cd "$(dirname "$0")/.."

rm -rf build
xcodegen generate

xcodebuild archive \
  -project Peak.xcodeproj \
  -scheme Peak \
  -configuration Release \
  -archivePath build/Peak.xcarchive \
  -destination "generic/platform=iOS" \
  -skipPackagePluginValidation \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  AD_HOC_CODE_SIGNING_ALLOWED=YES

mkdir -p build/Payload
cp -r build/Peak.xcarchive/Products/Applications/Peak.app build/Payload/

# Firma ad-hoc solo per incidere gli entitlement nel binario: AltStore rifirma
# con quelli che trova qui. Senza, HealthKit manca e Salute non si collega.
codesign -f -s - build/Payload/Peak.app/PlugIns/PeakWidgets.appex
codesign -f -s - --entitlements Peak/Peak.entitlements build/Payload/Peak.app
(cd build && zip -qry Peak.ipa Payload)

echo "build/Peak.ipa"
