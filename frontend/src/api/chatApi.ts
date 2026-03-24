import { apiClient } from './client'

export interface SourceRef {
  fileName:    string
  pageNumber?: number
  lineNumber?: number
  section?:    string
  snippet:     string
  documentId?: string
}

export interface ChatQueryRequest {
  question:      string
  modelProvider: string
  modelId:       string
  language?:     string
  imageBase64?:  string   // data URL, e.g. "data:image/png;base64,..."
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
}

export async function sendQuery(data: ChatQueryRequest): Promise<ChatQueryResponse> {
  const res = await apiClient.post<ChatQueryResponse>('/chat/query', data)
  return res.data
}
