//! Firebase REST implementations. Used only from Guardian / sync workers — never UI.

use std::time::Duration;

use async_trait::async_trait;
use reqwest::Client;
use serde::Deserialize;
use serde_json::{json, Value};
use tracing::debug;

use crate::error::{FirebaseError, FirebaseResult};
use crate::traits::{AuthClient, AuthTokens, CallableClient, FirestoreClient};

fn http_client() -> Client {
    Client::builder()
        .timeout(Duration::from_secs(20))
        .connect_timeout(Duration::from_secs(8))
        .pool_idle_timeout(Duration::from_secs(30))
        .build()
        .unwrap_or_else(|_| Client::new())
}

#[derive(Debug, Clone)]
pub struct FirebaseConfig {
    pub api_key: String,
    pub project_id: String,
}

impl FirebaseConfig {
    /// Production config: runtime env wins, then the value baked in at
    /// build time via `MERITSCREEN_FIREBASE_*` (CI secrets / local env
    /// file), then the public project id default. Returns `None` when
    /// no API key is available so callers can fall back to demo/mock.
    pub fn from_env_or_build() -> Option<Self> {
        let api_key = std::env::var("MERITSCREEN_FIREBASE_API_KEY")
            .ok()
            .filter(|v| !v.trim().is_empty())
            .or_else(|| {
                option_env!("MERITSCREEN_FIREBASE_API_KEY")
                    .map(str::to_string)
                    .filter(|v| !v.trim().is_empty())
            })?;
        let project_id = std::env::var("MERITSCREEN_FIREBASE_PROJECT_ID")
            .ok()
            .filter(|v| !v.trim().is_empty())
            .or_else(|| {
                option_env!("MERITSCREEN_FIREBASE_PROJECT_ID")
                    .map(str::to_string)
                    .filter(|v| !v.trim().is_empty())
            })
            .unwrap_or_else(|| "managing-screen-time".into());
        Some(Self {
            api_key,
            project_id,
        })
    }
}

impl FirebaseConfig {
    pub fn identity_url(&self) -> String {
        format!(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key={}",
            self.api_key
        )
    }

    pub fn secure_token_url(&self) -> String {
        format!(
            "https://securetoken.googleapis.com/v1/token?key={}",
            self.api_key
        )
    }

    pub fn firestore_base(&self) -> String {
        format!(
            "https://firestore.googleapis.com/v1/projects/{}/databases/(default)/documents",
            self.project_id
        )
    }

    pub fn callable_url(&self, name: &str) -> String {
        let region =
            std::env::var("MERITSCREEN_FIREBASE_REGION").unwrap_or_else(|_| "us-central1".into());
        // Gen2 / regional callables: https://REGION-PROJECT.cloudfunctions.net/NAME
        format!(
            "https://{}-{}.cloudfunctions.net/{}",
            region, self.project_id, name
        )
    }
}

#[derive(Debug, Clone)]
pub struct RestAuthClient {
    http: Client,
    config: FirebaseConfig,
}

impl RestAuthClient {
    pub fn new(config: FirebaseConfig) -> Self {
        Self {
            http: http_client(),
            config,
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CustomTokenResponse {
    id_token: String,
    refresh_token: String,
    expires_in: String,
    #[serde(default)]
    local_id: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RefreshResponse {
    id_token: String,
    refresh_token: String,
    expires_in: String,
    #[serde(default)]
    user_id: Option<String>,
}

fn extract_sub_from_jwt(token: &str) -> Option<String> {
    let mut parts = token.split('.');
    let _header = parts.next()?;
    let payload = parts.next()?;
    use base64::engine::general_purpose::URL_SAFE_NO_PAD;
    use base64::Engine;
    let unpadded = payload.trim_end_matches('=');
    let decoded = URL_SAFE_NO_PAD.decode(unpadded).ok()?;
    let v: Value = serde_json::from_slice(&decoded).ok()?;
    v.get("user_id")
        .or_else(|| v.get("sub"))
        .and_then(|u| u.as_str())
        .map(|s| s.to_string())
}

#[async_trait]
impl AuthClient for RestAuthClient {
    async fn sign_in_with_custom_token(&self, custom_token: &str) -> FirebaseResult<AuthTokens> {
        debug!("firebase auth custom_token exchange");
        let resp = self
            .http
            .post(self.config.identity_url())
            .json(&json!({ "token": custom_token, "returnSecureToken": true }))
            .send()
            .await
            .map_err(FirebaseError::Network)?;
        let status = resp.status().as_u16();
        let body = resp.text().await.map_err(FirebaseError::Network)?;
        if !(200..300).contains(&status) {
            return Err(FirebaseError::Http { status, body });
        }
        let parsed: CustomTokenResponse =
            serde_json::from_str(&body).map_err(|e| FirebaseError::Decode(e.to_string()))?;
        let user_id = parsed
            .local_id
            .or_else(|| extract_sub_from_jwt(&parsed.id_token))
            .unwrap_or_else(|| "user".into());
        Ok(AuthTokens {
            id_token: parsed.id_token,
            refresh_token: parsed.refresh_token,
            expires_in: parsed.expires_in.parse().unwrap_or(3600),
            user_id,
        })
    }

    async fn refresh_id_token(&self, refresh_token: &str) -> FirebaseResult<AuthTokens> {
        debug!("firebase auth refresh");
        let resp = self
            .http
            .post(self.config.secure_token_url())
            .form(&[
                ("grant_type", "refresh_token"),
                ("refresh_token", refresh_token),
            ])
            .send()
            .await
            .map_err(FirebaseError::Network)?;
        let status = resp.status().as_u16();
        let body = resp.text().await.map_err(FirebaseError::Network)?;
        if !(200..300).contains(&status) {
            return Err(FirebaseError::Http { status, body });
        }
        let parsed: RefreshResponse =
            serde_json::from_str(&body).map_err(|e| FirebaseError::Decode(e.to_string()))?;
        let user_id = parsed
            .user_id
            .or_else(|| extract_sub_from_jwt(&parsed.id_token))
            .unwrap_or_else(|| "user".into());
        Ok(AuthTokens {
            id_token: parsed.id_token,
            refresh_token: parsed.refresh_token,
            expires_in: parsed.expires_in.parse().unwrap_or(3600),
            user_id,
        })
    }
}

#[derive(Debug, Clone)]
pub struct RestFirestoreClient {
    http: Client,
    config: FirebaseConfig,
}

impl RestFirestoreClient {
    pub fn new(config: FirebaseConfig) -> Self {
        Self {
            http: http_client(),
            config,
        }
    }
}

#[async_trait]
impl FirestoreClient for RestFirestoreClient {
    async fn get_document(&self, path: &str, id_token: &str) -> FirebaseResult<Option<Value>> {
        let url = format!(
            "{}/{}",
            self.config.firestore_base(),
            path.trim_start_matches('/')
        );
        let resp = self
            .http
            .get(&url)
            .bearer_auth(id_token)
            .send()
            .await
            .map_err(FirebaseError::Network)?;
        let status = resp.status().as_u16();
        if status == 404 {
            return Ok(None);
        }
        let body = resp.text().await.map_err(FirebaseError::Network)?;
        if !(200..300).contains(&status) {
            return Err(FirebaseError::Http { status, body });
        }
        let value: Value =
            serde_json::from_str(&body).map_err(|e| FirebaseError::Decode(e.to_string()))?;
        Ok(Some(value))
    }

    async fn commit(&self, writes: Value, id_token: &str) -> FirebaseResult<Value> {
        let url = format!(
            "https://firestore.googleapis.com/v1/projects/{}/databases/(default)/documents:commit",
            self.config.project_id
        );
        let resp = self
            .http
            .post(url)
            .bearer_auth(id_token)
            .json(&json!({ "writes": writes }))
            .send()
            .await
            .map_err(FirebaseError::Network)?;
        let status = resp.status().as_u16();
        let body = resp.text().await.map_err(FirebaseError::Network)?;
        if !(200..300).contains(&status) {
            return Err(FirebaseError::Http { status, body });
        }
        serde_json::from_str(&body).map_err(|e| FirebaseError::Decode(e.to_string()))
    }
}

#[derive(Debug, Clone)]
pub struct RestCallableClient {
    http: Client,
    config: FirebaseConfig,
}

impl RestCallableClient {
    pub fn new(config: FirebaseConfig) -> Self {
        Self {
            http: http_client(),
            config,
        }
    }
}

#[async_trait]
impl CallableClient for RestCallableClient {
    async fn invoke(
        &self,
        name: &str,
        data: Value,
        id_token: Option<&str>,
    ) -> FirebaseResult<Value> {
        let mut req = self
            .http
            .post(self.config.callable_url(name))
            .json(&json!({ "data": data }));
        if let Some(token) = id_token {
            req = req.bearer_auth(token);
        }
        let resp = req.send().await.map_err(FirebaseError::Network)?;
        let status = resp.status().as_u16();
        let body = resp.text().await.map_err(FirebaseError::Network)?;
        if !(200..300).contains(&status) {
            return Err(FirebaseError::Http { status, body });
        }
        let value: Value =
            serde_json::from_str(&body).map_err(|e| FirebaseError::Decode(e.to_string()))?;
        Ok(value.get("result").cloned().unwrap_or(value))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_google_identity_custom_token_response() {
        // Real Google Identity Toolkit signInWithCustomToken payload structure:
        let raw = r#"{
            "kind": "identitytoolkit#VerifyCustomTokenResponse",
            "idToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiYWJjMTIzIiwic3ViIjoiYWJjMTIzIn0.sig",
            "refreshToken": "refresh-xyz",
            "expiresIn": "3600",
            "isNewUser": false
        }"#;

        let parsed: CustomTokenResponse = serde_json::from_str(raw).expect("parse custom token");
        assert!(parsed.id_token.starts_with("eyJ"));
        assert_eq!(parsed.refresh_token, "refresh-xyz");
        assert_eq!(parsed.expires_in, "3600");
        let uid = parsed
            .local_id
            .or_else(|| extract_sub_from_jwt(&parsed.id_token));
        assert_eq!(uid.as_deref(), Some("abc123"));
    }

    #[test]
    fn parses_refresh_response_camel_case() {
        let raw = r#"{
            "idToken": "new-id-token",
            "refreshToken": "new-refresh-token",
            "expiresIn": "3600",
            "userId": "user-456"
        }"#;
        let parsed: RefreshResponse = serde_json::from_str(raw).expect("parse refresh");
        assert_eq!(parsed.id_token, "new-id-token");
        assert_eq!(parsed.user_id.as_deref(), Some("user-456"));
    }
}
