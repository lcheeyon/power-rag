import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { motion } from 'framer-motion'
import { useAuth } from '../contexts/AuthContext'
import LanguageToggle from '../components/LanguageToggle'

export default function LoginPage() {
  const { t }      = useTranslation()
  const navigate   = useNavigate()
  const { login }  = useAuth()

  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('Admin@1234')

  // Clear any stale/expired token when the login page is shown
  useEffect(() => { localStorage.removeItem('jwt_token') }, [])
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await login(username, password)
      navigate('/chat', { replace: true })
    } catch {
      setError(t('login.invalidCredentials'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="min-h-screen flex items-center justify-center bg-[#0A0A0F]"
      data-testid="login-page"
    >
      <div className="absolute top-4 right-4">
        <LanguageToggle />
      </div>
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="glass-card w-full max-w-md p-8"
      >
        <div className="text-center mb-8">
          <h1 className="gradient-text text-3xl font-bold mb-2">Power RAG</h1>
          <p className="text-slate-400 text-sm">{t('login.subtitle')}</p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4" data-testid="login-form">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">
              {t('login.username')}
            </label>
            <input
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              data-testid="login-username-input"
              className="w-full bg-[#0A0A0F] border border-[#1E1E2E] rounded-lg px-4 py-2.5
                         text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500
                         placeholder:text-slate-600"
              placeholder={t('login.usernamePlaceholder')}
              disabled={loading}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">
              {t('login.password')}
            </label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              data-testid="login-password-input"
              className="w-full bg-[#0A0A0F] border border-[#1E1E2E] rounded-lg px-4 py-2.5
                         text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500
                         placeholder:text-slate-600"
              placeholder={t('login.passwordPlaceholder')}
              disabled={loading}
            />
          </div>
          {error && (
            <p className="text-red-400 text-sm" data-testid="login-error">{error}</p>
          )}
          <button
            type="submit"
            data-testid="login-submit-button"
            disabled={loading}
            className="w-full bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-600/50
                       text-white font-medium py-2.5 rounded-lg transition-colors duration-200 mt-2"
          >
            {loading ? '…' : t('login.signIn')}
          </button>
        </form>
      </motion.div>
    </div>
  )
}
