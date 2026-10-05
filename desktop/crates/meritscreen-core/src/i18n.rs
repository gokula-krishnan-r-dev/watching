//! Desktop string catalog keys (d10). Prefer keys aligned with Android `strings.xml`.
//!
//! UI loads JSON locales; Rust call sites use `t()` for child/parent facing copy
//! that must not hard-code English outside this module.

use std::collections::HashMap;
use std::sync::OnceLock;

/// Embedded English catalog (fallback).
pub const EN_JSON: &str = include_str!("../locales/en.json");

static EN: OnceLock<HashMap<String, String>> = OnceLock::new();

fn en_map() -> &'static HashMap<String, String> {
    EN.get_or_init(|| {
        serde_json::from_str(EN_JSON).unwrap_or_else(|_| HashMap::new())
    })
}

/// Look up a localized string. Missing keys return the key (fail soft for labs).
pub fn t(key: &str) -> String {
    en_map()
        .get(key)
        .cloned()
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
}
