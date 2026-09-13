import { request } from './http'
import type { ProjectRequest, ProjectVO } from '../types/api'

export const projectApi = {
  list: () => request<ProjectVO[]>({ url: '/api/v1/projects', method: 'GET' }),

  detail: (projectId: number) =>
    request<ProjectVO>({ url: `/api/v1/projects/${projectId}`, method: 'GET' }),

  create: (payload: ProjectRequest) =>
    request<ProjectVO>({ url: '/api/v1/projects', method: 'POST', data: payload }),

  update: (projectId: number, payload: ProjectRequest) =>
    request<ProjectVO>({ url: `/api/v1/projects/${projectId}`, method: 'PUT', data: payload }),

  remove: (projectId: number) =>
    request<void>({ url: `/api/v1/projects/${projectId}`, method: 'DELETE' }),
}
