//! In-memory / demo parent session. Firebase callables used when a client is injected;
//! otherwise local demo store (UI + unit tests).

use std::collections::HashMap;
use std::sync::Arc;

use meritscreen_core::app_config::{
    MAX_CHILDREN_PER_PARENT, PAIRING_CODE_LENGTH, PAIRING_TOKEN_TTL_MINUTES, PARENT_PIN_MAX_LENGTH,
    PARENT_PIN_MIN_LENGTH,
};
use meritscreen_core::{AppError, AppRule, ChildPolicy, FailLockScope, QuizMode, UiState};
use meritscreen_firebase::traits::{AuthClient, AuthTokens, CallableClient};
use meritscreen_firebase::{FirebaseConfig, RestAuthClient, RestCallableClient};
use meritscreen_security::hash_parent_pin;
use parking_lot::Mutex;
use rand::Rng;
use serde_json::json;
use tracing::info;
use uuid::Uuid;

use crate::models::{
    ChildCard, ChildDetail, DashboardSnapshot, DeviceEnforcementStatus, InventoryApp,
    NotificationPrefs, PairingOffer, ParentProfile,
};
use crate::oauth::{apple_sign_in_stub, google_sign_in_stub};

#[derive(Clone)]
struct ChildRecord {
    card: ChildCard,
    policy: ChildPolicy,
    app_rules: Vec<AppRule>,
    enforcement: Option<DeviceEnforcementStatus>,
}

struct Inner {
    profile: Option<ParentProfile>,
    tokens: Option<AuthTokens>,
    children: HashMap<String, ChildRecord>,
    pin_hash: Option<String>,
    notifications: NotificationPrefs,
    /// Demo OTP inbox: email → code (never logged).
    pending_otp: HashMap<String, String>,
}

pub struct ParentSession {
    inner: Mutex<Inner>,
    callables: Option<Arc<dyn CallableClient>>,
    auth: Option<Arc<dyn AuthClient>>,
    google_client_id: Option<String>,
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
            }),
            callables: None,
            auth: None,
            google_client_id: std::env::var("MERITSCREEN_GOOGLE_CLIENT_ID").ok(),
        }
    }

    pub fn with_backends(callables: Arc<dyn CallableClient>, auth: Arc<dyn AuthClient>) -> Self {
        let mut s = Self::demo();
        s.callables = Some(callables);
        s.auth = Some(auth);
        s
    }

    /// Demo by default; when `MERITSCREEN_FIREBASE_API_KEY` (+ optional project id) is set,
    /// wire REST Auth + callable backends for live Firebase (macOS / desktop E2E).
    /// Set `MERITSCREEN_PARENT_DEMO=1` to force the local OTP/demo store even with an API key.
    pub fn from_env() -> Self {
        if std::env::var("MERITSCREEN_PARENT_DEMO")
            .map(|v| matches!(v.as_str(), "1" | "true" | "TRUE" | "yes"))
            .unwrap_or(false)
        {
            info!("parent session: demo backends (MERITSCREEN_PARENT_DEMO)");
            return Self::demo();
        }
        let Ok(api_key) = std::env::var("MERITSCREEN_FIREBASE_API_KEY") else {
            info!("parent session: demo backends (no MERITSCREEN_FIREBASE_API_KEY)");
            return Self::demo();
        };
        if api_key.trim().is_empty() {
            return Self::demo();
        }
        let project_id = std::env::var("MERITSCREEN_FIREBASE_PROJECT_ID")
            .unwrap_or_else(|_| "managing-screen-time".into());
        let config = FirebaseConfig {
            api_key,
            project_id: project_id.clone(),
        };
        info!(%project_id, "parent session: live Firebase REST backends");
        Self::with_backends(
            Arc::new(RestCallableClient::new(config.clone())),
            Arc::new(RestAuthClient::new(config)),
        )
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
        info!("send_email_otp requested");
        if let Some(callables) = &self.callables {
            let token = self.id_token();
            callables
                .invoke("sendEmailOtp", json!({ "email": email }), token.as_deref())
                .await
                .map_err(|e| e.to_app_error())?;
            return Ok(());
        }
        // Demo: fixed code 424242 — never log it.
        let code = "424242".to_string();
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
            let result = callables
                .invoke(
                    "verifyEmailOtp",
                    json!({ "email": email, "code": code }),
                    None,
                )
                .await
                .map_err(|e| e.to_app_error())?;
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
            return Ok(profile);
        }

        let mut g = self.inner.lock();
        let expected = g.pending_otp.get(&email).cloned();
        if expected.as_deref() != Some(code.as_str()) && code != "424242" {
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
        g.profile = Some(profile.clone());
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
            ChildRecord {
                card: card.clone(),
                policy: ChildPolicy::default(),
                app_rules: Vec::new(),
                enforcement: None,
            },
        );
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
        rec.policy = policy;
        Ok(())
    }

    pub fn update_app_rules(&self, child_id: &str, rules: Vec<AppRule>) -> Result<(), AppError> {
        self.require_signed_in()?;
        let mut g = self.inner.lock();
        let rec = g
            .children
            .get_mut(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;
        rec.app_rules = rules;
        Ok(())
    }

    pub async fn create_pairing(&self, child_id: &str) -> Result<PairingOffer, AppError> {
        self.require_signed_in()?;
        {
            let g = self.inner.lock();
            if !g.children.contains_key(child_id) {
                return Err(AppError::not_found_msg("Child not found."));
            }
        }

        if let Some(callables) = &self.callables {
            let token = self
                .id_token()
                .ok_or_else(|| AppError::auth_msg("Sign in again to create a pairing code."))?;
            let result = callables
                .invoke(
                    "createPairingToken",
                    json!({ "childId": child_id }),
                    Some(&token),
                )
                .await
                .map_err(|e| e.to_app_error())?;
            return Ok(PairingOffer {
                child_id: child_id.into(),
                code: result["code"].as_str().unwrap_or("000000").into(),
                secret: result["secret"].as_str().unwrap_or("").into(),
                qr_payload: result["qrPayload"].as_str().unwrap_or("").into(),
                expires_at_epoch_ms: result["expiresAtEpochMs"].as_i64().unwrap_or(0),
            });
        }

        let code = random_digits(PAIRING_CODE_LENGTH as usize);
        let secret = hex::encode(Uuid::new_v4().as_bytes());
        let offer = PairingOffer {
            child_id: child_id.into(),
            qr_payload: format!("meritscreen://pair?c={code}&s={secret}"),
            code,
            secret,
            expires_at_epoch_ms: now_ms() + (PAIRING_TOKEN_TTL_MINUTES as i64) * 60_000,
        };
        info!(
            child_id,
            code_len = PAIRING_CODE_LENGTH,
            "pairing offer created (demo)"
        );
        Ok(offer)
    }

    pub fn mark_device_paired(&self, child_id: &str, platform: &str) -> Result<(), AppError> {
        let mut g = self.inner.lock();
        let rec = g
            .children
            .get_mut(child_id)
            .ok_or_else(|| AppError::not_found_msg("Child not found."))?;
        rec.card.paired_device_count = rec.card.paired_device_count.saturating_add(1);
        rec.card.platform_hint = Some(platform.to_string());
        if meritscreen_core::DevicePlatform::parse(platform)
            .map(|p| p.is_desktop())
            .unwrap_or(false)
        {
            rec.policy.quiz_mode = QuizMode::DeviceInterval;
            rec.card.quiz_mode = QuizMode::DeviceInterval;
            // Demo inventory so P13 allowlist has desktop appIds before live sync (d8).
            let inventory = demo_desktop_inventory(platform);
            let mut browser = AppRule::new("browser", &inventory[0].app_id);
            browser.display_name = inventory[0].label.clone();
            let mut notes = AppRule::new("notes", &inventory[1].app_id);
            notes.display_name = inventory[1].label.clone();
            rec.app_rules = vec![browser, notes];
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
        Ok(())
    }

    pub fn reset_pin(&self, new_pin: &str) -> Result<(), AppError> {
        self.require_signed_in()?;
        validate_pin(new_pin)?;
        self.inner.lock().pin_hash = Some(hash_parent_pin(new_pin));
        Ok(())
    }

    pub fn notification_prefs(&self) -> NotificationPrefs {
        self.inner.lock().notifications.clone()
    }

    pub fn set_notification_prefs(&self, prefs: NotificationPrefs) {
        self.inner.lock().notifications = prefs;
    }

    pub fn delete_family(&self) -> Result<(), AppError> {
        self.require_signed_in()?;
        let mut g = self.inner.lock();
        g.children.clear();
        g.pin_hash = None;
        if let Some(p) = g.profile.as_mut() {
            p.family_id = None;
        }
        Ok(())
    }

    pub fn sign_out(&self) {
        let mut g = self.inner.lock();
        g.profile = None;
        g.tokens = None;
        g.children.clear();
        g.pin_hash = None;
        g.pending_otp.clear();
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

    fn id_token(&self) -> Option<String> {
        self.inner
            .lock()
            .tokens
            .as_ref()
            .map(|t| t.id_token.clone())
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
        s.mark_device_paired(&child.child_id, "macos").unwrap();
        let detail = s.child_detail(&child.child_id).unwrap();
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
}
