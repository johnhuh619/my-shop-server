import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/shared/auth/useAuth'

export const RequireAuth = ({ children }: { children: ReactNode }) => {
  const auth = useAuth()
  const location = useLocation()

  if (auth.isBootstrapping) {
    return null
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" state={{ redirectTo: location.pathname }} replace />
  }

  return <>{children}</>
}
