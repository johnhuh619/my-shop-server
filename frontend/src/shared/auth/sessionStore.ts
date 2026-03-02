import { parseRoleFromToken, parseUserIdFromToken } from '@/shared/auth/jwt'
import type { LoginResponse, UserRole } from '@/shared/types/domain'

const REFRESH_TOKEN_KEY = 'minishop.refreshToken'

export interface SessionState {
  accessToken: string | null
  refreshToken: string | null
  userId: number | null
  role: UserRole | null
  email: string | null
  name: string | null
}

type SessionListener = (session: SessionState) => void

const initialRefreshToken = (() => {
  if (typeof window === 'undefined') {
    return null
  }

  return localStorage.getItem(REFRESH_TOKEN_KEY)
})()

let sessionState: SessionState = {
  accessToken: null,
  refreshToken: initialRefreshToken,
  userId: null,
  role: null,
  email: null,
  name: null,
}

const listeners = new Set<SessionListener>()

const notifyListeners = () => {
  listeners.forEach((listener) => listener(sessionState))
}

const saveRefreshToken = (refreshToken: string | null) => {
  if (typeof window === 'undefined') {
    return
  }

  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    return
  }

  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export const sessionStore = {
  getState: () => sessionState,

  subscribe: (listener: SessionListener) => {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },

  setFromLogin: (payload: LoginResponse) => {
    sessionState = {
      accessToken: payload.accessToken,
      refreshToken: payload.refreshToken,
      userId: payload.userId,
      role: parseRoleFromToken(payload.accessToken),
      email: payload.email,
      name: payload.name,
    }
    saveRefreshToken(payload.refreshToken)
    notifyListeners()
  },

  setAccessToken: (accessToken: string) => {
    sessionState = {
      ...sessionState,
      accessToken,
      userId: parseUserIdFromToken(accessToken),
      role: parseRoleFromToken(accessToken),
    }
    notifyListeners()
  },

  setRefreshToken: (refreshToken: string | null) => {
    sessionState = {
      ...sessionState,
      refreshToken,
    }
    saveRefreshToken(refreshToken)
    notifyListeners()
  },

  clear: () => {
    sessionState = {
      accessToken: null,
      refreshToken: null,
      userId: null,
      role: null,
      email: null,
      name: null,
    }
    saveRefreshToken(null)
    notifyListeners()
  },
}

