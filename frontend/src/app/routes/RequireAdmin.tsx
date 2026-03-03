import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/shared/auth/useAuth'

export const RequireAdmin = ({ children }: { children: ReactNode }) => {
  const auth = useAuth()

  if (auth.isBootstrapping) {
    return null
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (!auth.isAdmin) {
    return <Navigate to="/products" replace />
  }

  return <>{children}</>
}
