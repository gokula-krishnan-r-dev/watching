-- MeritScreen desktop SQLCipher schema v1
-- Child launcher + quiz read this store only (no Firebase on hot path).

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS policy (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    json TEXT NOT NULL,
    updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS app_rules (
    app_id TEXT PRIMARY KEY NOT NULL,
    label TEXT NOT NULL DEFAULT '',
    allowed INTEGER NOT NULL DEFAULT 0,
    block_minutes INTEGER,
    grant_on_pass_minutes INTEGER,
    emergency INTEGER NOT NULL DEFAULT 0,
    json TEXT NOT NULL DEFAULT '{}',
    updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS session_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    phase TEXT NOT NULL,
    json TEXT NOT NULL,
    updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS quiz_items (
    id TEXT PRIMARY KEY NOT NULL,
    pack_id TEXT NOT NULL DEFAULT 'builtin',
    skill_id TEXT NOT NULL DEFAULT '',
    difficulty INTEGER NOT NULL DEFAULT 1,
    json TEXT NOT NULL,
    updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS skill_state (
    skill_id TEXT PRIMARY KEY NOT NULL,
    json TEXT NOT NULL,
    updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS usage_dirty (
    day_key TEXT PRIMARY KEY NOT NULL,
    minutes INTEGER NOT NULL DEFAULT 0,
    dirty INTEGER NOT NULL DEFAULT 1,
    json TEXT NOT NULL DEFAULT '{}',
    updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    last_policy_pull_ms INTEGER,
    last_usage_upload_ms INTEGER,
    last_heartbeat_ms INTEGER,
    json TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS device_runtime (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    role TEXT NOT NULL DEFAULT 'unassigned',
    device_id TEXT,
    child_id TEXT,
    family_id TEXT,
    platform TEXT,
    inventory_hash TEXT,
    json TEXT NOT NULL DEFAULT '{}',
    updated_at_ms INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS prefs (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL
);

-- Cached installed-app inventory for parent allowlist sync (never on UI Firebase path).
CREATE TABLE IF NOT EXISTS installed_apps (
    app_id TEXT PRIMARY KEY NOT NULL,
    label TEXT NOT NULL DEFAULT '',
    icon_hash TEXT,
    json TEXT NOT NULL DEFAULT '{}',
    updated_at_ms INTEGER NOT NULL
);

-- Pending quiz attempt uploads (idempotent cloud write by attempt_id).
CREATE TABLE IF NOT EXISTS quiz_upload_queue (
    attempt_id TEXT PRIMARY KEY NOT NULL,
    json TEXT NOT NULL,
    uploaded INTEGER NOT NULL DEFAULT 0,
    updated_at_ms INTEGER NOT NULL
);
