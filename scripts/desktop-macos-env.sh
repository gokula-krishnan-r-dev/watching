#!/usr/bin/env bash
# Source Firebase env for MeritScreen desktop on macOS.
# Usage: source scripts/desktop-macos-env.sh
#
# Prefer existing shell exports; otherwise use the public Web API key for
# project managing-screen-time (same as apps/admin). Firebase CLI login is
# required for deploy / projects:list checks (see desktop-macos-e2e.sh).

_ms_desktop_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export MERITSCREEN_FIREBASE_PROJECT_ID="${MERITSCREEN_FIREBASE_PROJECT_ID:-managing-screen-time}"
export MERITSCREEN_FIREBASE_REGION="${MERITSCREEN_FIREBASE_REGION:-us-central1}"

if [[ -z "${MERITSCREEN_FIREBASE_API_KEY:-}" ]]; then
  # Public Firebase Web API key (client key) — not a server secret.
  export MERITSCREEN_FIREBASE_API_KEY="AIzaSyDNXTbTLRBCkCzxINt-Q-iahnFGkKfBCNM"
fi

# Isolate lab data from other runs / keychain noise.
export MERITSCREEN_DATA_DIR="${MERITSCREEN_DATA_DIR:-$HOME/Library/Application Support/MeritScreen/lab}"
export MERITSCREEN_SECRETS_DIR="${MERITSCREEN_SECRETS_DIR:-$HOME/Library/Application Support/MeritScreen/lab-secrets}"
mkdir -p "$MERITSCREEN_DATA_DIR" "$MERITSCREEN_SECRETS_DIR"

# Ensure HMAC + peer checks work in lab (same machine).
unset MERITSCREEN_IPC_HMAC_DISABLE 2>/dev/null || true
# Do not force mock sync when API key is present.
unset MERITSCREEN_SYNC_MOCK 2>/dev/null || true
unset MERITSCREEN_SYNC_DISABLE 2>/dev/null || true

export MERITSCREEN_DESKTOP_ROOT="$_ms_desktop_root"

echo "MeritScreen desktop Firebase env:"
echo "  PROJECT_ID=$MERITSCREEN_FIREBASE_PROJECT_ID"
echo "  REGION=$MERITSCREEN_FIREBASE_REGION"
echo "  API_KEY=${MERITSCREEN_FIREBASE_API_KEY:0:8}…"
echo "  DATA_DIR=$MERITSCREEN_DATA_DIR"
