# Spike: Linux X11 grab vs Wayland layer-shell

**Result:** CONDITIONAL — X11 path acceptable for **d11 beta**; Wayland best-effort only.

## Findings

| Session | Overlay / grab | App gating |
| --- | --- | --- |
| X11 | `XGrabKeyboard` / fullscreen override-redirect viable for kiosk-style overlay | Process watch + window close |
| Wayland | No global grab; need layer-shell (wlr) or kiosk compositor; GNOME/KDE differ | Same L3 process approach; overlay weaker |

## Product decision

- Linux is **not** a Windows/macOS v1 blocker.
- Supported beta distros: Ubuntu LTS + Fedora (explicit list in d11).
- Prefer documenting X11 session for strongest beta experience.
- Parent UI: show degraded `enforcementTier` when Wayland limits apply.

## Anti-goals

- No custom kernel modules. No claiming “fully locked” on Wayland.
