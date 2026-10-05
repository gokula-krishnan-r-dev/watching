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
        let region = std::env::var("MERITSCREEN_FIREBASE_REGION")
            .unwrap_or_else(|_| "us-central1".into());
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
struct CustomTokenResponse {
    id_token: String,
    refresh_token: String,
    expires_in: String,
    local_id: String,
}

#[derive(Debug, Deserialize)]
struct RefreshResponse {
    id_token: String,
    refresh_token: String,
    expires_in: String,
    user_id: String,
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
        Ok(AuthTokens {
            id_token: parsed.id_token,
            refresh_token: parsed.refresh_token,
            expires_in: parsed.expires_in.parse().unwrap_or(3600),
            user_id: parsed.local_id,
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
        Ok(AuthTokens {
            id_token: parsed.id_token,
            refresh_token: parsed.refresh_token,
            expires_in: parsed.expires_in.parse().unwrap_or(3600),
            user_id: parsed.user_id,
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
