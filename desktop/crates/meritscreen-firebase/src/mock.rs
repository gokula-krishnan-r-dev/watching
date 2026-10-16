//! In-memory Firebase mocks for unit tests.

use std::collections::HashMap;
use std::sync::Mutex;

use async_trait::async_trait;
use serde_json::{json, Value};

use crate::error::{FirebaseError, FirebaseResult};
use crate::traits::{
    AuthClient, AuthTokens, CallableClient, DeviceHeartbeatPatch, DeviceSyncClient,
    FirestoreClient, InstalledAppUpload,
};

#[derive(Default)]
pub struct MockAuthClient {
    pub fail: Mutex<bool>,
}

#[async_trait]
impl AuthClient for MockAuthClient {
    async fn sign_in_with_custom_token(&self, custom_token: &str) -> FirebaseResult<AuthTokens> {
        if *self.fail.lock().unwrap() {
            return Err(FirebaseError::Auth("mock auth failed".into()));
        }
        if custom_token.is_empty() {
            return Err(FirebaseError::Auth("empty custom token".into()));
        }
        Ok(AuthTokens {
            id_token: format!("id-{custom_token}"),
            refresh_token: format!("refresh-{custom_token}"),
            expires_in: 3600,
            user_id: "mock-user".into(),
        })
    }

    async fn refresh_id_token(&self, refresh_token: &str) -> FirebaseResult<AuthTokens> {
        if *self.fail.lock().unwrap() {
            return Err(FirebaseError::Auth("mock refresh failed".into()));
        }
        Ok(AuthTokens {
            id_token: format!("id-from-{refresh_token}"),
            refresh_token: refresh_token.into(),
            expires_in: 3600,
            user_id: "mock-user".into(),
        })
    }
}

#[derive(Default)]
pub struct MockFirestoreClient {
    pub docs: Mutex<HashMap<String, Value>>,
}

#[async_trait]
impl FirestoreClient for MockFirestoreClient {
    async fn get_document(&self, path: &str, _id_token: &str) -> FirebaseResult<Option<Value>> {
        Ok(self.docs.lock().unwrap().get(path).cloned())
    }

    async fn commit(&self, writes: Value, _id_token: &str) -> FirebaseResult<Value> {
        Ok(json!({ "writes": writes, "ok": true }))
    }
}

#[derive(Default)]
pub struct MockCallableClient {
    pub last_name: Mutex<Option<String>>,
    pub last_data: Mutex<Option<Value>>,
}

#[async_trait]
impl CallableClient for MockCallableClient {
    async fn invoke(
        &self,
        name: &str,
        data: Value,
        _id_token: Option<&str>,
    ) -> FirebaseResult<Value> {
        *self.last_name.lock().unwrap() = Some(name.into());
        *self.last_data.lock().unwrap() = Some(data.clone());
        Ok(json!({ "ok": true, "echo": data }))
    }
}

#[derive(Default)]
pub struct MockDeviceSyncClient {
    pub last_heartbeat: Mutex<Option<(String, DeviceHeartbeatPatch)>>,
    pub last_inventory: Mutex<Option<(String, String, usize)>>,
}

#[async_trait]
impl DeviceSyncClient for MockDeviceSyncClient {
    async fn patch_heartbeat(
        &self,
        device_id: &str,
        patch: DeviceHeartbeatPatch,
        _id_token: &str,
    ) -> FirebaseResult<()> {
        *self.last_heartbeat.lock().unwrap() = Some((device_id.into(), patch));
        Ok(())
    }

    async fn upload_installed_apps(
        &self,
        device_id: &str,
        apps: &[InstalledAppUpload],
        inventory_hash: &str,
        _id_token: &str,
    ) -> FirebaseResult<()> {
        *self.last_inventory.lock().unwrap() =
            Some((device_id.into(), inventory_hash.into(), apps.len()));
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn mock_auth_custom_token() {
        let auth = MockAuthClient::default();
        let tokens = auth.sign_in_with_custom_token("abc").await.unwrap();
        assert_eq!(tokens.id_token, "id-abc");
        assert_eq!(tokens.refresh_token, "refresh-abc");
    }

    #[tokio::test]
    async fn mock_auth_maps_to_app_error() {
        let auth = MockAuthClient {
            fail: Mutex::new(true),
        };
        let err = auth.sign_in_with_custom_token("x").await.unwrap_err();
        assert_eq!(err.to_app_error().user_message(), "mock auth failed");
    }

    #[tokio::test]
    async fn mock_firestore_get_and_callable() {
        let fs = MockFirestoreClient::default();
        fs.docs
            .lock()
            .unwrap()
            .insert("families/f1".into(), json!({"name": "A"}));
        let doc = fs.get_document("families/f1", "tok").await.unwrap();
        assert_eq!(doc.unwrap()["name"], "A");

        let call = MockCallableClient::default();
        let result = call
            .invoke("consumePairingToken", json!({"code": "000000"}), None)
            .await
            .unwrap();
        assert_eq!(result["ok"], true);
        assert_eq!(
            call.last_name.lock().unwrap().as_deref(),
            Some("consumePairingToken")
        );
    }
}
