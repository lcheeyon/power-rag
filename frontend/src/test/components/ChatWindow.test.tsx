import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import '../../i18n'
import ChatWindow from '../../components/ChatWindow'
import { MODEL_OPTIONS } from '../../components/ModelSelector'
import { server } from '../mocks/server'

const model = MODEL_OPTIONS[0]

function renderChat() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <ChatWindow model={model} language="en" />
    </QueryClientProvider>
  )
}

describe('ChatWindow', () => {
  beforeEach(() => localStorage.setItem('jwt_token', 'mock-jwt'))
  it('renders the chat window', () => {
    renderChat()
    expect(screen.getByTestId('chat-window')).toBeInTheDocument()
  })

  it('shows Jira quick prompts when MCP exposes Jira tools', async () => {
    renderChat()
    await waitFor(() => {
      expect(screen.getByTestId('jira-composer-hints')).toBeInTheDocument()
    })
    expect(screen.getByTestId('jira-empty-hint')).toBeInTheDocument()
  })

  it('renders the message list', () => {
    renderChat()
    expect(screen.getByTestId('chat-messages')).toBeInTheDocument()
  })

  it('renders the input area', () => {
    renderChat()
    expect(screen.getByTestId('chat-input-area')).toBeInTheDocument()
  })

  it('renders the text input', () => {
    renderChat()
    expect(screen.getByTestId('chat-input')).toBeInTheDocument()
  })

  it('renders the send button', () => {
    renderChat()
    expect(screen.getByTestId('chat-send-button')).toBeInTheDocument()
  })

  it('send button is disabled when input is empty', () => {
    renderChat()
    const btn = screen.getByTestId('chat-send-button') as HTMLButtonElement
    expect(btn.disabled).toBe(true)
  })

  it('send button is enabled after typing', () => {
    renderChat()
    const input = screen.getByTestId('chat-input')
    fireEvent.change(input, { target: { value: 'What is RAG?' } })
    const btn = screen.getByTestId('chat-send-button') as HTMLButtonElement
    expect(btn.disabled).toBe(false)
  })

  it('sends a message and shows AI response', async () => {
    renderChat()
    const input = screen.getByTestId('chat-input')
    fireEvent.change(input, { target: { value: 'What is RAG?' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))

    // User message appears
    await waitFor(() =>
      expect(screen.getByTestId('message-user')).toBeInTheDocument(),
    )

    // AI response appears
    await waitFor(() =>
      expect(screen.getByTestId('message-assistant')).toBeInTheDocument(),
    )
  })

  it('clears input after sending', async () => {
    renderChat()
    const input = screen.getByTestId('chat-input') as HTMLTextAreaElement
    fireEvent.change(input, { target: { value: 'Hello' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))
    await waitFor(() => expect(input.value).toBe(''))
  })

  it('shows confidence badge after response', async () => {
    renderChat()
    const input = screen.getByTestId('chat-input')
    fireEvent.change(input, { target: { value: 'What is RAG?' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))
    await waitFor(() =>
      expect(screen.getByTestId('confidence-badge')).toBeInTheDocument(),
    )
  })

  it('shows semantic cache notice when response is served from cache', async () => {
    server.use(
      http.post('/api/chat/query', () =>
        HttpResponse.json({
          answer:      'Same answer as before.',
          confidence:  0.87,
          cacheHit:    true,
          sources:     [],
        }),
      ),
    )
    renderChat()
    fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'Repeat question' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))
    await waitFor(() => {
      expect(screen.getByTestId('cache-hit-notice')).toBeInTheDocument()
    })
    expect(screen.getByText(/semantic cache/i)).toBeInTheDocument()
    expect(screen.getByText(/Redis/i)).toBeInTheDocument()
  })

  it('turns Jira issue keys in assistant text into browse links', async () => {
    server.use(
      http.post('/api/chat/query', () =>
        HttpResponse.json({
          answer:      'Latest ticket: KAN-7 (fixed).',
          confidence:  0.9,
          cacheHit:    false,
          sources:     [],
        }),
      ),
    )
    renderChat()
    fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'List Jira' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))
    await waitFor(() =>
      expect(screen.getByRole('link', { name: 'KAN-7' })).toHaveAttribute(
        'href',
        'https://powerrag.atlassian.net/browse/KAN-7',
      ),
    )
  })

  it('shows citation links and calls open + focus handlers', async () => {
    const onOpenKb = vi.fn()
    const onFocus = vi.fn()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={queryClient}>
        <ChatWindow
          model={model}
          language="en"
          onOpenKbDocument={onOpenKb}
          onFocusSourcesPanel={onFocus}
        />
      </QueryClientProvider>,
    )
    localStorage.setItem('jwt_token', 'mock-jwt-token')
    fireEvent.change(screen.getByTestId('chat-input'), { target: { value: 'Q?' } })
    fireEvent.click(screen.getByTestId('chat-send-button'))
    await waitFor(() => expect(screen.getByTestId('message-source-links')).toBeInTheDocument())
    const cite = screen.getByRole('button', { name: /doc\.pdf/ })
    fireEvent.click(cite)
    expect(onFocus).toHaveBeenCalled()
    expect(onOpenKb).toHaveBeenCalledWith('doc-1', 'doc.pdf')
  })
})
