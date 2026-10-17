//! Pure adaptive quiz picker + grader. No network.

use std::collections::{HashMap, HashSet};

use rand::rngs::StdRng;
use rand::seq::SliceRandom;
use rand::{Rng, SeedableRng};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum AgeBand {
    #[serde(rename = "AGE_3_TO_4")]
    Age3To4,
    #[serde(rename = "AGE_5_TO_6")]
    Age5To6,
    /// Combined early-learner band used by the Android builtin bank.
    #[serde(rename = "AGE_3_TO_6")]
    Age3To6,
    #[serde(rename = "AGE_7_TO_9")]
    Age7To9,
    #[serde(rename = "AGE_10_TO_12")]
    Age10To12,
}

impl AgeBand {
    /// The band whose questions this child may receive. Android has only three bands;
    /// desktop's finer 3–4 / 5–6 profiles both use the Android `AGE_3_TO_6` bank.
    pub fn bank_band(self) -> Self {
        match self {
            Self::Age3To4 | Self::Age5To6 | Self::Age3To6 => Self::Age3To6,
            other => other,
        }
    }

    /// Ages 3–6 ("early learners"): teach-first nursery flow + read-aloud on Android.
    pub fn is_early_learner(self) -> bool {
        self.bank_band() == Self::Age3To6
    }

    pub fn parse(raw: &str) -> Option<Self> {
        match raw.trim().to_ascii_uppercase().as_str() {
            "AGE_3_TO_4" | "AGE3TO4" => Some(Self::Age3To4),
            "AGE_5_TO_6" | "AGE5TO6" => Some(Self::Age5To6),
            "AGE_3_TO_6" | "AGE3TO6" => Some(Self::Age3To6),
            "AGE_7_TO_9" | "AGE7TO9" => Some(Self::Age7To9),
            "AGE_10_TO_12" | "AGE10TO12" => Some(Self::Age10To12),
            "3_TO_4" | "3-4" => Some(Self::Age3To4),
            "5_TO_6" | "5-6" => Some(Self::Age5To6),
            "3_TO_6" => Some(Self::Age3To6),
            "7_TO_9" => Some(Self::Age7To9),
            "10_TO_12" => Some(Self::Age10To12),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizChoice {
    pub id: String,
    #[serde(default)]
    pub text: String,
    pub correct: bool,
    #[serde(default)]
    pub image_tag: Option<String>,
    #[serde(default)]
    pub audio_key: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MiniLesson {
    pub title: String,
    pub body_lines: Vec<String>,
    #[serde(default)]
    pub illustration_asset_id: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizQuestion {
    pub id: String,
    pub age_band: AgeBand,
    pub topic: String,
    pub concept_id: String,
    pub concept_title: String,
    pub difficulty: i32,
    pub prompt: String,
    pub choices: Vec<QuizChoice>,
    pub why_correct: String,
    #[serde(default)]
    pub why_wrong_by_choice: HashMap<String, String>,
    pub concept_explainer: String,
    #[serde(default = "default_lang")]
    pub language: String,
    #[serde(default = "default_interaction")]
    pub interaction_type: String,
    #[serde(default)]
    pub prompt_tag: Option<String>,
    #[serde(default)]
    pub prompt_count: Option<i32>,
    #[serde(default)]
    pub prompt_audio_key: Option<String>,
    #[serde(default)]
    pub mini_lesson: Option<MiniLesson>,
    #[serde(default = "default_source")]
    pub source: String,
}

fn default_lang() -> String {
    "en".into()
}
fn default_interaction() -> String {
    "TAP_TEXT".into()
}
fn default_source() -> String {
    "builtin".into()
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TopicSkill {
    pub topic: String,
    pub level: i32,
    pub streak_correct: i32,
    #[serde(default)]
    pub weak_concepts: HashSet<String>,
    #[serde(default)]
    pub concept_titles: HashMap<String, String>,
    #[serde(default)]
    pub mastered_concepts: HashSet<String>,
    #[serde(default = "default_tier")]
    pub tier_label: String,
    #[serde(default)]
    pub total_attempts: u32,
    #[serde(default)]
    pub total_correct: u32,
    #[serde(default)]
    pub avg_response_time_ms: i64,
}

fn default_tier() -> String {
    "basic".into()
}

impl TopicSkill {
    pub fn new(topic: impl Into<String>) -> Self {
        Self {
            topic: topic.into(),
            level: 2,
            streak_correct: 0,
            weak_concepts: HashSet::new(),
            concept_titles: HashMap::new(),
            mastered_concepts: HashSet::new(),
            tier_label: "basic".into(),
            total_attempts: 0,
            total_correct: 0,
            avg_response_time_ms: 0,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizAnswerFeedback {
    pub correct: bool,
    pub result_line: String,
    pub why_line: String,
    pub concept_line: String,
    pub next_level: i32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizSessionResult {
    pub passed: bool,
    pub correct_count: u32,
    pub total: u32,
    pub percent: u32,
}

/// Embedded Android builtin bank (14 questions).
pub const BUILTIN_QUIZ_BANK_JSON: &str = include_str!("../assets/quiz_bank_builtin.json");

pub fn load_builtin_bank() -> Result<Vec<QuizQuestion>, serde_json::Error> {
    serde_json::from_str(BUILTIN_QUIZ_BANK_JSON)
}

pub struct AdaptiveQuizEngine;

impl AdaptiveQuizEngine {
    pub fn with_shuffled_choices(question: QuizQuestion, rng: &mut impl Rng) -> QuizQuestion {
        if question.choices.len() <= 1 {
            return question;
        }
        let mut q = question;
        q.choices.shuffle(rng);
        q
    }

    pub fn tier_label(level: i32, streak_correct: i32) -> &'static str {
        if level >= 5 && streak_correct >= 2 {
            "mastery"
        } else if level >= 5 || level == 4 {
            "advanced"
        } else if level == 3 {
            "intermediate"
        } else {
            "basic"
        }
    }

    pub fn pick_next(
        bank: &[QuizQuestion],
        _skills: &HashMap<String, TopicSkill>,
        recent_ids: &HashSet<String>,
        used_in_session: &HashSet<String>,
        last_wrong_concept_id: Option<&str>,
        preferred_level: i32,
        rng: &mut impl Rng,
    ) -> Option<QuizQuestion> {
        if bank.is_empty() {
            return None;
        }
        let fresh: Vec<&QuizQuestion> = bank
            .iter()
            .filter(|q| {
                !used_in_session.contains(&q.id)
                    && !recent_ids.contains(&q.id)
                    && !recent_ids.contains(&Self::prompt_history_key(&q.prompt))
            })
            .collect();
        let pool: Vec<&QuizQuestion> = if fresh.is_empty() {
            bank.iter()
                .filter(|q| !used_in_session.contains(&q.id))
                .collect()
        } else {
            fresh
        };
        if pool.is_empty() {
            return None;
        }

        if let Some(wrong) = last_wrong_concept_id {
            let mut easier: Vec<&QuizQuestion> = pool
                .iter()
                .copied()
                .filter(|q| q.concept_id == wrong && q.difficulty <= preferred_level)
                .collect();
            easier.sort_by_key(|q| q.difficulty);
            if let Some(first) = easier.first() {
                return Some(Self::with_shuffled_choices((*first).clone(), rng));
            }
        }

        let near: Vec<&QuizQuestion> = pool
            .iter()
            .copied()
            .filter(|q| q.difficulty >= preferred_level - 1 && q.difficulty <= preferred_level + 1)
            .collect();
        let candidates = if near.is_empty() { pool } else { near };
        let preferred_source: Vec<&QuizQuestion> = {
            let ai: Vec<&QuizQuestion> = candidates
                .iter()
                .copied()
                .filter(|q| q.source == "ai")
                .collect();
            if ai.is_empty() {
                candidates
            } else {
                ai
            }
        };

        let mut by_topic: HashMap<&str, Vec<&QuizQuestion>> = HashMap::new();
        for q in &preferred_source {
            by_topic.entry(q.topic.as_str()).or_default().push(*q);
        }
        let mut mixed = Vec::new();
        for list in by_topic.values_mut() {
            list.shuffle(rng);
            mixed.extend(list.iter().take(2).copied());
        }
        let pick_from = if mixed.is_empty() {
            preferred_source
        } else {
            mixed
        };
        let picked = (*pick_from.choose(rng)?).clone();
        Some(Self::with_shuffled_choices(picked, rng))
    }

    pub fn session_start_level(skills: &HashMap<String, TopicSkill>) -> i32 {
        if skills.is_empty() {
            return 2;
        }
        let mut levels: Vec<i32> = skills.values().map(|s| s.level).collect();
        levels.sort_unstable();
        levels[levels.len() / 2].clamp(1, 5)
    }

    pub fn grade_answer(
        question: &QuizQuestion,
        choice_id: &str,
        skill: &TopicSkill,
        response_time_ms: i64,
    ) -> (QuizAnswerFeedback, TopicSkill) {
        let choice = question.choices.iter().find(|c| c.id == choice_id);
        let correct = choice.is_some_and(|c| c.correct);
        let next_level = if correct && skill.streak_correct + 1 >= 2 {
            (skill.level + 1).min(5)
        } else if correct {
            skill.level
        } else {
            (skill.level - 1).max(1)
        };
        let next_streak = if correct { skill.streak_correct + 1 } else { 0 };
        let mut weak = skill.weak_concepts.clone();
        let mut titles = skill.concept_titles.clone();
        if correct {
            weak.remove(&question.concept_id);
            titles.remove(&question.concept_id);
        } else {
            weak.insert(question.concept_id.clone());
            titles.insert(question.concept_id.clone(), question.concept_title.clone());
        }
        let new_attempts = skill.total_attempts + 1;
        let new_correct = if correct {
            skill.total_correct + 1
        } else {
            skill.total_correct
        };
        let new_avg = if skill.total_attempts == 0 {
            response_time_ms
        } else if response_time_ms > 0 {
            (skill.avg_response_time_ms * i64::from(skill.total_attempts) + response_time_ms)
                / i64::from(new_attempts)
        } else {
            skill.avg_response_time_ms
        };
        let effective_streak = if correct && next_level > skill.level {
            0
        } else {
            next_streak
        };
        let next_tier = Self::tier_label(next_level, effective_streak).to_string();
        let mut mastered = skill.mastered_concepts.clone();
        if correct && (next_level >= 4 || effective_streak >= 2) {
            mastered.insert(question.concept_id.clone());
        }

        let feedback = QuizAnswerFeedback {
            correct,
            result_line: if correct {
                "That’s it".into()
            } else {
                "Not this one".into()
            },
            why_line: if correct {
                question.why_correct.clone()
            } else {
                question
                    .why_wrong_by_choice
                    .get(choice_id)
                    .cloned()
                    .unwrap_or_else(|| "That choice doesn’t match. Let’s look at the idea.".into())
            },
            concept_line: question.concept_explainer.clone(),
            next_level,
        };
        titles.retain(|k, _| weak.contains(k));
        let updated = TopicSkill {
            topic: skill.topic.clone(),
            level: next_level,
            streak_correct: effective_streak,
            weak_concepts: weak,
            concept_titles: titles,
            mastered_concepts: mastered,
            tier_label: next_tier,
            total_attempts: new_attempts,
            total_correct: new_correct,
            avg_response_time_ms: new_avg,
        };
        (feedback, updated)
    }

    pub fn finalize(correct_count: u32, total: u32, pass_score_percent: u32) -> QuizSessionResult {
        let safe_total = total.max(1);
        let percent = (correct_count * 100) / safe_total;
        QuizSessionResult {
            passed: percent >= pass_score_percent,
            correct_count,
            total: safe_total,
            percent,
        }
    }

    pub fn questions_per_quiz(configured: u32) -> u32 {
        configured.clamp(3, 5)
    }

    pub fn prompt_history_key(prompt: &str) -> String {
        let filtered: String = prompt
            .chars()
            .filter(|c| c.is_ascii_alphanumeric())
            .flat_map(|c| c.to_lowercase())
            .collect();
        format!("prompt:{filtered}")
    }

    pub fn seeded_rng(seed: u64) -> StdRng {
        StdRng::seed_from_u64(seed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builtin_bank_loads() {
        let bank = load_builtin_bank().expect("builtin json");
        assert_eq!(bank.len(), 14);
        assert!(!bank[0].choices.is_empty());
    }

    #[test]
    fn grade_correct_and_finalize_pass() {
        let bank = load_builtin_bank().unwrap();
        let q = &bank[0];
        let correct_id = q.choices.iter().find(|c| c.correct).unwrap().id.clone();
        let skill = TopicSkill::new(&q.topic);
        let (fb, next) = AdaptiveQuizEngine::grade_answer(q, &correct_id, &skill, 1200);
        assert!(fb.correct);
        assert_eq!(next.total_correct, 1);
        let result = AdaptiveQuizEngine::finalize(3, 3, 70);
        assert!(result.passed);
        assert_eq!(result.percent, 100);
    }

    #[test]
    fn pick_next_avoids_used() {
        let bank = load_builtin_bank().unwrap();
        let mut rng = AdaptiveQuizEngine::seeded_rng(42);
        let mut used = HashSet::new();
        used.insert(bank[0].id.clone());
        let picked = AdaptiveQuizEngine::pick_next(
            &bank,
            &HashMap::new(),
            &HashSet::new(),
            &used,
            None,
            2,
            &mut rng,
        )
        .unwrap();
        assert_ne!(picked.id, bank[0].id);
    }
}

/* ── Quiz pack validation (Android `QuizPackValidator`) ─────────────────────
 * Generated/AI packs must pass the same gates on every platform before a single
 * question reaches a child. Invalid items are dropped, never repaired silently
 * (except the documented early-learner TAP_TEXT downgrade). */

pub const VISUAL_TAXONOMY_JSON: &str = include_str!("../assets/quiz_visual_taxonomy.json");

/// Visual tags a visual question may reference (Android `VisualTaxonomy.allTags()`).
pub fn visual_taxonomy_tags() -> std::collections::HashSet<String> {
    serde_json::from_str::<serde_json::Value>(VISUAL_TAXONOMY_JSON)
        .ok()
        .and_then(|v| v["tags"].as_array().cloned())
        .unwrap_or_default()
        .iter()
        .filter_map(|t| t["tag"].as_str().map(str::to_string))
        .collect()
}

/// Emoji for a visual tag (empty string when unknown).
pub fn visual_tag_emoji(tag: &str) -> &'static str {
    match tag {
        "dog" => "🐶", "cat" => "🐱", "cow" => "🐮", "lion" => "🦁", "elephant" => "🐘",
        "rabbit" => "🐰", "fox" => "🦊", "bear" => "🐻", "duck" => "🦆", "owl" => "🦉",
        "apple" => "🍎", "banana" => "🍌", "orange" => "🍊", "strawberry" => "🍓",
        "grapes" => "🍇", "watermelon" => "🍉", "circle" => "⭕", "square" => "⬛",
        "triangle" => "🔺", "star" => "⭐", "heart" => "❤️", "color_red" => "🔴",
        "color_blue" => "🔵", "color_yellow" => "🟡", "color_green" => "🟢",
        "color_orange" => "🟠", "color_purple" => "🟣",
        _ => "",
    }
}

const UNSAFE_TERMS: [&str; 11] = [
    "kill", "die", "suicide", "blood", "gun", "weapon", "hate you", "@gmail", "http://", "https://", "password",
];

fn contains_unsafe(text: &str) -> bool {
    let lower = text.to_lowercase();
    UNSAFE_TERMS.iter().any(|t| lower.contains(t))
}

/// Parse a pack (bare array, `{"items": […]}`, or one object; ```json fences tolerated)
/// and validate every item for `expected` band. Mirrors Android accept/drop semantics.
pub fn parse_and_validate_pack(
    raw_json: &str,
    expected: AgeBand,
    avoid_ids: &std::collections::HashSet<String>,
) -> Vec<QuizQuestion> {
    let trimmed = raw_json.trim().trim_start_matches("```json").trim_start_matches("```").trim_end_matches("```").trim();
    let Ok(value) = serde_json::from_str::<serde_json::Value>(trimmed) else {
        return Vec::new();
    };
    let items: Vec<serde_json::Value> = match &value {
        serde_json::Value::Array(a) => a.clone(),
        serde_json::Value::Object(o) if o.contains_key("items") => o["items"].as_array().cloned().unwrap_or_default(),
        serde_json::Value::Object(_) => vec![value.clone()],
        _ => Vec::new(),
    };
    let known = visual_taxonomy_tags();
    let mut accepted: Vec<QuizQuestion> = Vec::new();
    let mut seen: std::collections::HashSet<String> = avoid_ids.clone();
    for item in &items {
        let Ok(mut q) = serde_json::from_value::<QuizQuestion>(item.clone()) else { continue };
        if q.topic.trim().is_empty() || q.concept_id.trim().is_empty() || q.prompt.trim().is_empty() { continue; }
        if q.why_correct.trim().is_empty() || q.concept_explainer.trim().is_empty() { continue; }
        if !(1..=5).contains(&q.difficulty) { continue; }
        if contains_unsafe(&q.prompt) || contains_unsafe(&q.why_correct) { continue; }
        q.choices.retain(|c| !c.id.trim().is_empty());
        if q.choices.len() < 2 || q.choices.iter().filter(|c| c.correct).count() != 1 { continue; }
        for c in q.choices.iter().filter(|c| !c.correct) {
            q.why_wrong_by_choice.entry(c.id.clone()).or_insert_with(|| "Not quite — try another answer.".into());
        }
        if expected.bank_band() == AgeBand::Age3To6 {
            let mut tags: Vec<&str> = q.prompt_tag.as_deref().into_iter().collect();
            tags.extend(q.choices.iter().filter_map(|c| c.image_tag.as_deref()));
            let unknown = tags.iter().any(|t| !known.contains(*t));
            let text_only = q.choices.iter().any(|c| !c.text.trim().is_empty());
            if unknown && text_only {
                q.interaction_type = "TAP_TEXT".into();
                q.prompt_tag = None;
                q.prompt_count = None;
                q.prompt_audio_key = None;
                for c in &mut q.choices { c.image_tag = None; c.audio_key = None; }
            } else if unknown {
                continue;
            } else if q.interaction_type == "TAP_TEXT" && q.choices.iter().all(|c| c.text.trim().is_empty() && c.image_tag.is_none()) {
                continue;
            }
        }
        if let Some(lesson) = &mut q.mini_lesson {
            if lesson.title.trim().is_empty() || lesson.body_lines.is_empty() {
                q.mini_lesson = None;
            } else {
                lesson.body_lines = lesson.body_lines.iter().take(3).map(|l| l.trim().to_string()).filter(|l| !l.is_empty()).collect();
                if lesson.body_lines.is_empty() {
                    q.mini_lesson = None;
                } else if expected.bank_band() == AgeBand::Age3To6 {
                    lesson.body_lines.truncate(1);
                }
            }
        }
        if q.id.trim().is_empty() || seen.contains(&q.id) {
            q.id = format!("ai_{}", pack_id_hash(&q.prompt, accepted.len()));
        }
        if seen.contains(&q.id) { continue; }
        seen.insert(q.id.clone());
        q.age_band = expected.bank_band();
        q.topic = q.topic.trim().to_lowercase().replace(' ', "-");
        q.concept_id = q.concept_id.trim().to_string();
        if q.concept_title.trim().is_empty() { q.concept_title = q.concept_id.clone(); }
        q.prompt = q.prompt.trim().to_string();
        q.why_correct = q.why_correct.trim().to_string();
        q.concept_explainer = q.concept_explainer.trim().to_string();
        if q.language.trim().is_empty() { q.language = "en".into(); }
        q.source = "ai".into();
        accepted.push(q);
    }
    accepted
}

fn pack_id_hash(seed: &str, n: usize) -> String {
    // Deterministic id for reproducibility (Android uses a random uuid).
    let mut h: u64 = 0xcbf29ce484222325;
    for b in seed.bytes().chain(n.to_be_bytes()) {
        h ^= u64::from(b);
        h = h.wrapping_mul(0x100000001b3);
    }
    format!("{h:016x}")
}

/// Android `shouldGenerate`: generate when the band has no AI items or the near-rung pool is low.
pub fn pack_should_generate(near_rung_unused: usize, ai_count_for_band: usize, low_threshold: usize) -> bool {
    ai_count_for_band == 0 || near_rung_unused < low_threshold
}

#[cfg(test)]
mod pack_tests {
    use super::*;

    fn item(id: &str) -> String {
        format!(
            r#"{{"id":"{id}","ageBand":"AGE_7_TO_9","topic":"Math Fun","conceptId":"add","conceptTitle":"Addition","difficulty":3,"prompt":"1+1?","choices":[{{"id":"a","text":"2","correct":true}},{{"id":"b","text":"3","correct":false}}],"whyCorrect":"Because.","whyWrongByChoice":{{}},"conceptExplainer":"Adding."}}"#
        )
    }

    #[test]
    fn pack_validation_accepts_and_normalizes() {
        let out = parse_and_validate_pack(&format!("[{}]", item("p1")), AgeBand::Age7To9, &HashSet::new());
        assert_eq!(out.len(), 1);
        assert_eq!(out[0].topic, "math-fun");
        assert_eq!(out[0].source, "ai");
        assert!(out[0].why_wrong_by_choice.contains_key("b"));
        let wrapped = format!("```json\n{{\"items\":[{}]}}\n```", item("p2"));
        assert_eq!(parse_and_validate_pack(&wrapped, AgeBand::Age7To9, &HashSet::new()).len(), 1);
    }

    #[test]
    fn pack_validation_drops_unsafe_and_invalid() {
        let bad_difficulty = item("p1").replace("\"difficulty\":3", "\"difficulty\":9");
        let two_correct = item("p2").replace("\"correct\":false", "\"correct\":true");
        let unsafe_q = item("p3").replace("1+1?", "visit https://example.com 1+1?");
        let raw = format!("[{bad_difficulty},{two_correct},{unsafe_q},{}]", item("ok"));
        let out = parse_and_validate_pack(&raw, AgeBand::Age7To9, &HashSet::new());
        assert_eq!(out.len(), 1);
        assert_eq!(out[0].id, "ok");
        let dup = format!("[{},{}]", item("x"), item("x"));
        let out = parse_and_validate_pack(&dup, AgeBand::Age7To9, &HashSet::new());
        assert_eq!(out.len(), 2);
        assert_ne!(out[0].id, out[1].id);
        assert!(pack_should_generate(5, 0, 8));
        assert!(!pack_should_generate(9, 3, 8));
    }

    #[test]
    fn early_learner_unknown_visual_tags_downgrade() {
        let visual = r#"{"id":"v1","ageBand":"AGE_3_TO_6","topic":"animals","conceptId":"dog","conceptTitle":"Dog","difficulty":1,"prompt":"Which is the dog?","choices":[{"id":"a","text":"Dog","correct":true,"imageTag":"dog"},{"id":"b","text":"Cat","correct":false,"imageTag":"invented_tag"}],"whyCorrect":"It barks.","whyWrongByChoice":{},"conceptExplainer":"Dogs bark.","interactionType":"TAP_IMAGE"}"#;
        let out = parse_and_validate_pack(&format!("[{visual}]"), AgeBand::Age3To6, &HashSet::new());
        assert_eq!(out.len(), 1);
        assert_eq!(out[0].interaction_type, "TAP_TEXT");
        assert!(out[0].choices.iter().all(|c| c.image_tag.is_none()));
        assert_eq!(visual_taxonomy_tags().len(), 32);
        assert_eq!(visual_tag_emoji("dog"), "🐶");
    }
}
