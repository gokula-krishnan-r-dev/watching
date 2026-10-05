use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::error::FirebaseResult;

#[derive(Debug, Clone)]
pub struct AuthTokens {
    pub id_token: String,
    pub refresh_token: String,
    pub expires_in: u64,
    pub user_id: String,
}

/// Device heartbeat fields (docs/15 §9) — Guardian only.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct DeviceHeartbeatPatch {
    pub guardian_state: String,
    pub enforcement_tier: String,
    pub tamper_flags: Value,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub agent_version: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub os_build: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InstalledAppUpload {
    /// Firestore field name mirrors Android: packageName holds desktop appId.
    pub package_name: String,
    pub label: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub icon_hash: Option<String>,
}

#[async_trait]
pub trait AuthClient: Send + Sync {
    async fn sign_in_with_custom_token(&self, custom_token: &str) -> FirebaseResult<AuthTokens>;
    async fn refresh_id_token(&self, refresh_token: &str) -> FirebaseResult<AuthTokens>;
}

#[async_trait]
pub trait FirestoreClient: Send + Sync {
    async fn get_document(&self, path: &str, id_token: &str) -> FirebaseResult<Option<Value>>;
    async fn commit(&self, writes: Value, id_token: &str) -> FirebaseResult<Value>;
}

#[async_trait]
pub trait CallableClient: Send + Sync {
    async fn invoke(
        &self,
        name: &str,
        data: Value,
        id_token: Option<&str>,
    ) -> FirebaseResult<Value>;
}

/// Guardian sync surface for device heartbeat + inventory (d7 local; d8 schedulers).
#[async_trait]
pub trait DeviceSyncClient: Send + Sync {
    async fn patch_heartbeat(
        &self,
        device_id: &str,
        patch: DeviceHeartbeatPatch,
        id_token: &str,
    ) -> FirebaseResult<()>;

    async fn upload_installed_apps(
        &self,
        device_id: &str,
        apps: &[InstalledAppUpload],
        inventory_hash: &str,
        id_token: &str,
    ) -> FirebaseResult<()>;
}
