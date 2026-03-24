-- Add storage_path column so the original uploaded file can be served back for citations
ALTER TABLE documents ADD COLUMN IF NOT EXISTS storage_path TEXT;
