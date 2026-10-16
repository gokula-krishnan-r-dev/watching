#!/usr/bin/env bash
# Lab install without a signed .pkg — user LaunchAgents (survives login, not boot).
# For true boot LaunchDaemon: sudo copy binaries to /Library/MeritScreen and
#   sudo meritscreen-guardian --install-daemon
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
cargo build -p meritscreen-guardian -p meritscreen-agent
BIN="$ROOT/target/debug"
"$BIN/meritscreen-guardian" --install-user --bin-dir "$BIN"
echo "Lab LaunchAgents installed. Check:"
echo "  launchctl print gui/\$(id -u)/com.meritscreen.guardian"
echo "  launchctl print gui/\$(id -u)/com.meritscreen.agent"
echo "IPC: /tmp/meritscreen.guardian.v1.sock"
echo "Uninstall: $BIN/meritscreen-guardian --uninstall-user"
