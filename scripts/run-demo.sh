#!/usr/bin/env bash
# MeritScreen device/emulator demo runner.
#
# Default: production Firebase (managing-screen-time cloud).
# Optional: local Emulator Suite with --emulators (-PuseFirebaseEmulators).
#
# Gradle: seeds the wrapper distribution from Homebrew when
# services.gradle.org times out (common on flaky networks). Dependency/plugin
# resolution still uses the network; only the Gradle zip is seeded locally.
#
# Cloud debug App Check: ensures a stable debug token in local.properties,
# registers it with Firebase when possible, and pins it on the device via adb.
#
# Usage:
#   ./scripts/run-demo.sh                  # cloud backend + install + launch
#   ./scripts/run-demo.sh --emulators      # Auth/Firestore/Functions emulators
#   ./scripts/run-demo.sh --reuse-apk      # skip Gradle if APK matches mode
#   ./scripts/run-demo.sh --no-avd         # assume a device/emulator is already up
#   ./scripts/run-demo.sh --rebuild        # force :app:installDebug
#   ./scripts/run-demo.sh --serial emulator-5554   # target (auto-starts if offline)
#   ./scripts/run-demo.sh --quiet          # minimal stdout (Make default)
#   make dev / make up-dev                 # preferred local entrypoints
#   ANDROID_SERIAL=emulator-5554 ./scripts/run-demo.sh --no-avd --reuse-apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=android-env.sh
source "$ROOT/scripts/android-env.sh"
# shellcheck source=app-check-debug.sh
source "$ROOT/scripts/app-check-debug.sh"

AVD_NAME="${AVD_NAME:-MeritScreen_API34_Lite}"
AVD_PORT="${AVD_PORT:-5554}"
APK_PATH="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
BUILD_CONFIG="$ROOT/app/build/generated/source/buildConfig/debug/com/meritscreen/app/BuildConfig.java"
PACKAGE="com.watching.app"
ACTIVITY="${PACKAGE}/com.meritscreen.app.MainActivity"
FIREBASE_PROJECT="${FIREBASE_PROJECT:-managing-screen-time}"
WRAPPER_PROPS="$ROOT/gradle/wrapper/gradle-wrapper.properties"
ANDROID_SERIAL="${ANDROID_SERIAL:-}"

MODE="cloud"          # cloud | emulators
REUSE_APK=0
START_AVD=1
FORCE_REBUILD=0
QUIET="${QUIET:-0}"
FIREBASE_PID=""

log()  { [[ "$QUIET" -eq 1 ]] || printf '==> %s\n' "$*"; }
warn() { printf 'warn: %s\n' "$*" >&2; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }

usage() {
  # Print leading comment block (stops before set -euo).
  awk 'NR==1{next} /^[^#]/{exit} {sub(/^# ?/,""); print}' "$0"
  exit 0
}

cleanup() {
  if [[ -n "${FIREBASE_PID}" ]] && kill -0 "$FIREBASE_PID" 2>/dev/null; then
    log "Stopping Firebase emulators (pid $FIREBASE_PID)"
    kill "$FIREBASE_PID" 2>/dev/null || true
    wait "$FIREBASE_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

while [[ $# -gt 0 ]]; do
  case "$1" in
    --emulators) MODE="emulators" ;;
    --cloud) MODE="cloud" ;;
    --reuse-apk) REUSE_APK=1 ;;
    --rebuild) FORCE_REBUILD=1; REUSE_APK=0 ;;
    --no-avd) START_AVD=0 ;;
    --quiet|-q) QUIET=1 ;;
    --serial)
      shift
      [[ $# -gt 0 ]] || die "--serial requires emulator-5554 / emulator-5556 / …"
      ANDROID_SERIAL="$1"
      export ANDROID_SERIAL
      ;;
    -h|--help) usage ;;
    *) die "Unknown flag: $1 (try --help)" ;;
  esac
  shift
done

# Serial pins the target device. If that emulator is offline, start it on its port.
if [[ -n "$ANDROID_SERIAL" ]]; then
  export ANDROID_SERIAL
  if [[ "$ANDROID_SERIAL" =~ ^emulator-([0-9]+)$ ]]; then
    AVD_PORT="${BASH_REMATCH[1]}"
  fi
  log "Targeting ANDROID_SERIAL=$ANDROID_SERIAL"
fi

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

device_online() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb -s "$ANDROID_SERIAL" get-state 2>/dev/null | grep -qx device
  else
    adb devices 2>/dev/null | awk 'NR>1 && $2=="device" { found=1 } END { exit !found }'
  fi
}

gradle_version_from_wrapper() {
  local url
  url="$(grep -E '^distributionUrl=' "$WRAPPER_PROPS" | head -1 | cut -d= -f2- | tr -d '\\')"
  # .../gradle-9.6.1-bin.zip → 9.6.1
  basename "$url" | sed -E 's/^gradle-([0-9.]+)-bin\.zip$/\1/'
}

# Cloud-only: register + pin App Check debug token (shared helper).
ensure_cloud_app_check_debug_token() {
  [[ "$MODE" == "cloud" ]] || return 0
  ensure_app_check_debug_token
  if device_online; then
    pin_app_check_debug_token "${ANDROID_SERIAL:-}"
    log "App Check debug token pinned for cloud demo"
  fi
}

# ---------------------------------------------------------------------------
# Gradle wrapper: seed from Homebrew when services.gradle.org times out
# ---------------------------------------------------------------------------
seed_gradle_wrapper_if_needed() {
  local ver
  ver="$(gradle_version_from_wrapper)"
  [[ -n "$ver" ]] || die "Could not parse Gradle version from $WRAPPER_PROPS"

  local dists="$HOME/.gradle/wrapper/dists/gradle-${ver}-bin"
  local ok_marker
  ok_marker="$(find "$dists" -name "gradle-${ver}-bin.zip.ok" 2>/dev/null | head -1 || true)"
  if [[ -n "$ok_marker" ]] && [[ -d "$(dirname "$ok_marker")/gradle-${ver}" ]]; then
    return 0
  fi

  local brew_home=""
  if [[ -d "/opt/homebrew/Cellar/gradle/${ver}/libexec" ]]; then
    brew_home="/opt/homebrew/Cellar/gradle/${ver}/libexec"
  elif command -v brew >/dev/null 2>&1; then
    local prefix
    prefix="$(brew --prefix "gradle@${ver}" 2>/dev/null || brew --prefix gradle 2>/dev/null || true)"
    if [[ -n "$prefix" && -d "${prefix}/libexec" ]]; then
      brew_home="${prefix}/libexec"
    fi
  fi
  [[ -d "$brew_home" ]] || die \
    "Gradle ${ver} wrapper cache missing and Homebrew gradle ${ver} not found. Install: brew install gradle@${ver} || brew install gradle"

  # Confirm Homebrew version matches wrapper (avoid silent mismatch).
  if [[ ! -f "$brew_home/bin/gradle" ]] && [[ ! -f "$brew_home/bin/gradle.bat" ]]; then
    # libexec layout still valid without top-level bin in some kegs
    [[ -d "$brew_home/lib" ]] || die "Homebrew gradle libexec looks incomplete: $brew_home"
  fi

  log "Seeding Gradle ${ver} wrapper cache from Homebrew (avoids services.gradle.org timeouts)"
  local hash_dir
  hash_dir="$(find "$dists" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1 || true)"
  if [[ -z "$hash_dir" ]]; then
    # Stable hash directory used by Gradle Wrapper for gradle-9.6.1-bin.zip.
    # If the distributionUrl changes, the wrapper creates a new hash on first run;
    # we fall back to creating under a known path so the next ./gradlew finds it.
    hash_dir="$dists/4ticwg1pgcbps2hj28r8so764"
  fi
  mkdir -p "$hash_dir"
  rm -f "$hash_dir"/gradle-*-bin.zip "$hash_dir"/gradle-*-bin.zip.part "$hash_dir"/gradle-*-bin.zip.lck
  rm -rf "$hash_dir/gradle-${ver}"

  local tmp
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/gradle-${ver}"
  cp -a "$brew_home/." "$tmp/gradle-${ver}/"
  (cd "$tmp" && zip -qr "$hash_dir/gradle-${ver}-bin.zip" "gradle-${ver}")
  unzip -q "$hash_dir/gradle-${ver}-bin.zip" -d "$hash_dir"
  touch "$hash_dir/gradle-${ver}-bin.zip.ok"
  rm -rf "$tmp"
  log "Gradle ${ver} distribution ready at $hash_dir"
}

apk_matches_mode() {
  [[ -f "$BUILD_CONFIG" ]] || return 1
  local flag
  flag="$(grep -E 'USE_FIREBASE_EMULATORS\s*=' "$BUILD_CONFIG" | head -1 || true)"
  if [[ "$MODE" == "emulators" ]]; then
    [[ "$flag" == *"= true;"* ]]
  else
    [[ "$flag" == *"= false;"* ]]
  fi
}

wait_for_boot() {
  local serial="${1:-${ANDROID_SERIAL:-}}"
  if [[ -n "$serial" ]]; then
    adb -s "$serial" wait-for-device
  else
    adb wait-for-device
  fi
  local i=0 prop
  while true; do
    if [[ -n "$serial" ]]; then
      prop="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    else
      prop="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    fi
    [[ "$prop" == "1" ]] && break
    i=$((i + 1))
    [[ $i -lt 90 ]] || die "AVD boot timed out${serial:+ ($serial)}"
    sleep 2
  done
}

start_avd() {
  local name="$1" port="$2"
  command -v emulator >/dev/null 2>&1 || die "Android emulator binary not on PATH (ANDROID_HOME=$ANDROID_HOME)"
  emulator -list-avds 2>/dev/null | grep -qx "$name" || die "AVD '$name' not found. Create it or set AVD_NAME=..."

  log "Starting AVD ${name} on :${port} (lite: 768MB, quiet)"
  # Lean flags for ~8GB Macs; silence emulator console noise.
  nohup emulator -avd "$name" \
    -port "$port" \
    -no-boot-anim -no-audio -no-snapshot-save \
    -gpu swiftshader_indirect -memory 768 -cores 2 \
    -logcat '*:S' \
    >/dev/null 2>&1 &
  export ANDROID_SERIAL="emulator-${port}"
  wait_for_boot "$ANDROID_SERIAL"
  log "AVD booted ($ANDROID_SERIAL)"
}

ensure_device() {
  if device_online; then
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
      log "Using $ANDROID_SERIAL"
    else
      log "Using connected Android device/emulator"
    fi
    return 0
  fi
  [[ "$START_AVD" -eq 1 ]] || die "No Android device/emulator connected (omit --no-avd, or start an AVD). Tip: make dev"

  start_avd "$AVD_NAME" "$AVD_PORT"
}

start_firebase_emulators() {
  require_cmd firebase
  log "Starting Firebase Emulator Suite (auth, firestore, functions)"
  firebase emulators:start --only auth,firestore,functions --project "$FIREBASE_PROJECT" \
    >/tmp/meritscreen-firebase-emu.log 2>&1 &
  FIREBASE_PID=$!
  local i=0
  until grep -q "All emulators ready" /tmp/meritscreen-firebase-emu.log 2>/dev/null; do
    i=$((i + 1))
    if ! kill -0 "$FIREBASE_PID" 2>/dev/null; then
      tail -40 /tmp/meritscreen-firebase-emu.log >&2 || true
      die "Firebase emulators exited early"
    fi
    [[ $i -lt 90 ]] || die "Firebase emulators failed to become ready (see /tmp/meritscreen-firebase-emu.log)"
    sleep 2
  done
  log "Emulators ready — UI http://127.0.0.1:4000/"
}

install_app() {
  local gradle_args=(:app:installDebug)
  if [[ "$MODE" == "emulators" ]]; then
    gradle_args+=(-PuseFirebaseEmulators=true)
    log "Building/installing debug APK → Firebase emulators (10.0.2.2)"
  else
    log "Building/installing debug APK → production Firebase (${FIREBASE_PROJECT})"
  fi

  if [[ "$REUSE_APK" -eq 1 && "$FORCE_REBUILD" -eq 0 && -f "$APK_PATH" ]]; then
    if apk_matches_mode; then
      log "Reusing existing APK (mode=${MODE}): $APK_PATH"
      if [[ "$QUIET" -eq 1 ]]; then
        adb install -r -t "$APK_PATH" >/dev/null
      else
        adb install -r -t "$APK_PATH"
      fi
      return 0
    fi
    warn "Existing APK does not match mode=${MODE}; rebuilding"
  fi

  # Ensure the Gradle *distribution* is local so the wrapper never hangs on
  # services.gradle.org. Dependency/plugin downloads still use the network.
  seed_gradle_wrapper_if_needed

  local gradle_q=()
  [[ "$QUIET" -eq 1 ]] && gradle_q=(--quiet)

  log "Running Gradle (distribution cached; dependencies may download)"
  if ! ./gradlew "${gradle_q[@]}" "${gradle_args[@]}"; then
    warn "Gradle installDebug encountered an issue. Attempting fallback assembleDebug and direct adb install..."
    local assemble_args=(:app:assembleDebug)
    if [[ "$MODE" == "emulators" ]]; then
      assemble_args+=(-PuseFirebaseEmulators=true)
    fi
    if ./gradlew "${gradle_q[@]}" "${assemble_args[@]}" && [[ -f "$APK_PATH" ]]; then
      log "Installing built APK directly via adb: $APK_PATH"
      if [[ "$QUIET" -eq 1 ]]; then
        adb install -r -t "$APK_PATH" >/dev/null
      else
        adb install -r -t "$APK_PATH"
      fi
    else
      die "Gradle build/install failed. Check network access to Google Maven / plugins.gradle.org"
    fi
  fi

  if ! apk_matches_mode; then
    warn "BuildConfig USE_FIREBASE_EMULATORS may not match mode=${MODE}; inspect $BUILD_CONFIG"
  fi
}

launch_app() {
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  adb shell am start -n "$ACTIVITY" >/dev/null
  log "Launched ${ACTIVITY}"
  if [[ "$MODE" == "cloud" ]]; then
    log "Sign up / sign in against production Auth (email/password)."
  else
    log "Create an account in-app; Auth emulator prints verify links in the Firebase log."
  fi
}

# ---------------------------------------------------------------------------
main() {
  require_cmd adb
  [[ -x "$ROOT/gradlew" ]] || die "gradlew missing"
  [[ -f "$WRAPPER_PROPS" ]] || die "Missing $WRAPPER_PROPS"

  if [[ "$MODE" == "emulators" ]]; then
    start_firebase_emulators
  else
    log "Using production Firebase project: ${FIREBASE_PROJECT}"
  fi

  ensure_device
  ensure_cloud_app_check_debug_token
  install_app
  launch_app
}

main
