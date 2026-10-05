#!/usr/bin/env bash
# Boot two iOS Simulators and install/launch MeritScreen on both for
# parent ↔ child pairing / sync testing.
#
# Defaults:
#   PARENT → MeritScreen_Parent
#   CHILD  → MeritScreen_Child
#
# Dual simulators are memory-heavy. Prefer this for pairing flows; for UI
# work on one role use: make ios-dev
#
# Usage:
#   ./scripts/run-ios-dual-demo.sh
#   ./scripts/run-ios-dual-demo.sh --rebuild
#   ./scripts/run-ios-dual-demo.sh --reuse
#   make ios-up-dev
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=ios-common.sh
source "$ROOT/scripts/ios-common.sh"

REUSE=0
FORCE_REBUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reuse) REUSE=1 ;;
    --rebuild) FORCE_REBUILD=1; REUSE=0 ;;
    --quiet|-q) QUIET=1 ;;
    -h|--help)
      awk 'NR==1{next} /^[^#]/{exit} {sub(/^# ?/,""); print}' "$0"
      exit 0
      ;;
    *) die "Unknown flag: $1" ;;
  esac
  shift
done

require_ios_tools

PARENT_UDID="$(ensure_simulator "$PARENT_SIM")"
CHILD_UDID="$(ensure_simulator "$CHILD_SIM")"

boot_simulator "$PARENT_UDID" "parent"
boot_simulator "$CHILD_UDID" "child"

if [[ "$FORCE_REBUILD" -eq 1 ]] || [[ "$REUSE" -eq 0 ]]; then
  build_app 1
else
  build_app 0
fi

install_and_launch "$PARENT_UDID" "parent"
install_and_launch "$CHILD_UDID" "child"

if [[ "$QUIET" -eq 0 ]]; then
  cat <<EOF

Ready.

  Parent  $PARENT_SIM  ($PARENT_UDID)
  Child   $CHILD_SIM   ($CHILD_UDID)
  Bundle  $(resolve_bundle_id)

  Tip: in Simulator, Window → Tile Window / use Device → Pair for side-by-side.
  make ios-reload   # rebuild + reinstall on both
  make ios-down     # shut down both
EOF
fi
