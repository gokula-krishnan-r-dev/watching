#!/usr/bin/env bash
# Deprecated entrypoint — use ./scripts/run-demo.sh
# Kept so existing docs/muscle-memory keep working.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec "$ROOT/scripts/run-demo.sh" --emulators "$@"
