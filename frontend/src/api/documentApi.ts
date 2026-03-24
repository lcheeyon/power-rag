import { apiClient } from './client'

export interface DocumentUploadResponse {
  documentId:  string
  fileName:    string
  chunkCount:  number
  status:      string
  uploadedAt:  string
}

export interface DocumentItem {
  id:          string
  fileName:    string
  fileType:    string
  fileSize:    number
  description: string
  status:      string
  chunkCount:  number
  createdAt:   string
}

export async function uploadDocument(
  file: File,
  description?: string,
): Promise<DocumentUploadResponse> {
  const form = new FormData()
  form.append('file', file)
  if (description) form.append('description', description)
  const res = await apiClient.post<DocumentUploadResponse>('/documents/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}

export async function listDocuments(): Promise<DocumentItem[]> {
  const res = await apiClient.get<DocumentItem[]>('/documents')
  return res.data
}

export async function deleteDocument(id: string): Promise<void> {
  await apiClient.delete(`/documents/${id}`)
}
