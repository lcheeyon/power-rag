import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { renderAssistantTextWithJiraIssueLinks } from './jiraIssueLinks'

function renderLinks(text: string, base = 'https://powerrag.atlassian.net') {
  return render(<p data-testid="wrap">{renderAssistantTextWithJiraIssueLinks(text, base)}</p>)
}

describe('renderAssistantTextWithJiraIssueLinks', () => {
  it('wraps issue keys in browse links opening a new tab', () => {
    renderLinks('Ticket KAN-5 is done. See also PROJ-12.')
    const a5 = screen.getByRole('link', { name: 'KAN-5' })
    expect(a5).toHaveAttribute('href', 'https://powerrag.atlassian.net/browse/KAN-5')
    expect(a5).toHaveAttribute('target', '_blank')
    expect(a5).toHaveAttribute('rel', 'noopener noreferrer')
    const a12 = screen.getByRole('link', { name: 'PROJ-12' })
    expect(a12).toHaveAttribute('href', 'https://powerrag.atlassian.net/browse/PROJ-12')
  })

  it('returns plain text when base URL is empty', () => {
    renderLinks('KAN-5 only', '')
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByText('KAN-5 only')).toBeInTheDocument()
  })

  it('returns empty string for empty input', () => {
    const { container } = render(<span>{renderAssistantTextWithJiraIssueLinks('', 'https://x.atlassian.net')}</span>)
    expect(container.textContent).toBe('')
  })

  it('splits abutted issue keys and inserts separators', () => {
    renderLinks('Latest: KAN-5KAN-4KAN-3')
    expect(screen.getByRole('link', { name: 'KAN-5' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'KAN-4' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'KAN-3' })).toBeInTheDocument()
    const wrap = screen.getByTestId('wrap')
    expect(wrap.textContent).toMatch(/KAN-5.*·.*KAN-4.*·.*KAN-3/)
  })

  it('shows same-line summary after em dash', () => {
    renderLinks('**KAN-5** — Fix login flow')
    expect(screen.getByRole('link', { name: 'KAN-5' })).toBeInTheDocument()
    expect(screen.getByTestId('wrap').textContent).toContain('Fix login flow')
  })
})
