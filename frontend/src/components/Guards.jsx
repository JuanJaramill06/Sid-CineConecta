import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { useProfile } from '../context/ProfileContext.jsx'

/**
 * Route guards. They only shape navigation: the server checks the token, the role and the
 * profile on every request (RNF2, RNF3), so hiding a route is never the security boundary.
 */
export function RequireClient() {
  const { isClient } = useAuth()
  const location = useLocation()
  return isClient ? <Outlet /> : <Navigate to="/login" replace state={{ from: location }} />
}

export function RequireProfile() {
  const { isClient } = useAuth()
  const { profile } = useProfile()
  const location = useLocation()
  if (!isClient) return <Navigate to="/login" replace state={{ from: location }} />
  if (!profile) return <Navigate to="/select-profile" replace state={{ from: location }} />
  return <Outlet />
}

export function RequireAdmin() {
  const { isAdmin } = useAuth()
  return isAdmin ? <Outlet /> : <Navigate to="/admin/login" replace />
}
