import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { fetchMcpTools } from '../api/chatApi'
import { mcpHasJiraTools } from '../utils/mcpTools'

export default function McpToolsPanel() {
  const { t } = useTranslation()
  const [listVisible, setListVisible] = useState(false)
  const { data, isLoading, isError } = useQuery({
    queryKey: ['mcp-tools'],
    queryFn:  fetchMcpTools,
    staleTime: 60_000,
  })

  return (
    <section
      className="flex-shrink-0 border-b border-[#1E1E2E] bg-[#0f0f16]"
      data-testid="mcp-tools-panel"
      aria-label={t('chat.mcpPanelTitle')}
    >
      <div className="flex items-center justify-between gap-3 px-6 py-2 border-b border-[#1E1E2E]/80">
        <h2 className="text-xs font-semibold text-amber-500/90 uppercase tracking-wider">
          {t('chat.mcpPanelTitle')}
        </h2>
        <button
          type="button"
          onClick={() => setListVisible(v => !v)}
          aria-expanded={listVisible}
          className="text-xs text-slate-400 hover:text-amber-400/95 border border-[#1E1E2E] rounded-md px-2.5 py-1
                     hover:border-amber-800/50 transition-colors shrink-0"
          data-testid="mcp-tools-toggle"
        >
          {listVisible ? t('chat.mcpToolsHide') : t('chat.mcpToolsShow')}
        </button>
      </div>
      {listVisible && (
        <div className="px-6 py-3">
      {isLoading && (
        <p className="text-xs text-slate-500">{t('chat.mcpPanelLoading')}</p>
      )}
      {isError && (
        <p className="text-xs text-red-400/90">{t('chat.mcpPanelError')}</p>
      )}
      {!isLoading && !isError && data && (
        <div className="space-y-2">
          {!data.mcpClientAvailable && (
            <p className="text-xs text-slate-500">{t('chat.mcpNotConfigured')}</p>
          )}
          {data.mcpClientAvailable && !data.ragMcpEnabled && (
            <p className="text-xs text-slate-500">{t('chat.mcpNotAttached')}</p>
          )}
          {data.mcpClientAvailable && data.ragMcpEnabled && data.tools.length === 0 && (
            <p className="text-xs text-slate-500">{t('chat.mcpNoTools')}</p>
          )}
          {data.tools.length > 0 && (
            <ul className="space-y-1.5">
              {data.tools.map(tool => (
                <li
                  key={tool.name}
                  className="text-xs border border-amber-900/40 rounded-lg px-2.5 py-1.5 bg-amber-950/20"
                >
                  <span className="font-mono text-amber-400/95">{tool.name}</span>
                  {tool.description ? (
                    <p className="text-slate-400 mt-0.5 leading-snug line-clamp-3">{tool.description}</p>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
          {data.mcpClientAvailable && data.ragMcpEnabled && data.tools.length > 0 && (
            <>
              <p className="text-[10px] text-slate-600">{t('chat.mcpPanelHint')}</p>
              {mcpHasJiraTools(data) && (
                <p className="text-[10px] text-slate-500 border-t border-amber-900/25 pt-2 mt-2">
                  {t('chat.jiraMcpPanelHint')}{' '}
                  <a
                    href={t('chat.jiraBoardUrl')}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-amber-500/90 hover:text-amber-400 underline underline-offset-2"
                  >
                    {t('chat.jiraBoardLink')}
                  </a>
                </p>
              )}
            </>
          )}
        </div>
      )}
        </div>
      )}
    </section>
  )
}
