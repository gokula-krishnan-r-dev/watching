//! Desktop string catalog keys (d10). Prefer keys aligned with Android `strings.xml`.
//!
//! UI loads JSON locales; Rust call sites use `t()` for child/parent facing copy
//! that must not hard-code English outside this module.

use std::collections::HashMap;
use std::sync::OnceLock;

/// Embedded English catalog (fallback).
pub const EN_JSON: &str = include_str!("../locales/en.json");
/// Embedded Spanish catalog.
pub const ES_JSON: &str = include_str!("../locales/es.json");

static EN: OnceLock<HashMap<String, String>> = OnceLock::new();
static ES: OnceLock<HashMap<String, String>> = OnceLock::new();

fn en_map() -> &'static HashMap<String, String> {
    EN.get_or_init(|| serde_json::from_str(EN_JSON).unwrap_or_else(|_| HashMap::new()))
}

fn es_map() -> &'static HashMap<String, String> {
    ES.get_or_init(|| serde_json::from_str(ES_JSON).unwrap_or_else(|_| HashMap::new()))
}

/// Look up a localized string in the default (English) catalog.
pub fn t(key: &str) -> String {
    t_locale("en", key)
}

/// Look up a localized string in a specific locale catalog, falling back to English.
pub fn t_locale(locale: &str, key: &str) -> String {
    let map = if locale.starts_with("es") {
        es_map()
    } else {
        en_map()
    };
    map.get(key)
        .cloned()
        .or_else(|| en_map().get(key).cloned())
        .unwrap_or_else(|| key.to_string())
}

pub fn t_args(key: &str, args: &[(&str, &str)]) -> String {
    let mut out = t(key);
    for (k, v) in args {
        out = out.replace(&format!("{{{k}}}"), v);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catalog_has_child_hot_path_keys() {
        for key in [
            "child_launcher_title",
            "child_quiz_progress",
            "child_fail_lock_title",
            "child_fail_lock_body",
            "a11y_quiz_choices",
            "a11y_fail_lock_dialog",
        ] {
            let v = t(key);
            assert_ne!(v, key, "missing locale key {key}");
        }
    }

    #[test]
    fn spanish_catalog_translates_and_falls_back() {
        let es = t_locale("es", "child_fail_lock_title");
        assert_eq!(es, "Tómate un descanso");

        let fallback = t_locale("es", "nonexistent_key_123");
        assert_eq!(fallback, "nonexistent_key_123");
    }
}
