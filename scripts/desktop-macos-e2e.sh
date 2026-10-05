#!/usr/bin/env bash
# Full macOS desktop build + Firebase-backed smoke.
#
#   make desktop-macos          # doctor + build + automated smoke
#   make desktop-macos-ui       # same, then launch Tauri UI
#   make desktop-macos-doctor   # Firebase CLI + toolchain only
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DESKTOP="$ROOT/desktop"
MODE="${1:-smoke}" # doctor | smoke | ui

red() { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

die() { red "ERROR: $*"; exit 1; }

require_macos() {
  [[ "$(uname -s)" == "Darwin" ]] || die "This target is macOS-only (uname=$(uname -s))"
}

doctor_toolchain() {
  command -v cargo >/dev/null || die "Rust/cargo missing. Install via rustup or Homebrew."
  command -v node >/dev/null || die "Node.js missing (need 20+ for UI)."
  command -v npm >/dev/null || die "npm missing."
  command -v curl >/dev/null || die "curl missing."
  green "Toolchain OK (cargo/node/npm)"
}

doctor_firebase_cli() {
  if ! command -v firebase >/dev/null; then
    yellow "Firebase CLI missing (optional for REST smoke). Install: npm i -g firebase-tools"
    return 1
  fi
  if ! firebase projects:list >/dev/null 2>&1; then
    yellow "Firebase CLI not logged in."
    yellow "In your own terminal run:"
    yellow "  firebase login"
    yellow "  firebase use managing-screen-time"
    yellow "(Cannot complete login from non-interactive make — browser auth required.)"
    return 1
  fi
  local proj
  proj="$(cd "$ROOT" && firebase use 2>/dev/null | sed -n 's/.*Using project \([^ ]*\).*/\1/p' || true)"
  if [[ -z "$proj" ]]; then
    proj="$(python3 -c "import json;print(json.load(open('$ROOT/.firebaserc')).get('projects',{}).get('default',''))" 2>/dev/null || true)"
  fi
  if [[ "$proj" != "managing-screen-time" ]]; then
    yellow "Selecting Firebase project managing-screen-time…"
    (cd "$ROOT" && firebase use managing-screen-time) || {
      yellow "firebase use failed — continuing with REST API key smoke"
      return 1
    }
  fi
  green "Firebase CLI OK (project managing-screen-time)"
  return 0
}

# Probe Identity Toolkit with the Web API key (does not need a user session).
probe_firebase_api() {
  # shellcheck disable=SC1091
  source "$ROOT/scripts/desktop-macos-env.sh"
  local url="https://identitytoolkit.googleapis.com/v1/accounts:createAuthUri?key=${MERITSCREEN_FIREBASE_API_KEY}"
  local code
  code="$(curl -sS -o /tmp/ms-firebase-probe.json -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -d "{\"continueUri\":\"http://localhost\",\"identifier\":\"probe@example.com\"}" \
    "$url" || true)"
  if [[ "$code" != "200" ]]; then
    yellow "Identity Toolkit probe HTTP $code — check API key / project restrictions"
    cat /tmp/ms-firebase-probe.json 2>/dev/null || true
    die "Firebase API key probe failed"
  fi
  green "Firebase Identity Toolkit reachable"
}

build_all() {
  # shellcheck disable=SC1091
  source "$ROOT/scripts/desktop-macos-env.sh"
  green "Building UI assets…"
  (cd "$DESKTOP/apps/meritscreen-ui/ui" && npm ci && npm run a11y-smoke && npm run build)
  green "Building Rust workspace (guardian + agent + ui)…"
  (cd "$DESKTOP" && cargo build -p meritscreen-guardian -p meritscreen-agent -p meritscreen-ui)
  green "Running conformance suite…"
  (cd "$DESKTOP" && \
    cargo test -p meritscreen-core vectors -- --quiet && \
    cargo test -p meritscreen-guardian --test resilience -- --quiet && \
    cargo test -p meritscreen-guardian --test ipc_ping -- --quiet && \
    cargo test -p meritscreen-updater -- --quiet)
  green "Build + conformance OK"
}

smoke_guardian_firebase() {
  # shellcheck disable=SC1091
  source "$ROOT/scripts/desktop-macos-env.sh"
  local bin="$DESKTOP/target/debug"
  [[ -x "$bin/meritscreen-guardian" ]] || die "guardian binary missing — build first"

  "$bin/meritscreen-guardian" --perf-smoke

  local sock="$MERITSCREEN_DATA_DIR/e2e.sock"
  export MERITSCREEN_IPC_SOCK="$sock"
  export MERITSCREEN_IPC_PEER_RELAX=1
  rm -f "$sock"

  yellow "Starting Guardian with live Firebase env (no agent)…"
  "$bin/meritscreen-guardian" --no-agent &
  local gpid=$!
  cleanup() {
    kill "$gpid" 2>/dev/null || true
    wait "$gpid" 2>/dev/null || true
  }
  trap cleanup EXIT

  local ok=0
  for _ in $(seq 1 40); do
    if [[ -S "$sock" ]] || [[ -e "$sock" ]]; then
      ok=1
      break
    fi
    # named path may appear after bind; also check process still up
    kill -0 "$gpid" 2>/dev/null || die "Guardian exited early"
    sleep 0.15
  done
  # Give accept loop a moment
  sleep 0.5
  kill -0 "$gpid" 2>/dev/null || die "Guardian exited early after start"

  # IPC ping via existing integration style: cargo test already covers ipc_ping.
  # Here we only assert Guardian stays up under Firebase env.
  green "Guardian running under Firebase env (pid=$gpid)"
  cleanup
  trap - EXIT
  green "Guardian Firebase smoke OK"
}

launch_ui() {
  # shellcheck disable=SC1091
  source "$ROOT/scripts/desktop-macos-env.sh"
  green "Launching MeritScreen UI (Firebase parent backends when API key set)…"
  yellow "Demo OTP still works offline; live OTP needs Resend + tester allowlist."
  yellow "Child path stays local/IPC. Sync uses Guardian Firebase when API key set."
  # Ensure Vite dist uses relative asset paths, then run with custom-protocol
  # (default feature) so the WebView loads bundled ui/dist — not dead localhost:1420.
  (cd "$DESKTOP/apps/meritscreen-ui/ui" && npm run build)
  cd "$DESKTOP"
  exec cargo run -p meritscreen-ui --features custom-protocol
}

main() {
  require_macos
  case "$MODE" in
    doctor)
      doctor_toolchain
      probe_firebase_api
      if doctor_firebase_cli; then
        green "desktop-macos doctor OK (CLI + REST)"
      else
        yellow "desktop-macos doctor OK (REST API key). Log in via firebase login for CLI deploy/ops."
      fi
      ;;
    smoke)
      doctor_toolchain
      probe_firebase_api
      doctor_firebase_cli || true
      build_all
      smoke_guardian_firebase
      green "desktop-macos E2E smoke OK"
      yellow "Next: make desktop-macos-ui   # interactive parent/child UI"
      yellow "If CLI not logged in yet: firebase login && firebase use managing-screen-time"
      ;;
    ui)
      doctor_toolchain
      probe_firebase_api
      doctor_firebase_cli || true
      build_all
      smoke_guardian_firebase
      launch_ui
      ;;
    *)
      die "unknown mode: $MODE (doctor|smoke|ui)"
      ;;
  esac
}

main
