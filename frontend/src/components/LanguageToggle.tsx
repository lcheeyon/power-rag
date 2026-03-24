import { useTranslation } from 'react-i18next'
import { SUPPORTED_LANGUAGES } from '../i18n'

export default function LanguageToggle() {
  const { i18n } = useTranslation()

  const currentIndex = SUPPORTED_LANGUAGES.findIndex(l => l.code === i18n.language)
  const current = SUPPORTED_LANGUAGES[currentIndex] ?? SUPPORTED_LANGUAGES[0]

  const handleToggle = () => {
    const next = SUPPORTED_LANGUAGES[(currentIndex + 1) % SUPPORTED_LANGUAGES.length]
    i18n.changeLanguage(next.code)
  }

  return (
    <button
      onClick={handleToggle}
      className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg border border-[#1E1E2E]
                 bg-[#12121A] text-slate-300 text-sm hover:border-indigo-500 transition-colors"
      aria-label={`Switch language — current: ${current.label}`}
      data-testid="language-toggle"
    >
      <span aria-hidden="true">{current.flag}</span>
      <span>{current.label}</span>
    </button>
  )
}
