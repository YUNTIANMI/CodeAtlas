import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../stores/AuthContext'
import { useToast } from '../../stores/ToastContext'

function initialsOf(name: string): string {
  return name.slice(0, 2).toUpperCase()
}

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  const displayName = user?.displayName || user?.username || '未登录'

  async function handleLogout() {
    await logout()
    toast.info('已退出登录')
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <span className="sidebar__logo">CA</span>
          <div className="sidebar__brand-text">
            <div className="sidebar__name">CodeAtlas</div>
            <div className="sidebar__slogan">AI 软件工程研发助手</div>
          </div>
        </div>

        <nav className="sidebar__nav">
          <NavLink
            to="/projects"
            className={({ isActive }) => `nav-item${isActive ? ' nav-item--active' : ''}`}
          >
            <span className="nav-item__icon" aria-hidden="true">
              ▦
            </span>
            项目空间
          </NavLink>
          <div className="sidebar__section">能力</div>
          <div className="sidebar__hint">文档问答 · 代码检索 · Code Review · Git 分析 · Agent</div>
        </nav>

        <div className="sidebar__foot">
          <div className="sidebar__user">
            <span className="avatar">{initialsOf(displayName)}</span>
            <div className="sidebar__user-info">
              <div className="sidebar__user-name">{displayName}</div>
              <div className="sidebar__user-mail">{user?.email}</div>
            </div>
          </div>
          <button type="button" className="btn btn--ghost btn--block" onClick={handleLogout}>
            退出登录
          </button>
        </div>
      </aside>

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
