import { request } from './http'
import type { LoginRequest, LoginResponse, RegisterRequest, UserVO } from '../types/api'

export const authApi = {
  register: (payload: RegisterRequest) =>
    request<UserVO>({ url: '/api/v1/auth/register', method: 'POST', data: payload }),

  login: (payload: LoginRequest) =>
    request<LoginResponse>({ url: '/api/v1/auth/login', method: 'POST', data: payload }),

  logout: () => request<void>({ url: '/api/v1/auth/logout', method: 'POST' }),

  me: () => request<UserVO>({ url: '/api/v1/users/me', method: 'GET' }),
}
