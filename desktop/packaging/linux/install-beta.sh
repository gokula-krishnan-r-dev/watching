#!/usr/bin/env bash
# Quick installation script for Linux beta testers.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "This script must be run as root (sudo ./install-beta.sh)"
  exit 1
fi

BIN_DIR="${1:-/usr/bin}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Installing MeritScreen Linux Beta..."

install -d /etc/systemd/system
install -d /etc/xdg/autostart

install -m 644 "${SCRIPT_DIR}/meritscreen-guardian.service" /etc/systemd/system/
install -m 644 "${SCRIPT_DIR}/meritscreen-agent.desktop" /etc/xdg/autostart/

systemctl daemon-reload
systemctl enable --now meritscreen-guardian.service

echo "MeritScreen Guardian service active:"
systemctl status meritscreen-guardian.service --no-pager || true
