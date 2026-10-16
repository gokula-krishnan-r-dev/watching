# Spike plan — Desktop App Check / custom attestation (non-blocking)

**Status:** Spike only — **does not block** Windows/macOS v1. See `SECURITY.md` and `docs/17` §6.

## Problem

Firebase App Check has no first-party attestation provider for desktop (no Play Integrity / DeviceCheck equivalent for Win/Mac consumer apps). Desktop Guardian uses REST Auth + Firestore under child custom tokens.

## Residual risk (v1)

- Stolen refresh / ID token from a compromised child machine can call child-scoped APIs until revoke.
- Mitigations already shipping: pairing consume rate limits, device revoke, `tamperFlags`, no App Check **enforcement** on desktop callables (monitor mode for mobile remains).

## Spike options (post-v1)

| Option | Idea | Cost / notes |
| --- | --- | --- |
| A. Custom App Check provider | Guardian posts a short-lived attestation JWT (device id + binary hash + OS) signed by our attestation service; Functions `enforceAppCheck` validates | Needs backend + key ops; replay window |
| B. Device-bound token binding | Bind refresh token to hardware id / TPM/Secure Enclave seal | Stronger on Mac T2/M-series; Windows TPM varies by SKU |
| C. mTLS to sync edge | Skip App Check; terminate TLS with client certs issued at pair | Heavier infra |

## Recommended next experiment

1. Prototype **Option A** against a single callable (`deviceHeartbeat`) in a staging project.
2. Measure false rejects on VM snapshots / cloned disks.
3. Keep desktop in **monitor** (log-only) until false-reject rate is acceptable.

## Anti-goals

- Do not block pairing or quiz offline path on App Check.
- Do not ship a kernel driver for “integrity.”
