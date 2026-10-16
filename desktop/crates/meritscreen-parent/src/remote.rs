//! Parent → Firestore write/read path, mirroring Android
//! (`FirestoreFamilyStore` / `FirestoreParentControlStore`).
//!
//! Document layout (shared with Android/iOS):
//! - `users/{uid}`                    { familyId, email }
//! - `families/{f}`                   { ownerUid, name, parentPinHash }
//! - `families/{f}/members/{uid}`     { role: "owner" }
//! - `families/{f}/children/{c}`      { displayName, ageBand, avatarId, language }
//! - `.../children/{c}/policy/current` ChildPolicyMapper fields
//! - `.../children/{c}/appRules/{id}`  { packageOrBundleId, displayName, allowed, ... }
//! - `.../children/{c}/devices/{d}`    { revoked }
//! Deletes go through `deleteChild` / `deleteFamily` callables (server wipes subtrees).

use std::sync::Arc;

use meritscreen_core::{AppError, AppRule, ChildPolicy};
use meritscreen_firebase::traits::{CallableClient, FirestoreClient};
use meritscreen_firebase::{app_rule_from_value, policy_from_value, to_firestore_fields, FirebaseError};
use serde_json::{json, Value};

#[derive(Clone)]
pub struct ParentRemote {
    firestore: Arc<dyn FirestoreClient>,
    callables: Arc<dyn CallableClient>,
    project_id: String,
}

/// A child profile loaded from Firestore.
#[derive(Debug, Clone)]
pub struct RemoteChild {
    pub child_id: String,
    pub display_name: String,
    pub age_band: String,
    pub policy: ChildPolicy,
    pub app_rules: Vec<AppRule>,
    pub paired_device_count: u32,
}

#[derive(Debug, Clone)]
pub struct RemoteFamily {
    pub family_id: String,
    pub parent_pin_hash: Option<String>,
    pub children: Vec<RemoteChild>,
}

fn map_err(e: FirebaseError) -> AppError {
    e.to_app_error()
}

pub fn policy_to_value(p: &ChildPolicy) -> Value {
    json!({
        "quizMode": p.quiz_mode.as_storage_str(),
        "failLockScope": "all_non_emergency",
        "allowRetryDuringCooldown": p.allow_retry_during_cooldown,
        "dailyCeilingMinutes": p.daily_ceiling_minutes,
        "questionsPerQuiz": p.questions_per_quiz.clamp(3, 5),
        "passScorePercent": p.pass_score_percent.clamp(50, 100),
        "rewardsEnabled": p.rewards_enabled,
        "weekendBonusEnabled": p.weekend_bonus_enabled,
        "extraMinutesOnPass": p.extra_minutes_on_pass.min(60),
        "defaultBlockMinutes": p.default_block_minutes.clamp(5, 240),
        "defaultCooldownMinutes": p.default_cooldown_minutes.clamp(1, 180),
        "quizIntervalMinutes": p.quiz_interval_minutes,
        "emergencyApps": p.emergency_apps,
        "paused": p.paused,
        // Today-only bonus. `bonusMinutesToday` is Android's field; `bonusDay` (child-local
        // YYYY-MM-DD) scopes it so it expires at midnight. The permanent limit is untouched.
        "bonusMinutesToday": if p.bonus_day.is_some() { p.bonus_minutes_today } else { 0 },
        "bonusDay": p.bonus_day,
    })
}

pub fn app_rule_to_value(r: &AppRule) -> Value {
    json!({
        "packageOrBundleId": r.package_or_bundle_id.trim(),
        "displayName": r.display_name.trim(),
        "allowed": r.allowed,
        "blockMinutes": r.block_minutes.clamp(5, 240),
        "grantOnPassMinutes": r.grant_on_pass_minutes.clamp(5, 240),
        "cooldownMinutes": r.cooldown_minutes.clamp(1, 180),
        "isEmergency": r.is_emergency,
    })
}

/// Firestore doc ids cannot contain `/`; desktop appIds look like `mac:com.apple.Safari`.
pub fn app_rule_doc_id(app_id: &str) -> String {
    app_id.replace('/', "_")
}

fn str_field(doc: &Value, key: &str) -> String {
    doc.pointer(&format!("/fields/{key}/stringValue"))
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string()
}

fn doc_id(doc: &Value) -> String {
    doc["name"]
        .as_str()
        .and_then(|n| n.rsplit('/').next())
        .unwrap_or("")
        .to_string()
}

impl ParentRemote {
    pub fn new(
        firestore: Arc<dyn FirestoreClient>,
        callables: Arc<dyn CallableClient>,
        project_id: impl Into<String>,
    ) -> Self {
        Self {
            firestore,
            callables,
            project_id: project_id.into(),
        }
    }

    fn name(&self, path: &str) -> String {
        format!(
            "projects/{}/databases/(default)/documents/{}",
            self.project_id,
            path.trim_start_matches('/')
        )
    }

    fn set_write(&self, path: &str, body: &Value) -> Value {
        json!({ "update": { "name": self.name(path), "fields": to_firestore_fields(body) } })
    }

    fn merge_write(&self, path: &str, body: &Value) -> Value {
        let mask: Vec<String> = body
            .as_object()
            .map(|m| m.keys().cloned().collect())
            .unwrap_or_default();
        json!({
            "update": { "name": self.name(path), "fields": to_firestore_fields(body) },
            "updateMask": { "fieldPaths": mask },
        })
    }

    async fn commit(&self, writes: Vec<Value>, token: &str) -> Result<(), AppError> {
        self.firestore
            .commit(Value::Array(writes), token)
            .await
            .map(|_| ())
            .map_err(map_err)
    }

    /// Load the parent's existing family (created on any platform), if any.
    pub async fn load_family(&self, uid: &str, token: &str) -> Result<Option<RemoteFamily>, AppError> {
        let user = self
            .firestore
            .get_document(&format!("users/{uid}"), token)
            .await
            .map_err(map_err)?;
        let Some(user) = user else { return Ok(None) };
        let family_id = str_field(&user, "familyId");
        if family_id.is_empty() {
            return Ok(None);
        }
        let family = self
            .firestore
            .get_document(&format!("families/{family_id}"), token)
            .await
            .map_err(map_err)?;
        let Some(family) = family else { return Ok(None) };
        let pin = str_field(&family, "parentPinHash");
        let mut children = Vec::new();
        let list = self
            .firestore
            .get_document(&format!("families/{family_id}/children"), token)
            .await
            .map_err(map_err)?;
        let docs = list
            .as_ref()
            .and_then(|v| v["documents"].as_array().cloned())
            .unwrap_or_default();
        for d in docs {
            let child_id = doc_id(&d);
            if child_id.is_empty() {
                continue;
            }
            let base = format!("families/{family_id}/children/{child_id}");
            let policy = self
                .firestore
                .get_document(&format!("{base}/policy/current"), token)
                .await
                .map_err(map_err)?
                .map(|v| policy_from_value(&v))
                .unwrap_or_default();
            let rules = self
                .firestore
                .get_document(&format!("{base}/appRules"), token)
                .await
                .map_err(map_err)?
                .and_then(|v| v["documents"].as_array().cloned())
                .unwrap_or_default()
                .iter()
                .map(|r| app_rule_from_value(&doc_id(r), r))
                .collect();
            let devices = self
                .firestore
                .get_document(&format!("{base}/devices"), token)
                .await
                .map_err(map_err)?
                .and_then(|v| v["documents"].as_array().cloned())
                .unwrap_or_default()
                .iter()
                .filter(|dv| dv.pointer("/fields/revoked/booleanValue").and_then(Value::as_bool) != Some(true))
                .count() as u32;
            children.push(RemoteChild {
                child_id,
                display_name: str_field(&d, "displayName"),
                age_band: str_field(&d, "ageBand"),
                policy,
                app_rules: rules,
                paired_device_count: devices,
            });
        }
        Ok(Some(RemoteFamily {
            family_id,
            parent_pin_hash: if pin.is_empty() { None } else { Some(pin) },
            children,
        }))
    }

    /// Create family + owner membership + user pointer + first child (Android `createFamily`).
    #[allow(clippy::too_many_arguments)]
    pub async fn create_family(
        &self,
        uid: &str,
        email: &str,
        family_id: &str,
        pin_hash: &str,
        child_id: &str,
        child_name: &str,
        age_band: &str,
        policy: &ChildPolicy,
        token: &str,
    ) -> Result<(), AppError> {
        let fam = format!("families/{family_id}");
        let writes = vec![
            self.set_write(&fam, &json!({ "ownerUid": uid, "name": "", "parentPinHash": pin_hash })),
            self.set_write(&format!("{fam}/members/{uid}"), &json!({ "role": "owner" })),
            self.merge_write(&format!("users/{uid}"), &json!({ "familyId": family_id, "email": email })),
            self.set_write(&format!("{fam}/children/{child_id}"), &child_body(child_name, age_band)),
            self.set_write(&format!("{fam}/children/{child_id}/policy/current"), &policy_to_value(policy)),
        ];
        self.commit(writes, token).await
    }

    pub async fn upsert_child(
        &self,
        family_id: &str,
        child_id: &str,
        child_name: &str,
        age_band: &str,
        policy: &ChildPolicy,
        token: &str,
    ) -> Result<(), AppError> {
        let base = format!("families/{family_id}/children/{child_id}");
        self.commit(
            vec![
                self.merge_write(&base, &child_body(child_name, age_band)),
                self.merge_write(&format!("{base}/policy/current"), &policy_to_value(policy)),
            ],
            token,
        )
        .await
    }

    pub async fn write_policy(
        &self,
        family_id: &str,
        child_id: &str,
        policy: &ChildPolicy,
        token: &str,
    ) -> Result<(), AppError> {
        // Merge, never replace: Android-only fields (aiQuizzesEnabled, curriculumFocusIds,
        // nursery*, region, …) that desktop's ChildPolicy doesn't model must survive.
        let path = format!("families/{family_id}/children/{child_id}/policy/current");
        self.commit(vec![self.merge_write(&path, &policy_to_value(policy))], token)
            .await
    }

    pub async fn write_app_rules(
        &self,
        family_id: &str,
        child_id: &str,
        rules: &[AppRule],
        token: &str,
    ) -> Result<(), AppError> {
        if rules.is_empty() {
            return Ok(());
        }
        let base = format!("families/{family_id}/children/{child_id}/appRules");
        let writes = rules
            .iter()
            .map(|r| self.merge_write(&format!("{base}/{}", app_rule_doc_id(&r.app_id)), &app_rule_to_value(r)))
            .collect();
        self.commit(writes, token).await
    }

    pub async fn set_pin_hash(&self, family_id: &str, pin_hash: &str, token: &str) -> Result<(), AppError> {
        self.commit(
            vec![self.merge_write(&format!("families/{family_id}"), &json!({ "parentPinHash": pin_hash }))],
            token,
        )
        .await
    }

    pub async fn set_notification_prefs(&self, uid: &str, prefs: &Value, token: &str) -> Result<(), AppError> {
        self.commit(
            vec![self.merge_write(&format!("users/{uid}"), &json!({ "notificationPrefs": prefs }))],
            token,
        )
        .await
    }

    pub async fn revoke_devices(&self, family_id: &str, child_id: &str, token: &str) -> Result<(), AppError> {
        let base = format!("families/{family_id}/children/{child_id}/devices");
        let docs = self
            .firestore
            .get_document(&base, token)
            .await
            .map_err(map_err)?
            .and_then(|v| v["documents"].as_array().cloned())
            .unwrap_or_default();
        let writes: Vec<Value> = docs
            .iter()
            .map(|d| self.merge_write(&format!("{base}/{}", doc_id(d)), &json!({ "revoked": true })))
            .collect();
        if writes.is_empty() {
            return Ok(());
        }
        self.commit(writes, token).await
    }

    pub async fn delete_child(&self, family_id: &str, child_id: &str, token: &str) -> Result<(), AppError> {
        self.callables
            .invoke("deleteChild", json!({ "familyId": family_id, "childId": child_id }), Some(token))
            .await
            .map(|_| ())
            .map_err(map_err)
    }

    pub async fn delete_family(&self, family_id: &str, token: &str) -> Result<(), AppError> {
        self.callables
            .invoke("deleteFamily", json!({ "familyId": family_id }), Some(token))
            .await
            .map(|_| ())
            .map_err(map_err)
    }
}

fn child_body(name: &str, age_band: &str) -> Value {
    json!({
        "displayName": name.trim(),
        "ageBand": normalize_age_band(age_band),
        "avatarId": "FOX",
        "language": "en",
    })
}

/// UI uses `7_to_9`; Firestore/Android store `AGE_7_TO_9`.
pub fn normalize_age_band(raw: &str) -> String {
    let t = raw.trim().to_ascii_uppercase();
    if t.is_empty() {
        return "AGE_7_TO_9".into();
    }
    if t.starts_with("AGE_") {
        t
    } else {
        format!("AGE_{t}")
    }
}

/// UI form of an Android age band (`AGE_7_TO_9` → `7_to_9`).
pub fn ui_age_band(raw: &str) -> String {
    raw.trim().trim_start_matches("AGE_").to_ascii_lowercase()
}

#[cfg(test)]
mod tests {
    use super::*;
    use meritscreen_firebase::mock::{MockCallableClient, MockFirestoreClient};

    #[test]
    fn age_band_round_trip() {
        assert_eq!(normalize_age_band("7_to_9"), "AGE_7_TO_9");
        assert_eq!(normalize_age_band("AGE_3_TO_6"), "AGE_3_TO_6");
        assert_eq!(ui_age_band("AGE_10_TO_12"), "10_to_12");
    }

    #[test]
    fn policy_value_matches_android_keys() {
        let v = policy_to_value(&ChildPolicy::default());
        for k in ["quizMode", "failLockScope", "questionsPerQuiz", "passScorePercent", "defaultBlockMinutes", "paused"] {
            assert!(v.get(k).is_some(), "missing {k}");
        }
        assert_eq!(v["failLockScope"], "all_non_emergency");
    }

    #[tokio::test]
    async fn load_family_reads_children() {
        let fs = Arc::new(MockFirestoreClient::default());
        {
            let mut d = fs.docs.lock().unwrap();
            d.insert("users/u1".into(), json!({"fields": {"familyId": {"stringValue": "f1"}}}));
            d.insert("families/f1".into(), json!({"fields": {"parentPinHash": {"stringValue": "pbkdf2$1$a$b"}}}));
            d.insert(
                "families/f1/children".into(),
                json!({"documents": [{"name": "projects/p/databases/(default)/documents/families/f1/children/c1",
                    "fields": {"displayName": {"stringValue": "Asha"}, "ageBand": {"stringValue": "AGE_7_TO_9"}}}]}),
            );
        }
        let r = ParentRemote::new(fs, Arc::new(MockCallableClient::default()), "p");
        let fam = r.load_family("u1", "t").await.unwrap().unwrap();
        assert_eq!(fam.family_id, "f1");
        assert_eq!(fam.parent_pin_hash.as_deref(), Some("pbkdf2$1$a$b"));
        assert_eq!(fam.children.len(), 1);
        assert_eq!(fam.children[0].display_name, "Asha");
        assert!(r.load_family("nobody", "t").await.unwrap().is_none());
    }

    #[tokio::test]
    async fn writes_commit_ok() {
        let r = ParentRemote::new(
            Arc::new(MockFirestoreClient::default()),
            Arc::new(MockCallableClient::default()),
            "p",
        );
        r.create_family("u", "e@x.com", "f", "h", "c", "Kid", "7_to_9", &ChildPolicy::default(), "t")
            .await
            .unwrap();
        r.write_app_rules("f", "c", &[AppRule::new("mac:com/x", "com.x")], "t").await.unwrap();
        r.delete_child("f", "c", "t").await.unwrap();
    }
}
