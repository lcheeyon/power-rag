-- ============================================================
-- Power RAG – Phase 2: Document Ingestion Schema
-- V2 – Documents, Chunks
-- ============================================================

-- ── Documents (ingestion audit log) ─────────────────────────
CREATE TABLE IF NOT EXISTS documents (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name   VARCHAR(512) NOT NULL,
    file_type   VARCHAR(20)  NOT NULL,   -- JAVA, PDF, EXCEL, WORD
    file_size   BIGINT       NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, INDEXED, FAILED
    chunk_count INTEGER,
    error_msg   TEXT,
    user_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_documents_user_id   ON documents (user_id);
CREATE INDEX IF NOT EXISTS idx_documents_status    ON documents (status);
CREATE INDEX IF NOT EXISTS idx_documents_file_type ON documents (file_type);
CREATE INDEX IF NOT EXISTS idx_documents_created   ON documents (created_at DESC);

-- ── Document Chunks (Qdrant cross-reference) ─────────────────
CREATE TABLE IF NOT EXISTS document_chunks (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    qdrant_id    VARCHAR(64) NOT NULL,
    chunk_index  INTEGER     NOT NULL,
    chunk_text   TEXT        NOT NULL,
    metadata     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON document_chunks (document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_qdrant_id   ON document_chunks (qdrant_id);
