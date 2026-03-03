/* eslint-disable react-refresh/only-export-components */
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { authApi } from '@/features/auth/api/authApi'
import { refreshAccessToken } from '@/shared/auth/refreshAccessToken'
import { sessionStore, type SessionState } from '@/shared/auth/sessionStore'
import type { LoginRequest, RegisterRequest } from '@/shared/types/domain'

interface AuthContextValue extends SessionState {
  isAuthenticated: boolean
  isAdmin: boolean
  isBootstrapping: boolean
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export const AuthProvider = ({ children }: PropsWithChildren) => {
  const [session, setSession] = useState<SessionState>(sessionStore.getState())
  const [isBootstrapping, setIsBootstrapping] = useState(true)

  useEffect(() => sessionStore.subscribe(setSession), [])

  useEffect(() => {
    const bootstrap = async () => {
      const { refreshToken, accessToken } = sessionStore.getState()

      if (refreshToken && !accessToken) {
        try {
          await refreshAccessToken()
        } catch {
          sessionStore.clear()
        }
      }

      setIsBootstrapping(false)
    }

    void bootstrap()
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      ...session,
      isAuthenticated: !!session.accessToken,
      isAdmin: session.role === 'ADMIN',
      isBootstrapping,
      login: async (payload) => {
        const loginResponse = await authApi.login(payload)
        sessionStore.setFromLogin(loginResponse)
      },
      register: async (payload) => {
        await authApi.register(payload)
      },
      logout: async () => {
        try {
          await authApi.logout(sessionStore.getState().refreshToken)
        } finally {
          sessionStore.clear()
        }
      },
    }),
    [isBootstrapping, session],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }

  return context
}


