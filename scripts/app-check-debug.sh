#!/usr/bin/env bash
# Shared App Check debug-token helpers for MeritScreen cloud demos.
# Source after android-env.sh. Expects ROOT to be set.
#
# Writes a stable UUID to gitignored local.properties, registers it with the
# Firebase App Check API when the Firebase CLI session is available, and can
# pin it on a device via adb before process start.
# shellcheck shell=bash

FIREBASE_PROJECT="${FIREBASE_PROJECT:-managing-screen-time}"
FIREBASE_ANDROID_APP_ID="${FIREBASE_ANDROID_APP_ID:-1:54296812917:android:9200830f4dbaed67a4473c}"
LOCAL_PROPS="${LOCAL_PROPS:-$ROOT/local.properties}"
APP_CHECK_TOKEN_PROP="${APP_CHECK_TOKEN_PROP:-debug.firebase.appcheck.app_check_token}"
QUIET="${QUIET:-0}"

_ms_ac_log()  { [[ "${QUIET}" -eq 1 ]] || printf '==> %s\n' "$*"; }
_ms_ac_warn() { printf 'warn: %s\n' "$*" >&2; }

_ms_ac_read_local_prop() {
  local key="$1"
  [[ -f "$LOCAL_PROPS" ]] || return 0
  grep -E "^${key}=" "$LOCAL_PROPS" 2>/dev/null | head -1 | cut -d= -f2- || true
}

_ms_ac_write_local_prop() {
  local key="$1" value="$2"
  touch "$LOCAL_PROPS"
  if grep -qE "^${key}=" "$LOCAL_PROPS" 2>/dev/null; then
    local tmp
    tmp="$(mktemp)"
    awk -v k="$key" -v v="$value" 'BEGIN{FS=OFS="="} $1==k{$0=k"="v} {print}' "$LOCAL_PROPS" >"$tmp"
    mv "$tmp" "$LOCAL_PROPS"
  else
    printf '%s=%s\n' "$key" "$value" >>"$LOCAL_PROPS"
  fi
}

_ms_ac_firebase_access_token() {
  python3 - <<'PY' 2>/dev/null || true
import json, os
path=os.path.expanduser("~/.config/configstore/firebase-tools.json")
try:
  with open(path) as f: print(json.load(f).get("tokens",{}).get("access_token",""))
except Exception:
  pass
PY
}

# Ensure local.properties has a token and register it with Firebase (best-effort).
# Prints the token on stdout when called as: ensure_app_check_debug_token --print
ensure_app_check_debug_token() {
  local print_token=0
  [[ "${1:-}" == "--print" ]] && print_token=1

  local token
  token="$(_ms_ac_read_local_prop appCheckDebugToken)"
  if [[ -z "$token" ]]; then
    if command -v uuidgen >/dev/null 2>&1; then
      token="$(uuidgen | tr '[:upper:]' '[:lower:]')"
    else
      token="$(python3 - <<'PY'
import uuid; print(uuid.uuid4())
PY
)"
    fi
    _ms_ac_write_local_prop appCheckDebugToken "$token"
    _ms_ac_log "Wrote appCheckDebugToken to local.properties (gitignored)"
  fi

  local access
  access="$(_ms_ac_firebase_access_token)"
  if [[ -n "$access" ]]; then
    local reg_out
    reg_out="$(python3 - "$access" "$FIREBASE_PROJECT" "$FIREBASE_ANDROID_APP_ID" "$token" <<'PY' 2>/dev/null || true
import json, sys, urllib.request, urllib.error

access, project, app_id, token = sys.argv[1:5]
base = f"https://firebaseappcheck.googleapis.com/v1beta/projects/{project}/apps/{app_id}/debugTokens"

def call(method, url, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Authorization": f"Bearer {access}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode()
            return r.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")

def register():
    return call("POST", base, {"displayName": "MeritScreen run-demo", "token": token})

status, body = register()
if status in (200, 201):
    print("registered", status)
    raise SystemExit(0)
text = body if isinstance(body, str) else json.dumps(body)
if status in (409, 400) and ("ALREADY_EXISTS" in text or "already" in text.lower()):
    print("already_registered")
    raise SystemExit(0)
if status == 401:
    print("Note: Firebase CLI session expired. App Check debug token will be pinned locally via adb (run 'firebase login' to re-authenticate CLI).")
    raise SystemExit(0)

if status == 400 and ("Maximum number of debug tokens" in text or "FAILED_PRECONDITION" in text):
    _, listing = call("GET", base)
    tokens = listing.get("debugTokens", []) if isinstance(listing, dict) else []
    ordered = sorted(tokens, key=lambda t: t.get("updateTime", ""), reverse=True)
    for entry in ordered[4:]:
        name = entry.get("name")
        if not name:
            continue
        call("DELETE", f"https://firebaseappcheck.googleapis.com/v1beta/{name}")
    status, body = register()
    if status in (200, 201):
        print("registered_after_prune", status)
        raise SystemExit(0)
    text = body if isinstance(body, str) else json.dumps(body)
    print(f"register_failed {status}: {text[:200]}")
    raise SystemExit(0)

print(f"register_failed {status}: {text[:200]}")
PY
)"
    if [[ "${QUIET}" -eq 0 && -n "$reg_out" ]]; then
      printf '%s\n' "$reg_out"
    fi
  else
    _ms_ac_warn "Firebase CLI token unavailable; ensure App Check debug token is registered in the console"
  fi

  if [[ "$print_token" -eq 1 ]]; then
    printf '%s\n' "$token"
  fi
}

# Pin token on one serial (or default adb target) before app process start.
pin_app_check_debug_token() {
  local serial="${1:-}"
  local token
  token="$(_ms_ac_read_local_prop appCheckDebugToken)"
  [[ -n "$token" ]] || return 0
  if [[ -n "$serial" ]]; then
    adb -s "$serial" shell setprop "$APP_CHECK_TOKEN_PROP" "$token" >/dev/null 2>&1 || \
      _ms_ac_warn "Could not set ${APP_CHECK_TOKEN_PROP} on $serial"
  else
    adb shell setprop "$APP_CHECK_TOKEN_PROP" "$token" >/dev/null 2>&1 || \
      _ms_ac_warn "Could not set ${APP_CHECK_TOKEN_PROP} via adb"
  fi
}
