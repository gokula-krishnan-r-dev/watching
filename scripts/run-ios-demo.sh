#!/usr/bin/env bash
# Boot one iOS Simulator and install/launch MeritScreen.
#
# Defaults:
#   Simulator → MeritScreen_Parent (created if missing)
#
# Usage:
#   ./scripts/run-ios-demo.sh
#   ./scripts/run-ios-demo.sh --rebuild
#   ./scripts/run-ios-demo.sh --reuse
#   ./scripts/run-ios-demo.sh --no-boot   # assume simulator already up
#   make ios-dev
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=ios-common.sh
source "$ROOT/scripts/ios-common.sh"

REUSE=0
FORCE_REBUILD=0
START_SIM=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reuse) REUSE=1 ;;
    --rebuild) FORCE_REBUILD=1; REUSE=0 ;;
    --no-boot) START_SIM=0 ;;
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
if [[ "$START_SIM" -eq 1 ]]; then
  boot_simulator "$PARENT_UDID" "parent"
fi

if [[ "$FORCE_REBUILD" -eq 1 ]] || [[ "$REUSE" -eq 0 ]]; then
  build_app 1
else
  build_app 0
fi

install_and_launch "$PARENT_UDID" "parent"

if [[ "$QUIET" -eq 0 ]]; then
  cat <<EOF

Ready.

  Parent  $PARENT_SIM  ($PARENT_UDID)
  Bundle  $(resolve_bundle_id)

  make ios-up-dev   # parent + child on two simulators
  make ios-reload   # rebuild + reinstall
  make ios-down     # shut down MeritScreen simulators
EOF
fi
