#!/usr/bin/env bash
# Codesign (hardened runtime) + notarize + staple MeritScreen .pkg (d4).
# Required secrets (CI / local keychain — never commit):
#   APPLE_TEAM_ID
#   APPLE_ID                  (Apple ID email for notarytool)
#   APPLE_APP_SPECIFIC_PASSWORD
#   MACOS_SIGNING_IDENTITY    e.g. "Developer ID Application: MeritScreen (TEAMID)"
#   MACOS_INSTALLER_IDENTITY  e.g. "Developer ID Installer: MeritScreen (TEAMID)"
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="${DIST:-$ROOT/target/macos-pkg}"
VERSION="${VERSION:-0.1.0}"
PKG="$DIST/MeritScreen-${VERSION}.pkg"
ENTITLEMENTS="$(dirname "$0")/entitlements.plist"

: "${MACOS_SIGNING_IDENTITY:?set MACOS_SIGNING_IDENTITY}"
: "${MACOS_INSTALLER_IDENTITY:?set MACOS_INSTALLER_IDENTITY}"
: "${APPLE_ID:?set APPLE_ID}"
: "${APPLE_TEAM_ID:?set APPLE_TEAM_ID}"
: "${APPLE_APP_SPECIFIC_PASSWORD:?set APPLE_APP_SPECIFIC_PASSWORD}"

if [[ ! -f "$PKG" ]]; then
  echo "missing $PKG — run ./build-pkg.sh first" >&2
  exit 1
fi

sign_bin() {
  local bin="$1"
  if [[ -f "$bin" ]]; then
    codesign --force --options runtime --timestamp \
      --entitlements "$ENTITLEMENTS" \
      --sign "$MACOS_SIGNING_IDENTITY" \
      "$bin"
    codesign --verify --verbose=2 "$bin"
  fi
}

sign_bin "$DIST/payload/Library/MeritScreen/meritscreen-guardian"
sign_bin "$DIST/payload/Library/MeritScreen/meritscreen-agent"
sign_bin "$DIST/payload/Library/MeritScreen/meritscreen-ui"

# Re-pack after signing payload binaries if needed; for skeleton we sign the product pkg.
productsign --sign "$MACOS_INSTALLER_IDENTITY" "$PKG" "$DIST/MeritScreen-${VERSION}-signed.pkg"
mv "$DIST/MeritScreen-${VERSION}-signed.pkg" "$PKG"

xcrun notarytool submit "$PKG" \
  --apple-id "$APPLE_ID" \
  --team-id "$APPLE_TEAM_ID" \
  --password "$APPLE_APP_SPECIFIC_PASSWORD" \
  --wait

xcrun stapler staple "$PKG"
spctl --assess --type install -vv "$PKG" || true
echo "Notarized + stapled: $PKG"
