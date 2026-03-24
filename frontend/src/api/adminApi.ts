import { apiClient } from './client'

export interface InteractionSummary {
  id:            string
  sessionId:     string
  queryText:     string
  queryLanguage: string
  responseText:  string
  modelProvider: string
  modelId:       string
  confidence:    number
  cacheHit:      boolean
  createdAt:     string
  feedbackCount: number
}

export interface PageResponse<T> {
  content:          T[]
  totalElements:    number
  totalPages:       number
  number:           number
  size:             number
}

export async function listInteractions(
  page = 0,
  size = 20,
): Promise<PageResponse<InteractionSummary>> {
  const res = await apiClient.get<PageResponse<InteractionSummary>>(
    '/admin/interactions',
    { params: { page, size } },
  )
  return res.data
}

export interface FeedbackItem {
  id:          string
  interactionId: string
  userId?:     string
  starRating:  number
  comment?:    string
  createdAt:   string
}

export async function getInteractionFeedback(id: string): Promise<FeedbackItem[]> {
  const res = await apiClient.get<FeedbackItem[]>(`/admin/interactions/${id}/feedback`)
  return res.data
}
