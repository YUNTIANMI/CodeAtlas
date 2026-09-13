import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import FormRow from '../components/FormRow'
import { ApiError } from '../services/http'
import { useAuth } from '../stores/AuthContext'
import { useToast } from '../stores/ToastContext'

export default function LoginPage() {
  const { user, initializing, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!initializing && user) {
    return <Navigate to="/projects" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const logged = await login({ username: username.trim(), password })
      toast.success(`欢迎回来，${logged.displayName || logged.username}`)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from && from !== '/login' ? from : '/projects', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '登录失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="auth-brand__logo">CA</span>
          <div>
            <div className="auth-brand__name">CodeAtlas</div>
            <div className="auth-brand__slogan">AI 软件工程研发助手</div>
          </div>
        </div>

        <h1 className="auth-title">登录</h1>
        <p className="auth-desc">使用你的账号进入工作空间，继续研发协作。</p>

        <form className="form" onSubmit={handleSubmit}>
          {error ? <div className="alert alert--error">{error}</div> : null}

          <FormRow label="用户名">
            <input
              className="input"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="请输入用户名"
              autoComplete="username"
              autoFocus
              required
            />
          </FormRow>

          <FormRow label="密码">
            <input
              className="input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="请输入密码"
              autoComplete="current-password"
              required
            />
          </FormRow>

          <button className="btn btn--primary btn--block" type="submit" disabled={submitting}>
            {submitting ? '登录中…' : '登录'}
          </button>
        </form>

        <div className="auth-foot">
          还没有账号？<Link to="/register">立即注册</Link>
        </div>

        <div className="demo-tip">
          安全提示：同一账号连续登录失败达到阈值后会触发限流保护（默认 5 次锁定 15 分钟）。
          登录成功后 JWT 有效期默认 2 小时，退出登录后 Token 立即失效。
        </div>
      </div>
    </div>
  )
}
