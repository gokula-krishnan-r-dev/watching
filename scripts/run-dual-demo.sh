#!/usr/bin/env bash
# Start (or reuse) two AVDs and install/launch MeritScreen on both for
# parent ↔ child sync testing.
#
# Defaults:
#   PARENT  → MeritScreen_API34_Lite  → emulator-5554 (host GPU, 512MB)
#   CHILD   → MeritScreen_API34_Child → emulator-5556 (swiftshader, 512MB)
#
# Lean defaults suit ~8GB Macs. Dual API-34 emulators are still memory-heavy;
# if either exits under pressure, use one emulator + a USB phone for the other role:
#   adb devices
#   ANDROID_SERIAL=<phone> ./scripts/run-demo.sh --no-avd --reuse-apk
#
# Usage:
#   ./scripts/run-dual-demo.sh
#   ./scripts/run-dual-demo.sh --reuse-apk
#   ./scripts/run-dual-demo.sh --rebuild
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=android-env.sh
source "$ROOT/scripts/android-env.sh"

PARENT_AVD="${PARENT_AVD:-MeritScreen_API34_Lite}"
CHILD_AVD="${CHILD_AVD:-MeritScreen_API34_Child}"
PARENT_PORT="${PARENT_PORT:-5554}"
CHILD_PORT="${CHILD_PORT:-5556}"
PARENT_SERIAL="emulator-${PARENT_PORT}"
CHILD_SERIAL="emulator-${CHILD_PORT}"
PACKAGE="com.watching.app"
ACTIVITY="${PACKAGE}/com.meritscreen.app.MainActivity"
APK_PATH="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
LOCAL_PROPS="$ROOT/local.properties"
REUSE_APK=0
FORCE_REBUILD=0
QUIET="${QUIET:-0}"

log()  { [[ "$QUIET" -eq 1 ]] || printf '==> %s\n' "$*"; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reuse-apk) REUSE_APK=1 ;;
    --rebuild) FORCE_REBUILD=1; REUSE_APK=0 ;;
    --quiet|-q) QUIET=1 ;;
    -h|--help)
      awk 'NR==1{next} /^[^#]/{exit} {sub(/^# ?/,""); print}' "$0"
      exit 0
      ;;
    *) die "Unknown flag: $1" ;;
  esac
  shift
done

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "Missing: $1"; }
require_cmd adb
require_cmd emulator

serial_online() {
  adb -s "$1" get-state 2>/dev/null | grep -qx device
}

wait_boot() {
  local serial="$1"
  adb -s "$serial" wait-for-device
  local i=0
  until [[ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    i=$((i + 1))
    [[ $i -lt 90 ]] || die "$serial boot timed out"
    sleep 2
  done
}

ensure_avd_exists() {
  local name="$1"
  emulator -list-avds 2>/dev/null | grep -qx "$name" || die "AVD '$name' not found. Create it first."
}

# Args: name port serial gpu memory cores
start_avd_if_needed() {
  local name="$1" port="$2" serial="$3" gpu="$4" memory="$5" cores="$6"
  if serial_online "$serial"; then
    log "$serial already online ($name)"
    return 0
  fi
  ensure_avd_exists "$name"
  log "Starting $name → $serial (gpu=$gpu mem=${memory}M)"
  nohup emulator -avd "$name" \
    -port "$port" \
    -no-boot-anim -no-audio -no-snapshot-save \
    -gpu "$gpu" -memory "$memory" -cores "$cores" \
    -logcat '*:S' \
    >/dev/null 2>&1 &
  wait_boot "$serial"
  log "$serial booted"
}

app_check_token() {
  [[ -f "$LOCAL_PROPS" ]] || return 0
  grep -E '^appCheckDebugToken=' "$LOCAL_PROPS" 2>/dev/null | head -1 | cut -d= -f2- || true
}

pin_and_launch() {
  local serial="$1" label="$2"
  local token
  token="$(app_check_token)"
  log "Install + launch on $serial ($label)"
  if [[ -n "$token" ]]; then
    adb -s "$serial" shell setprop debug.firebase.appcheck.app_check_token "$token" >/dev/null 2>&1 || true
  fi
  adb -s "$serial" install -r -t "$APK_PATH" >/dev/null
  adb -s "$serial" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  adb -s "$serial" shell am start -n "$ACTIVITY" >/dev/null
}

build_apk_if_needed() {
  local gradle_q=()
  [[ "$QUIET" -eq 1 ]] && gradle_q=(--quiet)
  if [[ "$FORCE_REBUILD" -eq 1 ]] || [[ "$REUSE_APK" -eq 0 ]] || [[ ! -f "$APK_PATH" ]]; then
    log "Building debug APK (production Firebase)"
    ./gradlew "${gradle_q[@]}" :app:assembleDebug
  else
    log "Reusing $APK_PATH"
  fi
  [[ -f "$APK_PATH" ]] || die "APK missing at $APK_PATH"
}

# ---------------------------------------------------------------------------
ensure_avd_exists "$PARENT_AVD"
ensure_avd_exists "$CHILD_AVD"

# Lean dual-boot for ~8GB Macs (host GPU on parent when available).
start_avd_if_needed "$PARENT_AVD" "$PARENT_PORT" "$PARENT_SERIAL" host 512 1
start_avd_if_needed "$CHILD_AVD" "$CHILD_PORT" "$CHILD_SERIAL" swiftshader_indirect 512 1

build_apk_if_needed
pin_and_launch "$PARENT_SERIAL" "parent"
pin_and_launch "$CHILD_SERIAL" "child"

if [[ "$QUIET" -eq 0 ]]; then
  cat <<EOF

Ready.

  Parent  $PARENT_SERIAL  ($PARENT_AVD)
  Child   $CHILD_SERIAL  ($CHILD_AVD)

  export ANDROID_SERIAL=$PARENT_SERIAL
  export ANDROID_SERIAL=$CHILD_SERIAL
EOF
fi
