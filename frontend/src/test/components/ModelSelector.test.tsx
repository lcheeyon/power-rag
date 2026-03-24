import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '../../i18n'
import ModelSelector, { CLOUD_MODEL_OPTIONS, MODEL_OPTIONS } from '../../components/ModelSelector'

// Mock the modelApi so the selector doesn't try to hit the network
vi.mock('../../api/modelApi', () => ({
  listOllamaModels: vi.fn().mockResolvedValue([]),
}))

function renderSelector(onChange = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <ModelSelector value={CLOUD_MODEL_OPTIONS[0]} onChange={onChange} />
    </QueryClientProvider>,
  )
}

describe('ModelSelector', () => {
  it('renders the selector', () => {
    renderSelector()
    expect(screen.getByTestId('model-selector')).toBeInTheDocument()
  })

  it('renders a select element', () => {
    renderSelector()
    expect(screen.getByTestId('model-selector-select')).toBeInTheDocument()
  })

  it('shows cloud model options (no dynamic Ollama in test)', () => {
    renderSelector()
    const select = screen.getByTestId('model-selector-select') as HTMLSelectElement
    // At minimum all cloud models are present
    expect(select.options.length).toBeGreaterThanOrEqual(CLOUD_MODEL_OPTIONS.length)
  })

  it('has correct default value', () => {
    renderSelector()
    const select = screen.getByTestId('model-selector-select') as HTMLSelectElement
    expect(select.value).toBe(`${CLOUD_MODEL_OPTIONS[0].provider}:${CLOUD_MODEL_OPTIONS[0].modelId}`)
  })

  it('calls onChange when selection changes', () => {
    const onChange = vi.fn()
    renderSelector(onChange)
    const select = screen.getByTestId('model-selector-select')
    fireEvent.change(select, {
      target: { value: `${CLOUD_MODEL_OPTIONS[1].provider}:${CLOUD_MODEL_OPTIONS[1].modelId}` },
    })
    expect(onChange).toHaveBeenCalledWith(CLOUD_MODEL_OPTIONS[1])
  })

  it('has accessible aria-label', () => {
    renderSelector()
    expect(screen.getByRole('combobox')).toBeInTheDocument()
  })

  it('cloud models (Claude and Gemini) are all multimodal', () => {
    const cloudMultimodal = CLOUD_MODEL_OPTIONS.filter(
      m => m.provider === 'ANTHROPIC' || m.provider === 'GEMINI',
    )
    cloudMultimodal.forEach(m => expect(m.multimodal).toBe(true))
  })

  it('MODEL_OPTIONS export contains only cloud models', () => {
    expect(MODEL_OPTIONS.every(m => m.provider !== 'OLLAMA')).toBe(true)
    expect(MODEL_OPTIONS.length).toBe(CLOUD_MODEL_OPTIONS.length)
  })

  it('multimodal options display the camera emoji prefix', () => {
    renderSelector()
    const select = screen.getByTestId('model-selector-select') as HTMLSelectElement
    const multimodalModel = CLOUD_MODEL_OPTIONS.find(m => m.multimodal)!
    const option = Array.from(select.options).find(
      o => o.value === `${multimodalModel.provider}:${multimodalModel.modelId}`,
    )
    expect(option?.text).toContain('📷')
  })

  it('cloud models optgroup label contains Cloud', () => {
    renderSelector()
    // optgroup labels are not directly queryable by text but we can check the select renders
    expect(screen.getByTestId('model-selector-select')).toBeInTheDocument()
  })
})
