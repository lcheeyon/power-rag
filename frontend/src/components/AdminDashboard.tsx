import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell,
} from 'recharts'
import { listInteractions, type InteractionSummary } from '../api/adminApi'
import ConfidenceBadge from './ConfidenceBadge'

const RATING_COLORS = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#6366f1']

export default function AdminDashboard() {
  const { t } = useTranslation()
  const [interactions, setInteractions]   = useState<InteractionSummary[]>([])
  const [page, setPage]                   = useState(0)
  const [totalPages, setTotalPages]       = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading]             = useState(false)
  const [error, setError]                 = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    listInteractions(page, 20)
      .then(data => {
        if (!cancelled) {
          setInteractions(data.content)
          setTotalPages(data.totalPages)
          setTotalElements(data.totalElements)
        }
      })
      .catch(() => {
        if (!cancelled) setError(t('errors.generic'))
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [page, t])

  // Build star-rating distribution from feedbackCount (simplified chart data)
  const modelCounts = interactions.reduce<Record<string, number>>((acc, i) => {
    acc[i.modelProvider] = (acc[i.modelProvider] ?? 0) + 1
    return acc
  }, {})
  const chartData = Object.entries(modelCounts).map(([name, count]) => ({ name, count }))

  return (
    <div className="p-6 space-y-6" data-testid="admin-dashboard">
      <div className="flex items-center justify-between">
        <h1 className="gradient-text text-2xl font-bold">{t('admin.title')}</h1>
        <button
          onClick={() => setPage(0)}
          className="text-sm text-slate-400 hover:text-slate-200 border border-[#1E1E2E]
                     px-3 py-1.5 rounded-lg transition-colors"
          data-testid="admin-refresh-button"
        >
          {t('admin.refresh')}
        </button>
      </div>

      {/* Summary stats */}
      <div className="grid grid-cols-3 gap-4">
        <div className="glass-card p-4 rounded-xl" data-testid="admin-stat-total">
          <p className="text-slate-500 text-xs uppercase tracking-wider">{t('admin.interactions')}</p>
          <p className="text-2xl font-bold text-slate-100 mt-1">{totalElements}</p>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <p className="text-slate-500 text-xs uppercase tracking-wider">{t('admin.analytics')}</p>
          <p className="text-2xl font-bold text-slate-100 mt-1">{page + 1} / {Math.max(totalPages, 1)}</p>
        </div>
        <div className="glass-card p-4 rounded-xl">
          <p className="text-slate-500 text-xs uppercase tracking-wider">Page</p>
          <p className="text-2xl font-bold text-slate-100 mt-1">{interactions.length}</p>
        </div>
      </div>

      {/* Chart */}
      {chartData.length > 0 && (
        <div className="glass-card p-4 rounded-xl" data-testid="admin-chart">
          <p className="text-slate-400 text-sm mb-3">{t('admin.interactions')} by Model</p>
          <ResponsiveContainer width="100%" height={160}>
            <BarChart data={chartData} margin={{ top: 4, right: 8, bottom: 4, left: -20 }}>
              <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#94a3b8' }} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} allowDecimals={false} />
              <Tooltip
                contentStyle={{ background: '#12121A', border: '1px solid #1E1E2E', borderRadius: 8 }}
                labelStyle={{ color: '#e2e8f0' }}
              />
              <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                {chartData.map((_, i) => (
                  <Cell key={i} fill={RATING_COLORS[i % RATING_COLORS.length]} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Table */}
      {loading ? (
        <div className="text-center text-slate-500 py-12" data-testid="admin-loading">
          <div className="w-8 h-8 border-2 border-[#1E1E2E] border-t-indigo-500 rounded-full
                          animate-spin mx-auto mb-3" />
          Loading…
        </div>
      ) : error ? (
        <p className="text-red-400 text-sm text-center py-8" data-testid="admin-error">{error}</p>
      ) : (
        <div className="glass-card rounded-xl overflow-hidden" data-testid="admin-table">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[#1E1E2E] text-slate-500 text-xs uppercase tracking-wider">
                <th className="text-left px-4 py-3">{t('admin.columns.query')}</th>
                <th className="text-left px-4 py-3">{t('admin.columns.model')}</th>
                <th className="text-left px-4 py-3">{t('admin.columns.confidence')}</th>
                <th className="text-left px-4 py-3">{t('admin.columns.cacheHit')}</th>
                <th className="text-left px-4 py-3">{t('admin.columns.createdAt')}</th>
              </tr>
            </thead>
            <tbody>
              {interactions.length === 0 ? (
                <tr>
                  <td colSpan={5} className="text-center text-slate-500 py-8">
                    No interactions yet.
                  </td>
                </tr>
              ) : (
                interactions.map(row => (
                  <tr
                    key={row.id}
                    className="border-b border-[#1E1E2E]/50 hover:bg-white/5 transition-colors"
                    data-testid="admin-table-row"
                  >
                    <td className="px-4 py-3 text-slate-300 max-w-xs truncate">{row.queryText}</td>
                    <td className="px-4 py-3 text-slate-400">{row.modelId}</td>
                    <td className="px-4 py-3">
                      <ConfidenceBadge confidence={row.confidence} />
                    </td>
                    <td className="px-4 py-3 text-slate-400">
                      {row.cacheHit ? (
                        <span className="text-indigo-400">✓</span>
                      ) : (
                        <span className="text-slate-600">–</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-500">
                      {new Date(row.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between px-4 py-3 border-t border-[#1E1E2E]">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="text-sm text-slate-400 disabled:opacity-40 hover:text-slate-200 transition-colors"
                data-testid="admin-prev-page"
              >
                ← Prev
              </button>
              <span className="text-xs text-slate-500">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="text-sm text-slate-400 disabled:opacity-40 hover:text-slate-200 transition-colors"
                data-testid="admin-next-page"
              >
                Next →
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
