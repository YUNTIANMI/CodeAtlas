import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="auth-page">
      <div className="auth-card" style={{ textAlign: 'center' }}>
        <h1 className="auth-title">页面不存在</h1>
        <p className="auth-desc">你访问的地址没有对应的页面。</p>
        <Link className="btn btn--primary btn--block" to="/projects">
          返回项目空间
        </Link>
      </div>
    </div>
  )
}
