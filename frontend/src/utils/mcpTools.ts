import type { McpToolsCapabilitiesResponse } from '../api/chatApi'

/** True when MCP is wired to chat and at least one registered tool name suggests Jira (Spring AI may prefix with server id). */
export function mcpHasJiraTools(data: McpToolsCapabilitiesResponse | undefined): boolean {
  if (!data?.mcpClientAvailable || !data.ragMcpEnabled || data.tools.length === 0) {
    return false
  }
  return data.tools.some(t => /jira/i.test(t.name))
}
