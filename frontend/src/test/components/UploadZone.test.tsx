import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import '../../i18n'

// Mock the documentApi before importing UploadZone
vi.mock('../../api/documentApi', () => ({
  uploadDocument: vi.fn().mockResolvedValue({
    documentId: 'doc-1',
    fileName:   'test.pdf',
    chunkCount: 5,
    status:     'INDEXED',
    uploadedAt: new Date().toISOString(),
  }),
}))

import UploadZone from '../../components/UploadZone'
import * as documentApi from '../../api/documentApi'

describe('UploadZone', () => {
  beforeEach(() => {
    vi.mocked(documentApi.uploadDocument).mockResolvedValue({
      documentId: 'doc-1',
      fileName:   'test.pdf',
      chunkCount: 5,
      status:     'INDEXED',
      uploadedAt: new Date().toISOString(),
    })
  })

  it('renders the upload zone', () => {
    render(<UploadZone />)
    expect(screen.getByTestId('upload-zone')).toBeInTheDocument()
  })

  it('renders the dropzone area', () => {
    render(<UploadZone />)
    expect(screen.getByTestId('upload-dropzone')).toBeInTheDocument()
  })

  it('renders the file input', () => {
    render(<UploadZone />)
    expect(screen.getByTestId('upload-input')).toBeInTheDocument()
  })

  it('file input accepts the right types', () => {
    render(<UploadZone />)
    const input = screen.getByTestId('upload-input') as HTMLInputElement
    expect(input.accept).toContain('.pdf')
    expect(input.accept).toContain('.java')
    expect(input.accept).toContain('.xlsx')
    expect(input.accept).toContain('.docx')
    expect(input.accept).toContain('.pptx')
    expect(input.accept).toContain('.png')
    expect(input.accept).toContain('.jpg')
    expect(input.accept).toContain('.jpeg')
    expect(input.accept).toContain('.gif')
    expect(input.accept).toContain('.webp')
  })

  it('calls onSuccess after successful upload', async () => {
    const onSuccess = vi.fn()
    render(<UploadZone onSuccess={onSuccess} />)
    const input = screen.getByTestId('upload-input')
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith('test.pdf'))
  })

  it('shows error for unsupported file type', async () => {
    render(<UploadZone />)
    const input = screen.getByTestId('upload-input')
    const file = new File(['content'], 'test.exe', { type: 'application/octet-stream' })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() =>
      expect(screen.getByTestId('upload-error')).toBeInTheDocument(),
    )
  })

  it('shows success message after upload', async () => {
    render(<UploadZone />)
    const input = screen.getByTestId('upload-input')
    const file = new File(['content'], 'report.pdf', { type: 'application/pdf' })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() =>
      expect(screen.getByTestId('upload-success')).toBeInTheDocument(),
    )
  })

  it('shows error message when upload fails', async () => {
    vi.mocked(documentApi.uploadDocument).mockRejectedValueOnce(new Error('Network error'))
    render(<UploadZone />)
    const input = screen.getByTestId('upload-input')
    const file = new File(['content'], 'report.pdf', { type: 'application/pdf' })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() =>
      expect(screen.getByTestId('upload-error')).toBeInTheDocument(),
    )
  })

  it('accepts PPTX files', async () => {
    const onSuccess = vi.fn()
    render(<UploadZone onSuccess={onSuccess} />)
    const input = screen.getByTestId('upload-input')
    const file = new File(['content'], 'slides.pptx', {
      type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith('slides.pptx'))
  })

  it('accepts PNG image files', async () => {
    const onSuccess = vi.fn()
    render(<UploadZone onSuccess={onSuccess} />)
    const input = screen.getByTestId('upload-input')
    const file = new File(['content'], 'diagram.png', { type: 'image/png' })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith('diagram.png'))
  })

  it('accepts JPG image files', async () => {
    const onSuccess = vi.fn()
    render(<UploadZone onSuccess={onSuccess} />)
    const input = screen.getByTestId('upload-input')
    const file = new File(['content'], 'photo.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith('photo.jpg'))
  })

  it('accepts WEBP image files', async () => {
    const onSuccess = vi.fn()
    render(<UploadZone onSuccess={onSuccess} />)
    const input = screen.getByTestId('upload-input')
    const file = new File(['content'], 'screenshot.webp', { type: 'image/webp' })
    fireEvent.change(input, { target: { files: [file] } })
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith('screenshot.webp'))
  })
})
