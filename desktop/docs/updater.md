# Desktop updater (d9 + d12 staged rings)

Ed25519-signed manifests with rollback protection and **percent cohorts**. Implementation: `meritscreen-updater`.

## Manifest

```json
{
  "version": "0.2.0",
  "channel": "stable",
  "minVersion": "0.1.0",
  "rolloutPercent": 25,
  "artifacts": [
    {
      "platform": "macos-aarch64",
      "url": "https://updates.example/MeritScreen-0.2.0.pkg",
      "sha256": "…",
      "sizeBytes": 12345678
    }
  ]
}
```

Signature: hex-encoded Ed25519 over the **raw manifest JSON bytes** (not re-serialized). Public key shipped in the client / CI secret for verify dry-runs.

## Rings (d12)

| Ring | Who | Notes |
| --- | --- | --- |
| `internal` | Eng / dogfood | Always on for enrolled internal devices |
| `pilot` | Closed pilot families | Full pilot cohort |
| `stable` | GA | `rolloutPercent` 0–100 of stable-enrolled devices |

Promotion path: **internal → pilot → stable@N% → stable@100%**.

Cohort: `SHA-256(deviceId|version)[0] % 100 < rolloutPercent` (deterministic).

Device enrollment ring must be ≥ manifest channel (pilot devices do not pull stable until promoted).

## Rollback protection

- Persist `accepted_floor` (last known-good version that passed health checks).
- Refuse candidates `< accepted_floor`.
- Refuse install when client `< manifest.minVersion` (force reinstall path).

## Ops

1. Create Storage / CDN bucket for signed artifacts (backend dependency).
2. Keep Ed25519 **signing** key offline / in release CI OIDC; only the verifying key in the binary.
3. Health-gated rollout: promote channel / raise % only after Guardian heartbeat healthy + crash SLO.
4. Manual recovery: reinstall signed `.pkg` / MSI if a bad update bricks the service.
5. Pilot exit before raising stable % — see [pilot-playbook.md](pilot-playbook.md).

## Dry-run

```bash
cd desktop
cargo test -p meritscreen-updater
```

Broken updater risk is accepted only with this verify path + manual recovery doc above.
