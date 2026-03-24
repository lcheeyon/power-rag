import { apiClient } from './client'

export interface SqlQueryResult {
  sql:            string | null
  columns:        string[]
  rows:           Record<string, unknown>[]
  rowCount:       number
  clarification:  string | null
  executionError: string | null
  durationMs:     number
}

export async function runSqlQuery(question: string, language: string): Promise<SqlQueryResult> {
  const res = await apiClient.post<SqlQueryResult>('/sql/query', { question, language })
  return res.data
}
