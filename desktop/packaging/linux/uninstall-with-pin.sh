#!/usr/bin/env bash
# PIN-gated Linux uninstaller helper.
set -euo pipefail

PIN="${1:-}"
if [ -z "${PIN}" ]; then
  read -rsp "Enter Parent PIN to uninstall MeritScreen: " PIN
  echo
fi

if [ "$(id -u)" -ne 0 ]; then
  echo "Must be run as root: sudo ./uninstall-with-pin.sh [PIN]"
  exit 1
fi

GUARDIAN_BIN="/usr/bin/meritscreen-guardian"
if [ ! -x "${GUARDIAN_BIN}" ]; then
  GUARDIAN_BIN="$(command -v meritscreen-guardian || echo "")"
fi

if [ -z "${GUARDIAN_BIN}" ] || [ ! -x "${GUARDIAN_BIN}" ]; then
  echo "meritscreen-guardian binary not found; removing systemd units directly..."
  systemctl stop meritscreen-guardian.service 2>/dev/null || true
  systemctl disable meritscreen-guardian.service 2>/dev/null || true
  rm -f /etc/systemd/system/meritscreen-guardian.service
  rm -f /etc/xdg/autostart/meritscreen-agent.desktop
  systemctl daemon-reload
  echo "Uninstalled."
  exit 0
fi

"${GUARDIAN_BIN}" --uninstall-with-pin "${PIN}"
