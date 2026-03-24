import { http, HttpResponse } from 'msw'

/**
 * MSW request handlers for API mocking in Vitest tests.
 */
export const handlers = [
  // Health endpoint
  http.get('/api/public/health', () =>
    HttpResponse.json({ status: 'UP', service: 'power-rag', timestamp: new Date().toISOString() }),
  ),

  // Version endpoint
  http.get('/api/public/version', () =>
    HttpResponse.json({ version: '1.0.0-SNAPSHOT', name: 'Power RAG' }),
  ),

  // Login endpoint
  http.post('/api/auth/login', async ({ request }) => {
    const body = await request.json() as { username: string; password: string }
    if (body.username === 'admin' && body.password === 'Admin@1234') {
      return HttpResponse.json({ token: 'mock-jwt-token-for-testing' })
    }
    return HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 })
  }),

  // Actuator health
  http.get('/actuator/health', () => HttpResponse.json({ status: 'UP' })),

  // Chat query
  http.post('/api/chat/query', async ({ request }) => {
    const body = await request.json() as { query: string }
    return HttpResponse.json({
      answer:      `Mock answer for: ${body.query}`,
      confidence:  0.87,
      cacheHit:    false,
      sources:     [
        { fileName: 'doc.pdf', pageNumber: 1, snippet: 'Relevant excerpt from document.' },
      ],
      modelProvider: 'ANTHROPIC',
      modelId:       'claude-sonnet-4-6',
    })
  }),

  // Admin interactions
  http.get('/api/admin/interactions', () =>
    HttpResponse.json({
      content: [
        {
          id:            'int-1',
          sessionId:     'sess-1',
          queryText:     'What is RAG?',
          queryLanguage: 'en',
          responseText:  'RAG stands for Retrieval-Augmented Generation.',
          modelProvider: 'ANTHROPIC',
          modelId:       'claude-sonnet-4-6',
          confidence:    0.92,
          cacheHit:      false,
          createdAt:     '2026-03-14T00:00:00Z',
          feedbackCount: 1,
        },
      ],
      totalElements: 1,
      totalPages:    1,
      number:        0,
      size:          20,
    }),
  ),

  // Document upload
  http.post('/api/documents/upload', () =>
    HttpResponse.json({
      documentId: 'doc-1',
      fileName:   'test.pdf',
      chunkCount: 5,
      status:     'INDEXED',
      uploadedAt: new Date().toISOString(),
    }),
  ),

  // Document list
  http.get('/api/documents', () => HttpResponse.json([])),

  // Interaction feedback
  http.get('/api/admin/interactions/:id/feedback', () => HttpResponse.json([])),
]
