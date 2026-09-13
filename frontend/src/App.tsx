import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import ProtectedRoute from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import ProjectDetailPage from './pages/ProjectDetailPage'
import ProjectListPage from './pages/ProjectListPage'
import RegisterPage from './pages/RegisterPage'
import { AuthProvider } from './stores/AuthContext'
import { ToastProvider } from './stores/ToastContext'

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/" element={<Navigate to="/projects" replace />} />
              <Route path="/projects" element={<ProjectListPage />} />
              <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
            </Route>
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </ToastProvider>
  )
}
