-- ============================================================
-- Power RAG – Phase 3: RAG Core
-- Adds FTS index on document_chunks for keyword retrieval leg
-- of the HybridRetriever, plus top_chunk_ids for audit trail.
-- ============================================================

ALTER TABLE interactions
    ADD COLUMN IF NOT EXISTS top_chunk_ids JSONB;

-- GIN index enables fast PostgreSQL full-text search on chunk content
CREATE INDEX IF NOT EXISTS idx_chunks_fts
    ON document_chunks USING GIN (to_tsvector('english', chunk_text));
