import { apiClient } from './client'

export interface SourceRef {
  fileName:    string
  pageNumber?: number
  lineNumber?: number
  section?:    string
  snippet:     string
  documentId?: string
}

/** One MCP tool call during the assistant turn (when dev profile enables MCP). */
export interface McpToolInvocationSummary {
  serverId:     string
  toolName:     string
  success:      boolean
  durationMs:   number
  errorMessage?: string
  argsSummary?:  string
}

export interface ChatQueryRequest {
  question:      string
  modelProvider: string
  modelId:       string
  language?:     string
  imageBase64?:  string   // data URL, e.g. "data:image/png;base64,..."
  /** IANA zone from the browser for MCP get_current_time (e.g. Asia/Singapore). */
  clientTimezone?: string
}

export interface ChatQueryResponse {
  answer:      string
  confidence:  number
  cacheHit:    boolean
  sources:     SourceRef[]
  modelProvider?:         string
  modelId?:               string
  error?:                 string
  generatedImageBase64?:  string   // data-URL when the backend generated an image
  /** Present and non-empty when the model used MCP tools (e.g. fetch). */
  mcpInvocations?:        McpToolInvocationSummary[]
}

export async function sendQuery(data: ChatQueryRequest): Promise<ChatQueryResponse> {
  const res = await apiClient.post<ChatQueryResponse>('/chat/query', data)
  return res.data
}

/** One tool the backend may expose to the model via MCP (names match Spring AI / MCP server). */
export interface McpToolDescriptor {
  name:        string
  description: string
}

export interface McpToolsCapabilitiesResponse {
  ragMcpEnabled:       boolean
  mcpClientAvailable: boolean
  tools:               McpToolDescriptor[]
}

export async function fetchMcpTools(): Promise<McpToolsCapabilitiesResponse> {
  const res = await apiClient.get<McpToolsCapabilitiesResponse>('/chat/mcp-tools')
  return res.data
}
