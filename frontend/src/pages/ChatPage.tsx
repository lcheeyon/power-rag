import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, NavLink } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../contexts/AuthContext'
import ModelSelector, { DEFAULT_MODEL, type ModelOption } from '../components/ModelSelector'
import LanguageToggle from '../components/LanguageToggle'
import UploadZone from '../components/UploadZone'
import ChatWindow from '../components/ChatWindow'
import type { SourceRef } from '../api/chatApi'
import { listDocuments, deleteDocument } from '../api/documentApi'

export default function ChatPage() {
  const { t, i18n } = useTranslation()
  const { logout }  = useAuth()
  const navigate    = useNavigate()
  const queryClient = useQueryClient()

  const [model, setModel]     = useState<ModelOption>(DEFAULT_MODEL)
  const [sources, setSources] = useState<SourceRef[]>([])

  const { data: documents = [] } = useQuery({
    queryKey: ['documents'],
    queryFn:  listDocuments,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteDocument,
    onSuccess:  () => queryClient.invalidateQueries({ queryKey: ['documents'] }),
  })

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div
      className="flex h-screen bg-[#0A0A0F]"
      data-testid="chat-page"
    >
      {/* Left sidebar – Knowledge Base */}
      <aside
        className="w-64 border-r border-[#1E1E2E] bg-[#12121A] flex flex-col"
        data-testid="knowledge-base-sidebar"
      >
        <div className="p-4 border-b border-[#1E1E2E]">
          <h2 className="text-sm font-semibold text-slate-300 uppercase tracking-wider">
            {t('sidebar.knowledgeBase')}
          </h2>
        </div>
        <div className="flex-1 p-4 overflow-y-auto flex flex-col gap-4">
          <UploadZone onSuccess={() => queryClient.invalidateQueries({ queryKey: ['documents'] })} />
          {documents.length === 0 ? (
            <p className="text-slate-500 text-xs">{t('sidebar.noDocuments')}</p>
          ) : (
            <ul className="space-y-1" data-testid="document-list">
              {documents.map(doc => (
                <li key={doc.id} className="flex items-center gap-1 group text-xs text-slate-400" title={doc.fileName}>
                  <span className="text-indigo-400 flex-shrink-0">&#8226;</span>
                  <span className="truncate flex-1">{doc.fileName}</span>
                  <button
                    onClick={() => deleteMutation.mutate(doc.id)}
                    disabled={deleteMutation.isPending}
                    className="flex-shrink-0 opacity-0 group-hover:opacity-100 text-slate-600
                               hover:text-red-400 transition-opacity disabled:cursor-not-allowed"
                    aria-label={`Delete ${doc.fileName}`}
                    data-testid="delete-document-button"
                  >
                    ✕
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>

      {/* Main chat area */}
      <main className="flex-1 flex flex-col min-w-0" data-testid="chat-main">
        <header className="h-16 border-b border-[#1E1E2E] flex items-center px-6 justify-between flex-shrink-0">
          <div className="flex items-center gap-4">
            <span className="gradient-text font-bold text-lg">Power RAG</span>
            <nav className="flex items-center gap-1">
              <NavLink
                to="/chat"
                className={({ isActive }) =>
                  `px-3 py-1 text-sm rounded-md transition-colors ${
                    isActive
                      ? 'text-indigo-400 bg-indigo-500/10 border border-indigo-500/30'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-[#1E1E2E]'
                  }`
                }
              >
                {t('nav.chat')}
              </NavLink>
              <NavLink
                to="/sql"
                className={({ isActive }) =>
                  `px-3 py-1 text-sm rounded-md transition-colors ${
                    isActive
                      ? 'text-indigo-400 bg-indigo-500/10 border border-indigo-500/30'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-[#1E1E2E]'
                  }`
                }
              >
                {t('nav.database')}
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <ModelSelector value={model} onChange={setModel} />
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
        <div className="flex-1 overflow-hidden">
          <ChatWindow
            model={model}
            language={i18n.language}
            onSourcesChange={setSources}
          />
        </div>
      </main>

      {/* Right sidebar – Sources */}
      <aside
        className="w-72 border-l border-[#1E1E2E] bg-[#12121A] flex flex-col"
        data-testid="sources-sidebar"
      >
        <div className="p-4 border-b border-[#1E1E2E]">
          <h2 className="text-sm font-semibold text-slate-300 uppercase tracking-wider">
            {t('sidebar.sources')}
          </h2>
        </div>
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {/* Ingested documents */}
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
              {t('sidebar.knowledgeBase')}
            </p>
            {documents.length === 0 ? (
              <p className="text-slate-500 text-xs">{t('sidebar.noDocuments')}</p>
            ) : (
              <ul className="space-y-1" data-testid="sources-document-list">
                {documents.map(doc => (
                  <li key={doc.id} className="text-xs text-slate-400 truncate" title={doc.fileName}>
                    <span className="text-indigo-400">&#8226;</span> {doc.fileName}
                    <span className="text-slate-600 ml-1">({doc.chunkCount} {t('sidebar.chunks')})</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Chat query sources */}
          {sources.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
                {t('chat.sources')}
              </p>
              <ul className="space-y-3">
                {sources.map((src, i) => (
                  <li
                    key={i}
                    className="border border-[#1E1E2E] rounded-lg p-3 text-sm"
                    data-testid="source-item"
                  >
                    {src.documentId ? (
                      <a
                        href={`/api/documents/${src.documentId}/file`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-indigo-400 font-medium truncate block hover:text-indigo-300 hover:underline"
                        title="Open document in new tab"
                      >
                        {src.fileName} ↗
                      </a>
                    ) : (
                      <p className="text-indigo-400 font-medium truncate">{src.fileName}</p>
                    )}
                    <p className="text-slate-500 text-xs mt-0.5 flex gap-2">
                      {src.pageNumber && <span>Page {src.pageNumber}</span>}
                      {src.lineNumber && !src.section?.includes('-row-') && (
                        <span>{src.fileName?.endsWith('.xlsx') ? 'Row' : 'Line'} {src.lineNumber}</span>
                      )}
                      {src.section   && <span>{src.section}</span>}
                    </p>
                    <p className="text-slate-400 text-xs mt-1 line-clamp-2">{src.snippet}</p>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </aside>
    </div>
  )
}
