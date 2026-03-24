import { useTranslation } from 'react-i18next'

interface Props {
  confidence: number
}

export default function ConfidenceBadge({ confidence }: Props) {
  const { t } = useTranslation()

  const level = confidence >= 0.8 ? 'high' : confidence >= 0.5 ? 'medium' : 'low'

  const colorClass = {
    high:   'bg-green-900/50 text-green-400 border-green-700',
    medium: 'bg-amber-900/50 text-amber-400 border-amber-700',
    low:    'bg-red-900/50   text-red-400   border-red-700',
  }[level]

  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs
                  font-medium border ${colorClass}`}
      data-testid="confidence-badge"
      data-level={level}
    >
      <span className="w-1.5 h-1.5 rounded-full bg-current" />
      {Math.round(confidence * 100)}% — {t(`confidence.${level}`)}
    </span>
  )
}
