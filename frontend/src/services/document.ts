import type { AxiosProgressEvent } from 'axios'
import { request } from './http'
import type { DocumentDetailVO, DocumentVO } from '../types/api'

/** 后端 DocumentService 允许的扩展名（md / txt / pdf）。 */
export const DOCUMENT_ACCEPT = '.md,.txt,.pdf'

export const documentApi = {
  upload: (projectId: number, file: File, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    return request<DocumentVO>({
      url: `/api/v1/projects/${projectId}/documents`,
      method: 'POST',
      data: form,
      onUploadProgress: (event: AxiosProgressEvent) => {
        if (onProgress && event.total) {
          onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
        }
      },
    })
  },

  list: (projectId: number) =>
    request<DocumentVO[]>({ url: `/api/v1/projects/${projectId}/documents`, method: 'GET' }),

  search: (projectId: number, keyword: string) =>
    request<DocumentVO[]>({
      url: `/api/v1/projects/${projectId}/documents/search`,
      method: 'GET',
      params: { keyword },
    }),

  detail: (documentId: number) =>
    request<DocumentDetailVO>({ url: `/api/v1/documents/${documentId}`, method: 'GET' }),

  remove: (documentId: number) =>
    request<void>({ url: `/api/v1/documents/${documentId}`, method: 'DELETE' }),
}
