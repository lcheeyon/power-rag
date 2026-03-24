import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '../../i18n'
import { AuthProvider } from '../../contexts/AuthContext'
import SqlPage from '../../pages/SqlPage'

// Mock the sqlApi so tests don't hit the network
vi.mock('../../api/sqlApi', () => ({
  runSqlQuery: vi.fn(),
}))

import { runSqlQuery } from '../../api/sqlApi'
const mockRunSqlQuery = vi.mocked(runSqlQuery)

function renderSqlPage() {
  localStorage.setItem('jwt_token', 'mock-jwt-token')
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter>
          <SqlPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('SqlPage', () => {
  beforeEach(() => {
    localStorage.setItem('jwt_token', 'mock-jwt-token')
    vi.clearAllMocks()
  })

  it('renders the sql page', () => {
    renderSqlPage()
    expect(screen.getByTestId('sql-page')).toBeInTheDocument()
  })

  it('renders the query input textarea', () => {
    renderSqlPage()
    expect(screen.getByTestId('sql-input')).toBeInTheDocument()
  })

  it('renders the run query button', () => {
    renderSqlPage()
    expect(screen.getByTestId('sql-submit')).toBeInTheDocument()
  })

  it('run button is disabled when input is empty', () => {
    renderSqlPage()
    expect(screen.getByTestId('sql-submit')).toBeDisabled()
  })

  it('run button is enabled when input has text', () => {
    renderSqlPage()
    fireEvent.change(screen.getByTestId('sql-input'), {
      target: { value: 'Show all approved applications' },
    })
    expect(screen.getByTestId('sql-submit')).not.toBeDisabled()
  })

  it('renders sample questions', () => {
    renderSqlPage()
    const samples = screen.getAllByTestId('sample-question')
    expect(samples.length).toBeGreaterThan(0)
  })

  it('clicking a sample populates the input', () => {
    renderSqlPage()
    const firstSample = screen.getAllByTestId('sample-question')[0]
    fireEvent.click(firstSample)
    const input = screen.getByTestId('sql-input') as HTMLTextAreaElement
    expect(input.value).toBeTruthy()
  })

  it('shows results table on successful query', async () => {
    mockRunSqlQuery.mockResolvedValueOnce({
      sql:            'SELECT * FROM grant_programs',
      columns:        ['id', 'name', 'status'],
      rows:           [{ id: 1, name: 'Green Fund', status: 'OPEN' }],
      rowCount:       1,
      clarification:  null,
      executionError: null,
      durationMs:     120,
    })

    renderSqlPage()
    fireEvent.change(screen.getByTestId('sql-input'), {
      target: { value: 'Show all grant programs' },
    })
    fireEvent.click(screen.getByTestId('sql-submit'))

    await waitFor(() => expect(screen.getByTestId('sql-table')).toBeInTheDocument())
    expect(screen.getByTestId('sql-generated')).toBeInTheDocument()
  })

  it('shows generated SQL in results', async () => {
    mockRunSqlQuery.mockResolvedValueOnce({
      sql:            'SELECT id, name FROM grant_programs WHERE status = \'OPEN\'',
      columns:        ['id', 'name'],
      rows:           [{ id: 1, name: 'Green Fund' }],
      rowCount:       1,
      clarification:  null,
      executionError: null,
      durationMs:     95,
    })

    renderSqlPage()
    fireEvent.change(screen.getByTestId('sql-input'), {
      target: { value: 'Show open programs' },
    })
    fireEvent.click(screen.getByTestId('sql-submit'))

    await waitFor(() => expect(screen.getByTestId('sql-generated')).toBeInTheDocument())
    expect(screen.getByText(/SELECT id, name FROM grant_programs/)).toBeInTheDocument()
  })

  it('shows clarification message when LLM asks for more info', async () => {
    mockRunSqlQuery.mockResolvedValueOnce({
      sql:            null,
      columns:        [],
      rows:           [],
      rowCount:       0,
      clarification:  'Could you please specify which time period you are interested in?',
      executionError: null,
      durationMs:     200,
    })

    renderSqlPage()
    fireEvent.change(screen.getByTestId('sql-input'), {
      target: { value: 'Show recent data' },
    })
    fireEvent.click(screen.getByTestId('sql-submit'))

    await waitFor(() => expect(screen.getByTestId('sql-clarification')).toBeInTheDocument())
  })

  it('shows execution error when SQL fails', async () => {
    mockRunSqlQuery.mockResolvedValueOnce({
      sql:            'SELECT * FROM nonexistent_table',
      columns:        [],
      rows:           [],
      rowCount:       0,
      clarification:  null,
      executionError: 'relation "nonexistent_table" does not exist',
      durationMs:     50,
    })

    renderSqlPage()
    fireEvent.change(screen.getByTestId('sql-input'), {
      target: { value: 'Show data from nonexistent table' },
    })
    fireEvent.click(screen.getByTestId('sql-submit'))

    await waitFor(() => expect(screen.getByTestId('sql-execution-error')).toBeInTheDocument())
  })

  it('shows network error message on API failure', async () => {
    mockRunSqlQuery.mockRejectedValueOnce(new Error('Network Error'))

    renderSqlPage()
    fireEvent.change(screen.getByTestId('sql-input'), {
      target: { value: 'Show all programs' },
    })
    fireEvent.click(screen.getByTestId('sql-submit'))

    await waitFor(() => expect(screen.getByTestId('sql-error')).toBeInTheDocument())
    expect(screen.getByText('Network Error')).toBeInTheDocument()
  })

  it('shows empty state message when query returns no rows', async () => {
    mockRunSqlQuery.mockResolvedValueOnce({
      sql:            'SELECT * FROM grant_applications WHERE status = \'WITHDRAWN\'',
      columns:        ['id', 'title'],
      rows:           [],
      rowCount:       0,
      clarification:  null,
      executionError: null,
      durationMs:     80,
    })

    renderSqlPage()
    fireEvent.change(screen.getByTestId('sql-input'), {
      target: { value: 'Show withdrawn applications' },
    })
    fireEvent.click(screen.getByTestId('sql-submit'))

    await waitFor(() => expect(screen.getByTestId('sql-empty')).toBeInTheDocument())
  })

  it('shows Power RAG branding in header', () => {
    renderSqlPage()
    expect(screen.getByText('Power RAG')).toBeInTheDocument()
  })
})
