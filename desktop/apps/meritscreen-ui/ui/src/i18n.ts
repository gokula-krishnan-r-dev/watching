import en from "./i18n/en.json";
import es from "./i18n/es.json";

export type LocaleId = "en" | "es";

const catalogs: Record<LocaleId, Record<string, string>> = {
  en: en as Record<string, string>,
  es: es as Record<string, string>,
};

let active: LocaleId = "en";

/** Detect locale from navigator; fall back to English. */
export function initLocale(preferred?: string): LocaleId {
  const raw = (preferred ?? navigator.language ?? "en").toLowerCase();
  if (raw.startsWith("es")) {
    active = "es";
  } else {
    active = "en";
  }
  document.documentElement.lang = active;
  return active;
}

export function t(key: string, vars?: Record<string, string | number>): string {
  let out = catalogs[active]?.[key] ?? catalogs.en[key] ?? key;
  if (vars) {
    for (const [k, v] of Object.entries(vars)) {
      out = out.replaceAll(`{${k}}`, String(v));
    }
  }
  return out;
}

/** Keys required for a11y smoke (launcher + quiz + fail lock). */
export const A11Y_REQUIRED_KEYS = [
  "a11y_quiz_dialog",
  "a11y_quiz_choices",
  "a11y_fail_lock_dialog",
  "a11y_launcher_apps",
  "a11y_cooldown_timer",
  "child_launcher_title",
  "child_quiz_progress",
  "child_fail_lock_title",
] as const;
