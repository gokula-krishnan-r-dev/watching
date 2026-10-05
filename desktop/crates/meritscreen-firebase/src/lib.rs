//! Firebase REST surface for Guardian sync. UI must never depend on this crate.
//!
//! App Check: no official desktop attestation provider — see SECURITY.md.

#![forbid(unsafe_code)]

pub mod child_sync;
pub mod error;
pub mod mock;
pub mod mock_sync;
pub mod paths;
pub mod rest;
pub mod rest_sync;
pub mod traits;

pub use child_sync::{
    policy_from_value, ChildRemoteClient, DeviceIds, DeviceStatus, PolicyBundle, QuizAttemptUpload,
    SkillStateUpload, UsageDayUpload,
};
pub use error::{FirebaseError, FirebaseResult};
pub use mock_sync::MockChildRemoteClient;
pub use rest::{FirebaseConfig, RestAuthClient, RestCallableClient, RestFirestoreClient};
pub use rest_sync::{OwnedRestChildRemoteClient, RestDeviceSyncClient};
pub use traits::{
    AuthClient, AuthTokens, CallableClient, DeviceHeartbeatPatch, DeviceSyncClient,
    FirestoreClient, InstalledAppUpload,
};

/// Documented residual risk until a custom attestation provider ships.
pub const APP_CHECK_DESKTOP_GAP: &str =
    "No Firebase App Check attestation provider for desktop; mitigate with rate limits, device-bound tokens, and revoke.";
