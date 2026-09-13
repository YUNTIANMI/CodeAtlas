import { request } from './http'
import type { ReviewRequest, ReviewResultVO, ReviewSeverity } from '../types/api'

export const reviewApi = {
  /** 提交代码审查，返回本次产生的全部问题项。 */
  review: (projectId: number, payload: ReviewRequest) =>
    request<ReviewResultVO[]>({
      url: `/api/v1/projects/${projectId}/review`,
      method: 'POST',
      data: payload,
    }),

  list: (projectId: number, severity?: ReviewSeverity) =>
    request<ReviewResultVO[]>({
      url: `/api/v1/projects/${projectId}/reviews`,
      method: 'GET',
      params: severity ? { severity } : undefined,
    }),

  detail: (id: number) => request<ReviewResultVO>({ url: `/api/v1/reviews/${id}`, method: 'GET' }),
}
