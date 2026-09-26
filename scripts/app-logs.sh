#!/usr/bin/env bash
# Stream MeritScreen logcat from running emulator(s). Ctrl+C to stop.
#   ./scripts/app-logs.sh
#   SERIAL=emulator-5556 ./scripts/app-logs.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=android-env.sh
source "$ROOT/scripts/android-env.sh"

PACKAGE="com.watching.app"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }

online_serials() {
  adb devices 2>/dev/null | awk '/^emulator-.*[[:space:]]device$/{print $1}'
}

follow() {
  local serial="$1" label="$2"
  while true; do
    local pid
    pid="$(adb -s "$serial" shell pidof -s "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    if [[ -z "$pid" ]]; then
      printf '[%s] waiting for %s…\n' "$label" "$PACKAGE"
      sleep 2
      continue
    fi
    printf '[%s] following pid %s (Ctrl+C to stop)\n' "$label" "$pid"
    # Exits when the process dies (reload / crash). Loop reattaches.
    adb -s "$serial" logcat --pid="$pid" -v brief || true
    sleep 1
  done
}

serials=()
if [[ -n "${SERIAL:-}" ]]; then
  serials=("$SERIAL")
else
  while IFS= read -r s; do
    [[ -n "$s" ]] && serials+=("$s")
  done < <(online_serials)
fi

[[ ${#serials[@]} -gt 0 ]] || die "no emulator running — start with: make dev"

if [[ ${#serials[@]} -eq 1 ]]; then
  follow "${serials[0]}" "${serials[0]}"
else
  pids=()
  for s in "${serials[@]}"; do
    follow "$s" "$s" &
    pids+=("$!")
  done
  trap 'kill "${pids[@]}" 2>/dev/null || true' INT TERM EXIT
  wait
fi
