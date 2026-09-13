import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { authApi } from '../services/auth'
import { clearToken, getToken, setToken, setUnauthorizedHandler } from '../services/http'
import type { LoginRequest, RegisterRequest, UserVO } from '../types/api'

interface AuthContextValue {
  user: UserVO | null
  /** 首次恢复会话中（用于避免刷新页面时闪现登录页）。 */
  initializing: boolean
  login: (payload: LoginRequest) => Promise<UserVO>
  register: (payload: RegisterRequest) => Promise<UserVO>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserVO | null>(null)
  const [initializing, setInitializing] = useState(true)

  // 任意请求返回 401 时清空登录态，交由 ProtectedRoute 跳转登录页
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null)
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  // 页面刷新后用本地 token 恢复会话
  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
      if (!getToken()) {
        setInitializing(false)
        return
      }
      try {
        const me = await authApi.me()
        if (!cancelled) {
          setUser(me)
        }
      } catch {
        clearToken()
      } finally {
        if (!cancelled) {
          setInitializing(false)
        }
      }
    }

    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (payload: LoginRequest) => {
    const result = await authApi.login(payload)
    setToken(result.token)
    setUser(result.user)
    return result.user
  }, [])

  const register = useCallback(async (payload: RegisterRequest) => {
    return authApi.register(payload)
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // 后端不可达或 token 已失效时，仍需清理本地状态
    } finally {
      clearToken()
      setUser(null)
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, initializing, login, register, logout }),
    [user, initializing, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth 必须在 AuthProvider 内使用')
  }
  return ctx
}
