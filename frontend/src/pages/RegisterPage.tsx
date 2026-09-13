import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import FormRow from '../components/FormRow'
import { ApiError } from '../services/http'
import { useAuth } from '../stores/AuthContext'
import { useToast } from '../stores/ToastContext'

export default function RegisterPage() {
  const { user, initializing, register, login } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!initializing && user) {
    return <Navigate to="/projects" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (password.length < 6) {
      setError('密码长度至少 6 位')
      return
    }
    if (password !== confirm) {
      setError('两次输入的密码不一致')
      return
    }

    setSubmitting(true)
    try {
      await register({
        username: username.trim(),
        email: email.trim(),
        password,
        displayName: displayName.trim() || undefined,
      })
      // 注册成功后直接登录，减少一次手动操作
      const logged = await login({ username: username.trim(), password })
      toast.success(`注册成功，欢迎加入 CodeAtlas，${logged.displayName || logged.username}`)
      navigate('/projects', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '注册失败，请稍后重试')
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

        <h1 className="auth-title">创建账号</h1>
        <p className="auth-desc">注册后即可创建项目，体验文档问答、代码检索与智能审查。</p>

        <form className="form" onSubmit={handleSubmit}>
          {error ? <div className="alert alert--error">{error}</div> : null}

          <FormRow label="用户名" hint="用于登录，建议字母与数字组合">
            <input
              className="input"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="请输入用户名"
              autoComplete="username"
              required
              minLength={3}
              autoFocus
            />
          </FormRow>

          <FormRow label="邮箱">
            <input
              className="input"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="name@example.com"
              autoComplete="email"
              required
            />
          </FormRow>

          <FormRow label="昵称" hint="可选，用于界面展示">
            <input
              className="input"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="如何称呼你"
              autoComplete="nickname"
            />
          </FormRow>

          <FormRow label="密码" hint="至少 6 位">
            <input
              className="input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="请设置密码"
              autoComplete="new-password"
              required
              minLength={6}
            />
          </FormRow>

          <FormRow label="确认密码">
            <input
              className="input"
              type="password"
              value={confirm}
              onChange={(event) => setConfirm(event.target.value)}
              placeholder="请再次输入密码"
              autoComplete="new-password"
              required
            />
          </FormRow>

          <button className="btn btn--primary btn--block" type="submit" disabled={submitting}>
            {submitting ? '注册中…' : '注册并登录'}
          </button>
        </form>

        <div className="auth-foot">
          已有账号？<Link to="/login">返回登录</Link>
        </div>
      </div>
    </div>
  )
}
