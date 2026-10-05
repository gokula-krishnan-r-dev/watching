# Spike: Windows custom shell / Assigned Access

**Result:** PASS (API path) — Strict mode (L2) is viable on supported SKUs; not required for v1 L1+L3.

## APIs / mechanisms

| Mechanism | Availability | Notes |
| --- | --- | --- |
| Per-user `Shell=` (Winlogon) | Pro / Enterprise / Education with policy or registry for that user | Replaces Explorer with MeritScreen UI for that standard account |
| Assigned Access / multi-app kiosk | Windows 10/11 Pro+ | Stronger kiosk; more setup |
| Windows 10/11 **Home** | Limited | No full Assigned Access; rely on L1 launcher + L3 process gating |
| Service + `CreateProcessAsUser` | All SKUs we target | Guardian starts Agent in interactive session after boot/login |

## Product decision

1. **Always ship L1 + L3** on Windows 10/11 (Home and Pro).
2. **Strict (L2)** is opt-in parent setting + C02 wizard, only when the OS SKU supports it.
3. Parent UI must show honest copy when Strict is unavailable (Home / admin child).
4. Direct-download MSI/MSIX is the primary child channel (Store sandbox may weaken L2/L3).

## Lab verify in d3 (not blocking d0 sign-off)

- [ ] Win 11 Pro VM: install Service → reboot → Agent in user session → IPC ping
- [ ] Optional: `Shell=` for standard user launches UI instead of Explorer
- [ ] SmartScreen reputation path with test-signed vs EV-signed build

## Anti-goals

- No kernel drivers, no Accessibility-based trapping, no fake “Device Owner” claims.
