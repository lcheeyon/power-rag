-- ============================================================
-- Power RAG – Initial Database Schema
-- V1 – Phase 1: Users, Sessions, Interactions, Feedback, Guardrail Flags
-- ============================================================

-- ── Users ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username          VARCHAR(64) NOT NULL UNIQUE,
    password_hash     TEXT        NOT NULL,
    email             VARCHAR(255)NOT NULL UNIQUE,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- ── Interactions (audit log) ──────────────────────────────────
CREATE TABLE IF NOT EXISTS interactions (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         REFERENCES users(id) ON DELETE SET NULL,
    session_id       UUID         NOT NULL,
    query_text       TEXT         NOT NULL,
    query_language   VARCHAR(10)  NOT NULL DEFAULT 'en',
    response_text    TEXT         NOT NULL,
    response_language VARCHAR(10) NOT NULL DEFAULT 'en',
    model_provider   VARCHAR(20)  NOT NULL,
    model_id         VARCHAR(80)  NOT NULL,
    confidence       NUMERIC(5,4),
    cache_hit        BOOLEAN      NOT NULL DEFAULT FALSE,
    sources          JSONB,
    guardrail_flag   BOOLEAN      NOT NULL DEFAULT FALSE,
    flag_reason      TEXT,
    duration_ms      INTEGER,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_interactions_user_id   ON interactions (user_id);
CREATE INDEX IF NOT EXISTS idx_interactions_created   ON interactions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_interactions_model     ON interactions (model_provider, model_id);
CREATE INDEX IF NOT EXISTS idx_interactions_language  ON interactions (query_language);
CREATE INDEX IF NOT EXISTS idx_interactions_cache_hit ON interactions (cache_hit);

-- ── User Feedback ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS feedback (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    interaction_id  UUID        NOT NULL REFERENCES interactions(id) ON DELETE CASCADE,
    user_id         UUID        REFERENCES users(id) ON DELETE SET NULL,
    thumbs          SMALLINT    CHECK (thumbs IN (-1, 0, 1)),
    star_rating     SMALLINT    CHECK (star_rating BETWEEN 1 AND 5),
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_interaction ON feedback (interaction_id);
CREATE INDEX IF NOT EXISTS idx_feedback_user        ON feedback (user_id);
CREATE INDEX IF NOT EXISTS idx_feedback_rating      ON feedback (star_rating);
CREATE INDEX IF NOT EXISTS idx_feedback_created     ON feedback (created_at DESC);

-- ── Guardrail Flags ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS guardrail_flags (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    interaction_id   UUID        REFERENCES interactions(id) ON DELETE CASCADE,
    stage            VARCHAR(10) NOT NULL CHECK (stage IN ('INPUT', 'OUTPUT')),
    rule_triggered   VARCHAR(120)NOT NULL,
    severity         VARCHAR(10) NOT NULL CHECK (severity IN ('WARN', 'BLOCK')),
    raw_content      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_guardrail_interaction ON guardrail_flags (interaction_id);
CREATE INDEX IF NOT EXISTS idx_guardrail_created     ON guardrail_flags (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_guardrail_severity    ON guardrail_flags (severity);

-- ── Seed: default admin user (password: Admin@1234 – change in prod) ──────
-- BCrypt hash of 'Admin@1234'
INSERT INTO users (id, username, password_hash, email, active, preferred_language)
VALUES (
    gen_random_uuid(),
    'admin',
    '$2b$10$Yz7t37MqXp9zGt4z3dk8NeWTezzJdxE6XQ82ehfhzs5yInE08D2j6',
    'admin@powerrag.local',
    TRUE,
    'en'
) ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE username = 'admin'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role)
SELECT id, 'USER' FROM users WHERE username = 'admin'
ON CONFLICT DO NOTHING;
