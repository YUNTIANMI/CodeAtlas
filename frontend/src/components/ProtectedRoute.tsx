import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../stores/AuthContext'
import Spinner from './Spinner'

export default function ProtectedRoute() {
  const { user, initializing } = useAuth()
  const location = useLocation()

  if (initializing) {
    return (
      <div className="page-center">
        <Spinner label="正在恢复登录状态…" />
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}
