//! REST implementations of device / child sync on top of [`FirestoreClient`].

use async_trait::async_trait;
use serde_json::{json, Value};
use tracing::debug;

use crate::child_sync::{
    app_rule_from_value, policy_from_value, to_firestore_fields, ChildRemoteClient, DeviceIds,
    DeviceStatus, PolicyBundle, QuizAttemptUpload, SkillStateUpload, UsageDayUpload,
};
use crate::error::{FirebaseError, FirebaseResult};
use crate::paths;
use crate::traits::{DeviceHeartbeatPatch, DeviceSyncClient, FirestoreClient, InstalledAppUpload};

/// Thin adapter: [`DeviceSyncClient`] needs full path context — use [`RestChildRemoteClient`] instead.
/// Kept so call sites that only patch heartbeat by device id can be migrated.
pub struct RestDeviceSyncClient<F: FirestoreClient> {
    firestore: F,
    family_id: String,
    child_id: String,
}

impl<F: FirestoreClient> RestDeviceSyncClient<F> {
    pub fn new(firestore: F, family_id: impl Into<String>, child_id: impl Into<String>) -> Self {
        Self {
            firestore,
            family_id: family_id.into(),
            child_id: child_id.into(),
        }
    }
}

#[async_trait]
impl<F: FirestoreClient + 'static> DeviceSyncClient for RestDeviceSyncClient<F> {
    async fn patch_heartbeat(
        &self,
        device_id: &str,
        patch: DeviceHeartbeatPatch,
        id_token: &str,
    ) -> FirebaseResult<()> {
        let ids = DeviceIds {
            family_id: self.family_id.clone(),
            child_id: self.child_id.clone(),
            device_id: device_id.into(),
        };
        let remote = RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: "default",
        };
        remote.patch_heartbeat(&ids, patch, id_token).await
    }

    async fn upload_installed_apps(
        &self,
        device_id: &str,
        apps: &[InstalledAppUpload],
        inventory_hash: &str,
        id_token: &str,
    ) -> FirebaseResult<()> {
        let ids = DeviceIds {
            family_id: self.family_id.clone(),
            child_id: self.child_id.clone(),
            device_id: device_id.into(),
        };
        let remote = RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: "default",
        };
        remote
            .upload_installed_apps(&ids, apps, inventory_hash, id_token)
            .await
    }
}

pub struct RestChildRemoteClient<'a, F: FirestoreClient> {
    pub firestore: &'a F,
    pub project_id: &'a str,
}

/// Owned wrapper for `'static` trait object use in Guardian.
pub struct OwnedRestChildRemoteClient<F: FirestoreClient + Send + Sync> {
    firestore: F,
    project_id: String,
}

impl<F: FirestoreClient + Send + Sync> OwnedRestChildRemoteClient<F> {
    pub fn new(firestore: F, project_id: impl Into<String>) -> Self {
        Self {
            firestore,
            project_id: project_id.into(),
        }
    }
}

#[async_trait]
impl<F: FirestoreClient + Send + Sync + 'static> ChildRemoteClient
    for OwnedRestChildRemoteClient<F>
{
    async fn pull_policy(
        &self,
        ids: &DeviceIds,
        id_token: &str,
    ) -> FirebaseResult<Option<PolicyBundle>> {
        RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: &self.project_id,
        }
        .pull_policy(ids, id_token)
        .await
    }

    async fn fetch_device_status(
        &self,
        ids: &DeviceIds,
        id_token: &str,
    ) -> FirebaseResult<DeviceStatus> {
        RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: &self.project_id,
        }
        .fetch_device_status(ids, id_token)
        .await
    }

    async fn family_exists(&self, family_id: &str, id_token: &str) -> FirebaseResult<bool> {
        RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: &self.project_id,
        }
        .family_exists(family_id, id_token)
        .await
    }

    async fn upload_usage_day(
        &self,
        ids: &DeviceIds,
        upload: &UsageDayUpload,
        id_token: &str,
    ) -> FirebaseResult<()> {
        RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: &self.project_id,
        }
        .upload_usage_day(ids, upload, id_token)
        .await
    }

    async fn upload_quiz_attempt(
        &self,
        ids: &DeviceIds,
        upload: &QuizAttemptUpload,
        id_token: &str,
    ) -> FirebaseResult<bool> {
        RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: &self.project_id,
        }
        .upload_quiz_attempt(ids, upload, id_token)
        .await
    }

    async fn replace_skill_state(
        &self,
        ids: &DeviceIds,
        skills: &[SkillStateUpload],
        id_token: &str,
    ) -> FirebaseResult<()> {
        RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: &self.project_id,
        }
        .replace_skill_state(ids, skills, id_token)
        .await
    }

    async fn patch_heartbeat(
        &self,
        ids: &DeviceIds,
        patch: DeviceHeartbeatPatch,
        id_token: &str,
    ) -> FirebaseResult<()> {
        RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: &self.project_id,
        }
        .patch_heartbeat(ids, patch, id_token)
        .await
    }

    async fn upload_installed_apps(
        &self,
        ids: &DeviceIds,
        apps: &[InstalledAppUpload],
        inventory_hash: &str,
        id_token: &str,
    ) -> FirebaseResult<()> {
        RestChildRemoteClient {
            firestore: &self.firestore,
            project_id: &self.project_id,
        }
        .upload_installed_apps(ids, apps, inventory_hash, id_token)
        .await
    }
}

impl<'a, F: FirestoreClient> RestChildRemoteClient<'a, F> {
    async fn pull_policy(
        &self,
        ids: &DeviceIds,
        id_token: &str,
    ) -> FirebaseResult<Option<PolicyBundle>> {
        let policy_path = paths::policy_current(&ids.family_id, &ids.child_id);
        let policy_doc = self.firestore.get_document(&policy_path, id_token).await?;
        let Some(policy_doc) = policy_doc else {
            return Ok(None);
        };
        let policy = policy_from_value(&policy_doc);

        // App rules: REST list is not on FirestoreClient — pull known empty and rely on
        // document map stored under policy bundle in sync_state when list API lands.
        // For now, accept optional `appRules` array embedded in policy doc (desktop write path).
        let mut app_rules = Vec::new();
        if let Some(arr) = policy_doc
            .get("appRules")
            .or_else(|| policy_doc.pointer("/fields/appRules"))
            .and_then(|v| v.as_array())
        {
            for (i, item) in arr.iter().enumerate() {
                let id = item
                    .get("appId")
                    .and_then(|x| x.as_str())
                    .unwrap_or(&format!("rule-{i}"))
                    .to_string();
                app_rules.push(app_rule_from_value(&id, item));
            }
        }

        let family = self
            .firestore
            .get_document(&paths::family_doc(&ids.family_id), id_token)
            .await?;
        let parent_pin_hash = family.as_ref().and_then(|f| {
            f.get("parentPinHash")
                .and_then(|x| x.as_str())
                .map(str::to_string)
                .or_else(|| {
                    f.pointer("/fields/parentPinHash/stringValue")
                        .and_then(|x| x.as_str())
                        .map(str::to_string)
                })
        });

        let child = self
            .firestore
            .get_document(&paths::child_doc(&ids.family_id, &ids.child_id), id_token)
            .await?;
        let child_display_name = child.as_ref().and_then(|c| {
            c.get("displayName")
                .and_then(|x| x.as_str())
                .map(str::to_string)
                .or_else(|| {
                    c.pointer("/fields/displayName/stringValue")
                        .and_then(|x| x.as_str())
                        .map(str::to_string)
                })
        });

        Ok(Some(PolicyBundle {
            policy,
            app_rules,
            parent_pin_hash,
            child_display_name,
        }))
    }

    async fn fetch_device_status(
        &self,
        ids: &DeviceIds,
        id_token: &str,
    ) -> FirebaseResult<DeviceStatus> {
        let path = paths::device_doc(&ids.family_id, &ids.child_id, &ids.device_id);
        match self.firestore.get_document(&path, id_token).await? {
            None => Ok(DeviceStatus {
                revoked: false,
                exists: false,
            }),
            Some(doc) => {
                let revoked = doc
                    .get("revoked")
                    .and_then(|x| x.as_bool())
                    .or_else(|| {
                        doc.pointer("/fields/revoked/booleanValue")
                            .and_then(|x| x.as_bool())
                    })
                    .unwrap_or(false);
                Ok(DeviceStatus {
                    revoked,
                    exists: true,
                })
            }
        }
    }

    async fn family_exists(&self, family_id: &str, id_token: &str) -> FirebaseResult<bool> {
        Ok(self
            .firestore
            .get_document(&paths::family_doc(family_id), id_token)
            .await?
            .is_some())
    }

    async fn upload_usage_day(
        &self,
        ids: &DeviceIds,
        upload: &UsageDayUpload,
        id_token: &str,
    ) -> FirebaseResult<()> {
        let path = paths::usage_day(&ids.family_id, &ids.child_id, &upload.day);
        let body = json!({
            "minutesUsed": upload.minutes_used,
            "minutesByApp": upload.minutes_by_app,
            "updatedAt": chrono_now_ms(),
        });
        self.merge_doc(&path, &body, id_token).await
    }

    async fn upload_quiz_attempt(
        &self,
        ids: &DeviceIds,
        upload: &QuizAttemptUpload,
        id_token: &str,
    ) -> FirebaseResult<bool> {
        let path = paths::quiz_attempt(&ids.family_id, &ids.child_id, &upload.attempt_id);
        if self
            .firestore
            .get_document(&path, id_token)
            .await?
            .is_some()
        {
            return Ok(false);
        }
        let body = json!({
            "createdAtEpochMs": upload.created_at_epoch_ms,
            "topics": upload.topics,
            "score": upload.score,
            "total": upload.total,
            "passed": upload.passed,
            "extraMinutesGranted": upload.extra_minutes_granted,
        });
        self.create_doc(&path, &body, id_token).await?;
        Ok(true)
    }

    async fn replace_skill_state(
        &self,
        ids: &DeviceIds,
        skills: &[SkillStateUpload],
        id_token: &str,
    ) -> FirebaseResult<()> {
        let mut topics = serde_json::Map::new();
        for s in skills {
            topics.insert(
                s.topic.clone(),
                json!({
                    "level": s.level,
                    "streakCorrect": s.streak_correct,
                    "weak": s.weak,
                    "weakConcepts": s.weak_concepts,
                    "totalAttempts": s.total_attempts,
                    "totalCorrect": s.total_correct,
                }),
            );
        }
        let path = paths::skill_state_current(&ids.family_id, &ids.child_id);
        let body = json!({ "topics": topics, "updatedAt": chrono_now_ms() });
        self.merge_doc(&path, &body, id_token).await
    }

    async fn patch_heartbeat(
        &self,
        ids: &DeviceIds,
        patch: DeviceHeartbeatPatch,
        id_token: &str,
    ) -> FirebaseResult<()> {
        debug!(device = %ids.device_id, "cloud heartbeat patch");
        let path = paths::device_doc(&ids.family_id, &ids.child_id, &ids.device_id);
        let body = json!({
            "guardianState": patch.guardian_state,
            "enforcementTier": patch.enforcement_tier,
            "tamperFlags": patch.tamper_flags,
            "agentVersion": patch.agent_version,
            "osBuild": patch.os_build,
            "lastSeenAt": chrono_now_ms(),
        });
        self.merge_doc(&path, &body, id_token).await
    }

    async fn upload_installed_apps(
        &self,
        ids: &DeviceIds,
        apps: &[InstalledAppUpload],
        inventory_hash: &str,
        id_token: &str,
    ) -> FirebaseResult<()> {
        let path = paths::device_doc(&ids.family_id, &ids.child_id, &ids.device_id);
        let list: Vec<Value> = apps
            .iter()
            .map(|a| {
                json!({
                    "packageName": a.package_name,
                    "label": a.label,
                    "iconHash": a.icon_hash,
                })
            })
            .collect();
        let body = json!({
            "installedApps": list,
            "installedAppsUpdatedAt": chrono_now_ms(),
            "inventoryHash": inventory_hash,
        });
        self.merge_doc(&path, &body, id_token).await
    }

    async fn merge_doc(&self, path: &str, body: &Value, id_token: &str) -> FirebaseResult<()> {
        let name = document_name(path, self.project_id);
        let fields = to_firestore_fields(body);
        let field_paths: Vec<String> = body
            .as_object()
            .map(|m| m.keys().cloned().collect())
            .unwrap_or_default();
        let write = json!({
            "update": { "name": name, "fields": fields },
            "updateMask": { "fieldPaths": field_paths },
        });
        self.firestore
            .commit(Value::Array(vec![write]), id_token)
            .await?;
        Ok(())
    }

    async fn create_doc(&self, path: &str, body: &Value, id_token: &str) -> FirebaseResult<()> {
        let name = document_name(path, self.project_id);
        let fields = to_firestore_fields(body);
        let write = json!({
            "update": { "name": name, "fields": fields },
            "currentDocument": { "exists": false },
        });
        match self
            .firestore
            .commit(Value::Array(vec![write]), id_token)
            .await
        {
            Ok(_) => Ok(()),
            Err(FirebaseError::Http { status: 409, .. }) => Ok(()),
            Err(e) => Err(e),
        }
    }
}

fn document_name(path: &str, project_id: &str) -> String {
    format!(
        "projects/{project_id}/databases/(default)/documents/{}",
        path.trim_start_matches('/')
    )
}

fn chrono_now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}
