//! Signed updater: Ed25519 manifest verify + version rollback floor (d9).
//!
//! Ops publishes a JSON manifest + detached signature (hex Ed25519 over the
//! canonical payload bytes). Guardian refuses installs below `accepted_floor`
//! and below `manifest.min_version`. CDN / Storage bucket wiring is ops-side;
//! this crate is the dry-run verify path.

#![forbid(unsafe_code)]

use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use semver::Version;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use thiserror::Error;

/// Staged rollout channel.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum UpdateChannel {
    Internal,
    Pilot,
    Stable,
}

impl UpdateChannel {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Internal => "internal",
            Self::Pilot => "pilot",
            Self::Stable => "stable",
        }
    }

    /// Ring order for promotion: internal → pilot → stable.
    pub fn rank(self) -> u8 {
        match self {
            Self::Internal => 0,
            Self::Pilot => 1,
            Self::Stable => 2,
        }
    }
}

/// Staged rollout policy (d12): channel + optional % of stable cohort.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RolloutPolicy {
    pub channel: UpdateChannel,
    /// 0–100. Applies when `channel == Stable` (percent of devices after pilot).
    /// Ignored for `internal` / `pilot` (those rings are allowlisted by enrollment).
    pub percent: u8,
}

impl RolloutPolicy {
    pub fn internal() -> Self {
        Self {
            channel: UpdateChannel::Internal,
            percent: 100,
        }
    }

    pub fn pilot() -> Self {
        Self {
            channel: UpdateChannel::Pilot,
            percent: 100,
        }
    }

    pub fn stable_percent(percent: u8) -> Self {
        Self {
            channel: UpdateChannel::Stable,
            percent: percent.min(100),
        }
    }
}

/// Stable, deterministic cohort bucket in `0..100` from device id + version.
pub fn cohort_bucket(device_id: &str, version: &str) -> u8 {
    let mut hasher = Sha256::new();
    hasher.update(device_id.as_bytes());
    hasher.update(b"|");
    hasher.update(version.as_bytes());
    let digest = hasher.finalize();
    digest[0] % 100
}

/// Whether this device should receive the update under the rollout policy.
///
/// Rules:
/// - Device enrolled ring must be ≥ manifest channel (e.g. pilot device can take pilot builds;
///   stable enrollment required for stable manifests).
/// - For `stable`, also require `cohort_bucket < percent`.
pub fn device_in_rollout(
    device_ring: UpdateChannel,
    device_id: &str,
    policy: &RolloutPolicy,
    manifest_version: &str,
) -> bool {
    if device_ring.rank() < policy.channel.rank() {
        return false;
    }
    match policy.channel {
        UpdateChannel::Internal | UpdateChannel::Pilot => true,
        UpdateChannel::Stable => {
            if policy.percent >= 100 {
                return true;
            }
            if policy.percent == 0 {
                return false;
            }
            cohort_bucket(device_id, manifest_version) < policy.percent
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateArtifact {
    pub platform: String,
    pub url: String,
    pub sha256: String,
    pub size_bytes: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateManifest {
    pub version: String,
    pub channel: UpdateChannel,
    /// Clients below this must upgrade (or reinstall).
    pub min_version: String,
    pub artifacts: Vec<UpdateArtifact>,
    /// Optional notes for parent UI (never secrets).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub release_notes: Option<String>,
    /// Stable % rollout (0–100). Default 100 when omitted (full stable ring).
    #[serde(default = "default_rollout_percent")]
    pub rollout_percent: u8,
}

fn default_rollout_percent() -> u8 {
    100
}

#[derive(Debug, Error)]
pub enum UpdateError {
    #[error("manifest json: {0}")]
    Json(String),
    #[error("invalid public key")]
    BadPublicKey,
    #[error("invalid signature encoding")]
    BadSignatureEncoding,
    #[error("signature verification failed")]
    BadSignature,
    #[error("semver parse: {0}")]
    Semver(String),
    #[error("rollback refused: candidate {candidate} < accepted floor {floor}")]
    Rollback { candidate: String, floor: String },
    #[error("client {client} below manifest min_version {min}")]
    BelowMin { client: String, min: String },
    #[error("artifact sha256 mismatch")]
    ArtifactHash,
    #[error("no artifact for platform {0}")]
    NoArtifact(String),
    #[error("device not in rollout cohort")]
    NotInRollout,
}

/// Verify Ed25519 signature over raw manifest bytes (UTF-8 JSON).
pub fn verify_manifest_signature(
    manifest_bytes: &[u8],
    signature_hex: &str,
    public_key_hex: &str,
) -> Result<UpdateManifest, UpdateError> {
    let pk_raw = hex::decode(public_key_hex.trim()).map_err(|_| UpdateError::BadPublicKey)?;
    let pk_arr: [u8; 32] = pk_raw
        .try_into()
        .map_err(|_| UpdateError::BadPublicKey)?;
    let verifying =
        VerifyingKey::from_bytes(&pk_arr).map_err(|_| UpdateError::BadPublicKey)?;

    let sig_raw =
        hex::decode(signature_hex.trim()).map_err(|_| UpdateError::BadSignatureEncoding)?;
    let sig_arr: [u8; 64] = sig_raw
        .try_into()
        .map_err(|_| UpdateError::BadSignatureEncoding)?;
    let signature = Signature::from_bytes(&sig_arr);

    verifying
        .verify(manifest_bytes, &signature)
        .map_err(|_| UpdateError::BadSignature)?;

    serde_json::from_slice(manifest_bytes).map_err(|e| UpdateError::Json(e.to_string()))
}

/// Refuse downgrades below the last accepted good version (rollback protection).
pub fn accept_candidate(
    current: &str,
    accepted_floor: &str,
    manifest: &UpdateManifest,
) -> Result<Version, UpdateError> {
    let candidate =
        Version::parse(&manifest.version).map_err(|e| UpdateError::Semver(e.to_string()))?;
    let floor =
        Version::parse(accepted_floor).map_err(|e| UpdateError::Semver(e.to_string()))?;
    let client = Version::parse(current).map_err(|e| UpdateError::Semver(e.to_string()))?;
    let min =
        Version::parse(&manifest.min_version).map_err(|e| UpdateError::Semver(e.to_string()))?;

    if candidate < floor {
        return Err(UpdateError::Rollback {
            candidate: candidate.to_string(),
            floor: floor.to_string(),
        });
    }
    if client < min {
        return Err(UpdateError::BelowMin {
            client: client.to_string(),
            min: min.to_string(),
        });
    }
    Ok(candidate)
}

pub fn verify_artifact_sha256(bytes: &[u8], expected_hex: &str) -> Result<(), UpdateError> {
    let digest = Sha256::digest(bytes);
    let got = hex::encode(digest);
    if got.eq_ignore_ascii_case(expected_hex.trim()) {
        Ok(())
    } else {
        Err(UpdateError::ArtifactHash)
    }
}

pub fn artifact_for_platform<'a>(
    manifest: &'a UpdateManifest,
    platform: &str,
) -> Result<&'a UpdateArtifact, UpdateError> {
    manifest
        .artifacts
        .iter()
        .find(|a| a.platform == platform)
        .ok_or_else(|| UpdateError::NoArtifact(platform.into()))
}

/// Dry-run: verify signature, rollback floor, rollout cohort, and artifact hash.
pub fn dry_run_verify(
    manifest_bytes: &[u8],
    signature_hex: &str,
    public_key_hex: &str,
    current_version: &str,
    accepted_floor: &str,
    platform: &str,
    artifact_bytes: &[u8],
) -> Result<UpdateManifest, UpdateError> {
    dry_run_verify_for_device(
        manifest_bytes,
        signature_hex,
        public_key_hex,
        current_version,
        accepted_floor,
        platform,
        artifact_bytes,
        UpdateChannel::Stable,
        "dry-run-device",
    )
}

/// Dry-run including device ring + % cohort (d12 staged rollout).
#[allow(clippy::too_many_arguments)]
pub fn dry_run_verify_for_device(
    manifest_bytes: &[u8],
    signature_hex: &str,
    public_key_hex: &str,
    current_version: &str,
    accepted_floor: &str,
    platform: &str,
    artifact_bytes: &[u8],
    device_ring: UpdateChannel,
    device_id: &str,
) -> Result<UpdateManifest, UpdateError> {
    let manifest = verify_manifest_signature(manifest_bytes, signature_hex, public_key_hex)?;
    let policy = RolloutPolicy {
        channel: manifest.channel,
        percent: manifest.rollout_percent.min(100),
    };
    if !device_in_rollout(device_ring, device_id, &policy, &manifest.version) {
        return Err(UpdateError::NotInRollout);
    }
    let _ = accept_candidate(current_version, accepted_floor, &manifest)?;
    let art = artifact_for_platform(&manifest, platform)?;
    verify_artifact_sha256(artifact_bytes, &art.sha256)?;
    Ok(manifest)
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::{Signer, SigningKey};
    use rand::rngs::OsRng;

    fn sign_manifest(manifest: &UpdateManifest) -> (Vec<u8>, String, String) {
        let bytes = serde_json::to_vec(manifest).unwrap();
        let signing = SigningKey::generate(&mut OsRng);
        let sig = signing.sign(&bytes);
        let pk = hex::encode(signing.verifying_key().as_bytes());
        let sig_hex = hex::encode(sig.to_bytes());
        (bytes, sig_hex, pk)
    }

    #[test]
    fn verify_and_accept_happy_path() {
        let blob = b"guardian-bin-v2";
        let sha = hex::encode(Sha256::digest(blob));
        let manifest = UpdateManifest {
            version: "0.2.0".into(),
            channel: UpdateChannel::Stable,
            min_version: "0.1.0".into(),
            artifacts: vec![UpdateArtifact {
                platform: "macos-aarch64".into(),
                url: "https://example.invalid/a".into(),
                sha256: sha,
                size_bytes: blob.len() as u64,
            }],
            release_notes: None,
            rollout_percent: 100,
        };
        let (bytes, sig, pk) = sign_manifest(&manifest);
        let got = dry_run_verify(
            &bytes,
            &sig,
            &pk,
            "0.1.0",
            "0.1.0",
            "macos-aarch64",
            blob,
        )
        .unwrap();
        assert_eq!(got.version, "0.2.0");
    }

    #[test]
    fn rollback_below_floor_refused() {
        let manifest = UpdateManifest {
            version: "0.1.0".into(),
            channel: UpdateChannel::Pilot,
            min_version: "0.1.0".into(),
            artifacts: vec![],
            release_notes: None,
            rollout_percent: 100,
        };
        let err = accept_candidate("0.2.0", "0.2.0", &manifest).unwrap_err();
        assert!(matches!(err, UpdateError::Rollback { .. }));
    }

    #[test]
    fn bad_signature_fails_closed() {
        let manifest = UpdateManifest {
            version: "0.2.0".into(),
            channel: UpdateChannel::Internal,
            min_version: "0.1.0".into(),
            artifacts: vec![],
            release_notes: None,
            rollout_percent: 100,
        };
        let (bytes, _sig, pk) = sign_manifest(&manifest);
        let bad = "00".repeat(64);
        assert!(matches!(
            verify_manifest_signature(&bytes, &bad, &pk),
            Err(UpdateError::BadSignature)
        ));
    }

    #[test]
    fn percent_rollout_is_deterministic() {
        let b = cohort_bucket("device-a", "0.3.0");
        assert_eq!(b, cohort_bucket("device-a", "0.3.0"));
        assert!(b < 100);
        let policy = RolloutPolicy::stable_percent(0);
        assert!(!device_in_rollout(
            UpdateChannel::Stable,
            "device-a",
            &policy,
            "0.3.0"
        ));
        let full = RolloutPolicy::stable_percent(100);
        assert!(device_in_rollout(
            UpdateChannel::Stable,
            "device-a",
            &full,
            "0.3.0"
        ));
    }

    #[test]
    fn pilot_device_cannot_take_stable_until_enrolled() {
        let policy = RolloutPolicy::stable_percent(100);
        assert!(!device_in_rollout(
            UpdateChannel::Pilot,
            "dev1",
            &policy,
            "0.3.0"
        ));
        assert!(device_in_rollout(
            UpdateChannel::Stable,
            "dev1",
            &policy,
            "0.3.0"
        ));
    }

    #[test]
    fn stable_percent_cohort_splits() {
        let policy = RolloutPolicy::stable_percent(10);
        let mut in_count = 0;
        for i in 0..200 {
            let id = format!("dev-{i}");
            if device_in_rollout(UpdateChannel::Stable, &id, &policy, "1.0.0") {
                in_count += 1;
            }
        }
        // Roughly ~10% of 200 ≈ 20; allow wide band for hash distribution.
        assert!(
            (5..40).contains(&in_count),
            "expected ~10% cohort, got {in_count}"
        );
    }
}
