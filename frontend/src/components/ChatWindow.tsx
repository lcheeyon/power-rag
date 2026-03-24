import { useState, useRef, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { sendQuery, type ChatQueryResponse, type SourceRef } from '../api/chatApi'
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
}

export default function ChatWindow({ model, language, onSourcesChange }: Props) {
  const { t } = useTranslation()
  const [messages, setMessages]       = useState<Message[]>([])
  const [input, setInput]             = useState('')
  const [pendingImage, setPendingImage] = useState<string | null>(null)
  const [loading, setLoading]         = useState(false)
  const [error, setError]             = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

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

  const handleSend = async () => {
    const text = input.trim()
    if ((!text && !pendingImage) || loading) return

    const imageSnap = pendingImage
    const userMsg: Message = {
      id:           crypto.randomUUID(),
      role:         'user',
      content:      text,
      imageBase64:  imageSnap ?? undefined,
    }
    setMessages(prev => [...prev, userMsg])
    setInput('')
    setPendingImage(null)
    setLoading(true)
    setError(null)

    try {
      const res = await sendQuery({
        question:      text || 'Describe this image.',
        modelProvider: model.provider,
        modelId:       model.modelId,
        language,
        imageBase64:   imageSnap ?? undefined,
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
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="flex flex-col h-full" data-testid="chat-window">
      {/* Message list */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4" data-testid="chat-messages">
        {messages.length === 0 && !loading && (
          <div className="flex items-center justify-center h-full">
            <p className="text-slate-500 text-sm">{t('chat.startConversation')}</p>
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
                    {msg.response?.generatedImageBase64 && (
                      <img
                        src={msg.response.generatedImageBase64}
                        alt="generated"
                        className="max-w-xs rounded-lg object-contain border border-[#1E1E2E]"
                      />
                    )}
                    <p className="text-slate-200 text-sm whitespace-pre-wrap">{msg.content}</p>
                  </>
                )}
                {msg.response && !msg.response.error && (
                  <div className="flex items-center gap-2 flex-wrap mt-2">
                    <ConfidenceBadge confidence={msg.response.confidence} />
                    {msg.response.cacheHit && (
                      <span className="text-xs text-indigo-400 border border-indigo-700 px-2 py-0.5 rounded-full">
                        {t('chat.cacheHit')}
                      </span>
                    )}
                    {msg.response.sources.length > 0 && (
                      <span className="text-xs text-slate-500">
                        {msg.response.sources.length} {t('chat.sources')}
                      </span>
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
