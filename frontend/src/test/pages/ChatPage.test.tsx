import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '../../i18n'
import { AuthProvider } from '../../contexts/AuthContext'
import ChatPage from '../../pages/ChatPage'

function renderChatPage() {
  localStorage.setItem('jwt_token', 'mock-jwt-token')
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter>
          <ChatPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}

describe('ChatPage', () => {
  beforeEach(() => localStorage.setItem('jwt_token', 'mock-jwt-token'))

  it('renders the chat page', () => {
    renderChatPage()
    expect(screen.getByTestId('chat-page')).toBeInTheDocument()
  })

  it('renders the knowledge base sidebar', () => {
    renderChatPage()
    expect(screen.getByTestId('knowledge-base-sidebar')).toBeInTheDocument()
  })

  it('renders the main chat area', () => {
    renderChatPage()
    expect(screen.getByTestId('chat-main')).toBeInTheDocument()
  })

  it('renders the sources sidebar', () => {
    renderChatPage()
    expect(screen.getByTestId('sources-sidebar')).toBeInTheDocument()
  })

  it('shows Power RAG branding in header', () => {
    renderChatPage()
    expect(screen.getByText('Power RAG')).toBeInTheDocument()
  })

  it('renders model selector', () => {
    renderChatPage()
    expect(screen.getByTestId('model-selector')).toBeInTheDocument()
  })

  it('renders language toggle', () => {
    renderChatPage()
    expect(screen.getByTestId('language-toggle')).toBeInTheDocument()
  })

  it('renders logout button', () => {
    renderChatPage()
    expect(screen.getByTestId('logout-button')).toBeInTheDocument()
  })

  it('renders upload zone in sidebar', () => {
    renderChatPage()
    expect(screen.getByTestId('upload-zone')).toBeInTheDocument()
  })

  it('renders chat window', () => {
    renderChatPage()
    expect(screen.getByTestId('chat-window')).toBeInTheDocument()
  })
})
