#!/usr/bin/env bash
# Build a product .pkg for MeritScreen (phase d4 skeleton).
# Signing + notarization require Apple Developer ID secrets (see notarize.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="${DIST:-$ROOT/target/macos-pkg}"
PAYLOAD="$DIST/payload"
SCRIPTS="$DIST/scripts"
VERSION="${VERSION:-0.1.0}"
IDENTIFIER="com.meritscreen.desktop"

mkdir -p "$PAYLOAD/Library/MeritScreen" \
  "$PAYLOAD/Library/LaunchDaemons" \
  "$PAYLOAD/Library/LaunchAgents" \
  "$SCRIPTS"

cargo build --release -p meritscreen-guardian -p meritscreen-agent --manifest-path "$ROOT/Cargo.toml"

cp "$ROOT/target/release/meritscreen-guardian" "$PAYLOAD/Library/MeritScreen/"
cp "$ROOT/target/release/meritscreen-agent" "$PAYLOAD/Library/MeritScreen/"
if [[ -f "$ROOT/target/release/meritscreen-ui" ]]; then
  cp "$ROOT/target/release/meritscreen-ui" "$PAYLOAD/Library/MeritScreen/"
fi

cp "$(dirname "$0")/com.meritscreen.guardian.plist" "$PAYLOAD/Library/LaunchDaemons/"
cp "$(dirname "$0")/com.meritscreen.agent.plist" "$PAYLOAD/Library/LaunchAgents/"

cat > "$SCRIPTS/postinstall" <<'EOF'
#!/bin/bash
set -euo pipefail
chmod 755 /Library/MeritScreen/meritscreen-guardian /Library/MeritScreen/meritscreen-agent || true
chmod 644 /Library/LaunchDaemons/com.meritscreen.guardian.plist
chmod 644 /Library/LaunchAgents/com.meritscreen.agent.plist
chown root:wheel /Library/LaunchDaemons/com.meritscreen.guardian.plist
launchctl bootout system /Library/LaunchDaemons/com.meritscreen.guardian.plist 2>/dev/null || true
launchctl bootstrap system /Library/LaunchDaemons/com.meritscreen.guardian.plist || true
# Agent: bootstrap into console user's gui domain when available.
UID_CONSOLE=$(stat -f %u /dev/console 2>/dev/null || true)
if [[ -n "${UID_CONSOLE:-}" ]]; then
  launchctl bootout "gui/${UID_CONSOLE}" /Library/LaunchAgents/com.meritscreen.agent.plist 2>/dev/null || true
  launchctl bootstrap "gui/${UID_CONSOLE}" /Library/LaunchAgents/com.meritscreen.agent.plist || true
fi
exit 0
EOF
chmod 755 "$SCRIPTS/postinstall"

pkgbuild \
  --root "$PAYLOAD" \
  --scripts "$SCRIPTS" \
  --identifier "$IDENTIFIER" \
  --version "$VERSION" \
  --install-location "/" \
  "$DIST/MeritScreen-component.pkg"

# Optional product archive wrapper (distribution XML can expand later).
cp "$DIST/MeritScreen-component.pkg" "$DIST/MeritScreen-${VERSION}.pkg"
echo "Built $DIST/MeritScreen-${VERSION}.pkg"
echo "Next: codesign binaries (hardened runtime) then ./notarize.sh"
