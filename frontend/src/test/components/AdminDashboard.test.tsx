import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import '../../i18n'
import AdminDashboard from '../../components/AdminDashboard'

function renderDashboard() {
  return render(<AdminDashboard />)
}

describe('AdminDashboard', () => {
  it('renders the dashboard', () => {
    renderDashboard()
    expect(screen.getByTestId('admin-dashboard')).toBeInTheDocument()
  })

  it('renders the refresh button', () => {
    renderDashboard()
    expect(screen.getByTestId('admin-refresh-button')).toBeInTheDocument()
  })

  it('shows the interactions table after loading', async () => {
    renderDashboard()
    await waitFor(() =>
      expect(screen.getByTestId('admin-table')).toBeInTheDocument(),
    )
  })

  it('shows a table row for each interaction', async () => {
    renderDashboard()
    await waitFor(() =>
      expect(screen.getAllByTestId('admin-table-row').length).toBeGreaterThan(0),
    )
  })

  it('shows confidence badge in table rows', async () => {
    renderDashboard()
    await waitFor(() =>
      expect(screen.getByTestId('confidence-badge')).toBeInTheDocument(),
    )
  })

  it('shows total elements stat', async () => {
    renderDashboard()
    await waitFor(() =>
      expect(screen.getByTestId('admin-stat-total')).toBeInTheDocument(),
    )
  })
})
