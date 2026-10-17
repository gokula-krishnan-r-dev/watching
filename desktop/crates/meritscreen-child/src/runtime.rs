//! Local child runtime — SessionEngine + AdaptiveQuizEngine, no Firebase.

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

use chrono::Datelike;
use meritscreen_core::app_config::{PAIRING_CODE_LENGTH, QUIZ_LOCKOUT_SECONDS};
use meritscreen_core::rewards::{award_quiz_pass, AwardResult};
use meritscreen_core::{
    load_builtin_bank, AdaptiveQuizEngine, AgeBand, AppError, AppRule, ChildPolicy, ExplorerProgress,
    QuizQuestion, QuizMode, SessionEngine, SessionPhase, SessionSnapshot, StickerUnlock, TopicSkill,
};
use meritscreen_security::{accounts, hash_parent_pin, verify_parent_pin, SecretStore};
use parking_lot::Mutex;
use rand::rngs::StdRng;
use rand::SeedableRng;
use serde::{Deserialize, Serialize};
use tracing::info;
use uuid::Uuid;

use crate::models::{
    PairingCredential,
    default_checklist, snapshot_metrics, ChecklistItem, ChildScreen, ChildUiState, LastAwardDto,
    LauncherApp, NurseryDto, QuizChoiceDto, QuizLockDto, QuizPromptDto,
};

/// 30 days of question history are excluded from picking (Android `QUIZ_QUESTION_HISTORY_DAYS`).
const HISTORY_WINDOW_MS: i64 = 30 * 24 * 60 * 60_000;

/// A completed quiz, queued until the Guardian uploads it (Android `QuizAttemptEntity`, dirty flag).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizAttemptRecord {
    pub attempt_id: String,
    pub created_at_epoch_ms: i64,
    pub topics: Vec<String>,
    pub score: u32,
    pub total: u32,
    pub passed: bool,
    pub extra_minutes_granted: u32,
    pub time_spent_seconds: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RecentQuestion {
    id: String,
    at_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PersistedChildSession {
    pub paired: bool,
    pub child_id: Option<String>,
    pub child_name: String,
    pub pin_hash: Option<String>,
    pub policy: ChildPolicy,
    pub session: SessionSnapshot,
    pub checklist_complete: bool,
    pub elapsed_ms: i64,
    pub saved_epoch_day: i64,
    pub saved_wall_ms: i64,
    #[serde(default)]
    pub active_app_id: Option<String>,
    #[serde(default)]
    pub active_app_name: Option<String>,
    #[serde(default)]
    pub app_usage_seconds: HashMap<String, u64>,
    #[serde(default)]
    pub app_display_names: HashMap<String, String>,
    #[serde(default)]
    pub quiz_seconds_today: u64,
    #[serde(default)]
    pub quiz_attempts_today: u32,
    #[serde(default)]
    pub quiz_passes_today: u32,
    #[serde(default)]
    pub daily_usage_history: HashMap<String, HashMap<String, u32>>,
    #[serde(default)]
    pub daily_total_history: HashMap<String, u32>,
    /// Adaptive skill state per topic (Android skillState) — persists across sessions.
    #[serde(default)]
    pub skills: HashMap<String, TopicSkill>,
    #[serde(default)]
    pub recent_questions: Vec<RecentQuestion>,
    #[serde(default)]
    pub explorer: ExplorerProgress,
    #[serde(default)]
    pub stickers: Vec<StickerUnlock>,
    #[serde(default)]
    pub pending_attempts: Vec<QuizAttemptRecord>,
    #[serde(default)]
    pub nursery_recent_videos: Vec<String>,
    #[serde(default)]
    pub nursery_last_category: Option<String>,
    #[serde(default)]
    pub last_award: Option<LastAwardDto>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChildReportTelemetry {
    pub child_id: Option<String>,
    pub child_name: String,
    pub minutes_used_today: u32,
    pub minutes_by_app: HashMap<String, u32>,
    pub app_labels: HashMap<String, String>,
    pub quiz_minutes_today: u32,
    pub quiz_attempts_today: u32,
    pub quiz_passes_today: u32,
    pub daily_usage_history: HashMap<String, HashMap<String, u32>>,
    pub daily_total_history: HashMap<String, u32>,
    pub skills: Vec<TopicSkill>,
    pub pending_attempts: Vec<QuizAttemptRecord>,
    pub explorer: ExplorerProgress,
    pub stickers: Vec<StickerUnlock>,
}

fn session_file_path(secrets_dir: Option<&Path>) -> Option<PathBuf> {
    if let Some(dir) = secrets_dir {
        return Some(dir.join("child_session.json"));
    }
    if let Some(proj) = directories::ProjectDirs::from("com", "MeritScreen", "MeritScreen") {
        let dir = proj.config_dir();
        let _ = std::fs::create_dir_all(dir);
        Some(dir.join("child_session.json"))
    } else {
        let dir = std::env::temp_dir().join("meritscreen");
        let _ = std::fs::create_dir_all(&dir);
        Some(dir.join("child_session.json"))
    }
}

struct ActiveQuiz {
    questions: Vec<QuizQuestion>,
    index: usize,
    correct: u32,
    used_ids: HashSet<String>,
    skills: HashMap<String, TopicSkill>,
    lock_until_ms: Option<i64>,
    last_wrong_concept: Option<String>,
    last_wrong_question: Option<QuizQuestion>,
    /// Monotonic start of the current question (real response times, Android SystemClock).
    started_elapsed_ms: i64,
    quiz_seconds_at_start: u64,
}

struct Inner {
    paired: bool,
    child_id: Option<String>,
    child_name: String,
    age_band: AgeBand,
    pin_hash: Option<String>,
    checklist: Vec<ChecklistItem>,
    checklist_complete: bool,
    policy: ChildPolicy,
    rules: Vec<AppRule>,
    session: SessionSnapshot,
    bank: Vec<QuizQuestion>,
    pack_source: String,
    elapsed_ms: i64,
    quiz: Option<ActiveQuiz>,
    last_feedback: Option<meritscreen_core::QuizAnswerFeedback>,
    last_result: Option<meritscreen_core::QuizSessionResult>,
    screen_override: Option<ChildScreen>,
    secrets_dir: Option<PathBuf>,
    active_app_id: Option<String>,
    active_app_name: Option<String>,
    app_usage_seconds: HashMap<String, u64>,
    app_display_names: HashMap<String, String>,
    quiz_seconds_today: u64,
    quiz_attempts_today: u32,
    quiz_passes_today: u32,
    daily_usage_history: HashMap<String, HashMap<String, u32>>,
    daily_total_history: HashMap<String, u32>,
    skills: HashMap<String, TopicSkill>,
    recent_questions: Vec<RecentQuestion>,
    explorer: ExplorerProgress,
    stickers: Vec<StickerUnlock>,
    pending_attempts: Vec<QuizAttemptRecord>,
    quiz_error: Option<String>,
    last_award: Option<LastAwardDto>,
    nursery: Option<NurseryDto>,
    nursery_recent_videos: Vec<String>,
    nursery_last_category: Option<String>,
    pack_raw: Option<String>,
    pack_merged: bool,
}

fn wall_now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn award_dto(r: &AwardResult) -> LastAwardDto {
    LastAwardDto {
        sticker_title: r.unlocked.as_ref().map(|u| u.title.clone()),
        sticker_emoji: r.unlocked.as_ref().map(|u| u.emoji.clone()),
        sticker_stage_label: r.unlocked.as_ref().map(|_u| {
            meritscreen_core::rewards::StickerStage::for_level(r.progress.explorer_level).label().to_string()
        }),
        xp_gained: r.xp_gained,
        leveled_up: r.leveled_up,
        weekend_bonus_applied: r.weekend_bonus_applied,
    }
}

/// Android `awardQuizPass` against the runtime's persisted progress.
fn award_pass(g: &mut Inner, attempt_id: Option<&str>, source: Option<&str>) -> AwardResult {
    let today = meritscreen_core::session::local_today_key();
    let yesterday = (chrono::Local::now().date_naive() - chrono::Duration::days(1)).format("%Y-%m-%d").to_string();
    let owned: std::collections::HashSet<String> = g.stickers.iter().map(|u| u.sticker_id.clone()).collect();
    let r = award_quiz_pass(
        &g.explorer,
        &owned,
        g.child_id.as_deref().unwrap_or("local"),
        attempt_id,
        source,
        g.policy.rewards_enabled,
        g.policy.weekend_bonus_enabled,
        SessionEngine::is_weekend_local(),
        &today,
        &yesterday,
        wall_now_ms(),
    );
    g.explorer = r.progress.clone();
    if let Some(u) = &r.unlocked {
        g.stickers.push(u.clone());
    }
    g.last_award = Some(award_dto(&r));
    r
}

pub struct ChildRuntime {
    inner: Mutex<Inner>,
}

impl Default for ChildRuntime {
    fn default() -> Self {
        Self::new()
    }
}

impl ChildRuntime {
    pub fn new() -> Self {
        Self::new_with_dir(None)
    }

    /// Use file-backed secrets (tests / isolation).
    pub fn with_secrets_dir(dir: impl Into<PathBuf>) -> Self {
        let p = dir.into();
        Self::new_with_dir(Some(p))
    }

    fn new_with_dir(secrets_dir: Option<PathBuf>) -> Self {
        let bank = load_builtin_bank().unwrap_or_default();
        let pack_source = if bank.is_empty() {
            "empty".into()
        } else {
            "builtin".into()
        };
        let policy = ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            quiz_interval_minutes: 50,
            ..ChildPolicy::default()
        };
        let mut inner = Inner {
            paired: false,
            child_id: None,
            child_name: String::new(),
            age_band: AgeBand::Age7To9,
            pin_hash: None,
            checklist: default_checklist(),
            checklist_complete: false,
            policy,
            rules: demo_rules(),
            session: SessionSnapshot::default(),
            bank,
            pack_source,
            elapsed_ms: 0,
            quiz: None,
            last_feedback: None,
            last_result: None,
            screen_override: None,
            secrets_dir: secrets_dir.clone(),
            active_app_id: None,
            active_app_name: None,
            app_usage_seconds: HashMap::new(),
            app_display_names: HashMap::new(),
            quiz_seconds_today: 0,
            quiz_attempts_today: 0,
            quiz_passes_today: 0,
            daily_usage_history: HashMap::new(),
            daily_total_history: HashMap::new(),
            skills: HashMap::new(),
            recent_questions: Vec::new(),
            explorer: ExplorerProgress::default(),
            stickers: Vec::new(),
            pending_attempts: Vec::new(),
            quiz_error: None,
            last_award: None,
            nursery: None,
            nursery_recent_videos: Vec::new(),
            nursery_last_category: None,
            pack_raw: session_file_path(secrets_dir.as_deref()).and_then(|p| p.parent().map(|d| d.join("quiz_pack.json"))).and_then(|p| std::fs::read_to_string(p).ok()),
            pack_merged: false,
        };

        // Restore persisted session across app restart / PC reboot
        if let Some(path) = session_file_path(secrets_dir.as_deref()) {
            if path.exists() {
                if let Ok(raw) = std::fs::read_to_string(&path) {
                    if let Ok(persisted) = serde_json::from_str::<PersistedChildSession>(&raw) {
                        if persisted.paired {
                            inner.paired = true;
                            inner.child_id = persisted.child_id;
                            inner.child_name = persisted.child_name;
                            inner.pin_hash = persisted.pin_hash;
                            inner.policy = persisted.policy;
                            inner.checklist_complete = persisted.checklist_complete;
                            inner.elapsed_ms = persisted.elapsed_ms;

                            // Handle calendar day rollover:
                            let now_day = chrono::Local::now().date_naive().num_days_from_ce() as i64;
                            let mut restored_session = persisted.session;
                            let mut app_usage = persisted.app_usage_seconds;
                            let app_labels = persisted.app_display_names;
                            let mut quiz_seconds = persisted.quiz_seconds_today;
                            let mut quiz_attempts = persisted.quiz_attempts_today;
                            let mut quiz_passes = persisted.quiz_passes_today;
                            let mut daily_usage = persisted.daily_usage_history;
                            let mut daily_total = persisted.daily_total_history;

                            if persisted.saved_epoch_day != 0 && persisted.saved_epoch_day != now_day {
                                if let Some(prev_date) = chrono::NaiveDate::from_num_days_from_ce_opt(persisted.saved_epoch_day as i32) {
                                    let prev_date_str = prev_date.format("%Y-%m-%d").to_string();
                                    let mut prev_mins_map = HashMap::new();
                                    for (app, sec) in &app_usage {
                                        let mins = if *sec >= 30 && *sec < 60 { 1 } else { (*sec / 60) as u32 };
                                        if mins > 0 {
                                            prev_mins_map.insert(app.clone(), mins);
                                        }
                                    }
                                    daily_usage.insert(prev_date_str.clone(), prev_mins_map);
                                    daily_total.insert(prev_date_str, restored_session.minutes_used_today);
                                }
                                restored_session.minutes_used_today = 0;
                                app_usage.clear();
                                quiz_seconds = 0;
                                quiz_attempts = 0;
                                quiz_passes = 0;
                            }

                            inner.session = restored_session;
                            inner.active_app_id = persisted.active_app_id;
                            inner.active_app_name = persisted.active_app_name;
                            inner.app_usage_seconds = app_usage;
                            inner.app_display_names = app_labels;
                            inner.quiz_seconds_today = quiz_seconds;
                            inner.quiz_attempts_today = quiz_attempts;
                            inner.quiz_passes_today = quiz_passes;
                            inner.daily_usage_history = daily_usage;
                            inner.daily_total_history = daily_total;
                            inner.skills = persisted.skills;
                            inner.recent_questions = persisted.recent_questions;
                            inner.explorer = persisted.explorer;
                            inner.stickers = persisted.stickers;
                            inner.pending_attempts = persisted.pending_attempts;
                            inner.nursery_recent_videos = persisted.nursery_recent_videos;
                            inner.nursery_last_category = persisted.nursery_last_category;
                            inner.last_award = persisted.last_award;
                            inner.screen_override = Some(ChildScreen::Launcher);
                        }
                    }
                }
            }
        }

        Self {
            inner: Mutex::new(inner),
        }
    }

    /// Flush in-memory session metrics to persistent storage immediately.
    pub fn flush_persisted(&self) {
        let g = self.inner.lock();
        self.save_persisted(&g);
    }

    fn save_persisted(&self, g: &Inner) {
        if !g.paired {
            return;
        }
        let Some(path) = session_file_path(g.secrets_dir.as_deref()) else {
            return;
        };
        let now_day = chrono::Local::now().date_naive().num_days_from_ce() as i64;
        let now_wall = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as i64)
            .unwrap_or(0);
        let persisted = PersistedChildSession {
            paired: g.paired,
            child_id: g.child_id.clone(),
            child_name: g.child_name.clone(),
            pin_hash: g.pin_hash.clone(),
            policy: g.policy.clone(),
            session: g.session.clone(),
            checklist_complete: g.checklist_complete,
            elapsed_ms: g.elapsed_ms,
            saved_epoch_day: now_day,
            saved_wall_ms: now_wall,
            active_app_id: g.active_app_id.clone(),
            active_app_name: g.active_app_name.clone(),
            app_usage_seconds: g.app_usage_seconds.clone(),
            app_display_names: g.app_display_names.clone(),
            quiz_seconds_today: g.quiz_seconds_today,
            quiz_attempts_today: g.quiz_attempts_today,
            quiz_passes_today: g.quiz_passes_today,
            daily_usage_history: g.daily_usage_history.clone(),
            daily_total_history: g.daily_total_history.clone(),
            skills: g.skills.clone(),
            recent_questions: g.recent_questions.clone(),
            explorer: g.explorer.clone(),
            stickers: g.stickers.clone(),
            pending_attempts: g.pending_attempts.clone(),
            nursery_recent_videos: g.nursery_recent_videos.clone(),
            nursery_last_category: g.nursery_last_category.clone(),
            last_award: g.last_award.clone(),
        };
        if let Ok(json) = serde_json::to_string_pretty(&persisted) {
            let tmp = path.with_extension("tmp");
            if std::fs::write(&tmp, json).is_ok() {
                let _ = std::fs::rename(tmp, path);
            }
        }
    }

    fn delete_persisted(&self, g: &Inner) {
        if let Some(path) = session_file_path(g.secrets_dir.as_deref()) {
            let _ = std::fs::remove_file(path);
        }
    }

    pub fn is_paired(&self) -> bool {
        self.inner.lock().paired
    }

    /// Permanent per-install device id (alnum, 32 chars) used for `consumePairingToken`.
    /// Survives unpair so a re-pair resumes the same Firestore device doc.
    pub fn device_id(&self) -> String {
        let store = self.secret_store();
        if let Ok(existing) = store.get(accounts::CHILD_DEVICE_ID) {
            let existing = existing.to_string();
            if existing.len() >= 8 && existing.chars().all(|c| c.is_ascii_alphanumeric()) {
                return existing;
            }
        }
        let id = format!("desk{}", Uuid::new_v4().simple());
        let _ = store.set(accounts::CHILD_DEVICE_ID, &id);
        id
    }

    /// Real pairing: bind credentials returned by `consumePairingToken`.
    /// Uses the parent's PIN hash from the family doc (no hardcoded PIN).
    pub fn bind_credential(&self, cred: &PairingCredential) -> Result<(), AppError> {
        if cred.child_id.trim().is_empty() || cred.family_id.trim().is_empty() {
            return Err(AppError::validation("Pairing response was incomplete. Try a new code."));
        }
        if cred
            .parent_pin_hash
            .as_ref()
            .map(|h| h.trim().is_empty())
            .unwrap_or(true)
        {
            return Err(AppError::validation(
                "Your parent hasn’t set a parent PIN yet. Ask them to set one in Watching (Account → Parent PIN), then enter the same code again.",
            ));
        }
        let store = self.secret_store();
        if !cred.refresh_token.is_empty() {
            store
                .set(accounts::CHILD_REFRESH, &cred.refresh_token)
                .map_err(|_| AppError::unknown())?;
        }
        let mut g = self.inner.lock();
        g.paired = true;
        g.child_id = Some(cred.child_id.clone());
        g.child_name = if cred.display_name.trim().is_empty() {
            "Friend".to_string()
        } else {
            cred.display_name.trim().to_string()
        };
        if let Some(band) = AgeBand::parse(&cred.age_band) {
            g.age_band = band;
        }
        g.pin_hash = cred
            .parent_pin_hash
            .as_ref()
            .filter(|h| !h.trim().is_empty())
            .cloned();
        g.checklist_complete = true;
        g.checklist = default_checklist();
        for item in &mut g.checklist {
            item.done = true;
        }
        g.screen_override = Some(ChildScreen::Launcher);
        g.session =
            SessionEngine::start_device_interval(g.session.clone(), g.elapsed_ms, &g.policy);
        g.quiz = None;
        self.save_persisted(&g);
        Ok(())
    }

    /// C01 — demo pairing: 6-digit code → local credential in secret store → Child role.
    pub fn pair(&self, code: &str, child_name: Option<&str>) -> Result<(), AppError> {
        let code = code.trim().replace(' ', "");
        if code.len() != PAIRING_CODE_LENGTH as usize || !code.chars().all(|c| c.is_ascii_digit()) {
            return Err(AppError::validation("Enter the 6-digit pairing code."));
        }
        // Demo: accept any valid-length code (parent demo mint or lab). Never log code.
        info!(code_len = code.len(), "child pairing accepted (local demo)");

        let store = self.secret_store();
        let refresh = format!("child-refresh-{}", Uuid::new_v4());
        store
            .set(accounts::CHILD_REFRESH, &refresh)
            .map_err(|_| AppError::unknown())?;

        let mut g = self.inner.lock();
        g.paired = true;
        g.child_id = Some(format!("child-{}", Uuid::new_v4()));
        g.child_name = child_name
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .unwrap_or("Friend")
            .to_string();
        // Dev-only demo pairing: demo PIN 1234 (real pairing uses `bind_credential`).
        g.pin_hash = Some(hash_parent_pin("1234"));
        g.checklist_complete = true;
        g.checklist = default_checklist();
        for item in &mut g.checklist {
            item.done = true;
        }
        g.screen_override = Some(ChildScreen::Launcher);
        // Start device-interval accrual immediately when paired!
        g.session =
            SessionEngine::start_device_interval(g.session.clone(), g.elapsed_ms, &g.policy);
        g.quiz = None;
        self.save_persisted(&g);
        Ok(())
    }

    pub fn unpair(&self) -> Result<(), AppError> {
        let store = self.secret_store();
        let _ = store.delete(accounts::CHILD_REFRESH);
        self.delete_persisted(&self.inner.lock());
        let bank = load_builtin_bank().unwrap_or_default();
        let secrets_dir = self.inner.lock().secrets_dir.clone();
        let mut g = self.inner.lock();
        g.paired = false;
        g.child_id = None;
        g.child_name.clear();
        g.pin_hash = None;
        g.checklist = default_checklist();
        g.checklist_complete = false;
        g.session = SessionSnapshot::default();
        g.quiz = None;
        g.last_feedback = None;
        g.last_result = None;
        g.screen_override = Some(ChildScreen::Pairing);
        g.elapsed_ms = 0;
        g.bank = bank;
        g.pack_source = "builtin".into();
        g.secrets_dir = secrets_dir;
        g.policy = ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            quiz_interval_minutes: 50,
            ..ChildPolicy::default()
        };
        g.rules = demo_rules();
        g.app_usage_seconds.clear();
        g.app_display_names.clear();
        g.quiz_seconds_today = 0;
        g.quiz_attempts_today = 0;
        g.quiz_passes_today = 0;
        g.daily_usage_history.clear();
        g.daily_total_history.clear();
        Ok(())
    }

    pub fn set_checklist_item(&self, id: &str, done: bool) -> Result<(), AppError> {
        let mut g = self.inner.lock();
        self.require_paired_locked(&g)?;
        let item = g
            .checklist
            .iter_mut()
            .find(|c| c.id == id)
            .ok_or_else(|| AppError::not_found_msg("Checklist item not found."))?;
        item.done = done;
        Ok(())
    }

    pub fn complete_checklist(&self) -> Result<(), AppError> {
        let mut g = self.inner.lock();
        self.require_paired_locked(&g)?;
        let missing: Vec<_> = g
            .checklist
            .iter()
            .filter(|c| c.recommended && !c.done)
            .map(|c| c.label.clone())
            .collect();
        if !missing.is_empty() {
            return Err(AppError::validation(
                "Finish the recommended setup steps before continuing.",
            ));
        }
        g.checklist_complete = true;
        g.screen_override = Some(ChildScreen::Launcher);
        // Start device-interval accrual when entering launcher.
        g.session =
            SessionEngine::start_device_interval(g.session.clone(), g.elapsed_ms, &g.policy);
        Ok(())
    }

    pub fn ui_state(&self) -> ChildUiState {
        let mut g = self.inner.lock();
        g.session = SessionEngine::expire_shield_if_needed(g.session.clone(), g.elapsed_ms);
        self.clear_quiz_lock_if_expired(&mut g);
        let screen = resolve_screen(&g);
        let (rem_block, rem_sec, cooldown, daily_rem, ceiling_hit) =
            snapshot_metrics(
                &g.session,
                Some(meritscreen_core::session::effective_ceiling(&g.policy, &g.session)),
                g.elapsed_ms,
            );
        let bonus_today = g.session.active_bonus_minutes(&g.policy);
        let quiz = g.quiz.as_ref().and_then(|q| {
            q.questions.get(q.index).map(|question| QuizPromptDto {
                id: question.id.clone(),
                prompt: question.prompt.clone(),
                choices: question.choices.iter().map(QuizChoiceDto::from).collect(),
                index: q.index as u32,
                total: q.questions.len() as u32,
                teach_title: question.mini_lesson.as_ref().map(|m| m.title.clone()),
                teach_body: question
                    .mini_lesson
                    .as_ref()
                    .map(|m| m.body_lines.clone())
                    .unwrap_or_default(),
                interaction_type: question.interaction_type.clone(),
                prompt_tag: question.prompt_tag.clone(),
                prompt_count: question.prompt_count,
            })
        });
        let quiz_lock = g.quiz.as_ref().and_then(|q| {
            q.lock_until_ms.map(|until| {
                let rem = ((until - g.elapsed_ms).max(0) / 1000) as u32;
                let wrong_q = q.last_wrong_question.as_ref();
                let correct_text = wrong_q
                    .and_then(|wq| wq.choices.iter().find(|c| c.correct))
                    .map(|c| {
                        if c.text.trim().is_empty() {
                            c.image_tag.clone().unwrap_or_default().replace('_', " ")
                        } else {
                            c.text.clone()
                        }
                    })
                    .unwrap_or_default();
                let (lesson_title, lesson_body) = match wrong_q.and_then(|wq| wq.mini_lesson.as_ref()) {
                    Some(m) => (m.title.clone(), m.body_lines.clone()),
                    None => (
                        wrong_q.map(|wq| wq.concept_title.clone()).unwrap_or_default(),
                        vec![g.last_feedback.as_ref().map(|f| f.concept_line.clone()).unwrap_or_default()],
                    ),
                };
                QuizLockDto {
                    remaining_seconds: rem,
                    concept_line: g
                        .last_feedback
                        .as_ref()
                        .map(|f| f.concept_line.clone())
                        .unwrap_or_default(),
                    why_line: g.last_feedback.as_ref().map(|f| f.why_line.clone()).unwrap_or_default(),
                    correct_choice_text: correct_text,
                    lesson_title,
                    lesson_body,
                }
            })
        });
        let mut minutes_by_app: HashMap<String, u32> = HashMap::new();
        for (k, v) in &g.app_usage_seconds {
            let mins = if *v >= 30 && *v < 60 { 1 } else { (*v / 60) as u32 };
            if mins > 0 {
                minutes_by_app.insert(k.clone(), mins);
            }
        }
        let quiz_minutes = if g.quiz_seconds_today >= 30 && g.quiz_seconds_today < 60 {
            1
        } else {
            (g.quiz_seconds_today / 60) as u32
        };
        ChildUiState {
            paired: g.paired,
            screen,
            phase: g.session.phase,
            child_name: g.child_name.clone(),
            apps: g
                .rules
                .iter()
                .filter(|r| r.allowed)
                .map(|r| LauncherApp {
                    app_id: r.app_id.clone(),
                    display_name: if r.display_name.is_empty() {
                        r.app_id.clone()
                    } else {
                        r.display_name.clone()
                    },
                    package_or_bundle_id: r.package_or_bundle_id.clone(),
                    is_emergency: r.is_emergency,
                    icon_tone: if r.is_emergency {
                        "emergency".into()
                    } else {
                        "primary".into()
                    },
                })
                .collect(),
            checklist: g.checklist.clone(),
            checklist_complete: g.checklist_complete,
            minutes_used_today: g.session.minutes_used_today,
            minutes_remaining_block: rem_block,
            seconds_remaining_block: rem_sec,
            block_duration_minutes: g.session.block_duration_minutes,
            cooldown_seconds: cooldown,
            daily_remaining_minutes: daily_rem,
            ceiling_hit,
            bonus_minutes_today: bonus_today,
            quiz,
            quiz_lock,
            last_feedback: g.last_feedback.clone(),
            last_result: g.last_result.clone(),
            pack_source: g.pack_source.clone(),
            elapsed_ms: g.elapsed_ms,
            active_app_id: g.active_app_id.clone(),
            active_app_name: g.active_app_name.clone(),
            minutes_by_app,
            app_labels: g.app_display_names.clone(),
            quiz_minutes_today: quiz_minutes,
            quiz_attempts_today: g.quiz_attempts_today,
            quiz_passes_today: g.quiz_passes_today,
            quiz_error: g.quiz_error.clone(),
            show_explanations: g.policy.show_explanations,
            early_learner: g.age_band.is_early_learner(),
            explorer: g.explorer.clone(),
            stickers: g.stickers.clone(),
            last_award: g.last_award.clone(),
            nursery: g.nursery.clone(),
            can_retry_during_cooldown: g.policy.allow_retry_during_cooldown,
        }
    }

    /// Validate + install a generated quiz pack (Android layout, `source:"ai"` items win
    /// picking preference). Returns how many questions were accepted.
    pub fn load_pack_json(&self, raw: &str) -> usize {
        let mut g = self.inner.lock();
        install_pack(&mut g, raw)
    }

    /// Retry the quiz during the fail-lock cooldown when the parent policy allows it.
    pub fn retry_quiz_during_cooldown(&self) -> Result<ChildUiState, AppError> {
        let mut g = self.inner.lock();
        self.require_ready_locked(&g)?;
        if g.session.phase != SessionPhase::Shielded || !g.policy.allow_retry_during_cooldown {
            return Err(AppError::validation("Retry during cooldown is not enabled by your parent."));
        }
        g.session = SessionEngine::end_fail_lock(g.session.clone());
        g.session.phase = SessionPhase::QuizDue;
        begin_quiz(&mut g);
        if g.nursery.is_none() {
            g.screen_override = Some(ChildScreen::Quiz);
        }
        self.save_persisted(&g);
        drop(g);
        Ok(self.ui_state())
    }

    /// Attempts not yet handed to the Guardian for Firestore upload.
    pub fn drain_pending_attempts(&self) -> Vec<QuizAttemptRecord> {
        let mut g = self.inner.lock();
        let out = std::mem::take(&mut g.pending_attempts);
        if !out.is_empty() {
            self.save_persisted(&g);
        }
        out
    }

    /// Early-learner teach-first finished: unlock time + sticker, like a passed quiz
    /// (Android `onNurseryComplete`, source `NURSERY_TEACH`).
    pub fn complete_nursery(&self) -> Result<ChildUiState, AppError> {
        let mut g = self.inner.lock();
        self.require_ready_locked(&g)?;
        if g.nursery.take().is_none() {
            return Err(AppError::validation("No nursery lesson in progress."));
        }
        award_pass(&mut g, None, Some("NURSERY_TEACH"));
        let rule = g
            .session
            .active_app_id
            .as_ref()
            .and_then(|id| g.rules.iter().find(|r| &r.app_id == id).cloned());
        g.session = SessionEngine::on_quiz_passed(
            g.session.clone(),
            g.elapsed_ms,
            rule.as_ref(),
            &g.policy,
            SessionEngine::is_weekend_local(),
        );
        g.screen_override = Some(ChildScreen::Launcher);
        self.save_persisted(&g);
        drop(g);
        Ok(self.ui_state())
    }

    /// Advance simulated monotonic clock (tests + lab “fast-forward”).
    pub fn tick(&self, delta_ms: i64) -> ChildUiState {
        let mut g = self.inner.lock();
        if !g.paired || !g.checklist_complete {
            return drop_and_state(self, g);
        }
        g.elapsed_ms = g.elapsed_ms.saturating_add(delta_ms.max(0));
        let active = g.session.phase == SessionPhase::InBlock;
        g.session = SessionEngine::tick(g.session.clone(), g.elapsed_ms, &g.policy, active);
        if g.session.phase == SessionPhase::QuizDue && g.quiz.is_none() {
            begin_quiz(&mut g);
            g.screen_override = Some(ChildScreen::Quiz);
        }
        drop(g);
        self.ui_state()
    }

    /// Second-by-second foreground monitoring tick.
    /// Returns `(ChildUiState, bool)` where bool is `true` if a quiz just became due.
    pub fn tick_foreground(
        &self,
        app_id: Option<&str>,
        app_name: Option<&str>,
        idle_seconds: u32,
        is_screen_locked: bool,
        delta_ms: i64,
    ) -> (ChildUiState, bool) {
        let mut g = self.inner.lock();
        if !g.paired {
            drop(g);
            return (self.ui_state(), false);
        }

        g.elapsed_ms = g.elapsed_ms.saturating_add(delta_ms.max(0));
        g.active_app_id = app_id.map(String::from);
        g.active_app_name = app_name.map(String::from);

        // Ensure session is in interval block if paired
        if g.session.phase == SessionPhase::Idle {
            g.session =
                SessionEngine::start_device_interval(g.session.clone(), g.elapsed_ms, &g.policy);
        }

        // Active use predicate: user is active if NOT locked, idle_seconds < IDLE_THRESHOLD_SECONDS
        // and either in DeviceInterval mode or a tracked app has focus
        let is_active = !is_screen_locked
            && idle_seconds < meritscreen_core::app_config::IDLE_THRESHOLD_SECONDS
            && (g.policy.quiz_mode == QuizMode::DeviceInterval || app_id.is_some());
        let is_in_quiz = g.session.phase == SessionPhase::QuizDue || g.quiz.is_some() || g.screen_override == Some(ChildScreen::Quiz);
        let delta_secs = (delta_ms.max(0) as u64) / 1000;
        if delta_secs > 0 {
            if is_in_quiz && !is_screen_locked {
                g.quiz_seconds_today = g.quiz_seconds_today.saturating_add(delta_secs);
            } else if is_active {
                if let Some(id) = app_id {
                    let ent = g.app_usage_seconds.entry(id.to_string()).or_default();
                    *ent = ent.saturating_add(delta_secs);
                    if let Some(name) = app_name {
                        g.app_display_names.insert(id.to_string(), name.to_string());
                    }
                }
            }
        }

        let was_quiz_due = g.session.phase == SessionPhase::QuizDue;

        g.session = SessionEngine::tick(g.session.clone(), g.elapsed_ms, &g.policy, is_active);

        let mut newly_due = false;
        if g.session.phase == SessionPhase::QuizDue && g.quiz.is_none() {
            begin_quiz(&mut g);
            g.screen_override = Some(ChildScreen::Quiz);
            if !was_quiz_due {
                newly_due = true;
            }
        }

        // Debounced persistence: save to disk every 5 seconds or when newly due
        if newly_due || (g.elapsed_ms % 5_000 < delta_ms) {
            self.save_persisted(&g);
        }

        drop(g);
        let state = self.ui_state();
        (state, newly_due)
    }

    pub fn active_app(&self) -> (Option<String>, Option<String>) {
        let g = self.inner.lock();
        (g.active_app_id.clone(), g.active_app_name.clone())
    }

    pub fn launch_app(&self, app_id: &str) -> Result<ChildUiState, AppError> {
        let mut g = self.inner.lock();
        self.require_ready_locked(&g)?;
        if matches!(
            g.session.phase,
            SessionPhase::QuizDue | SessionPhase::Shielded
        ) {
            return Err(AppError::validation(
                "Finish the quiz or wait for the cooldown before opening apps.",
            ));
        }
        let rule = g
            .rules
            .iter()
            .find(|r| r.app_id == app_id)
            .cloned()
            .ok_or_else(|| AppError::not_found_msg("App not on the allowlist."))?;
        g.session = SessionEngine::open_app(g.session.clone(), g.elapsed_ms, &rule, &g.policy);
        if g.policy.quiz_mode == QuizMode::DeviceInterval && g.session.phase == SessionPhase::Idle {
            g.session =
                SessionEngine::start_device_interval(g.session.clone(), g.elapsed_ms, &g.policy);
            g.session = SessionEngine::open_app(g.session.clone(), g.elapsed_ms, &rule, &g.policy);
        }
        drop(g);
        Ok(self.ui_state())
    }

    /// Test/lab helper: force quiz_due and open quiz UI.
    pub fn force_quiz_due(&self) -> Result<ChildUiState, AppError> {
        let mut g = self.inner.lock();
        self.require_ready_locked(&g)?;
        g.session.phase = SessionPhase::QuizDue;
        begin_quiz(&mut g);
        g.screen_override = Some(ChildScreen::Quiz);
        drop(g);
        Ok(self.ui_state())
    }

    pub fn answer_quiz(&self, choice_id: &str) -> Result<ChildUiState, AppError> {
        let mut g = self.inner.lock();
        self.require_ready_locked(&g)?;
        self.clear_quiz_lock_if_expired(&mut g);
        let elapsed = g.elapsed_ms;

        let (question, skill) = {
            let quiz = g
                .quiz
                .as_mut()
                .ok_or_else(|| AppError::validation("No quiz in progress."))?;
            if let Some(until) = quiz.lock_until_ms {
                if elapsed < until {
                    return Err(AppError::validation(
                        "Take a breath — the next question unlocks in a moment.",
                    ));
                }
                quiz.lock_until_ms = None;
            }
            let question = quiz
                .questions
                .get(quiz.index)
                .cloned()
                .ok_or_else(|| AppError::validation("Quiz finished."))?;
            let skill = quiz
                .skills
                .entry(question.topic.clone())
                .or_insert_with(|| TopicSkill::new(question.topic.clone()))
                .clone();
            (question, skill)
        };

        let response_ms = {
            let quiz = g.quiz.as_ref().ok_or_else(|| AppError::validation("No quiz in progress."))?;
            (elapsed - quiz.started_elapsed_ms).max(0)
        };
        let (feedback, updated) =
            AdaptiveQuizEngine::grade_answer(&question, choice_id, &skill, response_ms);
        let correct = feedback.correct;
        g.last_feedback = Some(feedback);

        {
            let quiz = g
                .quiz
                .as_mut()
                .ok_or_else(|| AppError::validation("No quiz in progress."))?;
            quiz.skills.insert(question.topic.clone(), updated.clone());
            if correct {
                quiz.correct += 1;
            } else {
                // 30 s Learn-More lock with the lesson; no skip (Android QUIZ_LOCKOUT_SECONDS).
                quiz.lock_until_ms = Some(elapsed + i64::from(QUIZ_LOCKOUT_SECONDS) * 1000);
                quiz.last_wrong_concept = Some(question.concept_id.clone());
                quiz.last_wrong_question = Some(question.clone());
            }
            quiz.index += 1;
            quiz.used_ids.insert(question.id.clone());
            quiz.started_elapsed_ms = elapsed;
        }
        // Android persists the skill + history after every answer, not just at the end.
        g.skills = g
            .quiz
            .as_ref()
            .map(|q| q.skills.clone())
            .unwrap_or_default();
        let now = wall_now_ms();
        g.recent_questions.push(RecentQuestion { id: question.id.clone(), at_ms: now });
        g.recent_questions
            .push(RecentQuestion { id: AdaptiveQuizEngine::prompt_history_key(&question.prompt), at_ms: now });
        g.recent_questions.retain(|r| now - r.at_ms <= HISTORY_WINDOW_MS);

        // Finalize when all questions answered and not locked.
        let done = g
            .quiz
            .as_ref()
            .is_some_and(|q| q.index >= q.questions.len() && q.lock_until_ms.is_none());
        if done {
            finish_quiz(&mut g);
            self.save_persisted(&g);
        }
        drop(g);
        Ok(self.ui_state())
    }

    pub fn open_pin_menu(&self) -> Result<ChildUiState, AppError> {
        let mut g = self.inner.lock();
        self.require_paired_locked(&g)?;
        g.screen_override = Some(ChildScreen::PinMenu);
        drop(g);
        Ok(self.ui_state())
    }

    pub fn set_pin_hash(&self, pin_hash: Option<String>) {
        let mut g = self.inner.lock();
        g.pin_hash = pin_hash;
        self.save_persisted(&g);
    }

    pub fn pin_hash(&self) -> Option<String> {
        self.inner.lock().pin_hash.clone()
    }

    pub fn verify_pin(&self, pin: &str) -> Result<bool, AppError> {
        let g = self.inner.lock();
        self.require_paired_locked(&g)?;
        let pin = pin.trim();
        let Some(hash) = g.pin_hash.as_deref() else {
            return Ok(pin == "1234");
        };
        Ok(verify_parent_pin(pin, hash))
    }

    pub fn pin_unpair(&self, pin: &str) -> Result<(), AppError> {
        if !self.verify_pin(pin)? {
            return Err(AppError::validation("That PIN is incorrect."));
        }
        self.unpair()
    }

    pub fn force_skip_quiz(&self) -> Result<ChildUiState, AppError> {
        let mut g = self.inner.lock();
        g.quiz = None;
        g.last_feedback = None;
        let rule = g
            .session
            .active_app_id
            .as_ref()
            .and_then(|id| g.rules.iter().find(|r| &r.app_id == id));
        g.session = SessionEngine::on_quiz_passed(g.session.clone(), g.elapsed_ms, rule, &g.policy, SessionEngine::is_weekend_local());
        // Ensure at least 15 minutes screen time grace window after parent PIN skip
        let grace_ms = 15 * 60_000;
        let current_grace = g.session.quiz_grace_until_elapsed_ms.unwrap_or(g.elapsed_ms);
        g.session.quiz_grace_until_elapsed_ms = Some(current_grace.max(g.elapsed_ms + grace_ms));
        g.screen_override = Some(ChildScreen::Launcher);
        self.save_persisted(&g);
        drop(g);
        Ok(self.ui_state())
    }

    pub fn skip_quiz_with_pin(&self, pin: &str) -> Result<ChildUiState, AppError> {
        if !self.verify_pin(pin)? {
            return Err(AppError::validation("That PIN is incorrect."));
        }
        self.force_skip_quiz()
    }

    pub fn dismiss_pin_menu(&self) -> ChildUiState {
        let mut g = self.inner.lock();
        g.screen_override = None;
        drop(g);
        self.ui_state()
    }

    pub fn phase(&self) -> SessionPhase {
        self.inner.lock().session.phase
    }

    pub fn get_report_telemetry(&self) -> ChildReportTelemetry {
        let g = self.inner.lock();
        let mut minutes_by_app: HashMap<String, u32> = HashMap::new();
        for (k, v) in &g.app_usage_seconds {
            let mins = if *v >= 30 && *v < 60 { 1 } else { (*v / 60) as u32 };
            if mins > 0 {
                minutes_by_app.insert(k.clone(), mins);
            }
        }
        let quiz_minutes = if g.quiz_seconds_today >= 30 && g.quiz_seconds_today < 60 {
            1
        } else {
            (g.quiz_seconds_today / 60) as u32
        };

        ChildReportTelemetry {
            child_id: g.child_id.clone(),
            child_name: g.child_name.clone(),
            minutes_used_today: g.session.minutes_used_today,
            minutes_by_app,
            app_labels: g.app_display_names.clone(),
            quiz_minutes_today: quiz_minutes,
            quiz_attempts_today: g.quiz_attempts_today,
            quiz_passes_today: g.quiz_passes_today,
            daily_usage_history: g.daily_usage_history.clone(),
            daily_total_history: g.daily_total_history.clone(),
            skills: g.skills.values().cloned().collect(),
            pending_attempts: g.pending_attempts.clone(),
            explorer: g.explorer.clone(),
            stickers: g.stickers.clone(),
        }
    }

    fn secret_store(&self) -> SecretStore {
        let dir = self.inner.lock().secrets_dir.clone();
        if let Some(d) = dir {
            SecretStore::file_backed(d)
        } else {
            SecretStore::platform()
        }
    }

    fn require_paired_locked(&self, g: &Inner) -> Result<(), AppError> {
        if g.paired {
            Ok(())
        } else {
            Err(AppError::auth_msg("Pair this device first."))
        }
    }

    fn require_ready_locked(&self, g: &Inner) -> Result<(), AppError> {
        self.require_paired_locked(g)?;
        if g.checklist_complete {
            Ok(())
        } else {
            Err(AppError::validation("Finish setup before using apps."))
        }
    }

    fn clear_quiz_lock_if_expired(&self, g: &mut Inner) {
        let elapsed = g.elapsed_ms;
        let should_finish = if let Some(quiz) = g.quiz.as_mut() {
            if let Some(until) = quiz.lock_until_ms {
                if elapsed >= until {
                    quiz.lock_until_ms = None;
                    quiz.started_elapsed_ms = elapsed;
                    quiz.index >= quiz.questions.len()
                } else {
                    false
                }
            } else {
                false
            }
        } else {
            false
        };
        if should_finish {
            finish_quiz(g);
        }
    }
}

fn drop_and_state(rt: &ChildRuntime, g: parking_lot::MutexGuard<'_, Inner>) -> ChildUiState {
    drop(g);
    rt.ui_state()
}

fn resolve_screen(g: &Inner) -> ChildScreen {
    if let Some(s) = g.screen_override {
        if s == ChildScreen::PinMenu {
            return ChildScreen::PinMenu;
        }
    }
    if !g.paired {
        return ChildScreen::Pairing;
    }
    if !g.checklist_complete {
        return ChildScreen::Checklist;
    }
    if g.nursery.is_some() {
        return ChildScreen::Nursery;
    }
    match g.session.phase {
        SessionPhase::QuizDue => ChildScreen::Quiz,
        SessionPhase::Shielded => ChildScreen::FailLock,
        SessionPhase::Idle | SessionPhase::InBlock => {
            let ceiling = meritscreen_core::session::effective_ceiling(&g.policy, &g.session);
            if g.session.minutes_used_today >= ceiling && g.session.phase == SessionPhase::Idle {
                ChildScreen::Ceiling
            } else {
                g.screen_override.unwrap_or(ChildScreen::Launcher)
            }
        }
    }
}

fn install_pack(g: &mut Inner, raw: &str) -> usize {
    let avoid: std::collections::HashSet<String> = g.bank.iter().map(|q| q.id.clone()).collect();
    let accepted = meritscreen_core::quiz::parse_and_validate_pack(raw, g.age_band.bank_band(), &avoid);
    let n = accepted.len();
    if n > 0 {
        let keys: std::collections::HashSet<String> =
            g.bank.iter().map(|q| AdaptiveQuizEngine::prompt_history_key(&q.prompt)).collect();
        g.bank.extend(accepted.into_iter().filter(|q| !keys.contains(&AdaptiveQuizEngine::prompt_history_key(&q.prompt))));
        g.pack_source = "ai".into();
    }
    n
}

fn begin_quiz(g: &mut Inner) {
    if !g.pack_merged {
        g.pack_merged = true;
        if let Some(raw) = g.pack_raw.clone() {
            install_pack(g, &raw);
        }
    }
    if g.bank.is_empty() {
        if let Ok(bank) = load_builtin_bank() {
            g.bank = bank;
            g.pack_source = "builtin".into();
        }
    }
    // Early learners (3–6) learn first, then play — no knowledge check unless the parent
    // enabled one (Android: AGE_3_TO_6 && !nurseryCheckEnabled → nursery teaching).
    if g.age_band.is_early_learner() && !g.policy.nursery_check_enabled {
        let curriculum = meritscreen_core::nursery::builtin_curriculum();
        let seed = (wall_now_ms() / 86_400_000).max(0) as u64;
        let (video, category) = if g.policy.nursery_video_enabled {
            curriculum
                .pick_video(&g.policy.nursery_playlist_id, &g.nursery_recent_videos, g.nursery_last_category.as_deref(), seed)
                .map(|(v, c)| (Some(v), Some(c)))
                .unwrap_or((None, None))
        } else {
            (None, None)
        };
        if let Some(v) = &video {
            g.nursery_recent_videos.push(v.video_id.clone());
            g.nursery_recent_videos = g.nursery_recent_videos.split_off(g.nursery_recent_videos.len().saturating_sub(8));
        }
        g.nursery_last_category = category.clone();
        g.nursery = Some(NurseryDto {
            video,
            video_category: category,
            items: curriculum.pick_teach_items(3, seed),
        });
        g.quiz_error = None;
        g.last_result = None;
        g.last_feedback = None;
        return;
    }
    let total = AdaptiveQuizEngine::questions_per_quiz(g.policy.questions_per_quiz) as usize;
    let mut rng = StdRng::seed_from_u64(g.elapsed_ms as u64 ^ 0xC5_1D_u64);
    let mut used = HashSet::new();
    let mut questions = Vec::new();
    let skills = g.skills.clone();
    let start_level = AdaptiveQuizEngine::session_start_level(&skills);
    let now = wall_now_ms();
    let recent: HashSet<String> = g
        .recent_questions
        .iter()
        .filter(|r| now - r.at_ms <= HISTORY_WINDOW_MS)
        .map(|r| r.id.clone())
        .collect();
    let band = g.age_band.bank_band();
    let bank: Vec<QuizQuestion> = g.bank.iter().filter(|q| q.age_band == band).cloned().collect();
    for _ in 0..total {
        if let Some(q) =
            AdaptiveQuizEngine::pick_next(&bank, &skills, &recent, &used, None, start_level, &mut rng)
        {
            used.insert(q.id.clone());
            questions.push(q);
        }
    }
    g.quiz_error = if questions.is_empty() {
        Some("Quiz questions aren’t ready yet. Try again later.".into())
    } else if questions.len() < total {
        Some("Not enough distinct quiz questions are available yet. Try again after the quiz pack finishes syncing.".into())
    } else {
        None
    };
    g.quiz = Some(ActiveQuiz {
        questions,
        index: 0,
        correct: 0,
        used_ids: used,
        skills,
        lock_until_ms: None,
        last_wrong_concept: None,
        last_wrong_question: None,
        started_elapsed_ms: g.elapsed_ms,
        quiz_seconds_at_start: g.quiz_seconds_today,
    });
    g.last_result = None;
    g.last_feedback = None;
}

fn finish_quiz(g: &mut Inner) {
    let Some(quiz) = g.quiz.take() else {
        return;
    };
    let total = quiz.questions.len() as u32;
    let result = AdaptiveQuizEngine::finalize(quiz.correct, total, g.policy.pass_score_percent);
    g.last_result = Some(result.clone());
    g.quiz_attempts_today = g.quiz_attempts_today.saturating_add(1);
    g.skills = quiz.skills.clone();
    let rule = g
        .session
        .active_app_id
        .as_ref()
        .and_then(|id| g.rules.iter().find(|r| &r.app_id == id).cloned());
    // Android records every completed quiz for upload, pass or fail.
    let weekend = SessionEngine::is_weekend_local();
    let mut extra = 0;
    if result.passed && g.policy.rewards_enabled {
        extra = g.policy.extra_minutes_on_pass;
        if g.policy.weekend_bonus_enabled && weekend {
            extra += meritscreen_core::app_config::WEEKEND_BONUS_EXTRA_MINUTES;
        }
    }
    let mut topics: Vec<String> = quiz.questions.iter().map(|q| q.topic.clone()).collect();
    topics.sort();
    topics.dedup();
    let attempt = QuizAttemptRecord {
        attempt_id: Uuid::new_v4().to_string(),
        created_at_epoch_ms: wall_now_ms(),
        topics,
        score: quiz.correct,
        total,
        passed: result.passed,
        extra_minutes_granted: extra,
        time_spent_seconds: g.quiz_seconds_today.saturating_sub(quiz.quiz_seconds_at_start) as u32,
    };
    let attempt_id = attempt.attempt_id.clone();
    g.pending_attempts.push(attempt);
    if result.passed {
        g.quiz_passes_today = g.quiz_passes_today.saturating_add(1);
        award_pass(g, Some(&attempt_id), None);
        g.session = SessionEngine::on_quiz_passed(
            g.session.clone(),
            g.elapsed_ms,
            rule.as_ref(),
            &g.policy,
            weekend,
        );
        g.screen_override = Some(ChildScreen::Launcher);
    } else {
        g.session = SessionEngine::on_quiz_failed(g.session.clone(), g.elapsed_ms, &g.policy, rule.as_ref());
        g.screen_override = Some(ChildScreen::FailLock);
    }
}

fn demo_rules() -> Vec<AppRule> {
    let mut browser = AppRule::new("browser", "app.browser");
    browser.display_name = "Browser".into();
    let mut calc = AppRule::new("calculator", "app.calculator");
    calc.display_name = "Calculator".into();
    let mut notes = AppRule::new("notes", "app.notes");
    notes.display_name = "Notes".into();
    let mut phone = AppRule::new("phone", "app.phone");
    phone.display_name = "Phone".into();
    phone.is_emergency = true;
    vec![browser, calc, notes, phone]
}

#[cfg(test)]
mod tests {
    #[test]
    fn bind_credential_uses_parent_pin_not_demo() {
        let dir = tempfile::tempdir().unwrap();
        let rt = super::ChildRuntime::with_secrets_dir(dir.path());
        let cred = crate::models::PairingCredential {
            family_id: "fam1".into(),
            child_id: "kid1".into(),
            device_id: "dev12345".into(),
            display_name: "Asha".into(),
            age_band: "AGE_7_TO_9".into(),
            parent_pin_hash: Some(meritscreen_security::hash_parent_pin("5791")),
            refresh_token: "rt".into(),
        };
        rt.bind_credential(&cred).unwrap();
        assert!(rt.is_paired());
        assert!(rt.verify_pin("5791").unwrap());
        assert!(!rt.verify_pin("1234").unwrap());
        assert_eq!(rt.ui_state().child_name, "Asha");
        let bad = crate::models::PairingCredential::default();
        assert!(rt.bind_credential(&bad).is_err());
        let mut no_pin = cred.clone();
        no_pin.parent_pin_hash = None;
        let rt2 = super::ChildRuntime::with_secrets_dir(tempfile::tempdir().unwrap().path());
        assert!(rt2.bind_credential(&no_pin).is_err());
        assert!(!rt2.is_paired());
    }

    #[test]
    fn bonus_extends_today_and_unlocks_ceiling() {
        let dir = tempfile::tempdir().unwrap();
        let rt = super::ChildRuntime::with_secrets_dir(dir.path());
        {
            let mut g = rt.inner.lock();
            g.paired = true;
            g.checklist_complete = true;
            g.policy.daily_ceiling_minutes = Some(30);
            g.session.minutes_used_today = 30;
            g.session.phase = meritscreen_core::SessionPhase::Idle;
        }
        let st = rt.ui_state();
        assert!(st.ceiling_hit);
        assert_eq!(st.daily_remaining_minutes, 0);
        {
            let mut g = rt.inner.lock();
            let today = meritscreen_core::session::local_today_key();
            g.policy.grant_bonus(15, &today).unwrap();
        }
        let st = rt.ui_state();
        assert_eq!(st.bonus_minutes_today, 15);
        assert_eq!(st.daily_remaining_minutes, 15);
        assert!(!st.ceiling_hit);
        assert_ne!(st.screen, crate::models::ChildScreen::Ceiling);
    }

    #[test]
    fn device_id_is_stable() {
        let dir = tempfile::tempdir().unwrap();
        let a = super::ChildRuntime::with_secrets_dir(dir.path()).device_id();
        let b = super::ChildRuntime::with_secrets_dir(dir.path()).device_id();
        assert_eq!(a, b);
        assert!(a.len() >= 8);
    }

    use super::*;
    use tempfile::tempdir;

    #[test]
    fn pairing_checklist_quiz_fail_pin_unpair() {
        let dir = tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        assert!(!rt.is_paired());
        rt.pair("424242", Some("Sam")).unwrap();
        assert!(rt.is_paired());

        for id in ["standard_account", "accessibility_ok"] {
            rt.set_checklist_item(id, true).unwrap();
        }
        rt.complete_checklist().unwrap();
        let st = rt.ui_state();
        assert_eq!(st.screen, ChildScreen::Launcher);
        assert_eq!(st.pack_source, "builtin");
        assert!(!st.apps.is_empty());

        rt.launch_app("browser").unwrap();
        // Fast-forward past interval → quiz due.
        let interval_ms = i64::from(rt.inner.lock().policy.quiz_interval_minutes) * 60_000 + 1_000;
        let st = rt.tick(interval_ms);
        assert_eq!(st.phase, SessionPhase::QuizDue);
        assert_eq!(st.screen, ChildScreen::Quiz);
        assert!(st.quiz.is_some());

        // Wrong answers until fail (or force fail via finishing with zeros).
        rt.force_quiz_due().unwrap();
        // Answer incorrectly by picking first choice repeatedly if needed.
        for _ in 0..5 {
            let st = rt.ui_state();
            if st.phase != SessionPhase::QuizDue {
                break;
            }
            if let Some(q) = st.quiz {
                let choice = q.choices.first().map(|c| c.id.clone()).unwrap();
                let _ = rt.answer_quiz(&choice);
                // Skip lock waits in test.
                rt.inner.lock().elapsed_ms += i64::from(QUIZ_LOCKOUT_SECONDS) * 1000 + 1;
                if let Some(quiz) = rt.inner.lock().quiz.as_mut() {
                    quiz.lock_until_ms = None;
                }
            } else {
                break;
            }
        }
        let st = rt.ui_state();
        // Either still in quiz or shielded/launcher after pass — ensure engine ran.
        assert!(matches!(
            st.phase,
            SessionPhase::QuizDue | SessionPhase::Shielded | SessionPhase::InBlock
        ));

        assert!(rt.verify_pin("1234").unwrap());
        assert!(!rt.verify_pin("0000").unwrap());
        rt.pin_unpair("1234").unwrap();
        assert!(!rt.is_paired());
    }

    #[test]
    fn test_skip_quiz_with_pin() {
        let dir = tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        rt.pair("222222", Some("Jordan")).unwrap();
        for id in ["standard_account", "accessibility_ok"] {
            rt.set_checklist_item(id, true).unwrap();
        }
        rt.complete_checklist().unwrap();
        rt.force_quiz_due().unwrap();
        assert_eq!(rt.ui_state().screen, ChildScreen::Quiz);

        // Incorrect pin fails
        assert!(rt.skip_quiz_with_pin("0000").is_err());
        assert_eq!(rt.ui_state().screen, ChildScreen::Quiz);

        // Correct pin skips quiz and returns to launcher
        let st = rt.skip_quiz_with_pin("1234").unwrap();
        assert_eq!(st.screen, ChildScreen::Launcher);
        assert_eq!(st.phase, SessionPhase::InBlock);
    }

    #[test]
    fn force_quiz_and_pass_path() {
        let dir = tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        rt.pair("111111", Some("Alex")).unwrap();
        rt.set_checklist_item("standard_account", true).unwrap();
        rt.set_checklist_item("accessibility_ok", true).unwrap();
        rt.complete_checklist().unwrap();
        rt.force_quiz_due().unwrap();

        // Answer all correctly by selecting correct choice ids from bank.
        loop {
            let st = rt.ui_state();
            if st.phase != SessionPhase::QuizDue {
                break;
            }
            let Some(prompt) = st.quiz else { break };
            let g = rt.inner.lock();
            let q = g
                .quiz
                .as_ref()
                .and_then(|aq| aq.questions.get(aq.index))
                .cloned();
            drop(g);
            let Some(q) = q else { break };
            let correct = q
                .choices
                .iter()
                .find(|c| c.correct)
                .map(|c| c.id.clone())
                .unwrap_or_else(|| prompt.choices[0].id.clone());
            rt.answer_quiz(&correct).unwrap();
        }
        let st = rt.ui_state();
        assert_eq!(st.phase, SessionPhase::InBlock);
        assert!(st.last_result.as_ref().is_some_and(|r| r.passed));
    }

    #[test]
    fn test_screen_lock_pauses_active_time() {
        let dir = tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        rt.pair("222222", Some("Jordan")).unwrap();
        rt.set_checklist_item("standard_account", true).unwrap();
        rt.set_checklist_item("accessibility_ok", true).unwrap();
        rt.complete_checklist().unwrap();

        // 1. When screen is locked, clock should pause
        let (st1, due1) = rt.tick_foreground(Some("com.google.Chrome"), Some("Google Chrome"), 0, true, 10_000);
        assert!(!due1);
        let sec1 = st1.seconds_remaining_block;

        // Tick again while still locked
        let (st2, _) = rt.tick_foreground(Some("com.google.Chrome"), Some("Google Chrome"), 0, true, 20_000);
        assert_eq!(st2.seconds_remaining_block, sec1, "Timer must pause while screen is locked");

        // 2. Unlock screen, clock should resume decrementing
        let (st3, _) = rt.tick_foreground(Some("com.google.Chrome"), Some("Google Chrome"), 0, false, 5_000);
        assert!(st3.seconds_remaining_block < sec1, "Timer must resume once screen is unlocked");
    }

    #[test]
    fn test_persist_and_restore_across_reboot() {
        let dir = tempdir().unwrap();
        {
            let rt1 = ChildRuntime::with_secrets_dir(dir.path());
            rt1.pair("333333", Some("Maya")).unwrap();
            rt1.set_checklist_item("standard_account", true).unwrap();
            rt1.set_checklist_item("accessibility_ok", true).unwrap();
            rt1.complete_checklist().unwrap();

            // Active use for 12 seconds
            for _ in 0..12 {
                rt1.tick_foreground(Some("com.apple.Safari"), Some("Safari"), 0, false, 1_000);
            }
            rt1.flush_persisted();
        }

        // Simulate PC restart: construct a fresh runtime with the same directory
        let rt2 = ChildRuntime::with_secrets_dir(dir.path());
        assert!(rt2.is_paired(), "Child must remain paired after PC restart");
        let st = rt2.ui_state();
        assert_eq!(st.child_name, "Maya");
        assert!(st.checklist_complete);
        assert_eq!(st.active_app_name.as_deref(), Some("Safari"));
        // 50 minutes = 3000s; after 12s active use, remaining should be 2988s
        assert!(st.seconds_remaining_block <= 2990);
    }

    #[test]
    fn test_skip_quiz_with_parent_pin() {
        let dir = tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        rt.pair("444444", Some("Jordan")).unwrap();
        rt.complete_checklist().unwrap();
        rt.force_quiz_due().unwrap();

        let st = rt.ui_state();
        assert_eq!(st.phase, SessionPhase::QuizDue);

        // Incorrect PIN fails
        let err = rt.skip_quiz_with_pin("9999");
        assert!(err.is_err());

        // Default PIN 1234 succeeds
        let res = rt.skip_quiz_with_pin("1234").unwrap();
        assert_eq!(res.phase, SessionPhase::InBlock);
        assert!(res.quiz.is_none());

        // Test with custom PIN
        rt.set_pin_hash(Some(hash_parent_pin("5678")));
        rt.force_quiz_due().unwrap();
        assert_eq!(rt.ui_state().phase, SessionPhase::QuizDue);

        // Custom PIN succeeds
        let res2 = rt.skip_quiz_with_pin("5678").unwrap();
        assert_eq!(res2.phase, SessionPhase::InBlock);
        assert!(res2.quiz.is_none());
    }

    #[test]
    fn test_quiz_pass_timer_continues_in_next_session() {
        let dir = tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        rt.pair("555555", Some("Leo")).unwrap();
        rt.complete_checklist().unwrap();

        // 1. Force a quiz due
        rt.force_quiz_due().unwrap();
        assert_eq!(rt.ui_state().phase, SessionPhase::QuizDue);

        // 2. Pass the quiz by answering correctly
        loop {
            let st = rt.ui_state();
            if st.phase != SessionPhase::QuizDue {
                break;
            }
            let Some(prompt) = st.quiz else { break };
            let g = rt.inner.lock();
            let q = g
                .quiz
                .as_ref()
                .and_then(|aq| aq.questions.get(aq.index))
                .cloned();
            drop(g);
            let Some(q) = q else { break };
            let correct = q
                .choices
                .iter()
                .find(|c| c.correct)
                .map(|c| c.id.clone())
                .unwrap_or_else(|| prompt.choices[0].id.clone());
            rt.answer_quiz(&correct).unwrap();
        }

        let st_passed = rt.ui_state();
        assert_eq!(st_passed.phase, SessionPhase::InBlock);
        assert_eq!(st_passed.screen, ChildScreen::Launcher);
        assert!(st_passed.last_result.as_ref().is_some_and(|r| r.passed));
        let initial_rem_sec = st_passed.seconds_remaining_block;
        assert!(initial_rem_sec > 0, "Next session must have positive seconds remaining");

        // 3. User uses desktop actively (e.g. mouse/trackpad browsing, idle_seconds = 5s)
        let (st_tick1, newly_due1) = rt.tick_foreground(Some("com.apple.Safari"), Some("Safari"), 5, false, 1000);
        assert!(!newly_due1);
        assert_eq!(st_tick1.seconds_remaining_block, initial_rem_sec - 1, "Timer must continue counting down on tick 1");

        let (st_tick2, _) = rt.tick_foreground(Some("com.apple.Safari"), Some("Safari"), 6, false, 1000);
        assert_eq!(st_tick2.seconds_remaining_block, initial_rem_sec - 2, "Timer must continue counting down on tick 2");

        // 4. Tick forward 10 seconds: timer must decrement by 10
        let (st_tick12, _) = rt.tick_foreground(Some("com.apple.Safari"), Some("Safari"), 10, false, 10_000);
        assert_eq!(st_tick12.seconds_remaining_block, initial_rem_sec - 12, "Timer must advance smoothly across larger delta");

        // 5. Active computer use even if on Desktop (app_id = None) still counts in DeviceInterval mode
        let (st_desktop, _) = rt.tick_foreground(None, None, 2, false, 1000);
        assert_eq!(st_desktop.seconds_remaining_block, initial_rem_sec - 13, "Timer must continue even on desktop/Finder");
    }

    #[test]
    fn test_quiz_skip_timer_continues() {
        let dir = tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        rt.pair("666666", Some("Sam")).unwrap();
        rt.complete_checklist().unwrap();

        rt.force_quiz_due().unwrap();
        assert_eq!(rt.ui_state().phase, SessionPhase::QuizDue);

        let st_skipped = rt.skip_quiz_with_pin("1234").unwrap();
        assert_eq!(st_skipped.phase, SessionPhase::InBlock);
        assert_eq!(st_skipped.screen, ChildScreen::Launcher);
        let rem_sec = st_skipped.seconds_remaining_block;

        // Verify timer continues counting down
        let (st1, _) = rt.tick_foreground(Some("app.browser"), Some("Browser"), 0, false, 1000);
        assert_eq!(st1.seconds_remaining_block, rem_sec - 1);

        let (st2, _) = rt.tick_foreground(Some("app.browser"), Some("Browser"), 0, false, 2000);
        assert_eq!(st2.seconds_remaining_block, rem_sec - 3);
    }
}


#[cfg(test)]
mod quiz_reporting_tests {
    use super::*;

    fn paired_runtime() -> (tempfile::TempDir, ChildRuntime) {
        let dir = tempfile::tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        {
            let mut g = rt.inner.lock();
            g.paired = true;
            g.child_id = Some("c1".into());
            g.checklist_complete = true;
            g.policy.questions_per_quiz = 3;
        }
        (dir, rt)
    }

    fn answer_all(rt: &ChildRuntime, correct: bool) {
        for _ in 0..10 {
            let st = rt.ui_state();
            let Some(q) = st.quiz else { break };
            let inner = rt.inner.lock();
            let question = inner.quiz.as_ref().unwrap().questions[q.index as usize].clone();
            drop(inner);
            let pick = question
                .choices
                .iter()
                .find(|c| c.correct == correct)
                .unwrap()
                .id
                .clone();
            rt.answer_quiz(&pick).unwrap();
            // Burn the 30 s lock if this was the last (wrong) answer.
            let mut g = rt.inner.lock();
            if let Some(quiz) = g.quiz.as_mut() {
                if quiz.lock_until_ms.is_some() {
                    g.elapsed_ms += 31_000;
                }
            }
            drop(g);
            let _ = rt.ui_state();
        }
    }

    #[test]
    fn passed_quiz_records_attempt_skill_sticker_and_xp() {
        let (_d, rt) = paired_runtime();
        rt.force_quiz_due().unwrap();
        answer_all(&rt, true);
        let st = rt.ui_state();
        assert!(st.last_result.unwrap().passed);
        assert_eq!(st.explorer.xp, 10);
        assert_eq!(st.stickers.len(), 1);
        assert_eq!(st.stickers[0].sticker_id, "sprout_star");
        assert_eq!(st.last_award.as_ref().unwrap().xp_gained, 10);
        let telem = rt.get_report_telemetry();
        assert_eq!(telem.pending_attempts.len(), 1);
        assert!(telem.pending_attempts[0].passed);
        assert!(!telem.skills.is_empty());
        assert_eq!(telem.skills.iter().map(|s| s.total_attempts).sum::<u32>(), 3);
        // Draining is one-shot (Guardian upload queue semantics).
        assert_eq!(rt.drain_pending_attempts().len(), 1);
        assert!(rt.drain_pending_attempts().is_empty());
    }

    #[test]
    fn failed_quiz_records_attempt_without_sticker() {
        let (_d, rt) = paired_runtime();
        rt.force_quiz_due().unwrap();
        answer_all(&rt, false);
        let st = rt.ui_state();
        assert!(!st.last_result.unwrap().passed);
        assert!(st.stickers.is_empty());
        assert_eq!(st.explorer.xp, 0);
        let telem = rt.get_report_telemetry();
        assert_eq!(telem.pending_attempts.len(), 1);
        assert!(!telem.pending_attempts[0].passed);
        assert!(telem.skills.iter().all(|s| !s.weak_concepts.is_empty()));
    }

    #[test]
    fn skills_and_history_survive_restart_and_shape_next_quiz() {
        let (dir, rt) = paired_runtime();
        rt.force_quiz_due().unwrap();
        answer_all(&rt, true);
        rt.flush_persisted();
        let rt2 = ChildRuntime::with_secrets_dir(dir.path());
        let telem = rt2.get_report_telemetry();
        assert_eq!(telem.skills.iter().map(|s| s.total_correct).sum::<u32>(), 3);
        let g = rt2.inner.lock();
        assert_eq!(g.recent_questions.len(), 6, "id + prompt key per question");
        assert_eq!(g.explorer.xp, 10);
    }

    #[test]
    fn early_learner_gets_nursery_teach_first() {
        let (_d, rt) = paired_runtime();
        rt.inner.lock().age_band = AgeBand::Age3To4;
        let st = rt.force_quiz_due().unwrap();
        assert_eq!(st.screen, ChildScreen::Nursery);
        let n = st.nursery.unwrap();
        assert_eq!(n.items.len(), 3);
        assert!(n.video.is_some());
        let done = rt.complete_nursery().unwrap();
        assert_eq!(done.screen, ChildScreen::Launcher);
        assert_eq!(done.stickers.len(), 1);
        assert_eq!(done.stickers[0].source, "NURSERY_TEACH");
        // With a parent-enabled knowledge check, questions appear instead.
        let (_d2, rt2) = paired_runtime();
        {
            let mut g = rt2.inner.lock();
            g.age_band = AgeBand::Age5To6;
            g.policy.nursery_check_enabled = true;
        }
        let st2 = rt2.force_quiz_due().unwrap();
        assert!(st2.quiz.is_some());
        assert!(st2.quiz.unwrap().interaction_type != "TAP_TEXT" || st2.early_learner);
    }
}

#[cfg(test)]
mod pack_and_retry_tests {
    use super::*;

    #[test]
    fn pack_install_validates_and_retry_respects_policy() {
        let dir = tempfile::tempdir().unwrap();
        let rt = ChildRuntime::with_secrets_dir(dir.path());
        {
            let mut g = rt.inner.lock();
            g.paired = true;
            g.checklist_complete = true;
        }
        let pack = r#"[{"id":"ai1","ageBand":"AGE_7_TO_9","topic":"geo","conceptId":"capitals","conceptTitle":"Capitals","difficulty":2,"prompt":"Capital of France?","choices":[{"id":"a","text":"Paris","correct":true},{"id":"b","text":"Rome","correct":false}],"whyCorrect":"Paris.","whyWrongByChoice":{},"conceptExplainer":"Capitals."},{"id":"bad","ageBand":"AGE_7_TO_9","topic":"x","conceptId":"x","conceptTitle":"x","difficulty":9,"prompt":"bad","choices":[],"whyCorrect":"x","whyWrongByChoice":{},"conceptExplainer":"x"}]"#;
        assert_eq!(rt.load_pack_json(pack), 1);
        let g = rt.inner.lock();
        assert_eq!(g.pack_source, "ai");
        assert!(g.bank.iter().any(|q| q.id == "ai1"));
        drop(g);
        // Retry blocked by default policy used in runtime (allow_retry default true in core Default? runtime policy default)
        let mut g = rt.inner.lock();
        g.policy.allow_retry_during_cooldown = false;
        g.session.phase = SessionPhase::Shielded;
        drop(g);
        assert!(rt.retry_quiz_during_cooldown().is_err());
        rt.inner.lock().policy.allow_retry_during_cooldown = true;
        let st = rt.retry_quiz_during_cooldown().unwrap();
        assert!(st.quiz.is_some() || st.nursery.is_some());
    }
}
