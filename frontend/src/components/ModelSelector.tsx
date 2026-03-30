import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery }       from '@tanstack/react-query'
import { listGeminiModels, listOllamaModels } from '../api/modelApi'

export interface ModelOption {
  provider:   string
  modelId:    string
  label:      string
  tier:       string
  multimodal: boolean
  local?:     boolean   // true = Ollama local model
}

/** Anthropic models — static list */
export const ANTHROPIC_MODEL_OPTIONS: ModelOption[] = [
  { provider: 'ANTHROPIC', modelId: 'claude-sonnet-4-6',         label: 'Claude Sonnet 4.6',   tier: 'balanced', multimodal: true },
  { provider: 'ANTHROPIC', modelId: 'claude-opus-4-6',           label: 'Claude Opus 4.6',     tier: 'powerful', multimodal: true },
  { provider: 'ANTHROPIC', modelId: 'claude-haiku-4-5-20251001', label: 'Claude Haiku 4.5',    tier: 'fast',     multimodal: true },
]

/**
 * Fallback when {@link listGeminiModels} is empty or fails — includes Gemini 2.5 and 3.x families.
 */
export const GEMINI_STATIC_FALLBACK: ModelOption[] = [
  { provider: 'GEMINI', modelId: 'gemini-2.5-flash',       label: 'Gemini 2.5 Flash',        tier: 'balanced', multimodal: true },
  { provider: 'GEMINI', modelId: 'gemini-2.5-pro',         label: 'Gemini 2.5 Pro',          tier: 'powerful', multimodal: true },
  { provider: 'GEMINI', modelId: 'gemini-2.5-flash-lite',  label: 'Gemini 2.5 Flash-Lite',   tier: 'fast',     multimodal: true },
  { provider: 'GEMINI', modelId: 'gemini-3-pro-image-preview', label: 'Nano Banana Pro (image)', tier: 'preview', multimodal: true },
  { provider: 'GEMINI', modelId: 'gemini-3.1-flash-image-preview', label: 'Nano Banana 2 (image)', tier: 'preview', multimodal: true },
  { provider: 'GEMINI', modelId: 'gemini-2.5-flash-image', label: 'Nano Banana (image)', tier: 'balanced', multimodal: true },
  { provider: 'GEMINI', modelId: 'gemini-3-pro-preview',   label: 'Gemini 3 Pro (preview)',  tier: 'preview',  multimodal: true },
  { provider: 'GEMINI', modelId: 'gemini-3.1-pro-preview',   label: 'Gemini 3.1 Pro (preview)', tier: 'preview', multimodal: true },
  { provider: 'GEMINI', modelId: 'gemini-3-flash-preview', label: 'Gemini 3 Flash (preview)', tier: 'preview', multimodal: true },
]

/** Static cloud models — tests and exports (Anthropic + Gemini fallback set) */
export const CLOUD_MODEL_OPTIONS: ModelOption[] = [
  ...ANTHROPIC_MODEL_OPTIONS,
  ...GEMINI_STATIC_FALLBACK,
]

/** For tests — cloud models only */
export const MODEL_OPTIONS = CLOUD_MODEL_OPTIONS

function inferGeminiTier(modelId: string): ModelOption['tier'] {
  const lower = modelId.toLowerCase()
  if (
    lower.includes('preview')
    || lower.includes('experimental')
    || lower.startsWith('gemini-exp')
  ) {
    return 'preview'
  }
  if (lower.startsWith('gemini-3') && lower.includes('flash') && lower.includes('lite')) {
    return 'fast'
  }
  if (lower.startsWith('gemini-3') && lower.includes('flash')) {
    return 'balanced'
  }
  if (lower.startsWith('gemini-3') && lower.includes('pro')) {
    return 'powerful'
  }
  if (lower.includes('flash-lite')) {
    return 'fast'
  }
  if (lower.includes('flash')) {
    return 'balanced'
  }
  if (lower.includes('pro')) {
    return 'powerful'
  }
  return 'balanced'
}

/** Default model shown on the chat screen */
export const DEFAULT_MODEL: ModelOption = {
  provider:   'GEMINI',
  modelId:    'gemini-2.5-flash-lite',
  label:      'Gemini 2.5 Flash-Lite',
  tier:       'fast',
  multimodal: true,
}

interface Props {
  value:    ModelOption
  onChange: (model: ModelOption) => void
}

export default function ModelSelector({ value, onChange }: Props) {
  const { t } = useTranslation()

  const { data: ollamaModels = [] } = useQuery({
    queryKey: ['ollama-models'],
    queryFn:  listOllamaModels,
    staleTime: 30_000,
    retry: false,
  })

  const { data: geminiCatalog } = useQuery({
    queryKey: ['gemini-models'],
    queryFn:  listGeminiModels,
    staleTime: 5 * 60_000,
    retry: false,
  })

  const geminiOptions: ModelOption[] = useMemo(() => {
    const rows = geminiCatalog
    if (rows && rows.length > 0) {
      return rows.map(m => ({
        provider:   'GEMINI',
        modelId:    m.modelId,
        label:      m.displayName?.trim() || m.modelId,
        tier:       inferGeminiTier(m.modelId),
        multimodal: m.multimodal,
      }))
    }
    return GEMINI_STATIC_FALLBACK
  }, [geminiCatalog])

  const localOptions: ModelOption[] = ollamaModels.map(m => ({
    provider:   'OLLAMA',
    modelId:    m.modelId,
    label:      m.label,
    tier:       'code',
    multimodal: m.multimodal,
    local:      true,
  }))

  const allOptions: ModelOption[] = [
    ...ANTHROPIC_MODEL_OPTIONS,
    ...geminiOptions,
    ...localOptions,
  ]

  const selectedKey = `${value.provider}:${value.modelId}`

  return (
    <div className="relative" data-testid="model-selector">
      <select
        value={selectedKey}
        onChange={e => {
          const found = allOptions.find(
            m => `${m.provider}:${m.modelId}` === e.target.value,
          )
          if (found) onChange(found)
        }}
        className="bg-[#12121A] border border-[#1E1E2E] text-slate-300 text-sm rounded-lg
                   px-3 py-1.5 pr-8 appearance-none cursor-pointer
                   focus:outline-none focus:ring-2 focus:ring-indigo-500"
        aria-label={t('model.selector')}
        data-testid="model-selector-select"
      >
        {ANTHROPIC_MODEL_OPTIONS.length > 0 && (
          <optgroup label={`☁️ ${t('model.providers.CLAUDE')}`}>
            {ANTHROPIC_MODEL_OPTIONS.map(m => (
              <option key={`${m.provider}:${m.modelId}`} value={`${m.provider}:${m.modelId}`}>
                {m.multimodal ? '📷 ' : '📝 '}{m.label} ({t(`model.tiers.${m.tier}`)})
              </option>
            ))}
          </optgroup>
        )}
        {geminiOptions.length > 0 && (
          <optgroup label={`☁️ ${t('model.providers.GEMINI')}`}>
            {geminiOptions.map(m => (
              <option key={`${m.provider}:${m.modelId}`} value={`${m.provider}:${m.modelId}`}>
                {m.multimodal ? '📷 ' : '📝 '}{m.label} ({t(`model.tiers.${m.tier}`)})
              </option>
            ))}
          </optgroup>
        )}
        {localOptions.length > 0 && (
          <optgroup label="🖥️ Local (Ollama)">
            {localOptions.map(m => (
              <option key={`${m.provider}:${m.modelId}`} value={`${m.provider}:${m.modelId}`}>
                {m.multimodal ? '📷 ' : '📝 '}{m.label}
              </option>
            ))}
          </optgroup>
        )}
      </select>
      <span className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 text-xs">
        ▾
      </span>
    </div>
  )
}
