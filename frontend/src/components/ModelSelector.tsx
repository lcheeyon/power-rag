import { useTranslation } from 'react-i18next'
import { useQuery }       from '@tanstack/react-query'
import { listOllamaModels } from '../api/modelApi'

export interface ModelOption {
  provider:   string
  modelId:    string
  label:      string
  tier:       string
  multimodal: boolean
  local?:     boolean   // true = Ollama local model
}

/** Static cloud models — always available */
export const CLOUD_MODEL_OPTIONS: ModelOption[] = [
  { provider: 'ANTHROPIC', modelId: 'claude-sonnet-4-6',         label: 'Claude Sonnet 4.6',   tier: 'balanced', multimodal: true  },
  { provider: 'ANTHROPIC', modelId: 'claude-opus-4-6',           label: 'Claude Opus 4.6',     tier: 'powerful', multimodal: true  },
  { provider: 'ANTHROPIC', modelId: 'claude-haiku-4-5-20251001', label: 'Claude Haiku 4.5',    tier: 'fast',     multimodal: true  },
  { provider: 'GEMINI',    modelId: 'gemini-2.5-flash',          label: 'Gemini 2.5 Flash',    tier: 'balanced', multimodal: true  },
  { provider: 'GEMINI',    modelId: 'gemini-2.5-pro',            label: 'Gemini 2.5 Pro',      tier: 'powerful', multimodal: true  },
]

/** For tests and fallback — cloud models only */
export const MODEL_OPTIONS = CLOUD_MODEL_OPTIONS

/** Default model shown on the chat screen */
export const DEFAULT_MODEL = CLOUD_MODEL_OPTIONS.find(
  m => m.provider === 'GEMINI' && m.modelId === 'gemini-2.5-pro',
)!

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

  const localOptions: ModelOption[] = ollamaModels.map(m => ({
    provider:   'OLLAMA',
    modelId:    m.modelId,
    label:      m.label,
    tier:       'code',
    multimodal: m.multimodal,
    local:      true,
  }))

  const allOptions: ModelOption[] = [...CLOUD_MODEL_OPTIONS, ...localOptions]

  // If current value no longer exists in allOptions (e.g. Ollama went away), keep it anyway
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
        {CLOUD_MODEL_OPTIONS.length > 0 && (
          <optgroup label="☁️ Cloud Models">
            {CLOUD_MODEL_OPTIONS.map(m => (
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
