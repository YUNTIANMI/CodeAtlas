import { request } from './http'
import type {
  InviteMemberRequest,
  ProjectMemberVO,
  UpdateMemberRoleRequest,
} from '../types/api'

export const memberApi = {
  list: (projectId: number) =>
    request<ProjectMemberVO[]>({ url: `/api/v1/projects/${projectId}/members`, method: 'GET' }),

  invite: (projectId: number, payload: InviteMemberRequest) =>
    request<ProjectMemberVO>({
      url: `/api/v1/projects/${projectId}/members`,
      method: 'POST',
      data: payload,
    }),

  updateRole: (projectId: number, userId: number, payload: UpdateMemberRoleRequest) =>
    request<ProjectMemberVO>({
      url: `/api/v1/projects/${projectId}/members/${userId}`,
      method: 'PUT',
      data: payload,
    }),

  remove: (projectId: number, userId: number) =>
    request<void>({
      url: `/api/v1/projects/${projectId}/members/${userId}`,
      method: 'DELETE',
    }),
}
