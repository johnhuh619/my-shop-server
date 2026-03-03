import { rawClient } from '@/shared/api/rawClient'
import { sessionStore } from '@/shared/auth/sessionStore'
import type { ApiResponse } from '@/shared/types/api'
import type { RefreshTokenResponse } from '@/shared/types/domain'

let refreshPromise: Promise<string> | null = null

export const refreshAccessToken = async () => {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const refreshToken = sessionStore.getState().refreshToken
      if (!refreshToken) {
        throw new Error('Refresh token is missing')
      }

      const { data } = await rawClient.post<ApiResponse<RefreshTokenResponse>>('/api/auth/refresh', {
        refreshToken,
      })

      if (!data.success || !data.data?.accessToken) {
        throw new Error(data.errorMessage || '토큰 갱신에 실패했습니다.')
      }

      sessionStore.setAccessToken(data.data.accessToken)
      return data.data.accessToken
    })().finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise
}

