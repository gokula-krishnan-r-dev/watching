//! Child remote sync contracts — policy pull, usage/quiz/skill upload, device status.
//! Guardian-only. UI must never depend on this module.

use async_trait::async_trait;
use meritscreen_core::{AppRule, ChildPolicy};
use serde::{Deserialize, Serialize};
use serde_json::{json, Map, Value};

use crate::error::FirebaseResult;
use crate::traits::{DeviceHeartbeatPatch, InstalledAppUpload};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceIds {
    pub family_id: String,
    pub child_id: String,
    pub device_id: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PolicyBundle {
    pub policy: ChildPolicy,
    pub app_rules: Vec<AppRule>,
    pub parent_pin_hash: Option<String>,
    pub child_display_name: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceStatus {
    pub revoked: bool,
    pub exists: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UsageDayUpload {
    pub day: String,
    pub minutes_used: u32,
    #[serde(default)]
    pub minutes_by_app: Map<String, Value>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizAttemptUpload {
    pub attempt_id: String,
    pub created_at_epoch_ms: i64,
    pub topics: Vec<String>,
    pub score: u32,
    pub total: u32,
    pub passed: bool,
    pub extra_minutes_granted: u32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SkillStateUpload {
    pub topic: String,
    pub level: i32,
    pub streak_correct: i32,
    pub weak: bool,
    #[serde(default)]
    pub weak_concepts: Vec<String>,
    #[serde(default)]
    pub total_attempts: u32,
    #[serde(default)]
    pub total_correct: u32,
}

/// Full child cloud surface used by Guardian schedulers.
#[async_trait]
pub trait ChildRemoteClient: Send + Sync {
    async fn pull_policy(
        &self,
        ids: &DeviceIds,
        id_token: &str,
    ) -> FirebaseResult<Option<PolicyBundle>>;

    async fn fetch_device_status(
        &self,
        ids: &DeviceIds,
        id_token: &str,
    ) -> FirebaseResult<DeviceStatus>;

    /// `Ok(false)` when the family document is missing (deleted).
    async fn family_exists(&self, family_id: &str, id_token: &str) -> FirebaseResult<bool>;

    async fn upload_usage_day(
        &self,
        ids: &DeviceIds,
        upload: &UsageDayUpload,
        id_token: &str,
    ) -> FirebaseResult<()>;

    /// Returns `Ok(true)` if written, `Ok(false)` if attempt already existed (idempotent).
    async fn upload_quiz_attempt(
        &self,
        ids: &DeviceIds,
        upload: &QuizAttemptUpload,
        id_token: &str,
    ) -> FirebaseResult<bool>;

    async fn replace_skill_state(
        &self,
        ids: &DeviceIds,
        skills: &[SkillStateUpload],
        id_token: &str,
    ) -> FirebaseResult<()>;

    async fn patch_heartbeat(
        &self,
        ids: &DeviceIds,
        patch: DeviceHeartbeatPatch,
        id_token: &str,
    ) -> FirebaseResult<()>;

    async fn upload_installed_apps(
        &self,
        ids: &DeviceIds,
        apps: &[InstalledAppUpload],
        inventory_hash: &str,
        id_token: &str,
    ) -> FirebaseResult<()>;
}

/// Parse a simplified or Firestore-REST-ish policy document into [`ChildPolicy`].
pub fn policy_from_value(value: &Value) -> ChildPolicy {
    let fields = unwrap_fields(value);
    let mut policy = ChildPolicy::default();
    if let Some(mode) = string_field(&fields, "quizMode") {
        policy.quiz_mode = meritscreen_core::QuizMode::from_storage(Some(&mode));
    }
    if let Some(n) = int_field(&fields, "quizIntervalMinutes") {
        policy.quiz_interval_minutes = n as u32;
    }
    if let Some(n) = int_field(&fields, "questionsPerQuiz") {
        policy.questions_per_quiz = n as u32;
    }
    if let Some(n) = int_field(&fields, "passScorePercent") {
        policy.pass_score_percent = n as u32;
    }
    if let Some(n) = int_field(&fields, "defaultBlockMinutes") {
        policy.default_block_minutes = n as u32;
    }
    if let Some(n) = int_field(&fields, "defaultCooldownMinutes") {
        policy.default_cooldown_minutes = n as u32;
    }
    if let Some(n) = int_field(&fields, "dailyCeilingMinutes") {
        policy.daily_ceiling_minutes = Some(n as u32);
    }
    if let Some(b) = bool_field(&fields, "allowRetryDuringCooldown") {
        policy.allow_retry_during_cooldown = b;
    }
    if let Some(b) = bool_field(&fields, "rewardsEnabled") {
        policy.rewards_enabled = b;
    }
    if let Some(b) = bool_field(&fields, "paused") {
        policy.paused = b;
    }
    policy
}

pub fn app_rule_from_value(app_id: &str, value: &Value) -> AppRule {
    let fields = unwrap_fields(value);
    let package = string_field(&fields, "packageOrBundleId").unwrap_or_else(|| app_id.into());
    let mut rule = AppRule::new(app_id, package);
    if let Some(name) = string_field(&fields, "displayName") {
        rule.display_name = name;
    }
    if let Some(b) = bool_field(&fields, "allowed") {
        rule.allowed = b;
    }
    if let Some(b) = bool_field(&fields, "isEmergency") {
        rule.is_emergency = b;
    }
    if let Some(n) = int_field(&fields, "blockMinutes") {
        rule.block_minutes = n as u32;
    }
    if let Some(n) = int_field(&fields, "cooldownMinutes") {
        rule.cooldown_minutes = n as u32;
    }
    rule
}

fn unwrap_fields(value: &Value) -> Value {
    value
        .get("fields")
        .cloned()
        .unwrap_or_else(|| value.clone())
}

fn string_field(fields: &Value, key: &str) -> Option<String> {
    let v = fields.get(key)?;
    if let Some(s) = v.as_str() {
        return Some(s.into());
    }
    v.get("stringValue")
        .and_then(|x| x.as_str())
        .map(str::to_string)
}

fn int_field(fields: &Value, key: &str) -> Option<i64> {
    let v = fields.get(key)?;
    if let Some(n) = v.as_i64() {
        return Some(n);
    }
    if let Some(n) = v.as_u64() {
        return Some(n as i64);
    }
    v.get("integerValue")
        .and_then(|x| x.as_str())
        .and_then(|s| s.parse().ok())
        .or_else(|| v.get("integerValue").and_then(|x| x.as_i64()))
}

fn bool_field(fields: &Value, key: &str) -> Option<bool> {
    let v = fields.get(key)?;
    if let Some(b) = v.as_bool() {
        return Some(b);
    }
    v.get("booleanValue").and_then(|x| x.as_bool())
}

/// Encode a JSON object as Firestore REST `fields` map (string/int/bool/map/array basics).
pub fn to_firestore_fields(value: &Value) -> Value {
    match value {
        Value::Object(map) => {
            let mut fields = Map::new();
            for (k, v) in map {
                fields.insert(k.clone(), to_firestore_value(v));
            }
            Value::Object(fields)
        }
        other => json!({ "value": to_firestore_value(other) }),
    }
}

pub fn to_firestore_value(value: &Value) -> Value {
    match value {
        Value::Null => json!({ "nullValue": null }),
        Value::Bool(b) => json!({ "booleanValue": b }),
        Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                json!({ "integerValue": i.to_string() })
            } else if let Some(u) = n.as_u64() {
                json!({ "integerValue": u.to_string() })
            } else {
                json!({ "doubleValue": n.as_f64().unwrap_or(0.0) })
            }
        }
        Value::String(s) => json!({ "stringValue": s }),
        Value::Array(arr) => json!({
            "arrayValue": { "values": arr.iter().map(to_firestore_value).collect::<Vec<_>>() }
        }),
        Value::Object(map) => {
            let mut fields = Map::new();
            for (k, v) in map {
                fields.insert(k.clone(), to_firestore_value(v));
            }
            json!({ "mapValue": { "fields": fields } })
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_plain_policy_json() {
        let v = json!({
            "quizMode": "device_interval",
            "quizIntervalMinutes": 20,
            "paused": false
        });
        let p = policy_from_value(&v);
        assert_eq!(p.quiz_mode.as_storage_str(), "device_interval");
        assert_eq!(p.quiz_interval_minutes, 20);
    }
}
