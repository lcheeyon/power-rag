import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '../i18n'
import App from '../App'

function renderApp(initialRoute = '/', authenticated = false) {
  if (authenticated) {
    localStorage.setItem('jwt_token', 'mock-jwt-token')
  } else {
    localStorage.removeItem('jwt_token')
  }
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialRoute]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('App', () => {
  beforeEach(() => localStorage.removeItem('jwt_token'))

  it('renders without crashing', () => {
    renderApp()
    expect(document.getElementById('root') || document.body).toBeTruthy()
  })

  it('renders the app-root element', () => {
    renderApp('/login')
    expect(screen.getByTestId('app-root')).toBeInTheDocument()
  })

  it('redirects unauthenticated / to /login', async () => {
    renderApp('/')
    const loginPage = await screen.findByTestId('login-page')
    expect(loginPage).toBeInTheDocument()
  })

  it('renders chat page on /chat route when authenticated', async () => {
    renderApp('/chat', true)
    const chatPage = await screen.findByTestId('chat-page')
    expect(chatPage).toBeInTheDocument()
  })

  it('renders login page on /login route', async () => {
    renderApp('/login')
    const loginPage = await screen.findByTestId('login-page')
    expect(loginPage).toBeInTheDocument()
  })

  it('renders admin page on /admin route when authenticated', async () => {
    renderApp('/admin', true)
    const adminPage = await screen.findByTestId('admin-page')
    expect(adminPage).toBeInTheDocument()
  })

  it('redirects unauthenticated /chat to /login', async () => {
    renderApp('/chat', false)
    const loginPage = await screen.findByTestId('login-page')
    expect(loginPage).toBeInTheDocument()
  })
})
