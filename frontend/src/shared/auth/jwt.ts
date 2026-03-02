import type { UserRole } from '@/shared/types/domain'

interface JwtPayload {
  sub?: string
  role?: UserRole
  exp?: number
}

const decodeBase64Url = (value: string) => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  return atob(padded)
}

export const parseJwtPayload = (token: string): JwtPayload | null => {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }

  try {
    const payload = decodeBase64Url(parts[1])
    return JSON.parse(payload) as JwtPayload
  } catch {
    return null
  }
}

export const parseRoleFromToken = (token: string): UserRole | null => {
  const payload = parseJwtPayload(token)
  return payload?.role ?? null
}

export const parseUserIdFromToken = (token: string): number | null => {
  const payload = parseJwtPayload(token)
  if (!payload?.sub) {
    return null
  }

  const userId = Number(payload.sub)
  return Number.isNaN(userId) ? null : userId
}

