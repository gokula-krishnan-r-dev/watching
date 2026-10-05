//! Firestore document paths shared with Android child sync.

pub fn family_doc(family_id: &str) -> String {
    format!("families/{family_id}")
}

pub fn child_doc(family_id: &str, child_id: &str) -> String {
    format!("families/{family_id}/children/{child_id}")
}

pub fn policy_current(family_id: &str, child_id: &str) -> String {
    format!("families/{family_id}/children/{child_id}/policy/current")
}

pub fn app_rules_collection(family_id: &str, child_id: &str) -> String {
    format!("families/{family_id}/children/{child_id}/appRules")
}

pub fn device_doc(family_id: &str, child_id: &str, device_id: &str) -> String {
    format!("families/{family_id}/children/{child_id}/devices/{device_id}")
}

pub fn usage_day(family_id: &str, child_id: &str, day: &str) -> String {
    format!("families/{family_id}/children/{child_id}/usageDays/{day}")
}

pub fn quiz_attempt(family_id: &str, child_id: &str, attempt_id: &str) -> String {
    format!("families/{family_id}/children/{child_id}/quizAttempts/{attempt_id}")
}

pub fn skill_state_current(family_id: &str, child_id: &str) -> String {
    format!("families/{family_id}/children/{child_id}/skillState/current")
}
