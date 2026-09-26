#!/usr/bin/env bash
# Shared Android SDK bootstrap for MeritScreen demo scripts / Make.
# Source only:  source "$(dirname "$0")/android-env.sh"
#
# Resolution order:
#   1. ANDROID_HOME / ANDROID_SDK_ROOT if they contain platform-tools/adb
#   2. sdk.dir from local.properties
#   3. ~/Library/Android/sdk
#   4. /opt/homebrew/share/android-commandlinetools

_ms_sdk_has_adb() {
  [[ -n "${1:-}" && -x "$1/platform-tools/adb" ]]
}

_ms_sdk_from_local_props() {
  local root props
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  props="$root/local.properties"
  [[ -f "$props" ]] || return 0
  grep -E '^sdk\.dir=' "$props" 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r' || true
}

_ms_resolve_sdk() {
  local candidate
  for candidate in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "$(_ms_sdk_from_local_props)" \
    "${HOME}/Library/Android/sdk" \
    "/opt/homebrew/share/android-commandlinetools"
  do
    if _ms_sdk_has_adb "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

_ms_sdk="$(_ms_resolve_sdk)" || {
  printf 'error: Android SDK not found (need platform-tools/adb). Set ANDROID_HOME or sdk.dir in local.properties.\n' >&2
  return 1 2>/dev/null || exit 1
}

export ANDROID_HOME="$_ms_sdk"
export ANDROID_SDK_ROOT="$_ms_sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
unset _ms_sdk
