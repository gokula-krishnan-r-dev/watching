# Store & policy readiness (d12)

Direct **signed download** is the primary child distribution path ([docs/17](../../docs/17-desktop-d0-product-lock.md)). Store SKUs (Microsoft Store / Mac App Store) may be reduced-capability — document if shipped.

## Privacy policy (desktop addendum)

Ensure the public privacy policy mentions:

- Always-on Guardian / Session Agent on the child PC (session phase, installed-app inventory labels, usage minutes).
- No GPS, contacts, SMS, photos, child email, keylogging, or other-app screen capture.
- Parent PIN and pairing material stored in OS secret store / SQLCipher.
- Tamper signals (`serviceStopped`, `uninstallAttempt`, `adminAccount`, `clockRollback`) shared with the parent account.
- Desktop App Check gap / monitor-mode residual risk (high level).

Ops owns publishing the live policy URL; this file is the eng checklist.

## Nutrition / data-safety labels

Draft labels for any store listing:

| Data type | Collected? | Linked to identity? | Purpose |
| --- | --- | --- | --- |
| App activity (usage minutes, quiz results) | Yes | Family/child id | Screen-time rules |
| Device identifiers | Yes (device id) | Child device | Pairing / revoke |
| Diagnostics (tamper, guardian state) | Yes | Device | Parent alerts / reliability |
| Precise location / contacts / photos | No | — | — |

## Direct-download primary

- Marketing and in-app parent copy: prefer “Download for Windows / Mac” over store-only.
- Store builds must not claim full L2/L3 if sandbox blocks it.

## Residual limitations to keep visible

See [test-matrix.md](test-matrix.md) and parent child-detail “device health” (enforcement tier + tamper flags).
