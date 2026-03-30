-- MCP tool invocation summaries for a chat turn (nullable when no tools used)
ALTER TABLE interactions ADD COLUMN mcp_invocations jsonb NULL;
