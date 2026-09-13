import { request } from './http'
import type {
  GitCommitVO,
  GitRepositoryVO,
  ImportRepoRequest,
  SyncResultVO,
} from '../types/api'

export const gitApi = {
  /** 导入仓库（只读集成）。 */
  importRepo: (projectId: number, payload: ImportRepoRequest) =>
    request<GitRepositoryVO>({
      url: `/api/v1/projects/${projectId}/git`,
      method: 'POST',
      data: payload,
    }),

  /** 同步提交记录。 */
  sync: (projectId: number, limit?: number) =>
    request<SyncResultVO>({
      url: `/api/v1/projects/${projectId}/git/sync`,
      method: 'POST',
      params: limit ? { limit } : undefined,
    }),

  commits: (projectId: number) =>
    request<GitCommitVO[]>({
      url: `/api/v1/projects/${projectId}/git/commits`,
      method: 'GET',
    }),

  commitDetail: (commitId: number) =>
    request<GitCommitVO>({ url: `/api/v1/git/commits/${commitId}`, method: 'GET' }),

  /** 生成该提交的 AI 摘要。 */
  commitSummary: (commitId: number) =>
    request<GitCommitVO>({
      url: `/api/v1/git/commits/${commitId}/summary`,
      method: 'GET',
    }),
}
