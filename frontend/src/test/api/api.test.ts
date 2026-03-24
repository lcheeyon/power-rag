/**
 * Tests for API modules.
 * JSON endpoints use MSW interceptors; upload tested via vi.spyOn on axios.
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { login }                                  from '../../api/authApi'
import { sendQuery }                              from '../../api/chatApi'
import { listInteractions, getInteractionFeedback } from '../../api/adminApi'
import { listDocuments, uploadDocument, deleteDocument } from '../../api/documentApi'
import { apiClient }                              from '../../api/client'

beforeEach(() => localStorage.removeItem('jwt_token'))
afterEach(() => vi.restoreAllMocks())

describe('authApi', () => {
  it('login returns a token for valid credentials', async () => {
    const result = await login({ username: 'admin', password: 'Admin@1234' })
    expect(result.token).toBe('mock-jwt-token-for-testing')
  })

  it('login rejects on invalid credentials', async () => {
    await expect(login({ username: 'bad', password: 'wrong' })).rejects.toBeDefined()
  })
})

describe('chatApi', () => {
  beforeEach(() => localStorage.setItem('jwt_token', 'mock-jwt'))

  it('sendQuery returns an answer', async () => {
    const result = await sendQuery({
      query:         'What is RAG?',
      modelProvider: 'ANTHROPIC',
      modelId:       'claude-sonnet-4-6',
    })
    expect(result.answer).toContain('Mock answer')
    expect(typeof result.confidence).toBe('number')
    expect(Array.isArray(result.sources)).toBe(true)
  })
})

describe('adminApi', () => {
  beforeEach(() => localStorage.setItem('jwt_token', 'mock-jwt'))

  it('listInteractions returns paginated data', async () => {
    const result = await listInteractions(0, 20)
    expect(result.content).toHaveLength(1)
    expect(result.totalElements).toBe(1)
    expect(result.content[0].queryText).toBe('What is RAG?')
  })

  it('getInteractionFeedback returns empty array', async () => {
    const result = await getInteractionFeedback('int-1')
    expect(Array.isArray(result)).toBe(true)
  })
})

describe('documentApi', () => {
  beforeEach(() => localStorage.setItem('jwt_token', 'mock-jwt'))

  it('listDocuments returns an array', async () => {
    const result = await listDocuments()
    expect(Array.isArray(result)).toBe(true)
  })

  it('uploadDocument returns upload response', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: { documentId: 'doc-1', fileName: 'test.pdf', chunkCount: 5, status: 'INDEXED', uploadedAt: '' },
    })
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' })
    const result = await uploadDocument(file)
    expect(result.documentId).toBe('doc-1')
    expect(result.status).toBe('INDEXED')
  })

  it('deleteDocument calls DELETE endpoint', async () => {
    vi.spyOn(apiClient, 'delete').mockResolvedValueOnce({ data: undefined })
    await deleteDocument('doc-abc')
    expect(apiClient.delete).toHaveBeenCalledWith('/documents/doc-abc')
  })

  it('uploadDocument with optional description includes description in FormData', async () => {
    const spy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: { documentId: 'doc-2', fileName: 'notes.pdf', chunkCount: 2, status: 'INDEXED', uploadedAt: '' },
    })
    const file = new File(['content'], 'notes.pdf', { type: 'application/pdf' })
    await uploadDocument(file, 'My notes')
    expect(spy).toHaveBeenCalledOnce()
    const formData = spy.mock.calls[0][1] as FormData
    expect(formData.get('description')).toBe('My notes')
  })
})
