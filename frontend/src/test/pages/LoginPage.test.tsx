import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import '../../i18n'
import { AuthProvider } from '../../contexts/AuthContext'
import LoginPage from '../../pages/LoginPage'

function renderLoginPage() {
  localStorage.removeItem('jwt_token')
  return render(
    <AuthProvider>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </AuthProvider>
  )
}

describe('LoginPage', () => {
  beforeEach(() => localStorage.removeItem('jwt_token'))

  it('renders the login page', () => {
    renderLoginPage()
    expect(screen.getByTestId('login-page')).toBeInTheDocument()
  })

  it('renders the login form', () => {
    renderLoginPage()
    expect(screen.getByTestId('login-form')).toBeInTheDocument()
  })

  it('renders username and password inputs', () => {
    renderLoginPage()
    expect(screen.getByTestId('login-username-input')).toBeInTheDocument()
    expect(screen.getByTestId('login-password-input')).toBeInTheDocument()
  })

  it('renders the sign in button', () => {
    renderLoginPage()
    expect(screen.getByTestId('login-submit-button')).toBeInTheDocument()
  })

  it('shows Power RAG branding', () => {
    renderLoginPage()
    expect(screen.getByText('Power RAG')).toBeInTheDocument()
  })

  it('updates username input value when typed', () => {
    renderLoginPage()
    const input = screen.getByTestId('login-username-input') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'testuser' } })
    expect(input.value).toBe('testuser')
  })

  it('updates password input value when typed', () => {
    renderLoginPage()
    const input = screen.getByTestId('login-password-input') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'mypassword' } })
    expect(input.value).toBe('mypassword')
  })

  it('password input has type=password', () => {
    renderLoginPage()
    const input = screen.getByTestId('login-password-input') as HTMLInputElement
    expect(input.type).toBe('password')
  })
})
