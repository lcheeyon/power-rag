import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import '../../i18n'
import LanguageToggle from '../../components/LanguageToggle'
import i18n from '../../i18n'

function renderToggle() {
  return render(<LanguageToggle />)
}

describe('LanguageToggle', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders the toggle button', () => {
    renderToggle()
    expect(screen.getByTestId('language-toggle')).toBeInTheDocument()
  })

  it('shows current language label', () => {
    renderToggle()
    expect(screen.getByTestId('language-toggle').textContent).toContain('English')
  })

  it('has accessible aria-label', () => {
    renderToggle()
    expect(screen.getByRole('button')).toHaveAttribute('aria-label')
  })

  it('cycles to next language on click', () => {
    renderToggle()
    const btn = screen.getByTestId('language-toggle')
    fireEvent.click(btn)
    expect(i18n.language).toBe('zh-CN')
  })

  it('cycles back to English after all languages', () => {
    i18n.changeLanguage('zh-TW')
    renderToggle()
    fireEvent.click(screen.getByTestId('language-toggle'))
    expect(i18n.language).toBe('en')
  })
})
