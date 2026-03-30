import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { sendQuery, fetchMcpTools, type ChatQueryResponse, type SourceRef } from '../api/chatApi'
import { mcpHasJiraTools } from '../utils/mcpTools'
import { renderAssistantTextWithJiraIssueLinks } from '../utils/jiraIssueLinks'
import type { ModelOption } from './ModelSelector'
import ConfidenceBadge from './ConfidenceBadge'

interface Message {
  id:           string
  role:         'user' | 'assistant'
  content:      string
  imageBase64?: string
  response?:    ChatQueryResponse
}

interface Props {
  model:    ModelOption
  language: string
  onSourcesChange?: (sources: SourceRef[]) => void
  /** Open uploaded KB file in a new tab (JWT-backed download). */
  onOpenKbDocument?: (documentId: string, fileName: string) => void
  /** Scroll/highlight the right-hand Sources panel (e.g. after clicking a citation). */
  onFocusSourcesPanel?: () => void
}

function dedupeSourcesByDoc(sources: SourceRef[]): SourceRef[] {
  const seen = new Set<string>()
  const out: SourceRef[] = []
  for (const s of sources) {
    const key = s.documentId ?? s.fileName
    if (seen.has(key)) continue
    seen.add(key)
    out.push(s)
  }
  return out
}

export default function ChatWindow({
  model,
  language,
  onSourcesChange,
  onOpenKbDocument,
  onFocusSourcesPanel,
}: Props) {
  const { t } = useTranslation()
  const [messages, setMessages]       = useState<Message[]>([])
  const [input, setInput]             = useState('')
  const [pendingImage, setPendingImage] = useState<string | null>(null)
  const [loading, setLoading]         = useState(false)
  const [error, setError]             = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  const { data: mcpData } = useQuery({
    queryKey: ['mcp-tools'],
    queryFn:  fetchMcpTools,
    staleTime: 60_000,
  })
  const jiraChatEnabled = mcpHasJiraTools(mcpData)
  const jiraBrowseBase = useMemo(() => {
    try {
      return new URL(t('chat.jiraBoardUrl')).origin
    } catch {
      return ''
    }
  }, [t, language])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  const readFileAsDataUrl = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload  = () => resolve(reader.result as string)
      reader.onerror = reject
      reader.readAsDataURL(file)
    })

  const handlePaste = useCallback(async (e: React.ClipboardEvent) => {
    const items = Array.from(e.clipboardData.items)
    const imageItem = items.find(item => item.type.startsWith('image/'))
    if (!imageItem) return
    e.preventDefault()
    const file = imageItem.getAsFile()
    if (!file) return
    const dataUrl = await readFileAsDataUrl(file)
    setPendingImage(dataUrl)
  }, [])

  const sendUserMessage = useCallback(
    async (rawText: string, imageSnap: string | null | undefined, options?: { fromJiraChip?: boolean }) => {
      const text = rawText.trim()
      const img  = imageSnap ?? undefined
      const question = text || (img ? 'Describe this image.' : '')
      if ((!question && !img) || loading) return

      if (options?.fromJiraChip) {
        setInput('')
        setPendingImage(null)
      }

      const userMsg: Message = {
        id:          crypto.randomUUID(),
        role:        'user',
        content:     text,
        imageBase64: img,
      }
      setMessages(prev => [...prev, userMsg])
      if (!options?.fromJiraChip) {
        setInput('')
        setPendingImage(null)
      }
      setLoading(true)
      setError(null)

      try {
        const clientTimezone =
          typeof Intl !== 'undefined'
            ? Intl.DateTimeFormat().resolvedOptions().timeZone
            : undefined
        const res = await sendQuery({
          question,
          modelProvider: model.provider,
          modelId:       model.modelId,
          language,
          imageBase64:   img,
          clientTimezone,
        })
        const aiMsg: Message = {
          id:       crypto.randomUUID(),
          role:     'assistant',
          content:  res.answer,
          response: res,
        }
        setMessages(prev => [...prev, aiMsg])
        onSourcesChange?.(res.sources)
      } catch {
        setError(t('errors.generic'))
      } finally {
        setLoading(false)
      }
    },
    [loading, model.modelId, model.provider, language, onSourcesChange, t],
  )

  const handleSend = () => {
    void sendUserMessage(input, pendingImage)
  }

  const sendJiraQuickPrompt = (i18nKey: string) => {
    void sendUserMessage(t(i18nKey), null, { fromJiraChip: true })
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const jiraChipClass =
    'text-left px-3 py-1.5 rounded-lg text-xs font-medium border border-indigo-500/35 bg-indigo-500/10 ' +
    'text-indigo-200 hover:bg-indigo-500/20 hover:border-indigo-400/50 transition-colors ' +
    'disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-indigo-500/10'

  return (
    <div className="flex flex-col h-full" data-testid="chat-window">
      {/* Message list */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4" data-testid="chat-messages">
        {messages.length === 0 && !loading && (
          <div className="flex flex-col items-center justify-center h-full px-6 text-center max-w-lg mx-auto">
            <p className="text-slate-500 text-sm">{t('chat.startConversation')}</p>
            {jiraChatEnabled && (
              <div className="mt-6 w-full space-y-3" data-testid="jira-empty-hint">
                <p className="text-slate-400 text-xs font-medium uppercase tracking-wide">
                  {t('chat.jiraQuickSection')}
                </p>
                <p className="text-slate-500 text-xs leading-relaxed">{t('chat.jiraQuickHint')}</p>
                <div className="flex flex-col sm:flex-row flex-wrap gap-2 justify-center">
                  <button
                    type="button"
                    disabled={loading}
                    className={jiraChipClass}
                    onClick={() => sendJiraQuickPrompt('chat.jiraChipList')}
                  >
                    {t('chat.jiraChipList')}
                  </button>
                  <button
                    type="button"
                    disabled={loading}
                    className={jiraChipClass}
                    onClick={() => sendJiraQuickPrompt('chat.jiraChipOpen')}
                  >
                    {t('chat.jiraChipOpen')}
                  </button>
                  <button
                    type="button"
                    disabled={loading}
                    className={jiraChipClass}
                    onClick={() => sendJiraQuickPrompt('chat.jiraChipOne')}
                  >
                    {t('chat.jiraChipOne')}
                  </button>
                </div>
                <a
                  href={t('chat.jiraBoardUrl')}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-block text-xs text-indigo-400 hover:text-indigo-300 underline underline-offset-2"
                >
                  {t('chat.jiraBoardLink')} ↗
                </a>
              </div>
            )}
          </div>
        )}

        {messages.map(msg => (
          <div
            key={msg.id}
            className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
            data-testid={`message-${msg.role}`}
          >
            {msg.role === 'user' ? (
              <div className="chat-bubble-user max-w-[70%] px-4 py-2.5 rounded-2xl rounded-tr-sm text-sm text-white space-y-2">
                {msg.imageBase64 && (
                  <img
                    src={msg.imageBase64}
                    alt="pasted"
                    className="max-w-xs max-h-48 rounded-lg object-contain"
                  />
                )}
                {msg.content && <p>{msg.content}</p>}
              </div>
            ) : (
              <div className="chat-bubble-ai max-w-[80%] px-4 py-3 rounded-2xl rounded-tl-sm space-y-2">
                {msg.response?.error ? (
                  <p className="text-red-400 text-sm whitespace-pre-wrap">⚠ {msg.response.error}</p>
                ) : (
                  <>
                    {msg.response?.cacheHit && (
                      <div
                        role="status"
                        data-testid="cache-hit-notice"
                        className="rounded-lg border border-cyan-800/70 bg-cyan-950/40 px-3 py-2.5 text-left"
                      >
                        <p className="text-xs font-semibold text-cyan-200 tracking-wide">
                          {t('chat.cacheHitNoticeTitle')}
                        </p>
                        <p className="text-[11px] text-cyan-100/85 leading-snug mt-1">
                          {t('chat.cacheHitNoticeBody')}
                        </p>
                      </div>
                    )}
                    {msg.response?.generatedImageBase64 && (
                      <img
                        src={msg.response.generatedImageBase64}
                        alt="generated"
                        className="max-w-xs rounded-lg object-contain border border-[#1E1E2E]"
                      />
                    )}
                    <p className="text-slate-200 text-sm whitespace-pre-wrap">
                      {jiraChatEnabled && jiraBrowseBase
                        ? renderAssistantTextWithJiraIssueLinks(msg.content, jiraBrowseBase)
                        : msg.content}
                    </p>
                  </>
                )}
                {msg.response && !msg.response.error && (
                  <div className="flex items-center gap-2 flex-wrap mt-2">
                    <ConfidenceBadge confidence={msg.response.confidence} />
                    {msg.response.cacheHit && (
                      <span
                        className="text-xs text-cyan-300/90 border border-cyan-700/60 px-2 py-0.5 rounded-full"
                        title={t('chat.cacheHitNoticeBody')}
                      >
                        {t('chat.cacheHit')}
                      </span>
                    )}
                    {(msg.response.mcpInvocations?.length ?? 0) > 0 && (
                      <details className="mt-1 w-full max-w-md">
                        <summary
                          className="text-xs text-amber-400 border border-amber-700/60 px-2 py-0.5 rounded-full cursor-pointer inline-block [&::-webkit-details-marker]:hidden marker:content-['']"
                          data-testid="mcp-tools-badge"
                        >
                          MCP · {msg.response.mcpInvocations!.length}{' '}
                          {msg.response.mcpInvocations!.length === 1 ? t('chat.mcpTool') : t('chat.mcpTools')}
                        </summary>
                        <ul className="mt-2 pl-1 text-xs text-slate-500 space-y-1 border-l border-amber-900/40 ml-1">
                          {msg.response.mcpInvocations!.map((inv, idx) => (
                            <li key={`${inv.toolName}-${idx}`}>
                              <span className="text-slate-400">{inv.toolName}</span>
                              <span className="text-slate-600"> · {inv.durationMs}ms</span>
                              {!inv.success && inv.errorMessage && (
                                <span className="text-amber-600/90"> — {inv.errorMessage}</span>
                              )}
                            </li>
                          ))}
                        </ul>
                      </details>
                    )}
                    {msg.response.sources.length > 0 && (
                      <div
                        className="flex flex-col gap-1.5 mt-1 w-full max-w-md"
                        data-testid="message-source-links"
                      >
                        <span className="text-xs text-slate-500">
                          {t('chat.sourceCitations')}
                        </span>
                        <ul className="flex flex-col gap-1">
                          {dedupeSourcesByDoc(msg.response.sources).map((src, idx) => (
                            <li key={`${src.documentId ?? 'noid'}-${src.fileName}-${idx}`}>
                              {src.documentId && onOpenKbDocument ? (
                                <button
                                  type="button"
                                  onClick={() => {
                                    onFocusSourcesPanel?.()
                                    void onOpenKbDocument(src.documentId!, src.fileName)
                                  }}
                                  className="text-indigo-400 hover:text-indigo-300 underline underline-offset-2 text-left text-xs"
                                  title={t('chat.openSourceNewTab')}
                                >
                                  {src.fileName} ↗
                                </button>
                              ) : (
                                <button
                                  type="button"
                                  onClick={() => onFocusSourcesPanel?.()}
                                  className="text-slate-400 hover:text-slate-300 underline underline-offset-2 text-left text-xs"
                                  title={t('chat.showSourcesPanel')}
                                >
                                  {src.fileName}
                                </button>
                              )}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}

        {loading && (
          <div className="flex justify-start" data-testid="chat-thinking">
            <div className="chat-bubble-ai px-4 py-3 rounded-2xl rounded-tl-sm">
              <span className="text-slate-400 text-sm animate-pulse">{t('chat.thinking')}</span>
            </div>
          </div>
        )}

        {error && (
          <p className="text-red-400 text-sm text-center" data-testid="chat-error">{error}</p>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="border-t border-[#1E1E2E] p-4" data-testid="chat-input-area">
        {jiraChatEnabled && (
          <div
            className="mb-3 flex flex-col gap-2 border-b border-[#1E1E2E] pb-3"
            data-testid="jira-composer-hints"
          >
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              {t('chat.jiraQuickSection')}
            </span>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={loading}
                className={jiraChipClass}
                onClick={() => sendJiraQuickPrompt('chat.jiraChipList')}
              >
                {t('chat.jiraChipList')}
              </button>
              <button
                type="button"
                disabled={loading}
                className={jiraChipClass}
                onClick={() => sendJiraQuickPrompt('chat.jiraChipOpen')}
              >
                {t('chat.jiraChipOpen')}
              </button>
              <button
                type="button"
                disabled={loading}
                className={jiraChipClass}
                onClick={() => sendJiraQuickPrompt('chat.jiraChipOne')}
              >
                {t('chat.jiraChipOne')}
              </button>
            </div>
          </div>
        )}
        {pendingImage && (
          <div className="mb-2 flex items-start gap-2">
            <img
              src={pendingImage}
              alt="pending"
              className="max-h-20 max-w-[160px] rounded-lg object-contain border border-[#1E1E2E]"
            />
            <button
              onClick={() => setPendingImage(null)}
              className="text-slate-500 hover:text-slate-300 text-xs mt-1"
              aria-label="Remove image"
            >
              ✕
            </button>
          </div>
        )}
        <div className="flex gap-2">
          <textarea
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            onPaste={handlePaste}
            placeholder={t('chat.placeholder')}
            rows={1}
            disabled={loading}
            className="flex-1 bg-[#0A0A0F] border border-[#1E1E2E] rounded-xl px-4 py-2.5
                       text-slate-100 text-sm resize-none focus:outline-none focus:ring-2
                       focus:ring-indigo-500 placeholder:text-slate-600 disabled:opacity-50"
            data-testid="chat-input"
          />
          <button
            onClick={handleSend}
            disabled={loading || (!input.trim() && !pendingImage)}
            className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-600/50
                       text-white rounded-xl text-sm font-medium transition-colors disabled:cursor-not-allowed"
            data-testid="chat-send-button"
          >
            ↑
          </button>
        </div>
      </div>
    </div>
  )
}
