/** Exact backend message (meritscreen_parent::session::SESSION_EXPIRED): sent only after a token refresh truly failed. */
export const SESSION_EXPIRED_MSG = "Your session expired. Please sign in again.";

/** True when a backend error means the parent must sign in again (refresh already retried server-side). */
export function isSessionExpired(err: unknown): boolean {
  const raw = err instanceof Error ? err.message : typeof err === "string" ? err : String(err ?? "");
  return raw.includes(SESSION_EXPIRED_MSG);
}

/** Turn Tauri/IPC/Firebase error strings into short, user-facing copy. */
export function friendlyError(err: unknown): string {
  let raw = err instanceof Error ? err.message : typeof err === "string" ? err : String(err ?? "");
  const ipc = raw.match(/command [`']?\w+[`']?: (.+)$/i);
  if (ipc?.[1]) raw = ipc[1];
  raw = raw.replace(/^error:\s*/i, "").trim();
  if (/unauthenticated|session expired/i.test(raw)) {
    return SESSION_EXPIRED_MSG;
  }
  if (/network|failed to fetch|offline|timed? ?out/i.test(raw)) {
    return "Can’t reach the server. Check your internet connection and try again.";
  }
  if (!raw || raw === "undefined" || raw === "null" || raw === "[object Object]") {
    return "Something went wrong. Please try again.";
  }
  return raw;
}

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export function validateEmail(email: string): string | null {
  const v = email.trim();
  if (!v) return "Enter your email address.";
  if (v.length > 254 || !EMAIL_RE.test(v)) return "Enter a valid email address, like name@example.com.";
  return null;
}
