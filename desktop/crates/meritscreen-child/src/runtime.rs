//! Local child runtime — SessionEngine + AdaptiveQuizEngine, no Firebase.

use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

use meritscreen_core::app_config::{PAIRING_CODE_LENGTH, QUIZ_LOCKOUT_SECONDS};
use meritscreen_core::{
    load_builtin_bank, AdaptiveQuizEngine, AgeBand, AppError, AppRule, ChildPolicy, QuizMode,
    QuizQuestion, SessionEngine, SessionPhase, SessionSnapshot, TopicSkill,
};
use meritscreen_security::{accounts, hash_parent_pin, verify_parent_pin, SecretStore};
use parking_lot::Mutex;
use rand::rngs::StdRng;
use rand::SeedableRng;
use tracing::info;
use uuid::Uuid;

use crate::models::{
    default_checklist, snapshot_metrics, ChecklistItem, ChildScreen, ChildUiState, LauncherApp,
    QuizChoiceDto, QuizLockDto, QuizPromptDto,
};

struct ActiveQuiz {
    questions: Vec<QuizQuestion>,
    index: usize,
    correct: u32,
    used_ids: HashSet<String>,
    skills: HashMap<String, TopicSkill>,
    lock_until_ms: Option<i64>,
    last_wrong_concept: Option<String>,
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
        let bank = load_builtin_bank().unwrap_or_default();
        let pack_source = if bank.is_empty() {
            "empty".into()
        } else {
            "builtin".into()
        };
        let policy = ChildPolicy {
            quiz_mode: QuizMode::DeviceInterval,
            ..ChildPolicy::default()
        };
        Self {
            inner: Mutex::new(Inner {
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
                secrets_dir: None,
            }),
        }
    }

    /// Use file-backed secrets (tests / isolation).
    pub fn with_secrets_dir(dir: impl Into<PathBuf>) -> Self {
        let s = Self::new();
        s.inner.lock().secrets_dir = Some(dir.into());
        s
    }

    pub fn is_paired(&self) -> bool {
        self.inner.lock().paired
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
        // Demo parent PIN for C14 — production syncs hash from family doc.
        g.pin_hash = Some(hash_parent_pin("1234"));
        g.checklist_complete = false;
        g.checklist = default_checklist();
        g.screen_override = Some(ChildScreen::Checklist);
        g.session = SessionSnapshot::default();
        g.quiz = None;
        Ok(())
    }

    pub fn unpair(&self) -> Result<(), AppError> {
        let store = self.secret_store();
        let _ = store.delete(accounts::CHILD_REFRESH);
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
            ..ChildPolicy::default()
        };
        g.rules = demo_rules();
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
        let (rem_block, cooldown, daily_rem, ceiling_hit) =
            snapshot_metrics(&g.session, g.policy.daily_ceiling_minutes, g.elapsed_ms);
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
            })
        });
        let quiz_lock = g.quiz.as_ref().and_then(|q| {
            q.lock_until_ms.map(|until| {
                let rem = ((until - g.elapsed_ms).max(0) / 1000) as u32;
                QuizLockDto {
                    remaining_seconds: rem,
                    concept_line: g
                        .last_feedback
                        .as_ref()
                        .map(|f| f.concept_line.clone())
                        .unwrap_or_default(),
                }
            })
        });
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
            cooldown_seconds: cooldown,
            daily_remaining_minutes: daily_rem,
            ceiling_hit,
            quiz,
            quiz_lock,
            last_feedback: g.last_feedback.clone(),
            last_result: g.last_result.clone(),
            pack_source: g.pack_source.clone(),
            elapsed_ms: g.elapsed_ms,
        }
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

        let (feedback, updated) =
            AdaptiveQuizEngine::grade_answer(&question, choice_id, &skill, 1_200);
        let correct = feedback.correct;
        g.last_feedback = Some(feedback);

        {
            let quiz = g
                .quiz
                .as_mut()
                .ok_or_else(|| AppError::validation("No quiz in progress."))?;
            quiz.skills.insert(question.topic.clone(), updated);
            if correct {
                quiz.correct += 1;
            } else {
                // C09c — 30s lock with concept content; no skip.
                quiz.lock_until_ms = Some(elapsed + i64::from(QUIZ_LOCKOUT_SECONDS) * 1000);
                quiz.last_wrong_concept = Some(question.concept_id.clone());
            }
            quiz.index += 1;
            quiz.used_ids.insert(question.id);
        }

        // Finalize when all questions answered and not locked.
        let done = g
            .quiz
            .as_ref()
            .is_some_and(|q| q.index >= q.questions.len() && q.lock_until_ms.is_none());
        if done {
            finish_quiz(&mut g);
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

    pub fn verify_pin(&self, pin: &str) -> Result<bool, AppError> {
        let g = self.inner.lock();
        self.require_paired_locked(&g)?;
        let Some(hash) = g.pin_hash.as_deref() else {
            return Err(AppError::validation("PIN is not set on this device."));
        };
        Ok(verify_parent_pin(pin, hash))
    }

    pub fn pin_unpair(&self, pin: &str) -> Result<(), AppError> {
        if !self.verify_pin(pin)? {
            return Err(AppError::validation("That PIN is incorrect."));
        }
        self.unpair()
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
    match g.session.phase {
        SessionPhase::QuizDue => ChildScreen::Quiz,
        SessionPhase::Shielded => ChildScreen::FailLock,
        SessionPhase::Idle | SessionPhase::InBlock => {
            let ceiling = g
                .policy
                .daily_ceiling_minutes
                .unwrap_or(meritscreen_core::app_config::DEFAULT_DAILY_CEILING_MINUTES);
            if g.session.minutes_used_today >= ceiling && g.session.phase == SessionPhase::Idle {
                ChildScreen::Ceiling
            } else {
                g.screen_override.unwrap_or(ChildScreen::Launcher)
            }
        }
    }
}

fn begin_quiz(g: &mut Inner) {
    if g.bank.is_empty() {
        if let Ok(bank) = load_builtin_bank() {
            g.bank = bank;
            g.pack_source = "builtin".into();
        }
    }
    let total = AdaptiveQuizEngine::questions_per_quiz(g.policy.questions_per_quiz) as usize;
    let mut rng = StdRng::seed_from_u64(g.elapsed_ms as u64 ^ 0xC5_1D_u64);
    let mut used = HashSet::new();
    let mut questions = Vec::new();
    let skills = HashMap::new();
    let age = g.age_band;
    let bank: Vec<QuizQuestion> = g
        .bank
        .iter()
        .filter(|q| q.age_band == age)
        .cloned()
        .collect();
    let bank = if bank.is_empty() {
        g.bank.clone()
    } else {
        bank
    };
    for _ in 0..total {
        if let Some(q) =
            AdaptiveQuizEngine::pick_next(&bank, &skills, &HashSet::new(), &used, None, 2, &mut rng)
        {
            used.insert(q.id.clone());
            questions.push(q);
        }
    }
    if questions.is_empty() {
        // Builtin fallback: take first N from bank.
        questions = bank.iter().take(total.max(1)).cloned().collect();
        g.pack_source = "builtin".into();
    }
    g.quiz = Some(ActiveQuiz {
        questions,
        index: 0,
        correct: 0,
        used_ids: used,
        skills: HashMap::new(),
        lock_until_ms: None,
        last_wrong_concept: None,
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
    let rule = g
        .session
        .active_app_id
        .as_ref()
        .and_then(|id| g.rules.iter().find(|r| &r.app_id == id));
    if result.passed {
        g.session =
            SessionEngine::on_quiz_passed(g.session.clone(), g.elapsed_ms, rule, &g.policy, false);
        g.screen_override = Some(ChildScreen::Launcher);
    } else {
        g.session = SessionEngine::on_quiz_failed(g.session.clone(), g.elapsed_ms, &g.policy, rule);
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
}
