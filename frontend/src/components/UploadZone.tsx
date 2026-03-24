import { useState, useCallback, DragEvent, ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { uploadDocument } from '../api/documentApi'

const ACCEPTED_TYPES = ['.java', '.pdf', '.xlsx', '.docx', '.pptx', '.png', '.jpg', '.jpeg', '.gif', '.webp']
const ACCEPTED_MIME = [
  'text/x-java-source',
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/webp',
]

interface Props {
  onSuccess?: (fileName: string) => void
}

function isAccepted(file: File): boolean {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  return ACCEPTED_TYPES.includes(ext) || ACCEPTED_MIME.includes(file.type)
}

export default function UploadZone({ onSuccess }: Props) {
  const { t } = useTranslation()
  const [dragging, setDragging]   = useState(false)
  const [uploading, setUploading] = useState(false)
  const [message, setMessage]     = useState<{ type: 'success' | 'error'; key: string } | null>(null)

  const handleFile = useCallback(async (file: File) => {
    if (!isAccepted(file)) {
      setMessage({ type: 'error', key: 'upload.error' })
      return
    }
    setUploading(true)
    setMessage(null)
    try {
      await uploadDocument(file)
      setMessage({ type: 'success', key: 'upload.success' })
      onSuccess?.(file.name)
    } catch {
      setMessage({ type: 'error', key: 'upload.error' })
    } finally {
      setUploading(false)
    }
  }, [onSuccess])

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }

  const onInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) handleFile(file)
    e.target.value = ''
  }

  return (
    <div data-testid="upload-zone">
      <label htmlFor="file-upload">
        <div
          onDragOver={e => { e.preventDefault(); setDragging(true) }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={`border-2 border-dashed rounded-lg p-6 text-center cursor-pointer transition-colors
            ${dragging
              ? 'border-indigo-500 bg-indigo-500/10'
              : 'border-[#1E1E2E] hover:border-indigo-500/60'}`}
          data-testid="upload-dropzone"
        >
          <div className="text-slate-400 text-sm">
            {uploading ? (
              <span data-testid="upload-uploading">{t('upload.uploading')}</span>
            ) : (
              <>
                <p className="font-medium text-slate-300">{t('upload.dropzone')}</p>
                <p className="text-xs mt-1 text-slate-500">{t('upload.supported')}</p>
              </>
            )}
          </div>
        </div>
      </label>
      <input
        id="file-upload"
        type="file"
        accept={ACCEPTED_TYPES.join(',')}
        onChange={onInputChange}
        className="sr-only"
        data-testid="upload-input"
        disabled={uploading}
      />
      {message && (
        <p
          className={`mt-2 text-xs ${message.type === 'success' ? 'text-green-400' : 'text-red-400'}`}
          data-testid={`upload-${message.type}`}
        >
          {t(message.key)}
        </p>
      )}
    </div>
  )
}
