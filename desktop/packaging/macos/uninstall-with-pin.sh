#!/usr/bin/env bash
# PIN-gated uninstall helper (d9). Does not claim silent MDM uninstall blocks.
set -euo pipefail

PIN="${1:-}"
BIN_DIR="${MERITSCREEN_BIN_DIR:-/Library/MeritScreen}"
GUARDIAN="${BIN_DIR}/meritscreen-guardian"

if [[ -z "$PIN" ]]; then
  echo "Usage: $0 <parent-pin>" >&2
  echo "Recording uninstallAttempt and aborting." >&2
  if [[ -x "$GUARDIAN" ]]; then
    "$GUARDIAN" --mark-uninstall-attempt || true
  fi
  exit 1
fi

if [[ ! -x "$GUARDIAN" ]]; then
  # Lab fallback
  GUARDIAN="$(dirname "$0")/../../target/release/meritscreen-guardian"
fi

exec "$GUARDIAN" --uninstall-with-pin "$PIN"
