import { Suspense, lazy } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { AuthProvider } from './contexts/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'

const ChatPage   = lazy(() => import('./pages/ChatPage'))
const LoginPage  = lazy(() => import('./pages/LoginPage'))
const AdminPage  = lazy(() => import('./pages/AdminPage'))
const SqlPage    = lazy(() => import('./pages/SqlPage'))

function LoadingSpinner() {
  return (
    <div
      className="flex items-center justify-center h-screen bg-[#0A0A0F]"
      data-testid="loading-spinner"
    >
      <div className="flex flex-col items-center gap-4">
        <div className="w-10 h-10 border-4 border-[#1E1E2E] border-t-indigo-500 rounded-full animate-spin" />
        <p className="text-slate-400 text-sm animate-pulse">Loading Power RAG…</p>
      </div>
    </div>
  )
}

export default function App() {
  const { i18n } = useTranslation()

  return (
    <AuthProvider>
      <div
        lang={i18n.language}
        data-testid="app-root"
        className="min-h-screen bg-[#0A0A0F]"
      >
        <Suspense fallback={<LoadingSpinner />}>
          <Routes>
            <Route path="/"        element={<Navigate to="/chat" replace />} />
            <Route path="/login"   element={<LoginPage />} />
            <Route path="/chat"    element={<ProtectedRoute><ChatPage /></ProtectedRoute>} />
            <Route path="/sql"     element={<ProtectedRoute><SqlPage /></ProtectedRoute>} />
            <Route path="/admin/*" element={<ProtectedRoute><AdminPage /></ProtectedRoute>} />
            <Route path="*"        element={<Navigate to="/chat" replace />} />
          </Routes>
        </Suspense>
      </div>
    </AuthProvider>
  )
}
