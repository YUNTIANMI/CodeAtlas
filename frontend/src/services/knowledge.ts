import { request } from './http'
import type {
  AskAnswerVO,
  BuildResultVO,
  IndexStatusVO,
  QueryRequest,
  SearchResultVO,
} from '../types/api'

export const knowledgeApi = {
  /** 构建/重建知识库索引。 */
  build: (projectId: number) =>
    request<BuildResultVO>({
      url: `/api/v1/projects/${projectId}/knowledge/build`,
      method: 'POST',
    }),

  status: (projectId: number) =>
    request<IndexStatusVO>({
      url: `/api/v1/projects/${projectId}/knowledge/status`,
      method: 'GET',
    }),

  /** 纯向量检索，返回命中的知识片段。 */
  search: (projectId: number, payload: QueryRequest) =>
    request<SearchResultVO[]>({
      url: `/api/v1/projects/${projectId}/knowledge/search`,
      method: 'POST',
      data: payload,
    }),

  /** RAG 问答，返回答案与引用来源。 */
  ask: (projectId: number, payload: QueryRequest) =>
    request<AskAnswerVO>({
      url: `/api/v1/projects/${projectId}/knowledge/ask`,
      method: 'POST',
      data: payload,
    }),

  /** 清空项目知识库。 */
  clear: (projectId: number) =>
    request<void>({ url: `/api/v1/projects/${projectId}/knowledge`, method: 'DELETE' }),
}
