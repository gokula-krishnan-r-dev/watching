//! Parent dashboard analytics (Android parity): reads the child's cloud telemetry —
//! `usageDays/{YYYY-MM-DD}`, `quizAttempts`, `skillState/current`, `stickerUnlocks`,
//! `explorerProgress/current`, `devices` — and summarizes it into one DTO per child.
//!
//! Parsing and aggregation are pure so they can be tested without the network.

use std::collections::{BTreeMap, HashMap};

use chrono::{DateTime, Duration, NaiveDate};
use meritscreen_core::{AppRule, ChildPolicy};
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

/// Days of history shown in the weekly chart.
pub const TREND_DAYS: i64 = 7;
/// Device considered online if seen within this window.
pub const ONLINE_WINDOW_MS: i64 = 15 * 60_000;
/// Device "offline" alert after this long without a heartbeat.
pub const OFFLINE_ALERT_MS: i64 = 24 * 60 * 60_000;

/// Collection paths (relative to the child doc), incl. REST list query params.
pub const USAGE_DAYS_QUERY: &str = "usageDays?pageSize=60";
pub const QUIZ_ATTEMPTS_QUERY: &str = "quizAttempts?pageSize=50&orderBy=createdAt%20desc";

/* ─────────────── Firestore REST value → plain JSON ─────────────── */

/// Convert a Firestore REST typed value (`{"integerValue":"3"}` …) to plain JSON.
pub fn plain(v: &Value) -> Value {
    let Some(obj) = v.as_object() else { return v.clone() };
    if let Some(s) = obj.get("stringValue") {
        return s.clone();
    }
    if let Some(i) = obj.get("integerValue") {
        return i
            .as_str()
            .and_then(|s| s.parse::<i64>().ok())
            .map(Value::from)
            .unwrap_or_else(|| i.clone());
    }
    if let Some(d) = obj.get("doubleValue") {
        return d.clone();
    }
    if let Some(b) = obj.get("booleanValue") {
        return b.clone();
    }
    if obj.contains_key("nullValue") {
        return Value::Null;
    }
    if let Some(t) = obj.get("timestampValue") {
        // Keep as epoch ms for convenience.
        return t
            .as_str()
            .and_then(|s| DateTime::parse_from_rfc3339(s).ok())
            .map(|d| Value::from(d.timestamp_millis()))
            .unwrap_or(Value::Null);
    }
    if let Some(m) = obj.get("mapValue") {
        return fields_to_plain(m.get("fields").unwrap_or(&Value::Null));
    }
    if let Some(a) = obj.get("arrayValue") {
        let vals = a.get("values").and_then(Value::as_array).cloned().unwrap_or_default();
        return Value::Array(vals.iter().map(plain).collect());
    }
    v.clone()
}

fn fields_to_plain(fields: &Value) -> Value {
    let mut out = Map::new();
    if let Some(f) = fields.as_object() {
        for (k, v) in f {
            out.insert(k.clone(), plain(v));
        }
    }
    Value::Object(out)
}

/// A REST document (`{"name": …, "fields": {…}}`) as `(id, plain fields)`.
pub fn plain_doc(doc: &Value) -> (String, Value) {
    let id = doc["name"].as_str().and_then(|n| n.rsplit('/').next()).unwrap_or("").to_string();
    (id, fields_to_plain(&doc["fields"]))
}

/// Documents from a REST list response.
pub fn plain_docs(list: Option<&Value>) -> Vec<(String, Value)> {
    list.and_then(|v| v["documents"].as_array())
        .map(|a| a.iter().map(plain_doc).collect())
        .unwrap_or_default()
}

fn int(v: &Value, k: &str) -> i64 {
    v.get(k).and_then(Value::as_i64).or_else(|| v.get(k).and_then(Value::as_f64).map(|f| f as i64)).unwrap_or(0)
}
fn opt_int(v: &Value, k: &str) -> Option<i64> {
    v.get(k).and_then(|x| x.as_i64().or_else(|| x.as_f64().map(|f| f as i64)))
}
fn string(v: &Value, k: &str) -> String {
    v.get(k).and_then(Value::as_str).unwrap_or("").to_string()
}
fn boolean(v: &Value, k: &str) -> bool {
    v.get(k).and_then(Value::as_bool).unwrap_or(false)
}
fn strings(v: &Value, k: &str) -> Vec<String> {
    v.get(k)
        .and_then(Value::as_array)
        .map(|a| a.iter().filter_map(|x| x.as_str().map(String::from)).collect())
        .unwrap_or_default()
}

/* ─────────────── Raw cloud records (Android field names) ─────────────── */

#[derive(Debug, Clone, Default, PartialEq)]
pub struct UsageDay {
    pub day: String,
    pub minutes_used: u32,
    pub minutes_by_app: BTreeMap<String, u32>,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct QuizAttempt {
    pub created_at_ms: i64,
    pub topics: Vec<String>,
    pub score: u32,
    pub total: u32,
    pub passed: bool,
    pub extra_minutes_granted: u32,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct TopicSkill {
    pub topic: String,
    pub level: u32,
    pub streak_correct: u32,
    pub weak: bool,
    pub weak_concepts: Vec<String>,
    pub mastered_count: u32,
    pub tier_label: String,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct StickerUnlock {
    pub sticker_id: String,
    pub title: String,
    pub emoji: String,
    pub unlocked_at_ms: i64,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct ExplorerProgress {
    pub xp: u32,
    pub level: u32,
    pub pass_streak_days: u32,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct DeviceRecord {
    pub device_id: String,
    pub platform: String,
    pub revoked: bool,
    pub last_seen_ms: Option<i64>,
    pub model: Option<String>,
    pub battery_percent: Option<u32>,
    pub os_version: Option<String>,
    pub app_version: Option<String>,
}

/// Everything loaded for one child.
#[derive(Debug, Clone, Default)]
pub struct ChildTelemetry {
    pub usage_days: Vec<UsageDay>,
    pub quiz_attempts: Vec<QuizAttempt>,
    /// Aggregate quiz counts when per-attempt records aren't available (local fallback).
    pub quiz_today_counts: Option<(u32, u32)>,
    pub skills: Vec<TopicSkill>,
    pub stickers: Vec<StickerUnlock>,
    pub explorer: Option<ExplorerProgress>,
    pub devices: Vec<DeviceRecord>,
}

pub fn parse_usage_day(id: &str, f: &Value) -> UsageDay {
    let mut by_app = BTreeMap::new();
    if let Some(m) = f.get("minutesByApp").and_then(Value::as_object) {
        for (k, v) in m {
            let n = v.as_i64().or_else(|| v.as_f64().map(|x| x as i64)).unwrap_or(0);
            if n > 0 {
                by_app.insert(k.clone(), n as u32);
            }
        }
    }
    UsageDay { day: id.to_string(), minutes_used: int(f, "minutesUsed").max(0) as u32, minutes_by_app: by_app }
}

pub fn parse_quiz_attempt(f: &Value) -> QuizAttempt {
    QuizAttempt {
        created_at_ms: opt_int(f, "createdAt").or_else(|| opt_int(f, "createdAtEpochMs")).unwrap_or(0),
        topics: strings(f, "topics"),
        score: int(f, "score").max(0) as u32,
        total: int(f, "total").max(0) as u32,
        passed: boolean(f, "passed"),
        extra_minutes_granted: int(f, "extraMinutesGranted").max(0) as u32,
    }
}

pub fn parse_skill_state(f: &Value) -> Vec<TopicSkill> {
    let Some(topics) = f.get("topics").and_then(Value::as_object) else { return Vec::new() };
    let mut out: Vec<TopicSkill> = topics
        .iter()
        .filter(|(_, v)| v.is_object())
        .map(|(topic, m)| {
            let ids = strings(m, "weakConcepts");
            let titles = m.get("weakConceptTitles").cloned().unwrap_or(Value::Null);
            let weak_concepts = ids
                .iter()
                .map(|id| titles.get(id).and_then(Value::as_str).filter(|s| !s.is_empty()).map(String::from).unwrap_or_else(|| humanize(id)))
                .collect::<Vec<_>>();
            TopicSkill {
                topic: topic.clone(),
                level: opt_int(m, "level").unwrap_or(2).max(0) as u32,
                streak_correct: int(m, "streakCorrect").max(0) as u32,
                weak: boolean(m, "weak") || !weak_concepts.is_empty(),
                weak_concepts,
                mastered_count: strings(m, "masteredConcepts").len() as u32,
                tier_label: Some(string(m, "tierLabel")).filter(|s| !s.is_empty()).unwrap_or_else(|| "basic".into()),
            }
        })
        .collect();
    out.sort_by(|a, b| a.topic.cmp(&b.topic));
    out
}

pub fn parse_sticker(f: &Value) -> Option<StickerUnlock> {
    let id = string(f, "stickerId");
    if id.is_empty() {
        return None;
    }
    Some(StickerUnlock {
        sticker_id: id,
        title: string(f, "title"),
        emoji: string(f, "emoji"),
        unlocked_at_ms: opt_int(f, "unlockedAt").or_else(|| opt_int(f, "unlockedAtEpochMs")).unwrap_or(0),
    })
}

pub fn parse_explorer(f: &Value) -> ExplorerProgress {
    ExplorerProgress {
        xp: int(f, "xp").max(0) as u32,
        level: opt_int(f, "explorerLevel").unwrap_or(1).max(1) as u32,
        pass_streak_days: int(f, "passStreakDays").max(0) as u32,
    }
}

pub fn parse_device(id: &str, f: &Value) -> DeviceRecord {
    let opt_s = |k: &str| Some(string(f, k)).filter(|s| !s.is_empty());
    DeviceRecord {
        device_id: id.to_string(),
        platform: opt_s("platform").unwrap_or_else(|| "android".into()),
        revoked: boolean(f, "revoked"),
        last_seen_ms: opt_int(f, "lastSeenAt"),
        model: opt_s("model"),
        battery_percent: opt_int(f, "batteryPercent").filter(|b| (0..=100).contains(b)).map(|b| b as u32),
        os_version: opt_s("osVersion"),
        app_version: opt_s("appVersion"),
    }
}

/// `fractions_basic` → "Fractions basic".
pub fn humanize(token: &str) -> String {
    let s = token.replace(['_', '-'], " ");
    let mut c = s.trim().chars();
    match c.next() {
        Some(f) => f.to_uppercase().collect::<String>() + c.as_str(),
        None => String::new(),
    }
}

/* ─────────────── Summary DTO for the dashboard ─────────────── */

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct DayPoint {
    pub day: String,
    pub minutes: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct AppShare {
    pub app_id: String,
    pub label: String,
    pub minutes: u32,
    pub percent: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct RecentAttempt {
    pub at_ms: i64,
    pub passed: bool,
    pub score: u32,
    pub total: u32,
    pub topic: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct QuizSummary {
    pub attempts_week: u32,
    pub passes_week: u32,
    pub pass_rate_percent: Option<u32>,
    pub attempts_today: u32,
    pub passes_today: u32,
    pub minutes_earned_week: u32,
    pub pass_streak_days: u32,
    pub last_attempt_at_ms: Option<i64>,
    pub recent: Vec<RecentAttempt>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct SkillLine {
    pub topic: String,
    pub label: String,
    pub level: u32,
    pub tier: String,
    pub concepts: Vec<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct SkillSummary {
    pub strengths: Vec<SkillLine>,
    pub needs_practice: Vec<SkillLine>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct StickerLine {
    pub emoji: String,
    pub title: String,
    pub unlocked_at_ms: i64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct RewardsSummary {
    pub xp: u32,
    pub level: u32,
    pub sticker_count: u32,
    pub recent_stickers: Vec<StickerLine>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct DeviceLine {
    pub device_id: String,
    pub platform: String,
    pub model: Option<String>,
    pub last_seen_at_ms: Option<i64>,
    pub online: bool,
    pub battery_percent: Option<u32>,
    pub app_version: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Alert {
    /// `info` | `warning` | `critical`
    pub severity: String,
    pub kind: String,
    pub message: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ChildInsights {
    pub child_id: String,
    pub display_name: String,
    pub age_band: String,
    pub paused: bool,
    pub daily_limit_minutes: Option<u32>,
    pub bonus_minutes_today: u32,
    pub used_today_minutes: u32,
    pub remaining_today_minutes: Option<u32>,
    pub week_total_minutes: u32,
    pub daily_average_minutes: u32,
    /// Change vs the previous 7 days; `None` when there is no prior data.
    pub week_delta_percent: Option<i32>,
    pub trend: Vec<DayPoint>,
    pub top_apps: Vec<AppShare>,
    pub quiz: QuizSummary,
    pub skills: SkillSummary,
    pub rewards: RewardsSummary,
    pub devices: Vec<DeviceLine>,
    pub alerts: Vec<Alert>,
    /// Set when this child's cloud data couldn't be loaded (others still render).
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct FamilyInsights {
    /// `cloud` (Firestore) or `local` (this computer only).
    pub source: String,
    pub generated_at_ms: i64,
    pub children: Vec<ChildInsights>,
}

/// Inputs describing the child that don't come from telemetry.
pub struct ChildContext<'a> {
    pub child_id: &'a str,
    pub display_name: &'a str,
    pub age_band: &'a str,
    pub policy: &'a ChildPolicy,
    pub rules: &'a [AppRule],
    /// Paired device count known locally (used when no device docs were loaded).
    pub paired_device_count: u32,
    /// Today's minutes known locally (max'ed with the cloud value).
    pub local_minutes_today: u32,
}

fn shift_day(day: NaiveDate, by: i64) -> String {
    (day + Duration::days(by)).format("%Y-%m-%d").to_string()
}

/// Summarize one child. `today` is the parent's local date; `now_ms` wall clock.
pub fn summarize(ctx: &ChildContext<'_>, t: &ChildTelemetry, today: NaiveDate, now_ms: i64) -> ChildInsights {
    let by_day: HashMap<&str, &UsageDay> = t.usage_days.iter().map(|d| (d.day.as_str(), d)).collect();
    let mins = |k: &str| by_day.get(k).map(|d| d.minutes_used).unwrap_or(0);

    let today_key = shift_day(today, 0);
    let used_today = mins(&today_key).max(ctx.local_minutes_today);

    let trend: Vec<DayPoint> = (0..TREND_DAYS)
        .rev()
        .map(|i| {
            let day = shift_day(today, -i);
            let minutes = if i == 0 { used_today } else { mins(&day) };
            DayPoint { day, minutes }
        })
        .collect();
    let week_total: u32 = trend.iter().map(|p| p.minutes).sum();
    let prev_total: u32 = (TREND_DAYS..TREND_DAYS * 2).map(|i| mins(&shift_day(today, -i))).sum();
    let week_delta_percent = if prev_total > 0 {
        Some(((week_total as f64 - prev_total as f64) / prev_total as f64 * 100.0).round() as i32)
    } else {
        None
    };
    let active_days = trend.iter().filter(|p| p.minutes > 0).count().max(1) as u32;

    // Top apps over the week, labelled from app rules.
    let label_for = |key: &str| -> String {
        ctx.rules
            .iter()
            .find(|r| r.app_id == key || r.package_or_bundle_id == key)
            .map(|r| r.display_name.clone())
            .filter(|n| !n.is_empty())
            .unwrap_or_else(|| key.rsplit('.').next().map(humanize).unwrap_or_else(|| key.to_string()))
    };
    let mut app_totals: BTreeMap<String, u32> = BTreeMap::new();
    for p in &trend {
        if let Some(d) = by_day.get(p.day.as_str()) {
            for (k, v) in &d.minutes_by_app {
                *app_totals.entry(k.clone()).or_default() += v;
            }
        }
    }
    let app_sum: u32 = app_totals.values().sum();
    let mut top_apps: Vec<AppShare> = app_totals
        .into_iter()
        .map(|(k, m)| AppShare {
            label: label_for(&k),
            percent: if app_sum > 0 { ((m as f64 / app_sum as f64) * 100.0).round() as u32 } else { 0 },
            app_id: k,
            minutes: m,
        })
        .collect();
    top_apps.sort_by(|a, b| b.minutes.cmp(&a.minutes).then(a.label.cmp(&b.label)));
    top_apps.truncate(5);

    // Quiz stats (7-day window by timestamp; today by local date).
    let week_start_ms = now_ms - TREND_DAYS * 24 * 60 * 60_000;
    let day_start_ms = today
        .and_hms_opt(0, 0, 0)
        .and_then(|d| d.and_local_timezone(chrono::Local).single())
        .map(|d| d.timestamp_millis())
        .unwrap_or(now_ms - 24 * 60 * 60_000);
    let mut attempts: Vec<&QuizAttempt> = t.quiz_attempts.iter().collect();
    attempts.sort_by(|a, b| b.created_at_ms.cmp(&a.created_at_ms));
    let week: Vec<&&QuizAttempt> = attempts.iter().filter(|a| a.created_at_ms >= week_start_ms).collect();
    let (mut attempts_today, mut passes_today) = (
        attempts.iter().filter(|a| a.created_at_ms >= day_start_ms).count() as u32,
        attempts.iter().filter(|a| a.created_at_ms >= day_start_ms && a.passed).count() as u32,
    );
    let mut attempts_week = week.len() as u32;
    let mut passes_week = week.iter().filter(|a| a.passed).count() as u32;
    if let Some((a, p)) = t.quiz_today_counts {
        attempts_today = attempts_today.max(a);
        passes_today = passes_today.max(p);
        attempts_week = attempts_week.max(a);
        passes_week = passes_week.max(p);
    }
    let quiz = QuizSummary {
        attempts_week,
        passes_week,
        pass_rate_percent: (attempts_week > 0).then(|| ((passes_week as f64 / attempts_week as f64) * 100.0).round() as u32),
        attempts_today,
        passes_today,
        minutes_earned_week: week.iter().filter(|a| a.passed).map(|a| a.extra_minutes_granted).sum(),
        pass_streak_days: t.explorer.as_ref().map(|e| e.pass_streak_days).unwrap_or(0),
        last_attempt_at_ms: attempts.first().map(|a| a.created_at_ms).filter(|&m| m > 0),
        recent: attempts
            .iter()
            .take(5)
            .map(|a| RecentAttempt {
                at_ms: a.created_at_ms,
                passed: a.passed,
                score: a.score,
                total: a.total,
                topic: a.topics.first().map(|s| humanize(s)).unwrap_or_default(),
            })
            .collect(),
    };

    // Skills.
    let line = |s: &TopicSkill| SkillLine {
        topic: s.topic.clone(),
        label: humanize(&s.topic),
        level: s.level,
        tier: s.tier_label.clone(),
        concepts: s.weak_concepts.iter().take(3).cloned().collect(),
    };
    let mut strengths: Vec<SkillLine> = t.skills.iter().filter(|s| !s.weak && (s.level >= 3 || s.mastered_count > 0)).map(line).collect();
    strengths.sort_by(|a, b| b.level.cmp(&a.level));
    strengths.truncate(4);
    let mut needs_practice: Vec<SkillLine> = t.skills.iter().filter(|s| s.weak).map(line).collect();
    needs_practice.truncate(4);

    // Rewards.
    let mut stickers = t.stickers.clone();
    stickers.sort_by(|a, b| b.unlocked_at_ms.cmp(&a.unlocked_at_ms));
    let rewards = RewardsSummary {
        xp: t.explorer.as_ref().map(|e| e.xp).unwrap_or(0),
        level: t.explorer.as_ref().map(|e| e.level).unwrap_or(1),
        sticker_count: stickers.len() as u32,
        recent_stickers: stickers
            .iter()
            .take(6)
            .map(|s| StickerLine {
                emoji: if s.emoji.is_empty() { "⭐".into() } else { s.emoji.clone() },
                title: if s.title.is_empty() { humanize(&s.sticker_id) } else { s.title.clone() },
                unlocked_at_ms: s.unlocked_at_ms,
            })
            .collect(),
    };

    // Devices.
    let devices: Vec<DeviceLine> = t
        .devices
        .iter()
        .filter(|d| !d.revoked)
        .map(|d| DeviceLine {
            device_id: d.device_id.clone(),
            platform: d.platform.clone(),
            model: d.model.clone(),
            last_seen_at_ms: d.last_seen_ms,
            online: d.last_seen_ms.is_some_and(|s| now_ms - s <= ONLINE_WINDOW_MS),
            battery_percent: d.battery_percent,
            app_version: d.app_version.clone(),
        })
        .collect();

    // Limits.
    let bonus = ctx.policy.active_bonus_minutes(&today_key, "");
    let limit = ctx.policy.daily_ceiling_minutes;
    let remaining = limit.map(|l| (l + bonus).saturating_sub(used_today));

    // Alerts, most severe first.
    let name = ctx.display_name;
    let mut alerts = Vec::new();
    let mut push = |sev: &str, kind: &str, msg: String| alerts.push(Alert { severity: sev.into(), kind: kind.into(), message: msg });
    let paired = !devices.is_empty() || ctx.paired_device_count > 0;
    if !paired {
        push("info", "not_paired", format!("{name} has no paired device yet."));
    }
    for d in &devices {
        if let Some(seen) = d.last_seen_at_ms {
            if now_ms - seen > OFFLINE_ALERT_MS {
                let label = d.model.clone().unwrap_or_else(|| d.platform.clone());
                push("warning", "device_offline", format!("{name}’s {label} hasn’t checked in for over a day."));
            }
        }
        if let Some(b) = d.battery_percent {
            if b <= 15 {
                push("info", "battery_low", format!("{name}’s device battery is at {b}%."));
            }
        }
    }
    if ctx.policy.paused {
        push("info", "paused", format!("{name}’s screen time is paused."));
    }
    match remaining {
        Some(0) => push("warning", "limit_reached", format!("{name} reached today’s screen-time limit.")),
        Some(r) if r <= 15 => push("info", "limit_near", format!("{name} has {r} min left today.")),
        _ => {}
    }
    if attempts_week >= 3 && quiz.pass_rate_percent.unwrap_or(100) < 50 {
        push("warning", "quiz_struggling", format!("{name} passed {passes_week} of {attempts_week} quizzes this week."));
    }
    if let Some(s) = needs_practice.first() {
        push("info", "needs_practice", format!("{name} could use practice in {}.", s.label.to_lowercase()));
    }
    let rank = |s: &str| match s {
        "critical" => 0,
        "warning" => 1,
        _ => 2,
    };
    alerts.sort_by_key(|a| rank(&a.severity));

    ChildInsights {
        child_id: ctx.child_id.to_string(),
        display_name: ctx.display_name.to_string(),
        age_band: ctx.age_band.to_string(),
        paused: ctx.policy.paused,
        daily_limit_minutes: limit,
        bonus_minutes_today: bonus,
        used_today_minutes: used_today,
        remaining_today_minutes: remaining,
        week_total_minutes: week_total,
        daily_average_minutes: week_total / active_days,
        week_delta_percent,
        trend,
        top_apps,
        quiz,
        skills: SkillSummary { strengths, needs_practice },
        rewards,
        devices,
        alerts,
        error: None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn d(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").unwrap()
    }

    #[test]
    fn plain_converts_firestore_types() {
        let v = json!({"mapValue": {"fields": {
            "n": {"integerValue": "42"}, "s": {"stringValue": "x"}, "b": {"booleanValue": true},
            "t": {"timestampValue": "2026-10-16T10:00:00Z"}, "a": {"arrayValue": {"values": [{"stringValue": "m"}]}},
            "z": {"nullValue": null}
        }}});
        let p = plain(&v);
        assert_eq!(p["n"], 42);
        assert_eq!(p["s"], "x");
        assert_eq!(p["b"], true);
        assert_eq!(p["t"], 1_792_144_800_000_i64);
        assert_eq!(p["a"][0], "m");
        assert!(p["z"].is_null());
    }

    #[test]
    fn parses_android_documents() {
        let u = parse_usage_day("2026-10-16", &json!({"minutesUsed": 50, "minutesByApp": {"com.mojang": 30, "com.zero": 0}}));
        assert_eq!(u.minutes_used, 50);
        assert_eq!(u.minutes_by_app.len(), 1);
        let s = parse_skill_state(&json!({"topics": {"fractions": {"level": 1, "weak": true, "weakConcepts": ["halves_basic"], "weakConceptTitles": {}}, "addition": {"level": 4, "masteredConcepts": ["a"]}}}));
        assert_eq!(s.len(), 2);
        assert_eq!(s[1].weak_concepts, vec!["Halves basic".to_string()]);
        assert!(parse_sticker(&json!({"title": "x"})).is_none());
        let dev = parse_device("d1", &json!({"batteryPercent": 140, "model": "Pixel"}));
        assert_eq!(dev.battery_percent, None);
        assert_eq!(dev.platform, "android");
    }

    #[test]
    fn summarize_builds_week_quiz_and_alerts() {
        let today = d("2026-10-16");
        let now = today.and_hms_opt(18, 0, 0).unwrap().and_local_timezone(chrono::Local).unwrap().timestamp_millis();
        let policy = ChildPolicy { daily_ceiling_minutes: Some(60), ..Default::default() };
        let mut rule = AppRule::new("mc", "com.mojang");
        rule.display_name = "Minecraft".into();
        let rules = vec![rule];
        let ctx = ChildContext { child_id: "c1", display_name: "Mia", age_band: "7_to_9", policy: &policy, rules: &rules, paired_device_count: 1, local_minutes_today: 0 };
        let mut t = ChildTelemetry::default();
        t.usage_days = vec![
            parse_usage_day("2026-10-16", &json!({"minutesUsed": 60, "minutesByApp": {"com.mojang": 40, "com.apple.Safari": 20}})),
            parse_usage_day("2026-10-15", &json!({"minutesUsed": 40})),
            parse_usage_day("2026-10-05", &json!({"minutesUsed": 200})),
        ];
        let h = 3_600_000;
        t.quiz_attempts = vec![
            QuizAttempt { created_at_ms: now - h, passed: false, total: 3, score: 1, topics: vec!["fractions".into()], ..Default::default() },
            QuizAttempt { created_at_ms: now - 2 * h, passed: false, total: 3, ..Default::default() },
            QuizAttempt { created_at_ms: now - 3 * h, passed: true, total: 3, score: 3, extra_minutes_granted: 15, ..Default::default() },
            QuizAttempt { created_at_ms: now - 30 * 24 * h, passed: true, ..Default::default() },
        ];
        t.explorer = Some(ExplorerProgress { xp: 120, level: 3, pass_streak_days: 4 });
        t.devices = vec![DeviceRecord { device_id: "d".into(), platform: "android".into(), last_seen_ms: Some(now - 2 * 24 * h), battery_percent: Some(10), ..Default::default() }];

        let i = summarize(&ctx, &t, today, now);
        assert_eq!(i.trend.len(), 7);
        assert_eq!(i.trend.last().unwrap().minutes, 60);
        assert_eq!(i.week_total_minutes, 100);
        assert_eq!(i.week_delta_percent, Some(-50));
        assert_eq!(i.daily_average_minutes, 50);
        assert_eq!(i.top_apps[0].label, "Minecraft");
        assert_eq!(i.top_apps[0].percent, 67);
        assert_eq!(i.quiz.attempts_week, 3);
        assert_eq!(i.quiz.pass_rate_percent, Some(33));
        assert_eq!(i.quiz.minutes_earned_week, 15);
        assert_eq!(i.quiz.pass_streak_days, 4);
        assert_eq!(i.quiz.recent[0].topic, "Fractions");
        assert_eq!(i.remaining_today_minutes, Some(0));
        assert!(!i.devices[0].online);
        let kinds: Vec<&str> = i.alerts.iter().map(|a| a.kind.as_str()).collect();
        assert!(kinds.contains(&"limit_reached"));
        assert!(kinds.contains(&"device_offline"));
        assert!(kinds.contains(&"battery_low"));
        assert!(kinds.contains(&"quiz_struggling"));
        assert_eq!(i.alerts[0].severity, "warning");
    }

    #[test]
    fn summarize_empty_child_is_calm() {
        let policy = ChildPolicy::default();
        let ctx = ChildContext { child_id: "c", display_name: "Leo", age_band: "", policy: &policy, rules: &[], paired_device_count: 0, local_minutes_today: 0 };
        let i = summarize(&ctx, &ChildTelemetry::default(), d("2026-10-16"), 0);
        assert_eq!(i.week_total_minutes, 0);
        assert_eq!(i.quiz.pass_rate_percent, None);
        assert_eq!(i.week_delta_percent, None);
        assert_eq!(i.alerts.len(), 1);
        assert_eq!(i.alerts[0].kind, "not_paired");
        assert_eq!(i.remaining_today_minutes, None);
    }
}
