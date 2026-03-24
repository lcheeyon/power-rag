import { createContext, useContext, useState, useCallback, ReactNode } from 'react'
import { login as apiLogin } from '../api/authApi'

interface AuthContextValue {
  token:           string | null
  isAuthenticated: boolean
  login:           (username: string, password: string) => Promise<void>
  logout:          () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

const TOKEN_KEY = 'jwt_token'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(
    () => localStorage.getItem(TOKEN_KEY),
  )

  const login = useCallback(async (username: string, password: string) => {
    const { token: newToken } = await apiLogin({ username, password })
    localStorage.setItem(TOKEN_KEY, newToken)
    setToken(newToken)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
  }, [])

  return (
    <AuthContext.Provider value={{ token, isAuthenticated: !!token, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
