import { useState, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import LanguageToggle from '../components/LanguageToggle'
import { runSqlQuery, type SqlQueryResult } from '../api/sqlApi'

const SAMPLE_QUESTIONS = [
  'Show all approved grant applications with their amounts',
  'Which grant programs have the highest available budget?',
  'List applicants who have more than one application',
  'What is the total amount disbursed so far?',
  'Show applications under review and their review scores',
]

export default function SqlPage() {
  const { t, i18n } = useTranslation()
  const { logout }  = useAuth()
  const navigate    = useNavigate()

  const [question, setQuestion] = useState('')
  const [loading,  setLoading]  = useState(false)
  const [result,   setResult]   = useState<SqlQueryResult | null>(null)
  const [error,    setError]    = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const handleSubmit = async (q?: string) => {
    const query = (q ?? question).trim()
    if (!query) return
    setLoading(true)
    setResult(null)
    setError(null)
    try {
      const res = await runSqlQuery(query, i18n.language)
      setResult(res)
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  const handleSample = (q: string) => {
    setQuestion(q)
    textareaRef.current?.focus()
  }

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex flex-col h-screen bg-[#0A0A0F]" data-testid="sql-page">
      {/* Header */}
      <header className="h-16 border-b border-[#1E1E2E] flex items-center px-6 justify-between flex-shrink-0">
        <div className="flex items-center gap-4">
          <span className="gradient-text font-bold text-lg">Power RAG</span>
          <nav className="flex items-center gap-1">
            <button
              onClick={() => navigate('/chat')}
              className="px-3 py-1 text-sm text-slate-400 hover:text-slate-200 rounded-md hover:bg-[#1E1E2E] transition-colors"
            >
              {t('nav.chat')}
            </button>
            <button
              className="px-3 py-1 text-sm text-indigo-400 bg-indigo-500/10 rounded-md border border-indigo-500/30"
            >
              {t('nav.database')}
            </button>
          </nav>
        </div>
        <div className="flex items-center gap-3">
          <LanguageToggle />
          <button
            onClick={handleLogout}
            className="text-sm text-slate-400 hover:text-slate-200 transition-colors"
            data-testid="logout-button"
          >
            {t('nav.logout')}
          </button>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Left panel — query input */}
        <div className="w-[420px] flex-shrink-0 border-r border-[#1E1E2E] flex flex-col p-5 gap-4 overflow-y-auto">
          <div>
            <h2 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-1">
              {t('sql.title')}
            </h2>
            <p className="text-xs text-slate-500">{t('sql.subtitle')}</p>
          </div>

          {/* Question input */}
          <div className="flex flex-col gap-2">
            <textarea
              ref={textareaRef}
              value={question}
              onChange={e => setQuestion(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                  e.preventDefault()
                  handleSubmit()
                }
              }}
              placeholder={t('sql.placeholder')}
              rows={4}
              className="w-full bg-[#12121A] border border-[#1E1E2E] rounded-lg px-3 py-2 text-sm
                         text-slate-300 placeholder-slate-600 resize-none
                         focus:outline-none focus:ring-2 focus:ring-indigo-500"
              data-testid="sql-input"
            />
            <button
              onClick={() => handleSubmit()}
              disabled={loading || !question.trim()}
              className="w-full py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40
                         disabled:cursor-not-allowed text-sm font-medium text-white transition-colors"
              data-testid="sql-submit"
            >
              {loading ? t('sql.running') : t('sql.run')}
            </button>
          </div>

          {/* Sample questions */}
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
              {t('sql.samples')}
            </p>
            <ul className="space-y-1">
              {SAMPLE_QUESTIONS.map((q, i) => (
                <li key={i}>
                  <button
                    onClick={() => handleSample(q)}
                    className="w-full text-left text-xs text-slate-400 hover:text-indigo-300
                               hover:bg-[#1E1E2E] rounded px-2 py-1.5 transition-colors"
                    data-testid="sample-question"
                  >
                    {q}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Right panel — results */}
        <div className="flex-1 overflow-y-auto p-5 flex flex-col gap-4" data-testid="sql-results-panel">
          {!result && !error && !loading && (
            <div className="flex-1 flex items-center justify-center">
              <p className="text-slate-600 text-sm">{t('sql.noResults')}</p>
            </div>
          )}

          {loading && (
            <div className="flex items-center justify-center flex-1">
              <div className="w-8 h-8 border-4 border-[#1E1E2E] border-t-indigo-500 rounded-full animate-spin" />
            </div>
          )}

          {error && (
            <div
              className="rounded-lg border border-red-800/50 bg-red-900/20 px-4 py-3 text-sm text-red-400"
              data-testid="sql-error"
            >
              {error}
            </div>
          )}

          {result && (
            <>
              {/* Duration badge */}
              <div className="flex items-center gap-2 text-xs text-slate-500">
                <span>{t('sql.duration', { ms: result.durationMs })}</span>
                {result.rowCount > 0 && (
                  <span className="px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                    {t('sql.rowCount', { count: result.rowCount })}
                  </span>
                )}
              </div>

              {/* Generated SQL */}
              {result.sql && (
                <div data-testid="sql-generated">
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
                    {t('sql.generatedSql')}
                  </p>
                  <pre
                    className="bg-[#12121A] border border-[#1E1E2E] rounded-lg px-4 py-3
                               text-xs text-emerald-400 overflow-x-auto whitespace-pre-wrap leading-relaxed"
                  >
                    {result.sql}
                  </pre>
                </div>
              )}

              {/* Clarification */}
              {result.clarification && (
                <div
                  className="rounded-lg border border-yellow-700/50 bg-yellow-900/20 px-4 py-3 text-sm text-yellow-300"
                  data-testid="sql-clarification"
                >
                  <p className="text-xs font-semibold text-yellow-500 uppercase tracking-wider mb-1">
                    {t('sql.clarification')}
                  </p>
                  {result.clarification}
                </div>
              )}

              {/* Execution error */}
              {result.executionError && (
                <div
                  className="rounded-lg border border-red-800/50 bg-red-900/20 px-4 py-3 text-sm text-red-400"
                  data-testid="sql-execution-error"
                >
                  <p className="text-xs font-semibold text-red-500 uppercase tracking-wider mb-1">
                    {t('sql.executionError')}
                  </p>
                  {result.executionError}
                </div>
              )}

              {/* Results table */}
              {result.rows.length > 0 && (
                <div data-testid="sql-table">
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
                    {t('sql.results')}
                  </p>
                  <div className="overflow-x-auto rounded-lg border border-[#1E1E2E]">
                    <table className="w-full text-xs text-slate-300 border-collapse">
                      <thead>
                        <tr className="bg-[#12121A]">
                          {result.columns.map(col => (
                            <th
                              key={col}
                              className="px-3 py-2 text-left font-semibold text-slate-400 border-b border-[#1E1E2E] whitespace-nowrap"
                            >
                              {col}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {result.rows.map((row, ri) => (
                          <tr
                            key={ri}
                            className="border-b border-[#1E1E2E]/50 hover:bg-[#12121A]/60 transition-colors"
                          >
                            {result.columns.map(col => (
                              <td key={col} className="px-3 py-2 whitespace-nowrap max-w-xs truncate" title={String(row[col] ?? '')}>
                                {row[col] === null || row[col] === undefined
                                  ? <span className="text-slate-600">NULL</span>
                                  : String(row[col])}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Empty result set */}
              {result.sql && result.rows.length === 0 && !result.executionError && (
                <p className="text-xs text-slate-500 text-center py-8" data-testid="sql-empty">
                  {t('sql.empty')}
                </p>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
