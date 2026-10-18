//! In-memory / demo parent session. Firebase callables used when a client is injected;
//! otherwise local demo store (UI + unit tests).

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use meritscreen_core::app_config::{
    MAX_CHILDREN_PER_PARENT, PAIRING_CODE_LENGTH, PAIRING_TOKEN_TTL_MINUTES, PARENT_PIN_MAX_LENGTH,
    PARENT_PIN_MIN_LENGTH,
};
use meritscreen_core::{AppError, AppRule, ChildPolicy, FailLockScope, QuizMode, UiState};
use meritscreen_firebase::traits::{AuthClient, AuthTokens, CallableClient};
use meritscreen_firebase::{FirebaseConfig, RestAuthClient, RestCallableClient, RestFirestoreClient};
use crate::remote::{ui_age_band, ParentRemote};
use meritscreen_security::{accounts, hash_parent_pin, verify_parent_pin, SecretStore};
use parking_lot::Mutex;
use rand::Rng;
use serde::{Deserialize, Serialize};
use serde_json::json;
use tracing::info;
use uuid::Uuid;

use crate::models::{
    AppMinutesRollup, ChildCard, ChildDetail, ChildReportsSnapshot, DailyMinutesPoint,
    DashboardSnapshot, DeviceEnforcementStatus, InventoryApp, NotificationPrefs, PairingOffer,
    ParentProfile, QuizWindowStats,
};
use crate::oauth::{apple_sign_in_stub, google_sign_in_stub};

pub const TESTER_PARENT_EMAILS: &[&str] = &[
    "tester1.parent@anajyo.com",
    "tester2.parent@anajyo.com",
    "tester3.parent@anajyo.com",
    "tester4.parent@anajyo.com",
    "tester5.parent@anajyo.com",
];

/// Dev-only shortcuts (fixed OTP codes, tester fallbacks, demo pairing).
/// Enabled in debug builds, or explicitly with `MERITSCREEN_DEV_SHORTCUTS=1`.
/// Release builds never accept fixed codes unless that flag is set by an operator.
pub fn dev_shortcuts_enabled() -> bool {
    cfg!(debug_assertions)
        || std::env::var("MERITSCREEN_DEV_SHORTCUTS")
            .map(|v| matches!(v.as_str(), "1" | "true" | "TRUE" | "yes"))
            .unwrap_or(false)
}

pub fn is_tester_parent_email(email: &str) -> bool {
    let lower = email.trim().to_ascii_lowercase();
    TESTER_PARENT_EMAILS.iter().any(|&e| e == lower)
        || (lower.starts_with("tester") && lower.ends_with("@anajyo.com"))
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChildRecord {
    pub card: ChildCard,
    pub policy: ChildPolicy,
    pub app_rules: Vec<AppRule>,
    pub enforcement: Option<DeviceEnforcementStatus>,
    #[serde(default)]
    pub minutes_by_app: HashMap<String, u32>,
    #[serde(default)]
    pub app_labels: HashMap<String, String>,
    #[serde(default)]
    pub quiz_minutes_today: u32,
    #[serde(default)]
    pub quiz_attempts_today: u32,
    #[serde(default)]
    pub quiz_passes_today: u32,
    #[serde(default)]
    pub daily_usage_history: HashMap<String, HashMap<String, u32>>,
    #[serde(default)]
    pub daily_total_history: HashMap<String, u32>,
}

impl ChildRecord {
    pub fn new(card: ChildCard) -> Self {
        Self {
            card,
            policy: ChildPolicy::default(),
            app_rules: Vec::new(),
            enforcement: None,
            minutes_by_app: HashMap::new(),
            app_labels: HashMap::new(),
            quiz_minutes_today: 0,
            quiz_attempts_today: 0,
            quiz_passes_today: 0,
            daily_usage_history: HashMap::new(),
            daily_total_history: HashMap::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PersistedParentSession {
    pub version: u32,
    pub profile: ParentProfile,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub tokens: Option<AuthTokens>,
    pub children: HashMap<String, ChildRecord>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pin_hash: Option<String>,
    pub notifications: NotificationPrefs,
    pub updated_at_epoch_ms: i64,
}

pub fn default_session_path() -> Option<PathBuf> {
    if let Ok(path) = std::env::var("MERITSCREEN_SESSION_PATH") {
        if !path.trim().is_empty() {
            return Some(PathBuf::from(path));
        }
    }
    if let Ok(dir) = std::env::var("MERITSCREEN_PARENT_SESSION_DIR") {
        if !dir.trim().is_empty() {
            return Some(PathBuf::from(dir).join("parent_session.json"));
        }
    }
    if let Some(p) = directories::ProjectDirs::from("com", "MeritScreen", "MeritScreen") {
        return Some(p.config_dir().join("parent_session.json"));
    }
    if let Ok(home) = std::env::var("HOME").or_else(|_| std::env::var("USERPROFILE")) {
        return Some(PathBuf::from(home).join(".meritscreen").join("parent_session.json"));
    }
    None
}

fn write_secure_session_file(path: &Path, content: &str) -> Result<(), std::io::Error> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = std::fs::set_permissions(parent, std::fs::Permissions::from_mode(0o700));
        }
    }
    #[cfg(unix)]
    {
        use std::io::Write;
        use std::os::unix::fs::OpenOptionsExt;
        let mut file = std::fs::OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .open(path)?;
        file.write_all(content.as_bytes())?;
        file.sync_all()?;
    }
    #[cfg(not(unix))]
    {
        std::fs::write(path, content)?;
    }
    Ok(())
}

#[derive(Clone, Debug)]
struct ActivePairingOffer {
    child_id: String,
    expires_at_epoch_ms: i64,
}

struct Inner {
    profile: Option<ParentProfile>,
    tokens: Option<AuthTokens>,
    children: HashMap<String, ChildRecord>,
    pin_hash: Option<String>,
    notifications: NotificationPrefs,
    /// Demo OTP inbox: email → code (never logged).
    pending_otp: HashMap<String, String>,
    /// Ephemeral active pairing offers: code -> details
    active_pairings: HashMap<String, ActivePairingOffer>,
}

pub struct ParentSession {
    inner: Mutex<Inner>,
    callables: Option<Arc<dyn CallableClient>>,
    auth: Option<Arc<dyn AuthClient>>,
    google_client_id: Option<String>,
    session_path: Option<PathBuf>,
    secret_store: Option<SecretStore>,
    remote: Option<ParentRemote>,
}

impl Default for ParentSession {
    fn default() -> Self {
        Self::demo()
    }
}

impl ParentSession {
    pub fn demo() -> Self {
        Self {
            inner: Mutex::new(Inner {
                profile: None,
                tokens: None,
                children: HashMap::new(),
                pin_hash: None,
                notifications: NotificationPrefs::default(),
                pending_otp: HashMap::new(),
                active_pairings: HashMap::new(),
            }),
            callables: None,
            auth: None,
            google_client_id: std::env::var("MERITSCREEN_GOOGLE_CLIENT_ID").ok(),
            session_path: None,
            secret_store: None,
            remote: None,
        }
    }

    /// Attach the Firestore parent store (live mode).
    pub fn with_remote(mut self, remote: ParentRemote) -> Self {
        self.remote = Some(remote);
        self
    }

    pub fn with_backends(callables: Arc<dyn CallableClient>, auth: Arc<dyn AuthClient>) -> Self {
        let mut s = Self::demo();
        s.callables = Some(callables);
        s.auth = Some(auth);
        s
    }

    pub fn with_session_path(mut self, path: impl Into<PathBuf>) -> Self {
        let path = path.into();
        self.session_path = Some(path);
        self.restore_persisted_session();
        self
    }

    pub fn with_secret_store(mut self, store: SecretStore) -> Self {
        self.secret_store = Some(store);
        self
    }

    /// Demo by default when explicitly requested via `MERITSCREEN_PARENT_DEMO=1`.
    /// Otherwise, uses live Firebase REST backends with default project key or environment override.
    /// In both cases, restores any persisted session from disk across restarts.
    pub fn from_env() -> Self {
        let session_path = default_session_path();
        if std::env::var("MERITSCREEN_PARENT_DEMO")
            .map(|v| matches!(v.as_str(), "1" | "true" | "TRUE" | "yes"))
            .unwrap_or(false)
        {
            info!("parent session: demo backends (MERITSCREEN_PARENT_DEMO)");
            let mut s = Self::demo();
            s.session_path = session_path;
            s.secret_store = Some(SecretStore::platform());
            s.restore_persisted_session();
            return s;
        }
        // Production: key comes from runtime env or the build-time env
        // (MERITSCREEN_FIREBASE_API_KEY, set from CI secrets / local env
        // file). No key is hard-coded; without one we stay in demo mode.
        let Some(config) = FirebaseConfig::from_env_or_build() else {
            let mut s = Self::demo();
            s.session_path = session_path;
            s.secret_store = Some(SecretStore::platform());
            s.restore_persisted_session();
            return s;
        };
        let project_id = config.project_id.clone();
        info!(%project_id, "parent session: live Firebase REST backends");
        let callables: Arc<dyn CallableClient> = Arc::new(RestCallableClient::new(config.clone()));
        let remote = ParentRemote::new(
            Arc::new(RestFirestoreClient::new(config.clone())),
            Arc::clone(&callables),
            project_id.clone(),
        );
        let mut s = Self::with_backends(callables, Arc::new(RestAuthClient::new(config)))
            .with_remote(remote);
        s.session_path = session_path;
        s.secret_store = Some(SecretStore::platform());
        s.restore_persisted_session();
        s
    }

    pub fn restore_persisted_session(&self) {
        let Some(path) = &self.session_path else {
            return;
        };
        if !path.exists() {
            return;
        }
        let raw = match std::fs::read_to_string(path) {
            Ok(s) => s,
            Err(e) => {
                tracing::warn!("Failed to read parent session file {:?}: {e}", path);
                return;
            }
        };
        let persisted: PersistedParentSession = match serde_json::from_str(&raw) {
            Ok(p) => p,
            Err(e) => {
                tracing::warn!("Failed to parse parent session file {:?}: {e}", path);
                return;
            }
        };

        // Also check if PIN hash or Refresh Token was saved in OS SecretStore
        let pin_hash = persisted.pin_hash.or_else(|| {
            self.secret_store
                .as_ref()
                .and_then(|s| s.get(accounts::PARENT_PIN_HASH).ok())
                .map(|p| p.to_string())
        });

        let mut tokens = persisted.tokens;
        if tokens.is_none() {
            if let Some(store) = &self.secret_store {
                if let Ok(refresh) = store.get(accounts::PARENT_REFRESH) {
                    tokens = Some(AuthTokens {
                        id_token: String::new(),
                        refresh_token: refresh.to_string(),
                        expires_in: 3600,
                        user_id: persisted.profile.uid.clone(),
                    });
                }
            }
        }

        info!(
            uid = %persisted.profile.uid,
            email = %persisted.profile.email,
            children_count = persisted.children.len(),
            "Parent session successfully restored from disk"
        );

        let mut g = self.inner.lock();
        g.profile = Some(persisted.profile);
        g.tokens = tokens;
        g.children = persisted.children;
        g.pin_hash = pin_hash;
        g.notifications = persisted.notifications;
    }

    pub fn persist_session(&self) {
        let Some(path) = &self.session_path else {
            return;
        };
        let (persisted, refresh_token_to_save, pin_hash_to_save) = {
            let g = self.inner.lock();
            let Some(profile) = g.profile.clone() else {
                return;
            };
            let refresh = g.tokens.as_ref().map(|t| t.refresh_token.clone());
            let pin = g.pin_hash.clone();
            let p = PersistedParentSession {
                version: 1,
                profile,
                tokens: g.tokens.clone(),
                children: g.children.clone(),
                pin_hash: g.pin_hash.clone(),
                notifications: g.notifications.clone(),
                updated_at_epoch_ms: now_ms(),
            };
            (p, refresh, pin)
        };

        match serde_json::to_string_pretty(&persisted) {
            Ok(json_str) => {
                if let Err(e) = write_secure_session_file(path, &json_str) {
                    tracing::warn!("Failed to write parent session file {:?}: {e}", path);
                } else {
                    tracing::debug!("Persisted parent session to {:?}", path);
                }
            }
            Err(e) => {
                tracing::warn!("Failed to serialize parent session: {e}");
            }
        }

        if let Some(store) = &self.secret_store {
            if let Some(ref_token) = refresh_token_to_save {
                if !ref_token.is_empty() {
                    let _ = store.set(accounts::PARENT_REFRESH, &ref_token);
                }
            }
            if let Some(pin) = pin_hash_to_save {
                let _ = store.set(accounts::PARENT_PIN_HASH, &pin);
            }
        }
    }

    pub fn clear_persisted_session(&self) {
        if let Some(path) = &self.session_path {
            if path.exists() {
                let _ = std::fs::remove_file(path);
                tracing::debug!("Removed parent session file {:?}", path);
            }
        }
        if let Some(store) = &self.secret_store {
            let _ = store.delete(accounts::PARENT_REFRESH);
            let _ = store.delete(accounts::PARENT_PIN_HASH);
        }
    }

    /// True when Firebase callables/auth are configured (not local demo).
    pub fn is_live(&self) -> bool {
        self.callables.is_some() && self.auth.is_some()
    }

    /// Child-device pairing via `consumePairingToken` (same callable Android uses).
    /// Returns credentials; never logs the code.
    pub async fn consume_pairing_remote(
        &self,
        code: &str,
        platform: &str,
        device_id: &str,
    ) -> Result<RemotePairing, AppError> {
        let clean = code.trim().replace([' ', '-'], "");
        if clean.len() != PAIRING_CODE_LENGTH as usize || !clean.chars().all(|c| c.is_ascii_digit()) {
            return Err(AppError::validation("Enter the 6-digit pairing code."));
        }
        let (Some(callables), Some(auth)) = (&self.callables, &self.auth) else {
            return Err(AppError::validation(
                "Pairing service is not configured on this computer.",
            ));
        };
        let result = callables
            .invoke(
                "consumePairingToken",
                json!({ "code": clean, "deviceId": device_id, "platform": platform }),
                None,
            )
            .await
            .map_err(|e| e.to_app_error())?;
        parse_remote_pairing(&result, auth.as_ref()).await
    }

    /// Local (same-computer / demo) policy + rules for a child, if known.
    pub fn local_child_policy(&self, child_id: &str) -> Option<(ChildPolicy, Vec<AppRule>)> {
        let g = self.inner.lock();
        g.children
            .get(child_id)
            .map(|r| (r.policy.clone(), r.app_rules.clone()))
    }

    /// Fetch a child's live policy from Firestore using the *child device's*
    /// refresh token (Android `FirestoreChildRemotePolicyClient` parity).
    /// `Ok(None)` = no live remote, a tester/demo identity, or no policy doc yet.
    pub async fn fetch_child_policy_for_device(
        &self,
        family_id: &str,
        child_id: &str,
        refresh_token: &str,
    ) -> Result<Option<(ChildPolicy, Vec<AppRule>)>, AppError> {
        let (Some(remote), Some(auth)) = (self.remote.clone(), self.auth.clone()) else {
            return Ok(None);
        };
        if refresh_token.trim().is_empty() {
            return Ok(None);
        }
        // Reuse the cached child ID token while fresh (Guardian does the same);
        // only refresh when expired, and keep the rotated tokens.
        let cached = self.secret_store.as_ref().and_then(|st| {
            st.get(accounts::CHILD_ID_TOKEN).ok().map(|t| t.to_string())
        });
        let id_token = match cached.filter(|t| token_is_fresh(t, now_ms())) {
            Some(t) => t,
            None => {
                let tokens = auth
                    .refresh_id_token(refresh_token)
                    .await
                    .map_err(|e| e.to_app_error())?;
                if let Some(st) = &self.secret_store {
                    let _ = st.set(accounts::CHILD_ID_TOKEN, &tokens.id_token);
                    let _ = st.set(accounts::CHILD_REFRESH, &tokens.refresh_token);
                }
                tokens.id_token
            }
        };
        remote.load_child_policy(family_id, child_id, &id_token).await
    }

    pub fn is_signed_in(&self) -> bool {
        self.inner.lock().profile.is_some()
    }

    pub fn dashboard(&self) -> UiState<DashboardSnapshot> {
        let g = self.inner.lock();
        let Some(profile) = g.profile.clone() else {
            return UiState::Error(AppError::auth_msg("Sign in to manage your family."));
        };
        let mut children: Vec<_> = g.children.values().map(|c| c.card.clone()).collect();
        children.sort_by(|a, b| a.display_name.cmp(&b.display_name));
        if children.is_empty() {
            return UiState::Empty;
        }
        UiState::Success(DashboardSnapshot {
            profile,
            children,
            max_children: MAX_CHILDREN_PER_PARENT,
        })
    }

    pub async fn send_email_otp(&self, email: &str) -> Result<(), AppError> {
        let email = normalize_email(email)?;
        info!("send_email_otp requested for {email}");
        if let Some(callables) = &self.callables {
            let token = self.id_token().filter(|t| token_is_fresh(t, now_ms()));
            match callables
                .invoke("sendEmailOtp", json!({ "email": email }), token.as_deref())
                .await
            {
                Ok(_) => return Ok(()),
                Err(e) => {
                    if dev_shortcuts_enabled() && is_tester_parent_email(&email) {
                        tracing::warn!(
                            "sendEmailOtp remote failed ({e:?}), allowing tester email locally"
                        );
                        self.inner.lock().pending_otp.insert(email, "123456".into());
                        return Ok(());
                    } else {
                        return Err(e.to_app_error());
                    }
                }
            }
        }
        // Demo / local mode: fixed code 123456 for tester accounts, or 424242 / 123456
        let code = if is_tester_parent_email(&email) {
            "123456"
        } else {
            "424242"
        }
        .to_string();
        self.inner.lock().pending_otp.insert(email, code);
        Ok(())
    }

    pub async fn verify_email_otp(
        &self,
        email: &str,
        code: &str,
    ) -> Result<ParentProfile, AppError> {
        let email = normalize_email(email)?;
        let code = code.trim().replace(' ', "");
        if code.len() != 6 || !code.chars().all(|c| c.is_ascii_digit()) {
            return Err(AppError::validation("Enter the 6-digit verification code."));
        }

        if let (Some(callables), Some(auth)) = (&self.callables, &self.auth) {
            match callables
                .invoke(
                    "verifyEmailOtp",
                    json!({ "email": email, "code": code }),
                    None,
                )
                .await
            {
                Ok(result) => {
                    let custom = result["customToken"]
                        .as_str()
                        .ok_or_else(|| AppError::auth_msg("Sign-in response missing token."))?;
                    let tokens = auth
                        .sign_in_with_custom_token(custom)
                        .await
                        .map_err(|e| e.to_app_error())?;
                    let profile = ParentProfile {
                        uid: tokens.user_id.clone(),
                        email: email.clone(),
                        family_id: None,
                    };
                    let mut g = self.inner.lock();
                    g.tokens = Some(tokens);
                    g.profile = Some(profile.clone());
                    drop(g);
                    self.persist_session();
                    return Ok(profile);
                }
                Err(e) => {
                    // If remote call failed but it's a known tester account with valid tester code:
                    if dev_shortcuts_enabled() && is_tester_parent_email(&email) && code == "123456" {
                        tracing::warn!(
                            "verifyEmailOtp remote failed ({e:?}), using local tester session"
                        );
                        let profile = ParentProfile {
                            uid: format!("tester-{}", Uuid::new_v4()),
                            email,
                            family_id: None,
                        };
                        let tokens = AuthTokens {
                            id_token: "tester-id-token".into(),
                            refresh_token: "tester-refresh-token".into(),
                            expires_in: 86400,
                            user_id: profile.uid.clone(),
                        };
                        let mut g = self.inner.lock();
                        g.tokens = Some(tokens);
                        g.profile = Some(profile.clone());
                        drop(g);
                        self.persist_session();
                        return Ok(profile);
                    }
                    return Err(e.to_app_error());
                }
            }
        }

        let mut g = self.inner.lock();
        let expected = g.pending_otp.get(&email).cloned();
        let dev = dev_shortcuts_enabled();
        let fixed = dev && (code == "123456" || code == "424242");
        let is_valid = match expected.as_deref() {
            Some(exp) => exp == code || fixed,
            None => fixed,
        };
        if !is_valid {
            return Err(AppError::validation(
                "That verification code is incorrect. Check your email and try again.",
            ));
        }
        g.pending_otp.remove(&email);
        let profile = ParentProfile {
            uid: format!("demo-{}", Uuid::new_v4()),
            email,
            family_id: None,
        };
        let tokens = AuthTokens {
            id_token: "demo-id-token".into(),
            refresh_token: "demo-refresh-token".into(),
            expires_in: 86400,
            user_id: profile.uid.clone(),
        };
        g.tokens = Some(tokens);
        g.profile = Some(profile.clone());
        drop(g);
        self.persist_session();
        Ok(profile)
    }

    pub fn google_sign_in_url(&self) -> Result<String, AppError> {
        google_sign_in_stub(self.google_client_id.as_deref())
    }

    pub fn apple_sign_in(&self) -> Result<String, AppError> {
        apple_sign_in_stub()
    }

    pub fn commit_onboarding(
        &self,
        child_name: &str,
        age_band: &str,
        pin: &str,
    ) -> Result<ChildCard, AppError> {
        self.require_signed_in()?;
        validate_pin(pin)?;
        let name = child_name.trim();
        if name.is_empty() {
            return Err(AppError::validation("Enter your child’s name."));
        }
        let mut g = self.inner.lock();
        if g.children.len() as u32 >= MAX_CHILDREN_PER_PARENT {
            return Err(AppError::validation(format!(
                "You can add up to {MAX_CHILDREN_PER_PARENT} children."
            )));
        }
        let family_id = g
            .profile
            .as_ref()
            .and_then(|p| p.family_id.clone())
            .unwrap_or_else(|| format!("fam-{}", Uuid::new_v4()));
        if let Some(p) = g.profile.as_mut() {
            p.family_id = Some(family_id);
        }
        g.pin_hash = Some(hash_parent_pin(pin));
        let child_id = format!("child-{}", Uuid::new_v4());
        let card = ChildCard {
            child_id: child_id.clone(),
            display_name: name.into(),
            age_band: age_band.trim().to_string(),
            minutes_used_today: 0,
            minutes_remaining_today: None,
            paired_device_count: 0,
            platform_hint: None,
            quiz_mode: QuizMode::AppBlock,
        };
        g.children.insert(
            child_id,
            ChildRecord::new(card.clone()),
        );
        drop(g);
        self.persist_session();
        Ok(card)
    }

    pub fn child_detail(&self, child_id: &str) -> Result<ChildDetail, AppError> {
        self.require_signed_in()?;
        let g = self.inner.lock();
        let rec = g
            .children
            .get(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;
        Ok(ChildDetail {
            card: rec.card.clone(),
            policy: rec.policy.clone(),
            app_rules: rec.app_rules.clone(),
            fail_lock_scope: FailLockScope::AllNonEmergency,
            enforcement: rec.enforcement.clone(),
        })
    }

    pub fn update_policy(&self, child_id: &str, mut policy: ChildPolicy) -> Result<(), AppError> {
        self.require_signed_in()?;
        let _ = FailLockScope::AllNonEmergency;
        let mut g = self.inner.lock();
        let rec = g
            .children
            .get_mut(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;
        if policy.quiz_mode == QuizMode::DeviceInterval {
            policy.quiz_interval_minutes =
                meritscreen_core::app_config::coerce_chunk_minutes(policy.quiz_interval_minutes);
        }
        rec.card.quiz_mode = policy.quiz_mode;
        // Bonus time only changes through `grant_bonus`; a settings save made from an older
        // snapshot must not overwrite (or forge) today's grant.
        policy.bonus_minutes_today = rec.policy.bonus_minutes_today;
        policy.bonus_day = rec.policy.bonus_day.clone();
        rec.policy = policy;
        drop(g);
        self.persist_session();
        Ok(())
    }

    /// Grant one-off bonus minutes for today (child-local midnight reset). Never changes the
    /// permanent daily limit. Returns the updated policy.
    pub fn grant_bonus(&self, child_id: &str, minutes: u32) -> Result<ChildPolicy, AppError> {
        self.require_signed_in()?;
        if minutes == 0 || minutes > meritscreen_core::app_config::EMERGENCY_UNLOCK_MINUTES {
            return Err(AppError::validation("Choose a bonus between 1 and 60 minutes."));
        }
        let today = meritscreen_core::session::local_today_key();
        let mut g = self.inner.lock();
        let rec = g
            .children
            .get_mut(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;
        rec.policy
            .grant_bonus(minutes, &today)
            .map_err(AppError::validation)?;
        let out = rec.policy.clone();
        drop(g);
        self.persist_session();
        Ok(out)
    }

    pub fn add_child(&self, child_name: &str, age_band: &str) -> Result<ChildCard, AppError> {
        self.require_signed_in()?;
        let name = child_name.trim();
        if name.is_empty() {
            return Err(AppError::validation("Enter your child’s name."));
        }
        let mut g = self.inner.lock();
        if g.children.len() as u32 >= MAX_CHILDREN_PER_PARENT {
            return Err(AppError::validation(format!(
                "You can add up to {MAX_CHILDREN_PER_PARENT} children."
            )));
        }
        let child_id = format!("child-{}", Uuid::new_v4());
        let card = ChildCard {
            child_id: child_id.clone(),
            display_name: name.into(),
            age_band: age_band.trim().to_string(),
            minutes_used_today: 0,
            minutes_remaining_today: None,
            paired_device_count: 0,
            platform_hint: None,
            quiz_mode: QuizMode::AppBlock,
        };
        g.children.insert(
            child_id,
            ChildRecord::new(card.clone()),
        );
        drop(g);
        self.persist_session();
        Ok(card)
    }

    pub fn remove_child(&self, child_id: &str) -> Result<(), AppError> {
        self.require_signed_in()?;
        let mut g = self.inner.lock();
        g.active_pairings.retain(|_, v| v.child_id != child_id);
        let removed = g.children.remove(child_id);
        drop(g);
        if removed.is_some() {
            self.persist_session();
            Ok(())
        } else {
            Err(AppError::not_found_msg("Child not found."))
        }
    }

    pub fn update_app_rules(&self, child_id: &str, rules: Vec<AppRule>) -> Result<(), AppError> {
        self.require_signed_in()?;
        let mut g = self.inner.lock();
        let rec = g
            .children
            .get_mut(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;
        if let Some(enf) = &mut rec.enforcement {
            for app in &mut enf.installed_apps {
                if let Some(r) = rules.iter().find(|r| r.app_id == app.app_id) {
                    app.allowed = r.allowed;
                }
            }
        }
        rec.app_rules = rules;
        drop(g);
        self.persist_session();
        Ok(())
    }

    pub async fn create_pairing(&self, child_id: &str) -> Result<PairingOffer, AppError> {
        self.require_signed_in()?;
        if self.inner.lock().pin_hash.as_ref().map(|h| h.is_empty()).unwrap_or(true) {
            return Err(AppError::validation(
                "Set a parent PIN first (Account → Parent PIN). Children need it to unlock settings.",
            ));
        }
        {
            let g = self.inner.lock();
            if !g.children.contains_key(child_id) {
                return Err(AppError::not_found_msg("Child not found."));
            }
        }

        if let Some(callables) = &self.callables {
            let token = self.ensure_id_token().await;
            if let Some(tok) = token {
                let call_res = callables
                    .invoke(
                        "createPairingToken",
                        json!({ "childId": child_id }),
                        Some(&tok),
                    )
                    .await;

                let call_res = match call_res {
                    Ok(res) => Ok(res),
                    Err(e) => {
                        // Token rejected: refresh once and retry.
                        if is_auth_rejection(&e) {
                            match self.refresh_id_token().await {
                                Ok(new_id) => {
                                    callables
                                        .invoke("createPairingToken", json!({ "childId": child_id }), Some(&new_id))
                                        .await
                                }
                                Err(_) => Err(e),
                            }
                        } else {
                            Err(e)
                        }
                    }
                };

                match call_res {
                    Ok(result) => {
                        let offer = PairingOffer {
                            child_id: child_id.into(),
                            code: result["code"].as_str().unwrap_or("000000").into(),
                            secret: result["secret"].as_str().unwrap_or("").into(),
                            qr_payload: result["qrPayload"].as_str().unwrap_or("").into(),
                            expires_at_epoch_ms: result["expiresAtEpochMs"].as_i64().unwrap_or(0),
                        };
                        {
                            let mut g = self.inner.lock();
                            g.active_pairings.insert(
                                offer.code.clone(),
                                ActivePairingOffer {
                                    child_id: child_id.into(),
                                    expires_at_epoch_ms: offer.expires_at_epoch_ms,
                                },
                            );
                        }
                        info!(
                            child_id,
                            code = %offer.code,
                            "Remote pairing offer created via Firebase"
                        );
                        return Ok(offer);
                    }
                    Err(err) => {
                        tracing::warn!(
                            child_id,
                            "Remote createPairingToken returned error ({err:?}); falling back to secure local pairing offer"
                        );
                    }
                }
            } else {
                tracing::warn!(
                    child_id,
                    "No Firebase ID token available for parent session; generating secure local pairing offer"
                );
            }
        }

        let code = random_digits(PAIRING_CODE_LENGTH as usize);
        let secret = hex::encode(Uuid::new_v4().as_bytes());
        let expires_at_epoch_ms = now_ms() + (PAIRING_TOKEN_TTL_MINUTES as i64) * 60_000;
        let offer = PairingOffer {
            child_id: child_id.into(),
            qr_payload: format!("meritscreen://pair?c={code}&s={secret}"),
            code: code.clone(),
            secret,
            expires_at_epoch_ms,
        };
        {
            let mut g = self.inner.lock();
            g.active_pairings.insert(
                code,
                ActivePairingOffer {
                    child_id: child_id.into(),
                    expires_at_epoch_ms,
                },
            );
        }
        info!(
            child_id,
            code_len = PAIRING_CODE_LENGTH,
            "pairing offer created (local fallback)"
        );
        Ok(offer)
    }

    pub fn consume_pairing(&self, code: &str, platform: &str) -> Result<String, AppError> {
        let clean_code = code.trim().replace([' ', '-'], "");
        let now = now_ms();
        let target_child_id = {
            let mut g = self.inner.lock();
            // Prune expired
            g.active_pairings.retain(|_, v| v.expires_at_epoch_ms > now);

            if let Some(pairing) = g.active_pairings.remove(&clean_code) {
                Some(pairing.child_id)
            } else if clean_code.len() == PAIRING_CODE_LENGTH as usize
                && clean_code.chars().all(|c| c.is_ascii_digit())
            {
                // In demo/offline mode:
                // 1. First prefer any child with 0 paired devices
                let unpaired_id = g.children.iter().find_map(|(id, rec)| {
                    if rec.card.paired_device_count == 0 {
                        Some(id.clone())
                    } else {
                        None
                    }
                });
                // 2. Otherwise pick the first child
                unpaired_id.or_else(|| g.children.keys().next().cloned())
            } else {
                None
            }
        };

        if let Some(child_id) = target_child_id {
            self.mark_device_paired(&child_id, platform)?;
            return Ok(child_id);
        }

        Err(AppError::validation("Invalid or expired pairing code."))
    }

    pub fn mark_device_paired(&self, child_id: &str, platform: &str) -> Result<(), AppError> {
        let mut g = self.inner.lock();
        let rec = g
            .children
            .get_mut(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;
        rec.card.paired_device_count = 1;
        rec.card.platform_hint = Some(platform.to_string());
        if meritscreen_core::DevicePlatform::parse(platform)
            .map(|p| p.is_desktop())
            .unwrap_or(false)
        {
            rec.policy.quiz_mode = QuizMode::DeviceInterval;
            rec.card.quiz_mode = QuizMode::DeviceInterval;
            // Demo inventory so P13 allowlist has desktop appIds before live sync (d8).
            let inventory = demo_desktop_inventory(platform);
            rec.app_rules = inventory
                .iter()
                .map(|app| {
                    let mut r = AppRule::new(&app.app_id, &app.app_id);
                    r.display_name = app.label.clone();
                    r.allowed = app.allowed;
                    r
                })
                .collect();
            rec.enforcement = Some(DeviceEnforcementStatus {
                guardian_state: "running".into(),
                enforcement_tier: "L1_L3".into(),
                tamper_flags: Vec::new(),
                degraded_message: degraded_for_platform(platform),
                strict_available: platform == "windows"
                    && degraded_for_platform(platform).is_none(),
                installed_apps: inventory,
            });
        }
        drop(g);
        self.persist_session();
        Ok(())
    }

    pub fn unpair_device(&self, child_id: &str) -> Result<(), AppError> {
        let mut g = self.inner.lock();
        let rec = g
            .children
            .get_mut(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;
        rec.card.paired_device_count = 0;
        rec.card.platform_hint = None;
        rec.enforcement = None;
        g.active_pairings.retain(|_, v| v.child_id != child_id);
        drop(g);
        self.persist_session();
        Ok(())
    }

    pub fn reset_pin(&self, new_pin: &str) -> Result<(), AppError> {
        self.require_signed_in()?;
        validate_pin(new_pin)?;
        self.inner.lock().pin_hash = Some(hash_parent_pin(new_pin));
        self.persist_session();
        Ok(())
    }

    pub fn pin_hash(&self) -> Option<String> {
        self.inner.lock().pin_hash.clone()
    }

    pub fn verify_pin(&self, pin: &str) -> bool {
        if let Some(ref hash) = self.inner.lock().pin_hash {
            verify_parent_pin(pin, hash)
        } else {
            false
        }
    }

    pub fn notification_prefs(&self) -> NotificationPrefs {
        self.inner.lock().notifications.clone()
    }

    pub fn set_notification_prefs(&self, prefs: NotificationPrefs) {
        self.inner.lock().notifications = prefs;
        self.persist_session();
    }

    pub fn delete_family(&self) -> Result<(), AppError> {
        self.require_signed_in()?;
        {
            let mut g = self.inner.lock();
            g.children.clear();
            g.pin_hash = None;
            if let Some(p) = g.profile.as_mut() {
                p.family_id = None;
            }
        }
        if let Some(store) = &self.secret_store {
            let _ = store.delete(accounts::PARENT_PIN_HASH);
        }
        self.persist_session();
        Ok(())
    }

    pub fn sign_out(&self) {
        {
            let mut g = self.inner.lock();
            g.profile = None;
            g.tokens = None;
            g.children.clear();
            g.pin_hash = None;
            g.pending_otp.clear();
        }
        self.clear_persisted_session();
    }

    pub fn profile(&self) -> Option<ParentProfile> {
        self.inner.lock().profile.clone()
    }

    fn require_signed_in(&self) -> Result<(), AppError> {
        if self.is_signed_in() {
            Ok(())
        } else {
            Err(AppError::auth_msg("Sign in to continue."))
        }
    }

    /// A usable ID token, or `None`. See [`Self::fresh_id_token`].
    pub async fn ensure_id_token(&self) -> Option<String> {
        self.fresh_id_token().await.ok()
    }

    /// Return the cached ID token if it is not expired (with a small skew), otherwise
    /// refresh it with the stored refresh token. Firebase ID tokens live one hour, and a
    /// persisted session can outlive that by days, so expiry must be checked every time.
    pub async fn fresh_id_token(&self) -> Result<String, AppError> {
        let current = self.id_token();
        if let Some(tok) = current.filter(|t| token_is_fresh(t, now_ms())) {
            return Ok(tok);
        }
        self.refresh_id_token().await
    }

    /// Force a token refresh (used after a 401 from Firestore / a callable).
    /// Network failures stay network errors; only a rejected / missing refresh token
    /// becomes "session expired".
    pub async fn refresh_id_token(&self) -> Result<String, AppError> {
        let refresh = self
            .inner
            .lock()
            .tokens
            .as_ref()
            .map(|t| t.refresh_token.clone())
            .filter(|s| !s.trim().is_empty());
        let (Some(refresh), Some(auth)) = (refresh, self.auth.clone()) else {
            return Err(AppError::auth_msg(SESSION_EXPIRED));
        };
        match auth.refresh_id_token(&refresh).await {
            Ok(new_tokens) => {
                let id_token = new_tokens.id_token.clone();
                self.inner.lock().tokens = Some(new_tokens);
                self.persist_session();
                info!("parent ID token refreshed");
                Ok(id_token)
            }
            Err(e) => {
                tracing::warn!("Failed to refresh parent session token: {e:?}");
                Err(refresh_failure(&e))
            }
        }
    }

    fn id_token(&self) -> Option<String> {
        self.inner
            .lock()
            .tokens
            .as_ref()
            .map(|t| t.id_token.clone())
            .filter(|s| !s.trim().is_empty())
    }

    pub fn record_child_telemetry(
        &self,
        child_id: &str,
        child_name: &str,
        minutes_used_today: u32,
        minutes_by_app: HashMap<String, u32>,
        app_labels: HashMap<String, String>,
        quiz_minutes_today: u32,
        quiz_attempts_today: u32,
        quiz_passes_today: u32,
        daily_usage_history: HashMap<String, HashMap<String, u32>>,
        daily_total_history: HashMap<String, u32>,
    ) {
        let mut g = self.inner.lock();
        if let Some(child) = g.children.get_mut(child_id) {
            child.card.minutes_used_today = minutes_used_today;
            child.minutes_by_app = minutes_by_app;
            child.app_labels = app_labels;
            child.quiz_minutes_today = quiz_minutes_today;
            child.quiz_attempts_today = quiz_attempts_today;
            child.quiz_passes_today = quiz_passes_today;
            child.daily_usage_history = daily_usage_history;
            child.daily_total_history = daily_total_history;
        } else {
            let card = ChildCard {
                child_id: child_id.to_string(),
                display_name: if child_name.is_empty() {
                    "Child".into()
                } else {
                    child_name.to_string()
                },
                age_band: "elementary".into(),
                minutes_used_today,
                minutes_remaining_today: None,
                paired_device_count: 1,
                platform_hint: Some("desktop".into()),
                quiz_mode: QuizMode::DeviceInterval,
            };
            let rec = ChildRecord {
                card,
                policy: ChildPolicy::default(),
                app_rules: Vec::new(),
                enforcement: None,
                minutes_by_app,
                app_labels,
                quiz_minutes_today,
                quiz_attempts_today,
                quiz_passes_today,
                daily_usage_history,
                daily_total_history,
            };
            g.children.insert(child_id.to_string(), rec);
        }
        drop(g);
        self.persist_session();
    }

    /// Dashboard analytics for every child. Reads Firestore (usageDays, quizAttempts,
    /// skillState, stickerUnlocks, explorerProgress, devices) when signed in to a real
    /// family; otherwise — or per child on failure — falls back to this computer's data.
    pub async fn family_insights(&self) -> Result<crate::analytics::FamilyInsights, AppError> {
        use crate::analytics::*;
        if self.profile().is_none() {
            return Err(AppError::auth_msg("Sign in to manage your family."));
        }
        let mut records: Vec<ChildRecord> = self.inner.lock().children.values().cloned().collect();
        records.sort_by(|a, b| a.card.display_name.to_lowercase().cmp(&b.card.display_name.to_lowercase()));
        let mut ctx = self.remote_ctx().await.ok().flatten();
        let mut refreshed = false;
        let family_id = ctx.as_ref().and_then(|c| c.family_id.clone());
        let today = chrono::Local::now().date_naive();
        let now_ms = chrono::Utc::now().timestamp_millis();
        let mut children = Vec::with_capacity(records.len());
        let mut any_cloud = false;
        for rec in &records {
            let local = local_telemetry(rec);
            let (telemetry, error) = match (&mut ctx, &family_id) {
                (Some(c), Some(f)) => match self.load_telemetry_retrying(c, f, &rec.card.child_id, &mut refreshed).await {
                    Ok(mut t) => {
                        any_cloud = true;
                        t.quiz_today_counts = local.quiz_today_counts;
                        (t, None)
                    }
                    Err(e) => (local, Some(e.user_message().to_string())),
                },
                _ => (local, None),
            };
            let mut rules = rec.app_rules.clone();
            for (id, label) in &rec.app_labels {
                let mut r = AppRule::new(id.clone(), id.clone());
                r.display_name = label.clone();
                rules.push(r);
            }
            let cctx = ChildContext {
                child_id: &rec.card.child_id,
                display_name: &rec.card.display_name,
                age_band: &rec.card.age_band,
                policy: &rec.policy,
                rules: &rules,
                paired_device_count: rec.card.paired_device_count,
                local_minutes_today: rec.card.minutes_used_today,
            };
            let mut ins = summarize(&cctx, &telemetry, today, now_ms);
            if let Some(enf) = &rec.enforcement {
                for flag in &enf.tamper_flags {
                    ins.alerts.insert(0, Alert {
                        severity: "critical".into(),
                        kind: "tamper".into(),
                        message: format!("{}: protection issue detected ({}).", rec.card.display_name, flag.replace('_', " ")),
                    });
                }
            }
            ins.error = error;
            children.push(ins);
        }
        Ok(FamilyInsights { source: if any_cloud { "cloud".into() } else { "local".into() }, generated_at_ms: now_ms, children })
    }

    async fn load_telemetry_retrying(
        &self,
        ctx: &mut RemoteCtx,
        family_id: &str,
        child_id: &str,
        refreshed: &mut bool,
    ) -> Result<crate::analytics::ChildTelemetry, AppError> {
        match ctx.remote.load_child_telemetry(family_id, child_id, &ctx.token).await {
            Err(AppError::Auth(_)) if !*refreshed => {
                *refreshed = true;
                ctx.token = self.refresh_id_token().await?;
                ctx.remote.load_child_telemetry(family_id, child_id, &ctx.token).await
            }
            other => other,
        }
    }

    pub fn child_reports(&self, child_id: &str) -> Result<ChildReportsSnapshot, AppError> {
        let g = self.inner.lock();
        let child = g
            .children
            .get(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;

        let mut top_apps: Vec<AppMinutesRollup> = Vec::new();
        let total_app_mins: u32 = child.minutes_by_app.values().copied().sum();
        for (app_id, mins) in &child.minutes_by_app {
            let label = child
                .app_labels
                .get(app_id)
                .cloned()
                .unwrap_or_else(|| app_id.clone());
            let pct = if total_app_mins > 0 {
                ((*mins as f64 / total_app_mins as f64) * 100.0).round() as u32
            } else {
                0
            };
            top_apps.push(AppMinutesRollup {
                app_id: app_id.clone(),
                display_name: label,
                minutes: *mins,
                percentage: pct,
            });
        }
        top_apps.sort_by(|a, b| b.minutes.cmp(&a.minutes));

        let today = chrono::Local::now().date_naive();
        let mut daily_trend: Vec<DailyMinutesPoint> = Vec::new();
        let mut weekly_total_minutes = 0u32;
        let mut weekly_quiz_minutes = 0u32;
        let mut active_days_count = 0u32;

        for days_ago in (0..7).rev() {
            if let Some(date) = today.checked_sub_signed(chrono::Duration::days(days_ago)) {
                let date_str = date.format("%Y-%m-%d").to_string();
                let (tot_m, quiz_m) = if days_ago == 0 {
                    (child.card.minutes_used_today, child.quiz_minutes_today)
                } else {
                    let m = child.daily_total_history.get(&date_str).copied().unwrap_or(0);
                    (m, 0)
                };
                weekly_total_minutes = weekly_total_minutes.saturating_add(tot_m);
                weekly_quiz_minutes = weekly_quiz_minutes.saturating_add(quiz_m);
                if tot_m > 0 {
                    active_days_count += 1;
                }
                daily_trend.push(DailyMinutesPoint {
                    date: date_str,
                    total_minutes: tot_m,
                    quiz_minutes: quiz_m,
                });
            }
        }

        let daily_average_minutes = if active_days_count > 0 {
            weekly_total_minutes / active_days_count
        } else {
            weekly_total_minutes / 7
        };

        let pass_rate_percent = if child.quiz_attempts_today > 0 {
            ((child.quiz_passes_today as f64 / child.quiz_attempts_today as f64) * 100.0).round()
                as u32
        } else {
            0
        };

        let quiz_stats = QuizWindowStats {
            total_attempts: child.quiz_attempts_today,
            passes: child.quiz_passes_today,
            pass_rate_percent,
            total_quiz_minutes: child.quiz_minutes_today,
        };

        let sync_status = if g.tokens.is_some() {
            "Synced with Firebase Family Cloud".to_string()
        } else {
            "Local Device Active Monitoring".to_string()
        };

        Ok(ChildReportsSnapshot {
            child_id: child_id.to_string(),
            child_name: child.card.display_name.clone(),
            today_minutes: child.card.minutes_used_today,
            today_quiz_minutes: child.quiz_minutes_today,
            weekly_total_minutes,
            weekly_quiz_minutes,
            daily_average_minutes,
            top_apps,
            daily_trend,
            quiz_stats,
            sync_status,
        })
    }

    pub async fn sync_child_usage_to_firebase(
        &self,
        child_id: &str,
        fb_config: &FirebaseConfig,
    ) -> Result<(), AppError> {
        let (family_id, tokens, minutes_used, minutes_by_app, quiz_minutes) = {
            let g = self.inner.lock();
            let family_id = g
                .profile
                .as_ref()
                .and_then(|p| p.family_id.clone())
                .ok_or_else(|| AppError::validation("No family ID"))?;
            let tokens = g
                .tokens
                .clone()
                .ok_or_else(|| AppError::auth_msg("Unauthenticated"))?;
            let child = g
                .children
                .get(child_id)
                .ok_or_else(|| AppError::not_found_msg("Child not found"))?;
            (
                family_id,
                tokens,
                child.card.minutes_used_today,
                child.minutes_by_app.clone(),
                child.quiz_minutes_today,
            )
        };

        let today = chrono::Local::now().date_naive().format("%Y-%m-%d").to_string();
        let mut minutes_by_app_map = serde_json::Map::new();
        for (k, v) in minutes_by_app {
            minutes_by_app_map.insert(k, serde_json::json!(v));
        }
        let upload = meritscreen_firebase::UsageDayUpload {
            day: today,
            minutes_used,
            minutes_by_app: minutes_by_app_map,
            quiz_minutes,
        };
        let ids = meritscreen_firebase::DeviceIds {
            family_id,
            child_id: child_id.to_string(),
            device_id: "desktop-primary".to_string(),
        };
        let client = meritscreen_firebase::create_rest_child_remote_client(fb_config);
        use meritscreen_firebase::ChildRemoteClient;
        client
            .upload_usage_day(&ids, &upload, &tokens.id_token)
            .await
            .map_err(|_| AppError::network())?;

        Ok(())
    }
}

fn normalize_email(email: &str) -> Result<String, AppError> {
    let e = email.trim().to_lowercase();
    if !e.contains('@') || e.len() < 5 {
        return Err(AppError::validation("Enter a valid email address."));
    }
    Ok(e)
}

fn validate_pin(pin: &str) -> Result<(), AppError> {
    let min = PARENT_PIN_MIN_LENGTH as usize;
    let max = PARENT_PIN_MAX_LENGTH as usize;
    if pin.len() < min || pin.len() > max || !pin.chars().all(|c| c.is_ascii_digit()) {
        return Err(AppError::validation("PIN must be exactly 4 digits."));
    }
    Ok(())
}

fn random_digits(len: usize) -> String {
    let mut rng = rand::thread_rng();
    (0..len)
        .map(|_| char::from(b'0' + rng.gen_range(0..10)))
        .collect()
}

/// Shown (and matched by the UI to redirect to sign-in) only when the refresh token is
/// missing or rejected.
pub const SESSION_EXPIRED: &str = "Your session expired. Please sign in again.";
/// See [`meritscreen_firebase::token::token_is_fresh`].
fn token_is_fresh(token: &str, now: i64) -> bool {
    meritscreen_firebase::token::token_is_fresh(token, now)
}

fn is_auth_rejection(e: &meritscreen_firebase::FirebaseError) -> bool {
    matches!(e, meritscreen_firebase::FirebaseError::Http { status: 401 | 403, .. } | meritscreen_firebase::FirebaseError::Auth(_))
}

/// Map a failed refresh: network stays retryable, anything the server rejected means
/// the session is really gone.
fn refresh_failure(e: &meritscreen_firebase::FirebaseError) -> AppError {
    use meritscreen_firebase::FirebaseError as F;
    match e {
        F::Network(_) => AppError::network(),
        F::Http { status, .. } if *status >= 500 => AppError::network(),
        _ => AppError::auth_msg(SESSION_EXPIRED),
    }
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn demo_desktop_inventory(platform: &str) -> Vec<InventoryApp> {
    match platform {
        "windows" => vec![
            InventoryApp {
                app_id: "win:Microsoft.WindowsCalculator_8wekyb3d8bbwe!App".into(),
                label: "Calculator".into(),
                allowed: true,
            },
            InventoryApp {
                app_id: "win:Microsoft.MicrosoftEdge_8wekyb3d8bbwe!App".into(),
                label: "Edge".into(),
                allowed: true,
            },
            InventoryApp {
                app_id: "win:spotify.exe".into(),
                label: "Spotify".into(),
                allowed: false,
            },
        ],
        "linux" => vec![
            InventoryApp {
                app_id: "linux:org.mozilla.firefox.desktop".into(),
                label: "Firefox".into(),
                allowed: true,
            },
            InventoryApp {
                app_id: "linux:org.gnome.Calculator.desktop".into(),
                label: "Calculator".into(),
                allowed: true,
            },
            InventoryApp {
                app_id: "linux:spotify.desktop".into(),
                label: "Spotify".into(),
                allowed: false,
            },
        ],
        _ => vec![
            InventoryApp {
                app_id: "mac:com.apple.Safari".into(),
                label: "Safari".into(),
                allowed: true,
            },
            InventoryApp {
                app_id: "mac:com.apple.Notes".into(),
                label: "Notes".into(),
                allowed: true,
            },
            InventoryApp {
                app_id: "mac:com.spotify.client".into(),
                label: "Spotify".into(),
                allowed: false,
            },
        ],
    }
}

fn degraded_for_platform(platform: &str) -> Option<String> {
    match platform {
        "macos" => Some(
            "Strict shell mode is not available on macOS. Launcher + app gating (L1+L3) still apply."
                .into(),
        ),
        "linux" => Some("Linux enforcement is best-effort (beta).".into()),
        "windows" => None, // demo assumes Pro+; Home copy comes from live Strict probe
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::show_device_interval;

    #[tokio::test]
    async fn otp_onboarding_pairing_policy_flow() {
        let s = ParentSession::demo();
        s.send_email_otp("Parent@Example.com").await.unwrap();
        let profile = s
            .verify_email_otp("parent@example.com", "424242")
            .await
            .unwrap();
        assert!(profile.email.contains('@'));

        let child = s.commit_onboarding("Alex", "7_to_9", "1234").unwrap();
        match s.dashboard() {
            UiState::Success(d) => {
                assert_eq!(d.children.len(), 1);
                assert_eq!(d.children[0].minutes_used_today, 0);
            }
            other => panic!("expected success, got {other:?}"),
        }

        let offer = s.create_pairing(&child.child_id).await.unwrap();
        assert_eq!(offer.code.len(), 6);
        let paired_child_id = s.consume_pairing(&offer.code, "macos").unwrap();
        assert_eq!(paired_child_id, child.child_id);
        let detail = s.child_detail(&child.child_id).unwrap();
        assert_eq!(detail.card.paired_device_count, 1);
        assert_eq!(detail.fail_lock_scope, FailLockScope::AllNonEmergency);
        assert_eq!(detail.policy.quiz_mode, QuizMode::DeviceInterval);
        assert!(show_device_interval(detail.card.platform_hint.as_deref()));
        let enf = detail.enforcement.expect("desktop enforcement status");
        assert_eq!(enf.enforcement_tier, "L1_L3");
        assert!(!enf.installed_apps.is_empty());
        assert!(enf.degraded_message.is_some());

        let mut policy = detail.policy;
        policy.quiz_interval_minutes = 30;
        s.update_policy(&child.child_id, policy).unwrap();
        s.delete_family().unwrap();
        assert!(matches!(s.dashboard(), UiState::Empty));
    }

    #[test]
    fn child_cap_enforced() {
        let s = ParentSession::demo();
        {
            let mut g = s.inner.lock();
            g.profile = Some(ParentProfile {
                uid: "u".into(),
                email: "a@b.co".into(),
                family_id: Some("f".into()),
            });
        }
        for i in 0..MAX_CHILDREN_PER_PARENT {
            s.commit_onboarding(&format!("C{i}"), "7_to_9", "1234")
                .unwrap();
        }
        let err = s.commit_onboarding("Extra", "7_to_9", "1234").unwrap_err();
        assert!(err.user_message().contains('5'));
    }

    #[tokio::test]
    async fn bonus_is_today_only_and_survives_settings_save() {
        let s = ParentSession::demo();
        s.verify_email_otp("test@example.com", "424242").await.unwrap();
        let c = s.commit_onboarding("Mia", "7_to_9", "1234").unwrap();
        let id = c.child_id.clone();
        let base = s.child_detail(&id).unwrap().policy;
        let limit = base.daily_ceiling_minutes;

        let p = s.grant_bonus_synced(&id, 15).await.unwrap();
        let p = { let _ = p; s.grant_bonus_synced(&id, 30).await.unwrap() };
        assert_eq!(p.bonus_minutes_today, 45);
        assert_eq!(p.bonus_day.as_deref(), Some(meritscreen_core::session::local_today_key().as_str()));
        assert_eq!(p.daily_ceiling_minutes, limit, "permanent limit unchanged");

        // A settings save built from the pre-bonus snapshot keeps the grant.
        s.update_policy(&id, ChildPolicy { paused: true, ..base }).unwrap();
        let after = s.child_detail(&id).unwrap().policy;
        assert!(after.paused);
        assert_eq!(after.bonus_minutes_today, 45);

        assert!(s.grant_bonus(&id, 0).is_err());
        assert!(s.grant_bonus(&id, 61).is_err());
        let v = crate::remote::policy_to_value(&after);
        assert_eq!(v["bonusMinutesToday"], 45);
        assert!(v["bonusDay"].is_string());
    }

    #[tokio::test]
    async fn multi_child_and_remove_flow() {
        let s = ParentSession::demo();
        s.verify_email_otp("test@example.com", "424242")
            .await
            .unwrap();

        let child1 = s.commit_onboarding("Child One", "7_to_9", "1234").unwrap();
        let child2 = s.add_child("Child Two", "10_to_12").unwrap();

        match s.dashboard() {
            UiState::Success(d) => {
                assert_eq!(d.children.len(), 2);
            }
            other => panic!("expected 2 children, got {other:?}"),
        }

        s.remove_child(&child1.child_id).unwrap();

        match s.dashboard() {
            UiState::Success(d) => {
                assert_eq!(d.children.len(), 1);
                assert_eq!(d.children[0].child_id, child2.child_id);
            }
            other => panic!("expected 1 child, got {other:?}"),
        }

        // Removing non-existent child returns NotFound
        let err = s.remove_child("non-existent").unwrap_err();
        assert!(err.user_message().contains("not found"));
    }

    #[tokio::test]
    async fn app_rules_sync_with_inventory() {
        let s = ParentSession::demo();
        s.verify_email_otp("parent@test.com", "424242")
            .await
            .unwrap();
        let child = s.commit_onboarding("Sam", "7_to_9", "5678").unwrap();

        s.mark_device_paired(&child.child_id, "macos").unwrap();
        let detail = s.child_detail(&child.child_id).unwrap();
        let enf = detail.enforcement.expect("enforcement present");

        // Safari should initially be allowed
        let safari_app = enf
            .installed_apps
            .iter()
            .find(|a| a.app_id.contains("Safari"))
            .unwrap();
        assert!(safari_app.allowed);

        // Update rules to block Safari
        let mut updated_rules = detail.app_rules.clone();
        for r in &mut updated_rules {
            if r.app_id.contains("Safari") {
                r.allowed = false;
            }
        }
        s.update_app_rules(&child.child_id, updated_rules).unwrap();

        // Verify that child_detail reflects the blocked Safari in both app_rules and installed_apps
        let updated_detail = s.child_detail(&child.child_id).unwrap();
        let updated_enf = updated_detail.enforcement.unwrap();
        let updated_safari = updated_enf
            .installed_apps
            .iter()
            .find(|a| a.app_id.contains("Safari"))
            .unwrap();
        assert!(!updated_safari.allowed);
    }

    #[tokio::test]
    async fn pin_reset_and_validation() {
        let s = ParentSession::demo();
        s.verify_email_otp("parent@test.com", "424242")
            .await
            .unwrap();
        s.commit_onboarding("Child", "7_to_9", "1234").unwrap();

        // Valid 4-digit PIN reset
        assert!(s.reset_pin("9876").is_ok());

        // Invalid: non-numeric
        assert!(s.reset_pin("abcd").is_err());
        // Invalid: too short
        assert!(s.reset_pin("123").is_err());
        // Invalid: too long
        assert!(s.reset_pin("12345").is_err());
    }

    #[tokio::test]
    async fn notification_prefs_and_sign_out() {
        let s = ParentSession::demo();
        s.verify_email_otp("parent@test.com", "424242")
            .await
            .unwrap();

        let initial_prefs = s.notification_prefs();
        assert!(initial_prefs.quiz_results);

        let new_prefs = NotificationPrefs {
            quiz_results: false,
            fail_lock_alerts: true,
            tamper_alerts: false,
        };
        s.set_notification_prefs(new_prefs.clone());
        assert_eq!(s.notification_prefs(), new_prefs);

        assert!(s.is_signed_in());
        s.sign_out();
        assert!(!s.is_signed_in());
        assert!(matches!(s.dashboard(), UiState::Error(_)));
    }

    #[tokio::test]
    async fn tester_accounts_and_otp_validation() {
        let s = ParentSession::demo();

        // 1. Send OTP to tester4.parent@anajyo.com
        s.send_email_otp("tester4.parent@anajyo.com").await.unwrap();

        // 2. 123456 verifies successfully
        let profile = s
            .verify_email_otp("tester4.parent@anajyo.com", "123456")
            .await
            .unwrap();
        assert_eq!(profile.email, "tester4.parent@anajyo.com");
        assert!(s.is_signed_in());

        // 3. Bad code fails with validation message
        let s2 = ParentSession::demo();
        s2.send_email_otp("tester1.parent@anajyo.com")
            .await
            .unwrap();
        let err = s2
            .verify_email_otp("tester1.parent@anajyo.com", "999999")
            .await
            .unwrap_err();
        assert_eq!(
            err.user_message(),
            "That verification code is incorrect. Check your email and try again."
        );
    }

    #[tokio::test]
    async fn session_persistence_roundtrip() {
        let dir = tempfile::tempdir().unwrap();
        let session_file = dir.path().join("parent_session.json");

        // 1. Create a session with session_path set, sign in and add a child
        let s = ParentSession::demo().with_session_path(&session_file);
        s.verify_email_otp("persisted.parent@example.com", "424242")
            .await
            .unwrap();
        let child = s.commit_onboarding("Alice", "7_to_9", "1234").unwrap();
        assert!(s.is_signed_in());
        assert!(session_file.exists());

        // 2. Open a brand new ParentSession from the same session_path (simulating app relaunch)
        let s2 = ParentSession::demo().with_session_path(&session_file);
        assert!(s2.is_signed_in());
        assert_eq!(
            s2.profile().unwrap().email,
            "persisted.parent@example.com"
        );
        let detail = s2.child_detail(&child.child_id).unwrap();
        assert_eq!(detail.card.display_name, "Alice");

        // 3. Signing out removes the session file and clears state
        s2.sign_out();
        assert!(!s2.is_signed_in());
        assert!(!session_file.exists());

        // 4. A third session on the cleared path starts signed out
        let s3 = ParentSession::demo().with_session_path(&session_file);
        assert!(!s3.is_signed_in());
    }

    struct FailingCallableClient;
    #[async_trait::async_trait]
    impl CallableClient for FailingCallableClient {
        async fn invoke(
            &self,
            _name: &str,
            _data: serde_json::Value,
            _id_token: Option<&str>,
        ) -> meritscreen_firebase::FirebaseResult<serde_json::Value> {
            Err(meritscreen_firebase::FirebaseError::Http {
                status: 401,
                body: "Unauthenticated".into(),
            })
        }
    }

    #[tokio::test]
    async fn create_pairing_fallback_on_unauthenticated_or_error() {
        let callables = Arc::new(FailingCallableClient);
        let auth = Arc::new(meritscreen_firebase::mock::MockAuthClient::default());
        let s = ParentSession::with_backends(callables, auth);
        s.verify_email_otp("tester1.parent@anajyo.com", "123456")
            .await
            .unwrap();
        let child = s.commit_onboarding("Gokul", "7_to_9", "1234").unwrap();

        // Even though remote callable fails with 401 Unauthenticated,
        // create_pairing must succeed with a valid 6-digit numeric pairing code!
        let offer = s.create_pairing(&child.child_id).await.unwrap();
        assert_eq!(offer.code.len(), 6);
        assert!(offer.code.chars().all(|c| c.is_ascii_digit()));
        assert_eq!(offer.child_id, child.child_id);

        // Pairing code can be consumed locally
        let target_child = s.consume_pairing(&offer.code, "macos").unwrap();
        assert_eq!(target_child, child.child_id);
    }
}

#[derive(Clone)]
struct RemoteCtx {
    remote: ParentRemote,
    token: String,
    uid: String,
    email: String,
    family_id: Option<String>,
}

#[derive(Clone)]
struct Snapshot {
    profile: Option<ParentProfile>,
    children: HashMap<String, ChildRecord>,
    pin_hash: Option<String>,
    notifications: NotificationPrefs,
}

/// Firestore-backed (write-through) parent operations. In live mode the remote
/// write must succeed; on failure local state is rolled back and the error is
/// returned so the UI can show it. Demo/offline mode is local-only.
impl ParentSession {
    pub fn has_remote(&self) -> bool {
        self.remote.is_some()
    }

    fn snapshot(&self) -> Snapshot {
        let g = self.inner.lock();
        Snapshot {
            profile: g.profile.clone(),
            children: g.children.clone(),
            pin_hash: g.pin_hash.clone(),
            notifications: g.notifications.clone(),
        }
    }

    fn restore(&self, snap: Snapshot) {
        {
            let mut g = self.inner.lock();
            g.profile = snap.profile;
            g.children = snap.children;
            g.pin_hash = snap.pin_hash;
            g.notifications = snap.notifications;
        }
        self.persist_session();
    }

    async fn remote_ctx(&self) -> Result<Option<RemoteCtx>, AppError> {
        let Some(remote) = self.remote.clone() else {
            return Ok(None);
        };
        let profile = self.profile().ok_or_else(|| AppError::auth_msg("Sign in to continue."))?;
        // Dev tester/demo sessions have no real Firebase identity.
        if profile.uid.starts_with("tester-") || profile.uid.starts_with("demo-") {
            return Ok(None);
        }
        let token = self.fresh_id_token().await?;
        Ok(Some(RemoteCtx {
            remote,
            token,
            uid: profile.uid,
            email: profile.email,
            family_id: profile.family_id,
        }))
    }

    /// Run a remote operation with a fresh token; on an auth failure refresh the token
    /// once and retry. `Ok(None)` means there is no remote (offline / tester- / demo- uid).
    async fn remote_call<T, F, Fut>(&self, f: F) -> Result<Option<T>, AppError>
    where
        F: Fn(RemoteCtx) -> Fut,
        Fut: std::future::Future<Output = Result<T, AppError>>,
    {
        let Some(ctx) = self.remote_ctx().await? else {
            return Ok(None);
        };
        match f(ctx.clone()).await {
            Err(AppError::Auth(first)) => {
                tracing::info!("remote call rejected ({first}); refreshing token and retrying once");
                let token = self.refresh_id_token().await?;
                f(RemoteCtx { token, ..ctx }).await.map(Some)
            }
            other => other.map(Some),
        }
    }

    async fn run_synced<F, Fut>(&self, snap: Snapshot, f: F) -> Result<(), AppError>
    where
        F: Fn(RemoteCtx) -> Fut,
        Fut: std::future::Future<Output = Result<(), AppError>>,
    {
        match self.remote_call(f).await {
            Ok(_) => Ok(()),
            Err(e) => {
                tracing::warn!(error = ?e, "remote write failed; rolling back");
                self.restore(snap);
                Err(e)
            }
        }
    }

    /// After sign-in: pull an existing family (e.g. created on Android) into local state.
    /// Returns true if a family exists.
    pub async fn hydrate_from_remote(&self) -> Result<bool, AppError> {
        let loaded = self.remote_call(|ctx| async move { ctx.remote.load_family(&ctx.uid, &ctx.token).await }).await?;
        let Some(loaded) = loaded else {
            return Ok(self.profile().and_then(|p| p.family_id).is_some());
        };
        let Some(fam) = loaded else {
            return Ok(false);
        };
        {
            let mut g = self.inner.lock();
            if let Some(p) = g.profile.as_mut() {
                p.family_id = Some(fam.family_id.clone());
            }
            if fam.parent_pin_hash.is_some() {
                g.pin_hash = fam.parent_pin_hash.clone();
            }
            let mut next = HashMap::new();
            for rc in fam.children {
                let mut rec = g.children.remove(&rc.child_id).unwrap_or_else(|| {
                    ChildRecord::new(ChildCard {
                        child_id: rc.child_id.clone(),
                        display_name: String::new(),
                        age_band: String::new(),
                        minutes_used_today: 0,
                        minutes_remaining_today: None,
                        paired_device_count: 0,
                        platform_hint: None,
                        quiz_mode: QuizMode::AppBlock,
                    })
                });
                rec.card.display_name = rc.display_name;
                rec.card.age_band = ui_age_band(&rc.age_band);
                rec.card.quiz_mode = rc.policy.quiz_mode;
                rec.card.paired_device_count = rc.paired_device_count;
                rec.policy = rc.policy;
                if !rc.app_rules.is_empty() {
                    rec.app_rules = rc.app_rules;
                }
                next.insert(rc.child_id, rec);
            }
            g.children = next;
        }
        self.persist_session();
        Ok(true)
    }

    pub async fn onboard_synced(&self, child_name: &str, age_band: &str, pin: &str) -> Result<ChildCard, AppError> {
        let snap = self.snapshot();
        let had_family = snap.profile.as_ref().and_then(|p| p.family_id.clone()).is_some();
        let card = self.commit_onboarding(child_name, age_band, pin)?;
        let (pin_hash, policy) = {
            let g = self.inner.lock();
            (
                g.pin_hash.clone().unwrap_or_default(),
                g.children.get(&card.child_id).map(|r| r.policy.clone()).unwrap_or_default(),
            )
        };
        let family_id = self.profile().and_then(|p| p.family_id).unwrap_or_default();
        let c = card.clone();
        self.run_synced(snap, |ctx| {
            let (family_id, pin_hash, policy, c) = (family_id.clone(), pin_hash.clone(), policy.clone(), c.clone());
            async move {
            if had_family {
                ctx.remote
                    .upsert_child(&family_id, &c.child_id, &c.display_name, &c.age_band, &policy, &ctx.token)
                    .await?;
                ctx.remote.set_pin_hash(&family_id, &pin_hash, &ctx.token).await
            } else {
                ctx.remote
                    .create_family(&ctx.uid, &ctx.email, &family_id, &pin_hash, &c.child_id, &c.display_name, &c.age_band, &policy, &ctx.token)
                    .await
            }
            }
        })
        .await?;
        Ok(card)
    }

    pub async fn add_child_synced(&self, child_name: &str, age_band: &str) -> Result<ChildCard, AppError> {
        let snap = self.snapshot();
        let card = self.add_child(child_name, age_band)?;
        let policy = self.inner.lock().children.get(&card.child_id).map(|r| r.policy.clone()).unwrap_or_default();
        let c = card.clone();
        self.run_synced(snap, |ctx| {
            let (c, policy) = (c.clone(), policy.clone());
            async move {
            let fam = ctx.family_id.ok_or_else(|| AppError::validation("Finish family setup first."))?;
            ctx.remote.upsert_child(&fam, &c.child_id, &c.display_name, &c.age_band, &policy, &ctx.token).await
            }
        })
        .await?;
        Ok(card)
    }

    pub async fn remove_child_synced(&self, child_id: &str) -> Result<(), AppError> {
        let snap = self.snapshot();
        self.remove_child(child_id)?;
        let id = child_id.to_string();
        self.run_synced(snap, |ctx| {
            let id = id.clone();
            async move {
            let fam = ctx.family_id.ok_or_else(AppError::not_found)?;
            ctx.remote.delete_child(&fam, &id, &ctx.token).await
            }
        })
        .await
    }

    pub async fn update_policy_synced(&self, child_id: &str, policy: ChildPolicy) -> Result<(), AppError> {
        let snap = self.snapshot();
        self.update_policy(child_id, policy)?;
        let stored = self.inner.lock().children.get(child_id).map(|r| r.policy.clone()).unwrap_or_default();
        let id = child_id.to_string();
        self.run_synced(snap, |ctx| {
            let (id, stored) = (id.clone(), stored.clone());
            async move {
            let fam = ctx.family_id.ok_or_else(AppError::not_found)?;
            ctx.remote.write_policy(&fam, &id, &stored, &ctx.token).await
            }
        })
        .await
    }

    pub async fn grant_bonus_synced(&self, child_id: &str, minutes: u32) -> Result<ChildPolicy, AppError> {
        let snap = self.snapshot();
        let stored = self.grant_bonus(child_id, minutes)?;
        let id = child_id.to_string();
        let to_write = stored.clone();
        self.run_synced(snap, |ctx| {
            let (id, to_write) = (id.clone(), to_write.clone());
            async move {
            let fam = ctx.family_id.ok_or_else(AppError::not_found)?;
            ctx.remote.write_policy(&fam, &id, &to_write, &ctx.token).await
            }
        })
        .await?;
        Ok(stored)
    }

    pub async fn update_app_rules_synced(&self, child_id: &str, rules: Vec<AppRule>) -> Result<(), AppError> {
        let snap = self.snapshot();
        self.update_app_rules(child_id, rules.clone())?;
        let id = child_id.to_string();
        self.run_synced(snap, |ctx| {
            let (id, rules) = (id.clone(), rules.clone());
            async move {
            let fam = ctx.family_id.ok_or_else(AppError::not_found)?;
            ctx.remote.write_app_rules(&fam, &id, &rules, &ctx.token).await
            }
        })
        .await
    }

    pub async fn reset_pin_synced(&self, new_pin: &str) -> Result<(), AppError> {
        let snap = self.snapshot();
        self.reset_pin(new_pin)?;
        let hash = self.pin_hash().unwrap_or_default();
        self.run_synced(snap, |ctx| {
            let hash = hash.clone();
            async move {
            let fam = ctx.family_id.ok_or_else(|| AppError::validation("Finish family setup first."))?;
            ctx.remote.set_pin_hash(&fam, &hash, &ctx.token).await
            }
        })
        .await
    }

    pub async fn set_notification_prefs_synced(&self, prefs: NotificationPrefs) -> Result<(), AppError> {
        let snap = self.snapshot();
        self.set_notification_prefs(prefs.clone());
        let v = serde_json::to_value(&prefs).unwrap_or_default();
        self.run_synced(snap, |ctx| {
            let v = v.clone();
            async move {
            ctx.remote.set_notification_prefs(&ctx.uid, &v, &ctx.token).await
            }
        })
        .await
    }

    pub async fn unpair_device_synced(&self, child_id: &str) -> Result<(), AppError> {
        let snap = self.snapshot();
        self.unpair_device(child_id)?;
        let id = child_id.to_string();
        self.run_synced(snap, |ctx| {
            let id = id.clone();
            async move {
            let fam = ctx.family_id.ok_or_else(AppError::not_found)?;
            ctx.remote.revoke_devices(&fam, &id, &ctx.token).await
            }
        })
        .await
    }

    pub async fn delete_family_synced(&self) -> Result<(), AppError> {
        let snap = self.snapshot();
        let family_id = snap.profile.as_ref().and_then(|p| p.family_id.clone());
        self.delete_family()?;
        self.run_synced(snap, |ctx| {
            let family_id = family_id.clone();
            async move {
            match family_id {
                Some(f) => ctx.remote.delete_family(&f, &ctx.token).await,
                None => Ok(()),
            }
            }
        })
        .await
    }
}

/// Result of a successful `consumePairingToken` + custom-token sign-in.
#[derive(Debug, Clone)]
pub struct RemotePairing {
    pub family_id: String,
    pub child_id: String,
    pub device_id: String,
    pub display_name: String,
    pub age_band: String,
    pub parent_pin_hash: Option<String>,
    pub refresh_token: String,
    /// Child device's ID token (immediate Firestore policy pull after pairing).
    pub id_token: String,
}

async fn parse_remote_pairing(
    result: &serde_json::Value,
    auth: &dyn AuthClient,
) -> Result<RemotePairing, AppError> {
    let s = |k: &str| result[k].as_str().unwrap_or("").to_string();
    let custom = s("customToken");
    if custom.is_empty() || s("familyId").is_empty() || s("childId").is_empty() {
        return Err(AppError::auth_msg("Pairing response was incomplete. Ask for a new code."));
    }
    let tokens = auth
        .sign_in_with_custom_token(&custom)
        .await
        .map_err(|e| e.to_app_error())?;
    let pin = s("parentPinHash");
    Ok(RemotePairing {
        family_id: s("familyId"),
        child_id: s("childId"),
        device_id: s("deviceId"),
        display_name: s("displayName"),
        age_band: s("ageBand"),
        parent_pin_hash: if pin.is_empty() { None } else { Some(pin) },
        refresh_token: tokens.refresh_token,
        id_token: tokens.id_token,
    })
}

#[cfg(test)]
mod remote_pairing_tests {
    use super::*;
    use async_trait::async_trait;
    use meritscreen_firebase::mock::MockAuthClient;
    use meritscreen_firebase::FirebaseResult;

    struct PairingCallable(serde_json::Value);

    #[async_trait]
    impl CallableClient for PairingCallable {
        async fn invoke(
            &self,
            name: &str,
            data: serde_json::Value,
            _t: Option<&str>,
        ) -> FirebaseResult<serde_json::Value> {
            assert_eq!(name, "consumePairingToken");
            assert_eq!(data["platform"], "macos");
            assert_eq!(data["code"], "123456");
            Ok(self.0.clone())
        }
    }

    #[tokio::test]
    async fn consume_pairing_remote_returns_credentials() {
        let s = ParentSession::with_backends(
            Arc::new(PairingCallable(json!({
                "customToken": "ct", "familyId": "f1", "childId": "c1", "deviceId": "d1",
                "parentPinHash": "pbkdf2$1$a$b", "displayName": "Asha", "ageBand": "AGE_7_TO_9"
            }))),
            Arc::new(MockAuthClient::default()),
        );
        assert!(s.is_live());
        let r = s.consume_pairing_remote("123 456", "macos", "devabcdef").await.unwrap();
        assert_eq!(r.child_id, "c1");
        assert_eq!(r.refresh_token, "refresh-ct");
        assert_eq!(r.parent_pin_hash.as_deref(), Some("pbkdf2$1$a$b"));
        assert!(s.consume_pairing_remote("12", "macos", "devabcdef").await.is_err());
    }

    #[tokio::test]
    async fn consume_pairing_remote_rejects_incomplete_response() {
        let s = ParentSession::with_backends(
            Arc::new(PairingCallable(json!({ "ok": true }))),
            Arc::new(MockAuthClient::default()),
        );
        assert!(s.consume_pairing_remote("123456", "macos", "devabcdef").await.is_err());
    }

    #[tokio::test]
    async fn demo_session_is_not_live() {
        let s = ParentSession::demo();
        assert!(!s.is_live());
        assert!(s.consume_pairing_remote("123456", "macos", "devabcdef").await.is_err());
    }
    #[tokio::test]
    async fn family_insights_falls_back_to_local_data() {
        let s = ParentSession::demo();
        let n = s.inner.lock().children.len();
        if s.profile().is_none() {
            assert!(s.family_insights().await.is_err());
            return;
        }
        let fi = s.family_insights().await.unwrap();
        assert_eq!(fi.source, "local");
        assert_eq!(fi.children.len(), n);
        for c in &fi.children {
            assert_eq!(c.trend.len(), 7);
            assert!(c.error.is_none());
        }
    }
}

/// Telemetry known on this computer (used offline / for dev sessions).
fn local_telemetry(rec: &ChildRecord) -> crate::analytics::ChildTelemetry {
    use crate::analytics::{ChildTelemetry, UsageDay};
    let today = chrono::Local::now().date_naive().format("%Y-%m-%d").to_string();
    let mut days: Vec<UsageDay> = rec
        .daily_total_history
        .iter()
        .filter(|(d, _)| **d != today)
        .map(|(d, m)| UsageDay {
            day: d.clone(),
            minutes_used: *m,
            minutes_by_app: rec.daily_usage_history.get(d).map(|h| h.iter().map(|(k, v)| (k.clone(), *v)).collect()).unwrap_or_default(),
        })
        .collect();
    days.push(UsageDay {
        day: today,
        minutes_used: rec.card.minutes_used_today,
        minutes_by_app: rec.minutes_by_app.iter().map(|(k, v)| (k.clone(), *v)).collect(),
    });
    ChildTelemetry {
        usage_days: days,
        quiz_today_counts: (rec.quiz_attempts_today > 0).then_some((rec.quiz_attempts_today, rec.quiz_passes_today)),
        ..Default::default()
    }
}

#[cfg(test)]
mod token_refresh_tests {
    use super::*;
    use crate::remote::ParentRemote;
    use async_trait::async_trait;
    use base64::Engine;
    use meritscreen_firebase::mock::MockFirestoreClient;
    use meritscreen_firebase::{FirebaseError, FirebaseResult};
    use std::sync::atomic::{AtomicUsize, Ordering};

    fn jwt(exp_ms: i64, tag: &str) -> String {
        let enc = |v: serde_json::Value| base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(v.to_string());
        format!("{}.{}.sig", enc(json!({"alg": "RS256"})), enc(json!({"exp": exp_ms / 1000, "tag": tag})))
    }

    /// Refresh mints a new valid JWT; `mode` 0 = ok, 1 = rejected (400), 2 = server down (503).
    struct Auth {
        calls: AtomicUsize,
        mode: usize,
    }
    #[async_trait]
    impl AuthClient for Auth {
        async fn sign_in_with_custom_token(&self, _c: &str) -> FirebaseResult<AuthTokens> {
            unreachable!()
        }
        async fn refresh_id_token(&self, refresh: &str) -> FirebaseResult<AuthTokens> {
            let n = self.calls.fetch_add(1, Ordering::SeqCst) + 1;
            match self.mode {
                1 => Err(FirebaseError::Http { status: 400, body: r#"{"error":{"message":"TOKEN_EXPIRED"}}"#.into() }),
                2 => Err(FirebaseError::Http { status: 503, body: String::new() }),
                _ => Ok(AuthTokens {
                    id_token: jwt(now_ms() + 3_600_000, &format!("fresh{n}")),
                    refresh_token: refresh.into(),
                    expires_in: 3600,
                    user_id: "u1".into(),
                }),
            }
        }
    }

    /// Callable that, like the Firebase gateway, rejects any token it doesn't accept with 401.
    struct Callable {
        accept: Box<dyn Fn(&str) -> bool + Send + Sync>,
        calls: Mutex<Vec<(String, String)>>,
    }
    #[async_trait]
    impl CallableClient for Callable {
        async fn invoke(&self, name: &str, _d: serde_json::Value, t: Option<&str>) -> FirebaseResult<serde_json::Value> {
            let t = t.unwrap_or("").to_string();
            self.calls.lock().push((name.into(), t.clone()));
            if (self.accept)(&t) {
                Ok(json!({"ok": true}))
            } else {
                Err(FirebaseError::Http { status: 401, body: r#"{"error":{"message":"Unauthenticated","status":"UNAUTHENTICATED"}}"#.into() })
            }
        }
    }

    fn session(uid: &str, id_token: String, mode: usize, accept: Box<dyn Fn(&str) -> bool + Send + Sync>) -> (ParentSession, Arc<Auth>, Arc<Callable>, String) {
        let auth = Arc::new(Auth { calls: AtomicUsize::new(0), mode });
        let callable = Arc::new(Callable { accept, calls: Mutex::new(Vec::new()) });
        let remote = ParentRemote::new(Arc::new(MockFirestoreClient::default()), callable.clone(), "p");
        let s = ParentSession::with_backends(callable.clone(), auth.clone()).with_remote(remote);
        {
            let mut g = s.inner.lock();
            g.profile = Some(ParentProfile { uid: uid.into(), email: "tester4.parent@anajyo.com".into(), family_id: Some("f1".into()) });
            g.tokens = Some(AuthTokens { id_token, refresh_token: "r".into(), expires_in: 3600, user_id: uid.into() });
        }
        let child = s.add_child("Test", "7_to_9").unwrap().child_id;
        (s, auth, callable, child)
    }

    fn accept_fresh() -> Box<dyn Fn(&str) -> bool + Send + Sync> {
        Box::new(|t: &str| t.contains('.') && token_is_fresh(t, now_ms()) && !t.is_empty())
    }

    #[test]
    fn freshness_uses_jwt_exp_with_skew() {
        let now = now_ms();
        assert!(token_is_fresh(&jwt(now + 3_600_000, "a"), now));
        assert!(!token_is_fresh(&jwt(now - 1, "a"), now));
        assert!(!token_is_fresh(&jwt(now + 60_000, "a"), now), "inside skew window");
        assert!(token_is_fresh("opaque-token", now));
        assert!(!token_is_fresh("", now));
    }

    #[tokio::test]
    async fn expired_persisted_token_is_refreshed_before_delete_child() {
        // The bug: a token persisted 1.5 h earlier was sent as-is and the callable answered 401.
        let (s, auth, callable, child) = session("nEOH", jwt(now_ms() - 5_400_000, "old"), 0, accept_fresh());
        s.remove_child_synced(&child).await.unwrap();
        assert_eq!(auth.calls.load(Ordering::SeqCst), 1);
        let calls = callable.calls.lock().clone();
        assert_eq!(calls.len(), 1, "no wasted call with the stale token");
        assert_eq!(calls[0].0, "deleteChild");
        assert!(!s.inner.lock().children.contains_key(&child));
    }

    #[tokio::test]
    async fn server_401_triggers_one_refresh_and_retry() {
        // Token looks valid locally but was revoked server-side.
        let revoked = jwt(now_ms() + 3_600_000, "revoked");
        let bad = revoked.clone();
        let (s, auth, callable, child) = session("nEOH", revoked, 0, Box::new(move |t: &str| t != bad));
        s.remove_child_synced(&child).await.unwrap();
        assert_eq!(auth.calls.load(Ordering::SeqCst), 1);
        assert_eq!(callable.calls.lock().len(), 2);
    }

    #[tokio::test]
    async fn rejected_refresh_reports_session_expired_and_rolls_back() {
        let (s, _a, callable, child) = session("nEOH", jwt(now_ms() - 1, "old"), 1, accept_fresh());
        let err = s.remove_child_synced(&child).await.unwrap_err();
        assert!(matches!(&err, AppError::Auth(m) if m == SESSION_EXPIRED), "{err:?}");
        assert!(callable.calls.lock().is_empty());
        assert!(s.inner.lock().children.contains_key(&child), "local change rolled back");
    }

    #[tokio::test]
    async fn refresh_outage_is_a_network_error_not_session_expired() {
        let (s, _a, _c, child) = session("nEOH", jwt(now_ms() - 1, "old"), 2, accept_fresh());
        let err = s.remove_child_synced(&child).await.unwrap_err();
        assert!(matches!(err, AppError::Network(_)), "{err:?}");
    }

    #[tokio::test]
    async fn every_synced_write_survives_an_expired_token() {
        let (s, auth, callable, child) = session("nEOH", jwt(now_ms() - 1, "old"), 0, accept_fresh());
        let policy = s.inner.lock().children[&child].policy.clone();
        s.update_policy_synced(&child, policy).await.unwrap();
        s.grant_bonus_synced(&child, 15).await.unwrap();
        s.update_app_rules_synced(&child, vec![]).await.unwrap();
        s.unpair_device_synced(&child).await.unwrap();
        s.set_notification_prefs_synced(NotificationPrefs::default()).await.unwrap();
        s.reset_pin_synced("4829").await.unwrap();
        s.add_child_synced("Second", "7_to_9").await.unwrap();
        s.remove_child_synced(&child).await.unwrap();
        s.family_insights().await.unwrap();
        s.delete_family_synced().await.unwrap();
        assert_eq!(auth.calls.load(Ordering::SeqCst), 1, "token refreshed once, then reused");
        assert!(callable.calls.lock().iter().all(|(_, t)| token_is_fresh(t, now_ms())));
    }

    #[tokio::test]
    async fn dev_tester_uid_stays_local_without_token_traffic() {
        let (s, auth, callable, child) = session("tester-abc", String::new(), 0, accept_fresh());
        s.remove_child_synced(&child).await.unwrap();
        assert_eq!(auth.calls.load(Ordering::SeqCst), 0);
        assert!(callable.calls.lock().is_empty());
    }
}
