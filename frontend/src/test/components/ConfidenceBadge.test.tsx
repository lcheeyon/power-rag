import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import '../../i18n'
import ConfidenceBadge from '../../components/ConfidenceBadge'

describe('ConfidenceBadge', () => {
  it('renders badge element', () => {
    render(<ConfidenceBadge confidence={0.9} />)
    expect(screen.getByTestId('confidence-badge')).toBeInTheDocument()
  })

  it('shows high level for confidence >= 0.8', () => {
    render(<ConfidenceBadge confidence={0.9} />)
    expect(screen.getByTestId('confidence-badge')).toHaveAttribute('data-level', 'high')
  })

  it('shows medium level for confidence 0.5–0.79', () => {
    render(<ConfidenceBadge confidence={0.65} />)
    expect(screen.getByTestId('confidence-badge')).toHaveAttribute('data-level', 'medium')
  })

  it('shows low level for confidence < 0.5', () => {
    render(<ConfidenceBadge confidence={0.3} />)
    expect(screen.getByTestId('confidence-badge')).toHaveAttribute('data-level', 'low')
  })

  it('displays percentage value', () => {
    render(<ConfidenceBadge confidence={0.87} />)
    expect(screen.getByTestId('confidence-badge').textContent).toContain('87%')
  })

  it('shows boundary high at exactly 0.8', () => {
    render(<ConfidenceBadge confidence={0.8} />)
    expect(screen.getByTestId('confidence-badge')).toHaveAttribute('data-level', 'high')
  })

  it('shows boundary medium at exactly 0.5', () => {
    render(<ConfidenceBadge confidence={0.5} />)
    expect(screen.getByTestId('confidence-badge')).toHaveAttribute('data-level', 'medium')
  })
})
