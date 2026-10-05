# Spike note — WebView quiz overlay focus (d6)

## Risk

Tauri WebView may lose topmost / focus races on Windows (other apps) and macOS (Spaces / fullscreen games). Child quiz and fail-lock UI in d6 are **in-process overlays** driven by local `ChildRuntime` + Tauri commands.

## Current path (d6)

- Quiz / fail lock / ceiling render as full-panel overlays in the UI process.
- No Close/Skip controls while `quiz_due`.
- Lab controls (`child_tick`, `child_force_quiz`) exercise the local simulated clock.

## Fallback (if pilot shows steal races)

Prefer the existing **native overlay shell** in `meritscreen-agent` (`--overlay-proof`) for quiz_due / shield, with UI WebView as secondary. Guardian owns phase persistence across UI kill (d7/d8).

## Honest limit

Document rather than fake: WebView alone is not a kiosk. Enforcement of non-allowed apps remains Guardian/Agent (d7).
