import { type ReactNode } from 'react'
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
} from 'react-router-dom'
import { AuthProvider, useAuth } from '@/contexts/AuthContext'
import { MainLayout } from '@/layouts/MainLayout'
import { AdminPublishPage } from '@/pages/AdminPublish'
import { AuditPage } from '@/pages/AuditPage'
import { LoginPage } from '@/pages/LoginPage'
import { PublishPage } from '@/pages/PublishPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return children
}

function HomeRedirect() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return (
    <Navigate
      to={user.role === 'admin' ? '/audit' : '/publish'}
      replace
    />
  )
}

function AdminOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== 'admin') return <Navigate to="/publish" replace />
  return children
}

/** 大 V 专属：管理员访问 /publish 时回到审核首页 */
function KolOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== 'kol') {
    return <Navigate to={user.role === 'admin' ? '/audit' : '/'} replace />
  }
  return children
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <MainLayout />
          </RequireAuth>
        }
      >
        <Route index element={<HomeRedirect />} />
        <Route
          path="audit"
          element={
            <AdminOnly>
              <AuditPage />
            </AdminOnly>
          }
        />
        <Route
          path="admin/publish"
          element={
            <AdminOnly>
              <AdminPublishPage />
            </AdminOnly>
          }
        />
        <Route
          path="publish"
          element={
            <KolOnly>
              <PublishPage />
            </KolOnly>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}
