#!/usr/bin/env bash
# Conditionally embed Screen Time / Family Controls appexes into MeritScreen.app.
#
# Debug: always embed (development Family Controls works on device).
# Release: skip unless FAMILY_CONTROLS_DISTRIBUTION=YES — store profiles reject
# the development-only entitlement, and ASC rejects appexes without it.
#
# After Apple approves Family Controls distribution:
#   1. Restore family-controls in *Release.entitlements
#   2. Set FAMILY_CONTROLS_DISTRIBUTION=YES on Release (or xcodebuild)
#   3. Archive again
#
# Invoked as an Xcode Run Script phase (target dependencies still build the appexes).
set -euo pipefail

SHOULD_EMBED=0
if [[ "${CONFIGURATION:-}" == "Debug" ]]; then
  SHOULD_EMBED=1
elif [[ "${FAMILY_CONTROLS_DISTRIBUTION:-}" == "YES" ]]; then
  SHOULD_EMBED=1
fi

PLUGINS="${TARGET_BUILD_DIR}/${WRAPPER_NAME}/PlugIns"
mkdir -p "$PLUGINS"

APPEXES=(
  MeritScreenDeviceActivity.appex
  MeritScreenShieldConfig.appex
  MeritScreenShieldAction.appex
)

if [[ "$SHOULD_EMBED" -ne 1 ]]; then
  echo "note: skipping Screen Time extension embed (Release without FAMILY_CONTROLS_DISTRIBUTION)"
  for appex in "${APPEXES[@]}"; do
    rm -rf "${PLUGINS}/${appex}"
  done
  if [[ -d "$PLUGINS" ]] && [[ -z "$(ls -A "$PLUGINS" 2>/dev/null || true)" ]]; then
    rmdir "$PLUGINS" || true
  fi
  exit 0
fi

for appex in "${APPEXES[@]}"; do
  src="${BUILT_PRODUCTS_DIR}/${appex}"
  dst="${PLUGINS}/${appex}"
  if [[ ! -d "$src" ]]; then
    echo "error: missing built extension: $src" >&2
    exit 1
  fi
  rm -rf "$dst"
  cp -R "$src" "$dst"
  echo "note: embedded ${appex}"
done
