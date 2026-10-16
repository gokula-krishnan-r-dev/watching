#!/usr/bin/env bash
# Build a product .pkg for MeritScreen (phase d4).
# Signing + notarization require Apple Developer ID secrets (see notarize.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="${DIST:-$ROOT/target/macos-pkg}"
PAYLOAD="$DIST/payload"
SCRIPTS="$DIST/scripts"
VERSION="${VERSION:-0.1.0}"
IDENTIFIER="com.meritscreen.desktop"
BUILD_CONFIG="${BUILD_CONFIG:-release}"
export COPYFILE_DISABLE=1

rm -rf "$PAYLOAD" "$SCRIPTS"
mkdir -p "$PAYLOAD/Library/MeritScreen" \
  "$PAYLOAD/Library/LaunchDaemons" \
  "$PAYLOAD/Library/LaunchAgents" \
  "$SCRIPTS"

echo "Building binaries (config: $BUILD_CONFIG)..."
if [[ "$BUILD_CONFIG" == "release" ]]; then
  cargo build --release -p meritscreen-guardian -p meritscreen-agent --manifest-path "$ROOT/Cargo.toml"
  BIN_DIR="$ROOT/target/release"
else
  cargo build -p meritscreen-guardian -p meritscreen-agent --manifest-path "$ROOT/Cargo.toml"
  BIN_DIR="$ROOT/target/debug"
fi

cp "$BIN_DIR/meritscreen-guardian" "$PAYLOAD/Library/MeritScreen/"
cp "$BIN_DIR/meritscreen-agent" "$PAYLOAD/Library/MeritScreen/"
if [[ -f "$BIN_DIR/meritscreen-ui" ]]; then
  cp "$BIN_DIR/meritscreen-ui" "$PAYLOAD/Library/MeritScreen/"
fi

chmod 755 "$PAYLOAD/Library/MeritScreen/meritscreen-guardian" "$PAYLOAD/Library/MeritScreen/meritscreen-agent"
if [[ -f "$PAYLOAD/Library/MeritScreen/meritscreen-ui" ]]; then
  chmod 755 "$PAYLOAD/Library/MeritScreen/meritscreen-ui"
fi

cp "$(dirname "$0")/com.meritscreen.guardian.plist" "$PAYLOAD/Library/LaunchDaemons/"
cp "$(dirname "$0")/com.meritscreen.agent.plist" "$PAYLOAD/Library/LaunchAgents/"

cat > "$SCRIPTS/postinstall" <<'EOF'
#!/bin/bash
set -euo pipefail
chmod 755 /Library/MeritScreen/meritscreen-guardian /Library/MeritScreen/meritscreen-agent || true
if [[ -f /Library/MeritScreen/meritscreen-ui ]]; then
  chmod 755 /Library/MeritScreen/meritscreen-ui || true
fi
chmod 644 /Library/LaunchDaemons/com.meritscreen.guardian.plist
chmod 644 /Library/LaunchAgents/com.meritscreen.agent.plist
chown root:wheel /Library/LaunchDaemons/com.meritscreen.guardian.plist
chown root:wheel /Library/LaunchAgents/com.meritscreen.agent.plist
launchctl bootout system /Library/LaunchDaemons/com.meritscreen.guardian.plist 2>/dev/null || true
launchctl bootstrap system /Library/LaunchDaemons/com.meritscreen.guardian.plist || true
# Agent: bootstrap into console user's gui domain when available.
UID_CONSOLE=$(stat -f %u /dev/console 2>/dev/null || true)
if [[ -n "${UID_CONSOLE:-}" && "${UID_CONSOLE}" != "0" ]]; then
  launchctl bootout "gui/${UID_CONSOLE}" /Library/LaunchAgents/com.meritscreen.agent.plist 2>/dev/null || true
  launchctl bootstrap "gui/${UID_CONSOLE}" /Library/LaunchAgents/com.meritscreen.agent.plist || true
fi
exit 0
EOF
chmod 755 "$SCRIPTS/postinstall"

dot_clean -m "$PAYLOAD" || true
dot_clean -m "$SCRIPTS" || true

echo "Packaging with pkgbuild..."
pkgbuild \
  --root "$PAYLOAD" \
  --scripts "$SCRIPTS" \
  --identifier "$IDENTIFIER" \
  --version "$VERSION" \
  --install-location "/" \
  "$DIST/MeritScreen-${VERSION}.pkg"

echo "SUCCESS: Built $DIST/MeritScreen-${VERSION}.pkg"
echo "Inspect with: pkgutil --expand-full $DIST/MeritScreen-${VERSION}.pkg /tmp/expanded-pkg"
echo "Next: sign with Developer ID Application + Installer and run ./notarize.sh"
