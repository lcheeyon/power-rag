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
