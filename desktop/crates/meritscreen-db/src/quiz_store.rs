//! SQLCipher persistence for quiz items and child topic skill state.
//! Hot path reads SQLCipher only — zero network or Firebase latency.

use meritscreen_core::{load_builtin_bank, QuizQuestion, TopicSkill};
use rusqlite::Connection;
use std::collections::HashMap;

use crate::error::{DbError, DbResult};

fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Store quiz questions into local SQLCipher `quiz_items` table.
pub fn insert_quiz_items(
    conn: &Connection,
    items: &[QuizQuestion],
    pack_id: &str,
) -> DbResult<usize> {
    let now = now_ms();
    let mut count = 0;
    for item in items {
        let json_raw =
            serde_json::to_string(item).map_err(|e| DbError::Migration(e.to_string()))?;
        conn.execute(
            "INSERT INTO quiz_items(id, pack_id, skill_id, difficulty, json, updated_at_ms)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)
             ON CONFLICT(id) DO UPDATE SET
               pack_id = excluded.pack_id,
               skill_id = excluded.skill_id,
               difficulty = excluded.difficulty,
               json = excluded.json,
               updated_at_ms = excluded.updated_at_ms",
            rusqlite::params![item.id, pack_id, item.topic, item.difficulty, json_raw, now],
        )
        .map_err(DbError::Query)?;
        count += 1;
    }
    Ok(count)
}

/// Load all cached quiz questions from local SQLCipher store.
pub fn load_quiz_bank(conn: &Connection) -> DbResult<Vec<QuizQuestion>> {
    let mut stmt = conn
        .prepare("SELECT json FROM quiz_items ORDER BY difficulty ASC, id ASC")
        .map_err(DbError::Query)?;
    let rows = stmt
        .query_map([], |r| r.get::<_, String>(0))
        .map_err(DbError::Query)?;
    let mut questions = Vec::new();
    for r in rows {
        let raw = r.map_err(DbError::Query)?;
        let q: QuizQuestion =
            serde_json::from_str(&raw).map_err(|e| DbError::Migration(e.to_string()))?;
        questions.push(q);
    }
    Ok(questions)
}

/// Load skill state for a single topic from local SQLCipher store.
pub fn load_skill_state(conn: &Connection, topic: &str) -> DbResult<Option<TopicSkill>> {
    let row = conn.query_row(
        "SELECT json FROM skill_state WHERE skill_id = ?1",
        [topic],
        |r| r.get::<_, String>(0),
    );
    match row {
        Ok(raw) => {
            let skill: TopicSkill =
                serde_json::from_str(&raw).map_err(|e| DbError::Migration(e.to_string()))?;
            Ok(Some(skill))
        }
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(DbError::Query(e)),
    }
}

/// Save / update skill state for a topic in local SQLCipher store.
pub fn save_skill_state(conn: &Connection, skill: &TopicSkill) -> DbResult<()> {
    let now = now_ms();
    let json_raw = serde_json::to_string(skill).map_err(|e| DbError::Migration(e.to_string()))?;
    conn.execute(
        "INSERT INTO skill_state(skill_id, json, updated_at_ms) VALUES (?1, ?2, ?3)
         ON CONFLICT(skill_id) DO UPDATE SET
           json = excluded.json,
           updated_at_ms = excluded.updated_at_ms",
        rusqlite::params![skill.topic, json_raw, now],
    )
    .map_err(DbError::Query)?;
    Ok(())
}

/// Load all topic skill states from local SQLCipher store.
pub fn load_all_skill_states(conn: &Connection) -> DbResult<HashMap<String, TopicSkill>> {
    let mut stmt = conn
        .prepare("SELECT skill_id, json FROM skill_state")
        .map_err(DbError::Query)?;
    let rows = stmt
        .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)))
        .map_err(DbError::Query)?;
    let mut map = HashMap::new();
    for r in rows {
        let (id, raw) = r.map_err(DbError::Query)?;
        let skill: TopicSkill =
            serde_json::from_str(&raw).map_err(|e| DbError::Migration(e.to_string()))?;
        map.insert(id, skill);
    }
    Ok(map)
}

/// Pre-seed local database with embedded builtin quiz bank if currently empty.
pub fn seed_builtin_quiz_bank_if_empty(conn: &Connection) -> DbResult<u32> {
    let count: u32 = conn
        .query_row("SELECT COUNT(*) FROM quiz_items", [], |r| r.get(0))
        .map_err(DbError::Query)?;
    if count > 0 {
        return Ok(count);
    }
    let bank = load_builtin_bank().map_err(|e| DbError::Migration(e.to_string()))?;
    let inserted = insert_quiz_items(conn, &bank, "builtin")?;
    Ok(inserted as u32)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::open_with_key;
    use tempfile::tempdir;

    #[test]
    fn seed_and_query_quiz_bank() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("quiz.db");
        let key = b"quiz-test-key-32-bytes-long!!!!!";
        let conn = open_with_key(&path, key).unwrap();

        let count = seed_builtin_quiz_bank_if_empty(&conn).unwrap();
        assert_eq!(count, 14);

        let bank = load_quiz_bank(&conn).unwrap();
        assert_eq!(bank.len(), 14);
        assert!(!bank[0].choices.is_empty());

        // Re-seed is a no-op
        let count2 = seed_builtin_quiz_bank_if_empty(&conn).unwrap();
        assert_eq!(count2, 14);
    }

    #[test]
    fn skill_state_round_trip() {
        let dir = tempdir().unwrap();
        let path = dir.path().join("skill.db");
        let key = b"skill-test-key-32-bytes-long!!!!";
        let conn = open_with_key(&path, key).unwrap();

        let mut skill = TopicSkill::new("math");
        skill.level = 4;
        skill.total_attempts = 10;
        skill.total_correct = 8;
        skill.weak_concepts.insert("fractions".into());

        save_skill_state(&conn, &skill).unwrap();

        let loaded = load_skill_state(&conn, "math")
            .unwrap()
            .expect("loaded skill");
        assert_eq!(loaded.topic, "math");
        assert_eq!(loaded.level, 4);
        assert_eq!(loaded.total_attempts, 10);
        assert_eq!(loaded.total_correct, 8);
        assert!(loaded.weak_concepts.contains("fractions"));

        let all = load_all_skill_states(&conn).unwrap();
        assert_eq!(all.len(), 1);
        assert_eq!(all["math"].level, 4);
    }
}
