import type { AxiosProgressEvent } from 'axios'
import { request } from './http'
import type { CodeFileDetailVO, CodeFileVO, StructureNode } from '../types/api'
import { CODE_EXTENSIONS } from '../utils/codeUpload'

/** <input accept> 取值，与后端 FileParser 支持的源码扩展名保持一致。 */
export const CODE_ACCEPT = CODE_EXTENSIONS.map((ext) => `.${ext}`).join(',')

export const CODE_LANGUAGES = ['java', 'cpp', 'py', 'js', 'ts'] as const

export interface CodeUploadOptions {
  /** 入库相对路径，形如 src/main/java/UserService.java；留空则仅用文件名。 */
  path?: string
  /** 显式指定文件名，避免部分浏览器把相对路径塞进 multipart 的 filename。 */
  fileName?: string
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

export const codeApi = {
  upload: (projectId: number, file: File, options: CodeUploadOptions = {}) => {
    const form = new FormData()
    form.append('file', file, options.fileName || file.name)
    if (options.path) {
      form.append('path', options.path)
    }
    return request<CodeFileVO>({
      url: `/api/v1/projects/${projectId}/code`,
      method: 'POST',
      data: form,
      signal: options.signal,
      onUploadProgress: (event: AxiosProgressEvent) => {
        if (options.onProgress && event.total) {
          options.onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
        }
      },
    })
  },

  list: (projectId: number, language?: string) =>
    request<CodeFileVO[]>({
      url: `/api/v1/projects/${projectId}/code`,
      method: 'GET',
      params: language ? { language } : undefined,
    }),

  structure: (projectId: number) =>
    request<StructureNode>({
      url: `/api/v1/projects/${projectId}/code/structure`,
      method: 'GET',
    }),

  detail: (fileId: number) =>
    request<CodeFileDetailVO>({ url: `/api/v1/code/${fileId}`, method: 'GET' }),

  remove: (fileId: number) => request<void>({ url: `/api/v1/code/${fileId}`, method: 'DELETE' }),
}
