#!/usr/bin/env bash
# Fast incremental rebuild + reinstall when Kotlin sources change.
# Does NOT restart the emulator — only assembleDebug + adb install + relaunch.
#
# Usage:
#   ./scripts/watch-reload.sh
#   make watch
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=android-env.sh
source "$ROOT/scripts/android-env.sh"

PACKAGE="com.watching.app"
ACTIVITY="${PACKAGE}/com.meritscreen.app.MainActivity"
APK_PATH="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
POLL_SEC="${WATCH_POLL_SEC:-1}"
DEBOUNCE_SEC="${WATCH_DEBOUNCE_SEC:-1}"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }

online_serials() {
  adb devices 2>/dev/null | awk '/^emulator-.*[[:space:]]device$/{print $1}'
}

# Fingerprint of all app Kotlin/XML sources (cheap poll; no brew deps).
sources_stamp() {
  find app/src features core -type f \( -name '*.kt' -o -name '*.xml' -o -name '*.kts' \) \
    -print0 2>/dev/null \
    | xargs -0 stat -f '%m %N' 2>/dev/null \
    | sort \
    | md5 -q
}

push_apk() {
  local serials=()
  local s
  while IFS= read -r s; do
    [[ -n "$s" ]] && serials+=("$s")
  done < <(online_serials)

  [[ ${#serials[@]} -gt 0 ]] || die "no emulator online — run: make dev"

  printf '→ building (%s device(s))…\n' "${#serials[@]}"
  local t0=$SECONDS
  if ! ./gradlew :app:assembleDebug --quiet; then
    printf '✗ build failed — fix errors, watcher still running\n' >&2
    return 1
  fi
  [[ -f "$APK_PATH" ]] || { printf '✗ APK missing after build\n' >&2; return 1; }

  for s in "${serials[@]}"; do
    if ! adb -s "$s" install -r -t "$APK_PATH" >/dev/null; then
      printf '✗ install failed on %s\n' "$s" >&2
      return 1
    fi
    adb -s "$s" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
    adb -s "$s" shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
  done
  printf '✓ live in %ss\n' "$((SECONDS - t0))"
}

# Portable check (macOS ships Bash 3.2 — no mapfile).
_has_device=0
while IFS= read -r _s; do
  [[ -n "$_s" ]] && _has_device=1 && break
done < <(online_serials)
[[ "$_has_device" -eq 1 ]] || die "no emulator online — run: make dev  (then make watch)"

printf 'Watching Kotlin/XML — save a file to push to the emulator.\n'
printf 'Ctrl+C to stop. Emulator stays running.\n\n'

# First push so the session starts from current sources.
push_apk || true

last="$(sources_stamp || echo none)"
pending=0
pending_since=0

while true; do
  sleep "$POLL_SEC"
  now="$(sources_stamp || echo none)"
  if [[ "$now" != "$last" ]]; then
    last="$now"
    pending=1
    pending_since=$SECONDS
    printf '· change detected…\n'
  fi
  if [[ "$pending" -eq 1 && $((SECONDS - pending_since)) -ge "$DEBOUNCE_SEC" ]]; then
    # Re-check stamp after debounce (batch rapid saves).
    last="$(sources_stamp || echo none)"
    pending=0
    push_apk || true
  fi
done
