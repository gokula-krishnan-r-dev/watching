# Desktop localization (d10)

## Pipeline

| Layer | Location | Notes |
| --- | --- | --- |
| UI (Tauri WebView) | `apps/meritscreen-ui/ui/src/i18n/<locale>.json` + `i18n.ts` | `t(key, vars)`; `initLocale()` from `navigator.language` |
| Rust catalog | `crates/meritscreen-core/locales/en.json` + `i18n::t` | Child/parent facing strings that must not hard-code English |
| Android parity | Prefer the same key names as Android `strings.xml` where practical | e.g. `child_fail_lock_title` |

## Add a locale

1. Copy `en.json` → `es.json` (UI) and `meritscreen-core/locales/es.json` (Rust).
2. Register in `i18n.ts` `catalogs` / Rust loader.
3. Keep keys stable; never interpolate secrets into strings.

## Smoke

```bash
cd desktop/apps/meritscreen-ui/ui && npm run a11y-smoke
cargo test -p meritscreen-core i18n
```
