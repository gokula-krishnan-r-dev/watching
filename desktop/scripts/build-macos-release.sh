#!/usr/bin/env bash
# Build shareable Watching macOS DMG (+ Guardian .pkg) on a Mac.
# Firebase key is baked in at build time from ~/.config/watching/firebase.env
# (or the environment); it is never committed.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f "$HOME/.config/watching/firebase.env" ]]; then set -a; source "$HOME/.config/watching/firebase.env"; set +a; fi
: "${MERITSCREEN_FIREBASE_API_KEY:?Set MERITSCREEN_FIREBASE_API_KEY (see ~/.config/watching/firebase.env)}"
export MERITSCREEN_FIREBASE_PROJECT_ID="${MERITSCREEN_FIREBASE_PROJECT_ID:-managing-screen-time}"
unset MERITSCREEN_DEV_SHORTCUTS MERITSCREEN_PARENT_DEMO || true
TRIPLE=$(rustc -vV | sed -n 's/host: //p')
echo "Building Guardian + Agent (release, $TRIPLE)..."
cargo build --release -p meritscreen-guardian -p meritscreen-agent
mkdir -p apps/meritscreen-ui/binaries
cp target/release/meritscreen-guardian "apps/meritscreen-ui/binaries/meritscreen-guardian-$TRIPLE"
cp target/release/meritscreen-agent "apps/meritscreen-ui/binaries/meritscreen-agent-$TRIPLE"
echo "Building Watching.app + DMG..."
(cd apps/meritscreen-ui/ui && npm ci); (cd apps/meritscreen-ui && ./ui/node_modules/.bin/tauri build --config tauri.release.conf.json --bundles dmg,app)
echo "Building Guardian installer .pkg (installs LaunchDaemon/Agent)..."
BUILD_CONFIG=release ./packaging/macos/build-pkg.sh || echo "WARN: pkg build failed; DMG is still shareable for parent/UI use"
OUT="$HOME/Desktop/Watching-Builds"; mkdir -p "$OUT"
cp -f target/release/bundle/dmg/*.dmg "$OUT/" 2>/dev/null || true
cp -f target/macos-pkg/*.pkg "$OUT/" 2>/dev/null || true
ls -lh "$OUT"
