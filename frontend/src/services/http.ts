import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { Result } from '../types/api'

const TOKEN_KEY = 'codeatlas.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 401 时由 AuthProvider 注册的回调，用于清理状态并跳转登录页。 */
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

/** 业务错误：由 HTTP 状态码与后端 Result.code 共同构成。 */
export class ApiError extends Error {
  readonly status?: number
  readonly code?: number

  constructor(message: string, status?: number, code?: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  // RAG 问答与 Agent 任务耗时较长，超时给足
  timeout: 120_000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Result<unknown>>) => {
    const status = error.response?.status
    const payload = error.response?.data

    if (status === 401) {
      clearToken()
      unauthorizedHandler?.()
    }

    const message =
      payload?.message && payload.message !== 'success'
        ? payload.message
        : error.message || '网络请求失败，请检查后端服务是否已启动'

    return Promise.reject(new ApiError(message, status, payload?.code))
  },
)

/**
 * 发起请求并解包统一响应体。
 * 后端在 Phase 11 后返回真实 HTTP 状态码，因此失败分支已由拦截器处理；
 * 这里额外兜底处理 HTTP 200 但 code !== 0 的情况。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<Result<T>>(config)
  const body = response.data

  if (body && typeof body.code === 'number' && body.code !== 0) {
    throw new ApiError(body.message || '请求失败', response.status, body.code)
  }

  return body?.data as T
}

export default http
