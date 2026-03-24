-- ============================================================
-- Power RAG – Phase 8: Interaction Audit + Feedback
-- The feedback table was created in V1 with thumbs + star_rating.
-- This migration adds a unique index to prevent duplicate ratings
-- per user per interaction.
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_feedback_interaction_user
    ON feedback(interaction_id, user_id);
