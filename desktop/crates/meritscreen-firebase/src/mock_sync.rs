//! In-memory [`ChildRemoteClient`] for Guardian sync unit tests.

use std::collections::{HashMap, HashSet};
use std::sync::Mutex;

use async_trait::async_trait;
use serde_json::Value;

use crate::child_sync::{
    ChildRemoteClient, DeviceIds, DeviceStatus, PolicyBundle, QuizAttemptUpload, SkillStateUpload,
    UsageDayUpload,
};
use crate::error::FirebaseResult;
use crate::paths;
use crate::traits::{DeviceHeartbeatPatch, InstalledAppUpload};

#[derive(Default)]
pub struct MockChildRemoteClient {
    pub policies: Mutex<HashMap<String, PolicyBundle>>,
    pub devices: Mutex<HashMap<String, Value>>,
    pub families: Mutex<HashSet<String>>,
    pub usage: Mutex<Vec<(String, UsageDayUpload)>>,
    pub quiz: Mutex<HashSet<String>>,
    pub skills: Mutex<Vec<(String, Vec<SkillStateUpload>)>>,
    pub heartbeats: Mutex<Vec<(String, DeviceHeartbeatPatch)>>,
    pub inventories: Mutex<Vec<(String, String, usize)>>,
    pub fail_next: Mutex<bool>,
}

impl MockChildRemoteClient {
    pub fn with_family(family_id: &str) -> Self {
        let m = Self::default();
        m.families.lock().unwrap().insert(family_id.into());
        m
    }

    fn key(ids: &DeviceIds) -> String {
        format!("{}/{}/{}", ids.family_id, ids.child_id, ids.device_id)
    }
}

#[async_trait]
impl ChildRemoteClient for MockChildRemoteClient {
    async fn pull_policy(
        &self,
        ids: &DeviceIds,
        _id_token: &str,
    ) -> FirebaseResult<Option<PolicyBundle>> {
        if *self.fail_next.lock().unwrap() {
            *self.fail_next.lock().unwrap() = false;
            return Err(crate::error::FirebaseError::Other("mock offline".into()));
        }
        let key = format!("{}/{}", ids.family_id, ids.child_id);
        Ok(self.policies.lock().unwrap().get(&key).cloned())
    }

    async fn fetch_device_status(
        &self,
        ids: &DeviceIds,
        _id_token: &str,
    ) -> FirebaseResult<DeviceStatus> {
        let key = Self::key(ids);
        let devices = self.devices.lock().unwrap();
        match devices.get(&key) {
            None => Ok(DeviceStatus {
                revoked: false,
                exists: false,
            }),
            Some(v) => Ok(DeviceStatus {
                exists: true,
                revoked: v.get("revoked").and_then(|x| x.as_bool()).unwrap_or(false),
            }),
        }
    }

    async fn family_exists(&self, family_id: &str, _id_token: &str) -> FirebaseResult<bool> {
        Ok(self.families.lock().unwrap().contains(family_id))
    }

    async fn upload_usage_day(
        &self,
        ids: &DeviceIds,
        upload: &UsageDayUpload,
        _id_token: &str,
    ) -> FirebaseResult<()> {
        self.usage
            .lock()
            .unwrap()
            .push((Self::key(ids), upload.clone()));
        Ok(())
    }

    async fn upload_quiz_attempt(
        &self,
        _ids: &DeviceIds,
        upload: &QuizAttemptUpload,
        _id_token: &str,
    ) -> FirebaseResult<bool> {
        let mut set = self.quiz.lock().unwrap();
        if set.contains(&upload.attempt_id) {
            return Ok(false);
        }
        set.insert(upload.attempt_id.clone());
        Ok(true)
    }

    async fn replace_skill_state(
        &self,
        ids: &DeviceIds,
        skills: &[SkillStateUpload],
        _id_token: &str,
    ) -> FirebaseResult<()> {
        self.skills
            .lock()
            .unwrap()
            .push((Self::key(ids), skills.to_vec()));
        Ok(())
    }

    async fn patch_heartbeat(
        &self,
        ids: &DeviceIds,
        patch: DeviceHeartbeatPatch,
        _id_token: &str,
    ) -> FirebaseResult<()> {
        // Ensure device exists after first heartbeat.
        self.devices
            .lock()
            .unwrap()
            .entry(Self::key(ids))
            .or_insert_with(|| serde_json::json!({ "revoked": false }));
        self.heartbeats
            .lock()
            .unwrap()
            .push((Self::key(ids), patch));
        Ok(())
    }

    async fn upload_installed_apps(
        &self,
        ids: &DeviceIds,
        apps: &[InstalledAppUpload],
        inventory_hash: &str,
        _id_token: &str,
    ) -> FirebaseResult<()> {
        self.inventories
            .lock()
            .unwrap()
            .push((Self::key(ids), inventory_hash.into(), apps.len()));
        let _ = paths::device_doc(&ids.family_id, &ids.child_id, &ids.device_id);
        Ok(())
    }
}
