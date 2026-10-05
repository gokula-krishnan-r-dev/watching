#!/usr/bin/env bash
# Shared helpers for MeritScreen iOS simulator runners.
# shellcheck shell=bash

IOS_DIR="${IOS_DIR:-$ROOT/ios}"
PROJECT="${IOS_PROJECT:-$IOS_DIR/MeritScreen.xcodeproj}"
SCHEME="${IOS_SCHEME:-MeritScreen}"
CONFIGURATION="${IOS_CONFIGURATION:-Debug}"
DERIVED_DATA="${IOS_DERIVED_DATA:-$IOS_DIR/.build/DerivedData}"
APP_NAME="${IOS_APP_NAME:-MeritScreen.app}"
BUNDLE_ID="${IOS_BUNDLE_ID:-}"
PARENT_SIM="${IOS_PARENT_SIM:-MeritScreen_Parent}"
CHILD_SIM="${IOS_CHILD_SIM:-MeritScreen_Child}"
SIM_DEVICE_TYPE="${IOS_SIM_DEVICE_TYPE:-}"
SIM_RUNTIME="${IOS_SIM_RUNTIME:-}"
QUIET="${QUIET:-0}"

log()  { [[ "$QUIET" -eq 1 ]] || printf '==> %s\n' "$*"; }
warn() { printf 'warn: %s\n' "$*" >&2; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "Missing: $1 (install Xcode + CLT)"; }

require_ios_tools() {
  require_cmd xcrun
  require_cmd xcodebuild
  xcrun simctl help >/dev/null 2>&1 || die "simctl unavailable — open Xcode once to finish setup"
  [[ -d "$PROJECT" ]] || die "Xcode project missing at $PROJECT"
}

app_path() {
  printf '%s/Build/Products/%s-iphonesimulator/%s' "$DERIVED_DATA" "$CONFIGURATION" "$APP_NAME"
}

resolve_bundle_id() {
  if [[ -n "$BUNDLE_ID" ]]; then
    printf '%s' "$BUNDLE_ID"
    return 0
  fi
  local app
  app="$(app_path)"
  if [[ -f "$app/Info.plist" ]]; then
    /usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist" 2>/dev/null && return 0
  fi
  # Fallback matches ios/MeritScreen.xcodeproj PRODUCT_BUNDLE_IDENTIFIER
  printf 'com.meritscreentest.app'
}

pick_runtime() {
  if [[ -n "$SIM_RUNTIME" ]]; then
    printf '%s' "$SIM_RUNTIME"
    return 0
  fi
  local runtime
  runtime="$(xcrun simctl list runtimes available 2>/dev/null \
    | awk -F' - ' '/iOS/{print $NF}' \
    | tail -1 \
    | tr -d '[:space:]')"
  [[ -n "$runtime" ]] || die "No iOS Simulator runtime installed"
  printf '%s' "$runtime"
}

pick_device_type() {
  if [[ -n "$SIM_DEVICE_TYPE" ]]; then
    printf '%s' "$SIM_DEVICE_TYPE"
    return 0
  fi
  local candidates=(
    'com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro'
    'com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro'
    'com.apple.CoreSimulator.SimDeviceType.iPhone-15-Pro'
    'com.apple.CoreSimulator.SimDeviceType.iPhone-17'
    'com.apple.CoreSimulator.SimDeviceType.iPhone-16'
    'com.apple.CoreSimulator.SimDeviceType.iPhone-15'
  )
  local available
  available="$(xcrun simctl list devicetypes 2>/dev/null)"
  local c
  for c in "${candidates[@]}"; do
    if grep -Fq "($c)" <<<"$available"; then
      printf '%s' "$c"
      return 0
    fi
  done
  # Last resort: first iPhone device type
  printf '%s' "$(awk -F'[()]' '/iPhone/{print $2; exit}' <<<"$available")"
}

udid_for_name() {
  local name="$1"
  # Lines look like: "    MeritScreen_Parent (D9BF…-…) (Shutdown)"
  xcrun simctl list devices available 2>/dev/null \
    | grep -F "    ${name} (" \
    | head -1 \
    | sed -E 's/.*\(([0-9A-Fa-f-]{36})\).*/\1/'
}

ensure_simulator() {
  local name="$1"
  local udid
  udid="$(udid_for_name "$name" || true)"
  if [[ -n "$udid" ]]; then
    printf '%s' "$udid"
    return 0
  fi
  local dtype runtime
  dtype="$(pick_device_type)"
  runtime="$(pick_runtime)"
  [[ -n "$dtype" ]] || die "No iPhone simulator device type found"
  log "Creating simulator '$name' ($dtype)"
  udid="$(xcrun simctl create "$name" "$dtype" "$runtime")"
  [[ -n "$udid" ]] || die "Failed to create simulator '$name'"
  printf '%s' "$udid"
}

simulator_state() {
  local udid="$1"
  xcrun simctl list devices 2>/dev/null \
    | grep -F "$udid" \
    | sed -n 's/.*(\(Booted\|Shutdown\|Creating\|Booting\)).*/\1/p' \
    | head -1
}

boot_simulator() {
  local udid="$1" label="$2"
  local state
  state="$(simulator_state "$udid")"
  if [[ "$state" == "Booted" ]]; then
    log "$label already booted ($udid)"
  else
    log "Booting $label ($udid)"
    xcrun simctl boot "$udid" >/dev/null 2>&1 || true
    xcrun simctl bootstatus "$udid" -b >/dev/null
  fi
  # Bring Simulator.app UI forward (idempotent).
  open -a Simulator --args -CurrentDeviceUDID "$udid" >/dev/null 2>&1 || true
}

build_app() {
  local force="${1:-0}"
  local app
  app="$(app_path)"
  if [[ "$force" -eq 0 ]] && [[ -d "$app" ]]; then
    log "Reusing $app"
    return 0
  fi
  [[ -d "$PROJECT" ]] || die "Missing project: $PROJECT"
  log "Building $SCHEME ($CONFIGURATION) for iOS Simulator"
  local xb_args=(
    -project "$PROJECT"
    -scheme "$SCHEME"
    -configuration "$CONFIGURATION"
    -destination 'generic/platform=iOS Simulator'
    -derivedDataPath "$DERIVED_DATA"
    CODE_SIGNING_ALLOWED=NO
    CODE_SIGNING_REQUIRED=NO
    CODE_SIGN_IDENTITY=
    build
  )
  if [[ "$QUIET" -eq 1 ]]; then
    xcodebuild -quiet "${xb_args[@]}" >/dev/null
  else
    xcodebuild "${xb_args[@]}"
  fi
  [[ -d "$app" ]] || die "Build succeeded but app missing at $app"
}

install_and_launch() {
  local udid="$1" label="$2"
  local app bundle
  app="$(app_path)"
  bundle="$(resolve_bundle_id)"
  [[ -d "$app" ]] || die "App not built: $app"
  log "Install + launch on $label ($udid)"
  xcrun simctl terminate "$udid" "$bundle" >/dev/null 2>&1 || true
  xcrun simctl uninstall "$udid" "$bundle" >/dev/null 2>&1 || true
  xcrun simctl install "$udid" "$app"
  xcrun simctl launch "$udid" "$bundle" >/dev/null
}

shutdown_named_sims() {
  local name udid
  for name in "$PARENT_SIM" "$CHILD_SIM"; do
    udid="$(udid_for_name "$name" || true)"
    [[ -n "$udid" ]] || continue
    log "Shutting down $name ($udid)"
    xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
  done
}
