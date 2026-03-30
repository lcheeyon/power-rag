import { apiClient } from './client'

export interface OllamaModelInfo {
  modelId:       string
  label:         string
  multimodal:    boolean
  family:        string
  parameterSize: string
}

export async function listOllamaModels(): Promise<OllamaModelInfo[]> {
  const res = await apiClient.get<OllamaModelInfo[]>('/models/ollama')
  return res.data
}

export interface GeminiModelInfo {
  modelId:       string
  displayName:   string
  description:   string
  multimodal:    boolean
}

/** Models from Google AI {@code models.list} (same key as Spring AI Gemini chat). */
export async function listGeminiModels(): Promise<GeminiModelInfo[]> {
  const res = await apiClient.get<GeminiModelInfo[]>('/models/gemini')
  return res.data ?? []
}
